"""업로드 PII 이미지의 주민/법인 등록번호 영역을 검정 마스킹.

2배 업스케일 후 OCR(작은 인쇄체 검출 개선) → 아래 두 경우만 마스킹한다.
 (1) 풀 RRN 단일 박스: 6자리 [-.]? 7자리 (점/공백 섞임 허용).
 (2) 분할 케이스: 6자리(YYMMDD) 박스 + 같은 줄 '바로 오른쪽'의 7자리 숫자류 박스 → 둘 다.
오른쪽이 7자리 숫자류일 때만 확장한다(통장 계좌번호·사업자번호 등 오버마스킹 방지).
패턴 미검출 시 masked=False + 원본 base64 반환 → 백엔드는 fail-open(원본 저장).
법인등록번호도 6-7 자리 동일 형태라 같은 패턴으로 커버된다.
"""

import base64
import re

import cv2

from . import ocr_engine

UPSCALE = 2.0  # 소형 인쇄체(주민번호) 검출률 개선용. 좌표는 마스킹 전에 원배율로 환원.

# (1) 풀 RRN 한 박스: 890722-1580713 / 890722 1580713 / 8907221580713 (점/공백 허용)
_RRN_FULL = re.compile(r"\d{6}\s*[-.]?\s*\d{7}")
# (2-right) 뒷 7자리 숫자류: 정확 7자리 또는 OCR 변형(예: 15807.13 = \d{5}.\d{2})
_BACK7 = re.compile(r"^(?:\d{7}|\d{4,5}[.\s]?\d{2,3})$")


def _digits(s):
    return re.sub(r"\D", "", s)


def _is_front6(text):
    """앞 6자리(YYMMDD) 박스 판정 — 오검출 축소 위해 월/일 범위까지 확인."""
    d = _digits(text)
    if len(d) != 6:
        return False
    mm, dd = int(d[2:4]), int(d[4:6])
    return 1 <= mm <= 12 and 1 <= dd <= 31


def _is_back7(text):
    """뒤 7자리 숫자류 박스 판정(6자리 박스의 짝일 때만 사용)."""
    return bool(_BACK7.match(text.replace(" ", "")))


def _bbox(box):
    xs = [p[0] for p in box]
    ys = [p[1] for p in box]
    return min(xs), min(ys), max(xs), max(ys)


def _find_rrn_boxes(dets):
    """마스킹할 bbox 목록(업스케일 좌표) 산출. dets: [{text, score, box:[[x,y]x4]}]."""
    masks = []
    for d in dets:
        t = d["text"].replace(" ", "")
        if _RRN_FULL.search(t):  # (1) 풀 RRN 단일 박스
            masks.append(_bbox(d["box"]))
            continue
        if _is_front6(t):        # (2) 앞 6자리 → 같은 줄 오른쪽 7자리류 짝이 있을 때만
            x0, y0, x1, y1 = _bbox(d["box"])
            fw = x1 - x0
            for e in dets:
                if e is d:
                    continue
                ex0, ey0, ex1, ey1 = _bbox(e["box"])
                same_line = not (ey1 < y0 or ey0 > y1)   # y 겹침(같은 줄)
                adjacent = 0 <= ex0 - x1 < fw * 1.5       # 바로 오른쪽(간격 제한)
                if same_line and adjacent and _is_back7(e["text"]):
                    masks.append((x0, y0, x1, y1))
                    masks.append((ex0, ey0, ex1, ey1))
                    break
    return masks


def mask_pii(img_bgr):
    """BGR ndarray → {ok, masked, masked_image_base64(png)}."""
    up = cv2.resize(img_bgr, None, fx=UPSCALE, fy=UPSCALE, interpolation=cv2.INTER_CUBIC)
    masks = _find_rrn_boxes(ocr_engine.run(up))

    out = img_bgr.copy()
    h, w = out.shape[:2]
    pad = 4
    for (x0, y0, x1, y1) in masks:
        rx0 = max(0, int(x0 / UPSCALE) - pad)
        ry0 = max(0, int(y0 / UPSCALE) - pad)
        rx1 = min(w, int(x1 / UPSCALE) + pad)
        ry1 = min(h, int(y1 / UPSCALE) + pad)
        cv2.rectangle(out, (rx0, ry0), (rx1, ry1), (0, 0, 0), -1)

    ok, buf = cv2.imencode(".png", out)
    if not ok:
        return {"ok": False, "masked": False, "masked_image_base64": ""}
    return {"ok": True, "masked": bool(masks),
            "masked_image_base64": base64.b64encode(buf.tobytes()).decode("ascii")}
