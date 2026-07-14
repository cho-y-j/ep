"""환경변수 기반 설정 + 전처리 플래그 해석.

전처리 플래그는 환경변수로 기본값을 정하고, /ocr 쿼리 파라미터로 요청별 덮어쓴다.
이진화(binarize)는 기본 OFF — PP-OCRv5 는 딥러닝이라 이진화가 오히려 해가 될 수 있어
A/B 비교용으로만 켠다.
"""

import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() in ("1", "true", "yes", "on")


def _env_int(name: str, default: int) -> int:
    v = os.getenv(name)
    if v is None or v.strip() == "":
        return default
    try:
        return int(v)
    except ValueError:
        return default


# OCR 엔진
OCR_LANG = os.getenv("OCR_LANG", "korean")
PDF_DPI = _env_int("PDF_DPI", 200)  # verify-api(PDFBox 200 DPI) 와 동일

# 전처리 기본값 (환경변수). 쿼리 파라미터가 있으면 요청별로 덮어쓴다.
DEFAULT_GRAYSCALE = _env_bool("PREPROCESS_GRAYSCALE", True)
DEFAULT_BINARIZE = _env_bool("PREPROCESS_BINARIZE", False)
DEFAULT_BINARIZE_METHOD = os.getenv("PREPROCESS_BINARIZE_METHOD", "otsu")  # otsu | adaptive
DEFAULT_DESKEW = _env_bool("PREPROCESS_DESKEW", False)
DEFAULT_DENOISE = _env_bool("PREPROCESS_DENOISE", False)
DEFAULT_MAX_SIDE = _env_int("PREPROCESS_MAX_SIDE", 1600)  # 긴 변 1600px 로 다운스케일(추론 속도↑, 인쇄체 인식 유지). 0=off


@dataclass
class PreprocessConfig:
    grayscale: bool
    binarize: bool
    binarize_method: str
    deskew: bool
    denoise: bool
    max_side: int


def resolve_preprocess(grayscale=None, binarize=None, binarize_method=None,
                       deskew=None, denoise=None, max_side=None) -> PreprocessConfig:
    """환경변수 기본값 + 쿼리 파라미터(None 이 아니면 우선) 병합."""
    return PreprocessConfig(
        grayscale=DEFAULT_GRAYSCALE if grayscale is None else grayscale,
        binarize=DEFAULT_BINARIZE if binarize is None else binarize,
        binarize_method=DEFAULT_BINARIZE_METHOD if binarize_method is None else binarize_method,
        deskew=DEFAULT_DESKEW if deskew is None else deskew,
        denoise=DEFAULT_DENOISE if denoise is None else denoise,
        max_side=DEFAULT_MAX_SIDE if max_side is None else max_side,
    )
