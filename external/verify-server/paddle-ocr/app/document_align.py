"""영역-크롭 OCR: (옵션) 원근보정 → 필드 박스 크롭 → OCR → 파서 정규화.

메인 백엔드가 템플릿(영역맵)을 요청 바디로 전달 → paddle-ocr 는 stateless 유지.
크롭마다 ocr_engine.run + reading_order.reconstruct 를 재사용하고, key 별 parser 로 정제한다.
"""

import re

import cv2
import numpy as np

from . import ocr_engine
from .reading_order import reconstruct


# ─── parser (python 정규식 정규화) ──────────────────────────
def _parse_text(t):
    return t.strip()


def _parse_vehicle_no(t):
    m = re.search(r"\d{2,3}[가-힣]\d{4}", t)
    return m.group(0) if m else t.strip()


# 영업용 번호판 지역명(2한글) 접두 — 실제 시도명일 때만 붙인다(OCR 오독 '긴북' 등은 버림).
_PLATE_REGIONS = frozenset((
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종",
    "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주"))


def _parse_plate(t):
    # 번호판: 코어(83사1725)는 항상, 지역명 접두는 유효 시도명일 때만 → '경기99사9489'·'전북83사1725'.
    m = re.search(r"([가-힣]{2})?\s*(\d{2,3}\s*[가-힣]\s*\d{4})", t)
    if not m:
        return t.strip()
    core = re.sub(r"\s", "", m.group(2))
    region = m.group(1)
    return region + core if region in _PLATE_REGIONS else core


def _parse_model(t):
    # 차명은 한 단어 — OCR 이 알파벳/숫자 사이에 넣은 공백까지 제거해 '호룡SKY4504N고소작업차'.
    return re.sub(r"\s+", "", t).strip()


def _parse_year(t):
    m = re.search(r"20\d{2}([-./]\d{1,2})?", t)
    return m.group(0) if m else t.strip()


_DATE_RE = r"\d{4}[-./]\d{1,2}[-./]\d{1,2}"


def _norm_date(s):
    parts = re.split(r"[-./]", s)
    if len(parts) == 3:
        y, m, d = parts
        return f"{int(y):04d}-{int(m):02d}-{int(d):02d}"
    return s


def _parse_date(t):
    m = re.search(_DATE_RE, t)
    return _norm_date(m.group(0)) if m else t.strip()


def _parse_date_range_end(t):
    ms = re.findall(_DATE_RE, t)
    return _norm_date(ms[-1]) if ms else t.strip()


def _parse_biz_no(t):
    m = re.search(r"\d{3}-?\d{2}-?\d{5}", t)
    return m.group(0) if m else t.strip()


_PARSERS = {
    "text": _parse_text,
    "vehicle_no": _parse_vehicle_no,
    "plate": _parse_plate,
    "model": _parse_model,
    "year": _parse_year,
    "date": _parse_date,
    "date_range_end": _parse_date_range_end,
    "biz_no": _parse_biz_no,
}


# ─── 밴드 모드(kind) — 넉넉한 영역 + 값 패턴으로 위치무관 추출 ──────────
# 고정영역은 손정렬 코너 편차에 박스가 밀려 오추출 → 라벨 옆이 아니라 '값의 패턴'으로 찾는다.
_LABELWORDS = re.compile(r"등록번호|차명|차대|최초|형식|차종|용도|원동기|번호|본거지|성명|주소|사용|제원|검사|구분")
# 성명 밴드에서 값이 아닌 라벨어(합쳐 인식될 때) 배제용.
_NAMELABELS = re.compile(r"성명|이름|성별|직위|직급")


def _enhance_for_ocr(bgr):
    """스캔앱식 보정: 그레이스케일 → CLAHE 대비보정 → 언샵(선명화). OCR 정확도·미리보기 가독성↑."""
    g = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    g = cv2.createCLAHE(2.5, (8, 8)).apply(g)
    blur = cv2.GaussianBlur(g, (0, 0), 3)
    g = cv2.addWeighted(g, 1.6, blur, -0.6, 0)
    return cv2.cvtColor(g, cv2.COLOR_GRAY2BGR)


def _pick_value(texts, kind):
    """밴드 안 OCR 텍스트들에서 kind 패턴에 맞는 값을 고른다."""
    if kind == "plate":
        for t in texts:
            # '구 등록번호'(옛 번호)는 현재 등록번호와 동일 패턴 → 라벨 '구등' 서명이 붙은 텍스트는 배제.
            if re.search(r"구\s*등", t):
                continue
            m = re.search(r"\d{2,3}\s*[가-힣]\s*\d{4}", t)
            if m:
                return m.group(0).replace(" ", "")
    elif kind == "vin":
        for t in texts:
            s = re.sub(r"\s", "", t)
            m = re.search(r"[A-Z0-9]{13,20}", s)
            if m and re.search(r"[A-Z]", m.group(0)) and re.search(r"\d", m.group(0)):
                return m.group(0)
    elif kind == "year":
        for t in texts:
            m = re.search(r"(19|20)\d{2}", re.sub(r"\s", "", t))
            if m:
                return m.group(0)
    elif kind == "date":
        for t in texts:
            m = re.search(r"(19|20)\d{2}\s*[.\-]\s*\d{1,2}\s*[.\-]\s*\d{1,2}", t)
            if m:
                return re.sub(r"\s", "", m.group(0))
    elif kind == "hangul":
        # 모델명(차명)은 한글 위주. '제 202405-003179' 같은 숫자 위주 텍스트 배제 위해 한글 3자 이상 요구.
        # ★번호판(전북83사1725: 전·북·사=한글3자 + 긴문자열이라 max-len 에 걸림)은 명시 배제 —
        #   차명이 짧은 차량에서 차량번호가 model 로 새는 버그 방지.
        cands = [t for t in texts
                 if len(re.findall(r"[가-힣]", t)) >= 3
                 and not _LABELWORDS.search(t)
                 and not re.search(r"\d{2,3}\s*[가-힣]\s*\d{3,4}", t)]
        if cands:
            return max(cands, key=len)
    elif kind == "alnum":
        # 형식/모델·차대 코드(D30S-7, FDA0R-1000-01154). 영대문자·숫자·구분자(-./) 3자+ & 숫자 1개+.
        # 숫자 요구로 'S.OD' 같은 인접 잡음(글자만) 오추출 방지.
        for t in texts:
            if _LABELWORDS.search(t):
                continue
            m = re.search(r"[A-Z0-9][A-Z0-9\-./]{2,}", re.sub(r"\s", "", t))
            if m and re.search(r"\d", m.group(0)):
                return m.group(0)
    elif kind == "name":
        # 성명(한글 2~4자). 번호줄(숫자 포함)·라벨어 배제 후 최장 후보(3자 이름 우선).
        cands = []
        for t in texts:
            if re.search(r"\d", t) or _NAMELABELS.search(t):
                continue
            cands += [w for w in re.findall(r"[가-힣]{2,4}", t) if not _NAMELABELS.search(w)]
        if cands:
            return max(cands, key=len)
    elif kind == "licno":
        # 면허/자격/등록 번호. 하이픈으로 이어진 숫자군 2개+(선택적 2자 한글 접두: 조종사 '울산01-…').
        # 주민번호(하이픈 1개)·날짜(밴드에서 공간적으로 배제)와 구분됨.
        for t in texts:
            m = re.search(r"(?:[가-힣]{2})?\d{1,4}(?:-\d{1,6}){2,}", re.sub(r"\s", "", t))
            if m:
                return m.group(0)
    elif kind == "date_end":
        # 유효기간/적성검사 등 여러 날짜 중 가장 늦은(만료) 날짜.
        # OCR 이 '.'을 ',' 또는 ':'로 오인하는 경우까지 구분자로 허용 후 '.'로 정규화.
        found = [re.sub(r"[,:]", ".", re.sub(r"\s", "", m.group(0))) for t in texts
                 for m in re.finditer(r"(?:19|20)\d{2}\s*[.,:\-]\s*\d{1,2}\s*[.,:\-]\s*\d{1,2}", t)]
        if found:
            return max(found, key=lambda s: tuple(int(x) for x in re.split(r"[.\-]", s)[:3]))
    return ""


def _order_corners(pts):
    """4점 → [TL, TR, BR, BL] 순 (합 최소=TL, 합 최대=BR, y-x 최소=TR, y-x 최대=BL)."""
    pts = np.array(pts, dtype="float32").reshape(4, 2)
    s = pts.sum(axis=1)
    d = (pts[:, 1] - pts[:, 0])
    return np.array([pts[np.argmin(s)], pts[np.argmin(d)],
                     pts[np.argmax(s)], pts[np.argmax(d)]], dtype="float32")


def _warp(img, corners, w, h):
    src = _order_corners(corners)
    dst = np.array([[0, 0], [w, 0], [w, h], [0, h]], dtype="float32")
    m = cv2.getPerspectiveTransform(src, dst)
    return cv2.warpPerspective(img, m, (w, h))


# ─── 회전 자동보정 (자연비율 warp → 방향감지 → upright) ──────────────
def _natural_warp(img, corners):
    """코너 변길이(W×H) 그대로 원근보정 — 템플릿 aspect 강제 없이 de-skew 만.
    (portrait 강제로 landscape 사진을 늘려 추출 전멸시키던 문제를 없앤다.)"""
    src = _order_corners(corners)
    tl, tr, br, bl = src
    w = max(np.linalg.norm(tr - tl), np.linalg.norm(br - bl))
    h = max(np.linalg.norm(bl - tl), np.linalg.norm(br - tr))
    w = max(int(round(float(w))), 1)
    h = max(int(round(float(h))), 1)
    dst = np.array([[0, 0], [w, 0], [w, h], [0, h]], dtype="float32")
    m = cv2.getPerspectiveTransform(src, dst)
    return cv2.warpPerspective(img, m, (w, h))


def _resize_long(img, target):
    """긴 변을 target px 로 (가로세로비 유지)."""
    h, w = img.shape[:2]
    s = float(target) / float(max(h, w))
    if abs(s - 1.0) < 1e-3:
        return img
    return cv2.resize(img, (max(1, int(round(w * s))), max(1, int(round(h * s)))))


def _hangul_len(s):
    return sum(1 for ch in s if "가" <= ch <= "힣")


def _auto_orient(img, probe_long=280, early=3.0):
    """90°/180°/270° 로 눕거나 뒤집힌 문서를 upright 로.
    자연비율 warp 결과의 가로세로비로 후보를 좁히고(landscape=90/270, portrait=0/180),
    축소본(≈probe_long)만 소수 방향 OCR 해 한글 confidence 합이 최대인 방향을 고른다.
    정방향(0) 을 먼저 보고 명백히 upright 면(≥early) 나머지 OCR 을 생략(속도) — 뒤집힌 방향은
    한글 인식이 0 근처라 오판 없음. 정방향 문서는 k=0 으로 뽑혀 무영향(회귀 없음)."""
    h, w = img.shape[:2]
    if w > h * 1.15:        # landscape quad + portrait 문서 → 90/270 회전
        cands = (1, 3)
    elif h > w * 1.15:      # portrait → 0/180 (정방향 0 을 먼저)
        cands = (0, 2)
    else:                   # 정사각 근처(코너 불명확) → 4방향 다 확인
        cands = (0, 1, 2, 3)
    small = _resize_long(img, probe_long)
    best_k, best = cands[0], -1.0
    for k in cands:
        probe = np.ascontiguousarray(np.rot90(small, k))
        dets = ocr_engine.run(probe)
        score = sum(d.get("score", 0.0) * _hangul_len(d.get("text", "")) for d in dets)
        if score > best:
            best, best_k = score, k
        if best >= early:   # 명백히 upright → 남은 방향 생략
            break
    return img if best_k == 0 else np.ascontiguousarray(np.rot90(img, best_k))


# ─── 라벨기반 추출 (라벨을 찾아 그 옆 값을 읽음 — 서식 버전차에 강건) ──────
_CIRCLED = set("①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳")
# 값의 오른쪽 경계(다음 필드 라벨) 판정용 라벨어 — 신·구 서식 공통 자동차등록증 필드.
_BOUNDARY_WORDS = ("자동차등록번호", "차종", "용도", "차명", "형식", "원동기", "차대",
                   "사용본거지", "성명", "명칭", "주민", "법인", "최초등록", "생년월일",
                   "주소", "제원", "정격", "길이", "높이", "너비")


def _hangul_str(s):
    return "".join(ch for ch in s if "가" <= ch <= "힣")


def _is_label_boundary(text):
    """값 오른쪽 끝: 원숫자(①②…)로 시작하거나 라벨어로 시작하는 토큰이면 다음 필드로 본다.
    (OCR 이 '형식'→'행식' 처럼 라벨을 오독해도 원숫자 접두로 경계를 잡는다.)"""
    t = text.strip()
    if t and t[0] in _CIRCLED:
        return True
    h = _hangul_str(t)
    return bool(h) and any(h.startswith(w) for w in _BOUNDARY_WORDS)


def _group_lines(dets):
    """OCR 검출들을 reading_order 와 같은 cy-밴드로 묶어 라인별(왼→오) 토큰 리스트로."""
    items = []
    for d in dets:
        box = d.get("box") or []
        if not box:
            continue
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        items.append({"text": d.get("text", ""), "score": d.get("score", 0.0),
                      "x0": min(xs), "cy": (min(ys) + max(ys)) / 2.0,
                      "h": max(1.0, float(max(ys) - min(ys)))})
    items.sort(key=lambda it: it["cy"])
    bands = []
    for it in items:
        if bands:
            last = bands[-1]
            ref = last["cy_sum"] / last["n"]
            if abs(it["cy"] - ref) <= 0.5 * max(it["h"], last["h_max"]):
                last["items"].append(it)
                last["cy_sum"] += it["cy"]
                last["n"] += 1
                last["h_max"] = max(last["h_max"], it["h"])
                continue
        bands.append({"items": [it], "cy_sum": it["cy"], "n": 1, "h_max": it["h"]})
    return [sorted(b["items"], key=lambda it: it["x0"]) for b in bands]


def _fuzzy_find(concat, label):
    """concat 안에서 label 을 퍼지 매칭(길이 4+ 는 1글자 오독 허용) → 끝 인덱스, 없으면 -1.
    (코너 편차로 warp 가 흐려져 '등록번호'→'등목번호' 처럼 1글자 오독돼도 잡는다.
     짧은 라벨은 오탐 방지 위해 정확매칭.)"""
    n, L = len(label), len(concat)
    if n == 0 or L < n:
        return -1
    maxd = 1 if n >= 4 else 0
    for i in range(L - n + 1):
        d = sum(1 for a, b in zip(concat[i:i + n], label) if a != b)
        if d <= maxd:
            return i + n - 1
    return -1


def _substantial(t):
    """값 토큰인지(잡음 아님): 공백제거 3자+ & (한글 2자+ 또는 숫자 포함)."""
    s = re.sub(r"\s", "", t)
    return len(s) >= 3 and (_hangul_len(t) >= 2 or bool(re.search(r"\d", t)))


def _extract_label(lines, field):
    """라벨을 찾아 그 옆 값을 반환. return (value_text, matched_line_text, avg_score).

    두 모드:
      - left=true : 행의 '맨 왼쪽 한글토큰'이 라벨과 정확히 일치(왼열 라벨). 값=라벨조각 소비 후
        첫 실값 토큰(first). 차명처럼 '명'이 떨어져나가거나(오독) 짧은 라벨의 퍼지 오탐을 피한다.
      - 기본     : 행 한글concat 에서 라벨을 퍼지로 찾고, 라벨 오른쪽~다음 라벨 전까지를 값으로.
        라벨·값이 한 토큰에 붙어도(예: '최초등록일: 2023년…') 라벨 이후 잔여문자를 값으로 자른다.
    """
    labels = field.get("label") or []
    excludes = field.get("exclude") or []
    for line in lines:
        toks = [it["text"] for it in line]
        avg = sum(it["score"] for it in line) / len(line) if line else 0.0
        if field.get("left"):
            lead = next((t for t in toks if _hangul_str(t)), None)
            if lead is None or _hangul_str(lead) not in labels:
                continue
            lset = set("".join(labels))
            i = 0                                   # 라벨조각(한글 ⊆ 라벨) / 잡음 토큰 소비
            while i < len(toks) and (not _hangul_str(toks[i]) or set(_hangul_str(toks[i])) <= lset):
                i += 1
            value = next((t for t in toks[i:] if _substantial(t)), "").strip()
            return value, " ".join(toks), avg
        concat = ""            # 한글만 이어붙인 문자열(라벨 매칭용)
        pmap = []              # pmap[k] = (토큰idx, 토큰내 문자idx)
        for i, t in enumerate(toks):
            for j, ch in enumerate(t):
                if "가" <= ch <= "힣":
                    concat += ch
                    pmap.append((i, j))
        if any(e in concat for e in excludes):
            continue
        end = -1
        for s in labels:
            end = _fuzzy_find(concat, s)
            if end >= 0:
                break
        if end < 0:
            continue
        ti, cj = pmap[end]                          # 라벨 마지막 글자가 있는 토큰/위치
        pieces = [toks[ti][cj + 1:]]                # 그 토큰의 라벨 이후 잔여(merged 대비)
        for t in toks[ti + 1:]:
            if _is_label_boundary(t):
                break
            pieces.append(t)
        value = " ".join(x for x in pieces if x.strip()).strip()
        return value, " ".join(toks), avg
    return "", "", 0.0


def extract_regions(img, template, corners=None, return_warped=False):
    """img(BGR ndarray) + template(dict) → {aligned, fields, regions, warped_image_base64?}.

    corners(4점) 있으면 warp. template.auto_orient 이면 자연비율 warp→회전 자동보정(upright)
    후 라벨기반 추출, 아니면 기존처럼 aspect(w,h) 로 강제 warp 후 밴드/박스 추출.
    """
    aspect = template.get("aspect") or {}
    aligned = False
    orig = img  # 표시/저장 미리보기용 — 강제 aspect 왜곡 없는 '자연 비율' warp 원본
    if corners:
        if template.get("auto_orient"):
            # 자연비율 warp 로 de-skew → 회전 자동보정(upright) → OCR 용 크기로 리스케일.
            img = _auto_orient(_natural_warp(img, corners))
            img = _resize_long(img, int(template.get("ocr_long") or 1600))
        else:
            w = int(aspect.get("w") or img.shape[1])
            h = int(aspect.get("h") or img.shape[0])
            img = _warp(img, corners, w, h)
        aligned = True
    h, w = int(img.shape[0]), int(img.shape[1])

    # 밴드(kind)/라벨 필드가 있으면 warp 를 스캔앱식 보정 → OCR 정확도·미리보기 가독성↑.
    has_band = any(f.get("kind") for f in template.get("fields", []))
    has_label = any(f.get("label") for f in template.get("fields", []))
    enhanced = _enhance_for_ocr(img) if (has_band or has_label) else None

    # 라벨 모드: upright 이미지(넉넉밴드 크롭)를 1회 OCR → 라인 그룹핑(재사용).
    label_lines = None
    if has_label:
        cy = template.get("label_crop") or [0.0, 1.0]
        ly0 = max(0, min(int(round(cy[0] * h)), h - 1))
        ly1 = max(ly0 + 1, min(int(round(cy[1] * h)), h))
        src = enhanced if enhanced is not None else img
        label_lines = _group_lines(ocr_engine.run(src[ly0:ly1]))

    fields = {}
    regions = []
    for f in template.get("fields", []):
        key = f.get("key")
        if f.get("label"):
            # 라벨 모드: 박스 없이 라벨을 찾아 그 옆 값을 읽는다(서식 버전차에 강건).
            value, line_text, score = _extract_label(label_lines, f)
            parser = _PARSERS.get(f.get("parser") or "text", _parse_text)
            fields[key] = parser(value)
            regions.append({"key": key, "text": line_text, "score": round(score, 4)})
            continue
        box = f.get("box") or [0, 0, 0, 0]
        x0 = max(0, min(int(round(box[0] * w)), w - 1))
        y0 = max(0, min(int(round(box[1] * h)), h - 1))
        x1 = max(x0 + 1, min(int(round((box[0] + box[2]) * w)), w))
        y1 = max(y0 + 1, min(int(round((box[1] + box[3]) * h)), h))
        kind = f.get("kind")
        if kind:
            # 밴드 모드: 넉넉한 영역을 보정본에서 OCR → 값 패턴으로 추출 (코너 편차에 강건).
            crop = enhanced[y0:y1, x0:x1]
            dets = ocr_engine.run(crop)
            texts = [d.get("text", "") for d in dets]
            fields[key] = _pick_value(texts, kind)
            score = sum(d["score"] for d in dets) / len(dets) if dets else 0.0
            regions.append({"key": key, "text": " | ".join(texts), "score": round(score, 4)})
        else:
            crop = img[y0:y1, x0:x1]
            dets = ocr_engine.run(crop)
            text, _ = reconstruct(dets)
            parser = _PARSERS.get(f.get("parser") or "text", _parse_text)
            fields[key] = parser(text)
            score = sum(d["score"] for d in dets) / len(dets) if dets else 0.0
            regions.append({"key": key, "text": text, "score": round(score, 4)})

    result = {"aligned": aligned, "fields": fields, "regions": regions}
    if return_warped:
        # 표시/저장 미리보기: 컬러 + '자연 비율'(강제 aspect 로 가로 카드가 세로로 늘어나는 왜곡 방지).
        #   auto_orient 는 이미 자연비율이라 img 그대로, aspect 모드는 코너로 자연-warp 를 따로 만든다.
        #   ※OCR 은 위 img(템플릿 aspect)로 이미 끝났으므로 무관. 마스킹도 컬러라야 검출됨.
        preview = _natural_warp(orig, corners) if (corners and not template.get("auto_orient")) else img
        ok, buf = cv2.imencode(".png", preview)
        if ok:
            import base64
            result["warped_image_base64"] = base64.b64encode(buf.tobytes()).decode("ascii")
    return result


def detect_corners(img):
    """Phase 2: 문서 4모서리 추정.

    (1) Otsu 밝은-종이 분리(어두운 바닥 위 문서) + (2) Canny 최대 사각형 후보 중
    '가장 큰 사각형'을 골라 내부 표 테두리(작은 사각형) 오검출을 피한다.
    실패 시 이미지 꼭짓점 폴백. return: {detected, corners:[[x,y]x4], image_size:[w,h]}.
    자동은 best-effort — FE 4모서리 드래그로 사용자가 최종 보정.
    """
    h, w = int(img.shape[0]), int(img.shape[1])
    fallback = [[0, 0], [w, 0], [w, h], [0, h]]

    def _quad(c):
        peri = cv2.arcLength(c, True)
        for eps in (0.02, 0.03, 0.05, 0.08):
            ap = cv2.approxPolyDP(c, eps * peri, True)
            if len(ap) == 4:
                return ap.reshape(4, 2).astype("float32")
        return cv2.boxPoints(cv2.minAreaRect(c)).astype("float32")

    candidates = []
    try:
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        blur = cv2.GaussianBlur(gray, (7, 7), 0)
        # (1) 밝은 종이 영역(어두운 바닥 위 문서) — 내부 표 테두리 대신 종이 전체를 잡음
        _, th = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        ks = max(9, (w + h) // 80)
        k = cv2.getStructuringElement(cv2.MORPH_RECT, (ks, ks))
        th = cv2.morphologyEx(th, cv2.MORPH_CLOSE, k, iterations=2)
        th = cv2.morphologyEx(th, cv2.MORPH_OPEN, k)
        cnts, _ = cv2.findContours(th, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if cnts:
            c = max(cnts, key=cv2.contourArea)
            if 0.25 * w * h < cv2.contourArea(c) <= 0.999 * w * h:
                candidates.append(_quad(c))
        # (2) Canny 최대 사각형(밝은 배경 등 폴백)
        edged = cv2.dilate(cv2.Canny(blur, 50, 150), np.ones((3, 3), np.uint8))
        cnts2, _ = cv2.findContours(edged, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        for c in sorted(cnts2, key=cv2.contourArea, reverse=True)[:6]:
            ap = cv2.approxPolyDP(c, 0.02 * cv2.arcLength(c, True), True)
            if len(ap) == 4 and cv2.contourArea(c) > 0.25 * w * h:
                candidates.append(ap.reshape(4, 2).astype("float32"))
                break
    except Exception:
        pass

    # 가장 큰 사각형 선택(내부 표 테두리 회피)
    best, best_area = None, 0.25 * w * h
    for q in candidates:
        a = cv2.contourArea(q.astype("float32"))
        if a > best_area:
            best, best_area = q, a
    if best is not None:
        return {"detected": True, "corners": _order_corners(best).tolist(), "image_size": [w, h]}
    return {"detected": False, "corners": fallback, "image_size": [w, h]}
