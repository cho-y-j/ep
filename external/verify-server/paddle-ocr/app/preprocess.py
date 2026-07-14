"""OpenCV 전처리. 모든 단계는 플래그로 on/off (config.PreprocessConfig).

반환은 항상 3채널 BGR uint8 — PaddleOCR 이 3채널 입력을 기대하므로,
그레이스케일/이진화 결과도 BGR 로 되돌려 넘긴다.
"""

import cv2
import numpy as np

from .config import PreprocessConfig


def _to_gray(img: np.ndarray) -> np.ndarray:
    if img.ndim == 2:
        return img
    return cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)


def _normalize_size(img: np.ndarray, max_side: int) -> np.ndarray:
    """긴 변이 max_side 를 넘으면 축소(해상도 정규화). max_side<=0 이면 무시."""
    h, w = img.shape[:2]
    longer = max(h, w)
    if max_side <= 0 or longer <= max_side:
        return img
    scale = max_side / float(longer)
    return cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)


def _denoise(gray: np.ndarray) -> np.ndarray:
    return cv2.fastNlMeansDenoising(gray, None, h=10, templateWindowSize=7, searchWindowSize=21)


def _deskew(gray: np.ndarray) -> np.ndarray:
    """전경 픽셀 분포(minAreaRect)로 기울기 추정 후 보정."""
    thr = cv2.threshold(cv2.bitwise_not(gray), 0, 255,
                        cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]
    coords = cv2.findNonZero(thr)
    if coords is None:
        return gray
    angle = cv2.minAreaRect(coords)[-1]
    if angle < -45:
        angle = 90 + angle
    if abs(angle) < 0.5:
        return gray
    h, w = gray.shape[:2]
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(gray, m, (w, h),
                          flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE)


def _binarize(gray: np.ndarray, method: str) -> np.ndarray:
    if method == "adaptive":
        return cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                     cv2.THRESH_BINARY, 31, 15)
    return cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY | cv2.THRESH_OTSU)[1]


def apply(img: np.ndarray, cfg: PreprocessConfig) -> np.ndarray:
    """활성화된 단계만 적용하고 3채널 BGR 로 반환.

    순서: 해상도 정규화 → (그레이) → 노이즈제거 → deskew → 이진화 → BGR 복원.
    모든 그레이 기반 단계가 꺼져 있으면(binarize/deskew/denoise/grayscale 전부 off)
    원본 BGR 을 그대로 넘긴다.
    """
    out = _normalize_size(img, cfg.max_side)
    if not (cfg.grayscale or cfg.binarize or cfg.deskew or cfg.denoise):
        return out
    gray = _to_gray(out)
    if cfg.denoise:
        gray = _denoise(gray)
    if cfg.deskew:
        gray = _deskew(gray)
    if cfg.binarize:
        gray = _binarize(gray, cfg.binarize_method)
    return cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)
