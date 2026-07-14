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
    "year": _parse_year,
    "date": _parse_date,
    "date_range_end": _parse_date_range_end,
    "biz_no": _parse_biz_no,
}


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


def extract_regions(img, template, corners=None, return_warped=False):
    """img(BGR ndarray) + template(dict) → {aligned, fields, regions, warped_image_base64?}.

    corners(4점) 있으면 aspect(w,h) 로 warp, 없으면 원본 평면 가정(스킵).
    """
    aspect = template.get("aspect") or {}
    aligned = False
    if corners:
        w = int(aspect.get("w") or img.shape[1])
        h = int(aspect.get("h") or img.shape[0])
        img = _warp(img, corners, w, h)
        aligned = True
    else:
        h, w = int(img.shape[0]), int(img.shape[1])

    fields = {}
    regions = []
    for f in template.get("fields", []):
        key = f.get("key")
        box = f.get("box") or [0, 0, 0, 0]
        x0 = max(0, min(int(round(box[0] * w)), w - 1))
        y0 = max(0, min(int(round(box[1] * h)), h - 1))
        x1 = max(x0 + 1, min(int(round((box[0] + box[2]) * w)), w))
        y1 = max(y0 + 1, min(int(round((box[1] + box[3]) * h)), h))
        crop = img[y0:y1, x0:x1]
        dets = ocr_engine.run(crop)
        text, _ = reconstruct(dets)
        parser = _PARSERS.get(f.get("parser") or "text", _parse_text)
        fields[key] = parser(text)
        score = sum(d["score"] for d in dets) / len(dets) if dets else 0.0
        regions.append({"key": key, "text": text, "score": round(score, 4)})

    result = {"aligned": aligned, "fields": fields, "regions": regions}
    if return_warped:
        ok, buf = cv2.imencode(".png", img)
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
