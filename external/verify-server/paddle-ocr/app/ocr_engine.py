"""PaddleOCR(PP-OCRv5, CPU) 래퍼. 지연 초기화 싱글턴.

GTX 1060(Pascal) 은 PP-OCRv5 GPU 추론이 불가하므로 CPU 로 강제한다.
"""

import threading

import numpy as np
import paddle
import paddleocr
from paddleocr import PaddleOCR

from . import config

_engine = None
_lock = threading.Lock()


def _build():
    paddle.set_device("cpu")  # Pascal(GTX1060) 은 PP-OCRv5 GPU 불가 → CPU 강제
    base = {"lang": config.OCR_LANG}
    # enable_mkldnn=False: paddle 3.3.1 CPU 의 oneDNN(PIR) 미구현 op
    #   (ConvertPirAttribute2RuntimeAttribute, onednn_instruction.cc:116) 회피. env FLAGS_use_mkldnn 는 paddlex 가 무시함.
    # device=cpu(Pascal은 v5 GPU 불가) + 문서 방향/보정/텍스트라인 방향 모듈 비활성.
    # 마이너 버전이 특정 kwargs 를 안 받으면 점진적으로 줄여 폴백(단, enable_mkldnn 최대한 유지).
    # text_detection_model_name=mobile_det: 기본 server_det 은 CPU에서 추론 1건에 수 분 → 경량 mobile_det 로 몇 초.
    # text_recognition_model_name=korean_PP-OCRv5_mobile_rec 명시: 미지정 시 paddleocr 가 비한국어 rec(PP-OCRv6)을
    #   끌어와 한자 섞인 지저분한 인식이 됨. det=mobile(속도), rec=korean(품질) 둘 다 고정.
    for extra in (
        {"device": "cpu", "enable_mkldnn": False,
         "text_detection_model_name": "PP-OCRv5_mobile_det",
         "text_recognition_model_name": "korean_PP-OCRv5_mobile_rec",
         "use_doc_orientation_classify": False, "use_doc_unwarping": False,
         "use_textline_orientation": False},
        {"device": "cpu", "enable_mkldnn": False,
         "text_detection_model_name": "PP-OCRv5_mobile_det",
         "text_recognition_model_name": "korean_PP-OCRv5_mobile_rec"},
        {"device": "cpu", "enable_mkldnn": False,
         "text_detection_model_name": "PP-OCRv5_mobile_det"},
        {"device": "cpu", "enable_mkldnn": False},
        {"enable_mkldnn": False},
        {},
    ):
        try:
            return PaddleOCR(**base, **extra)
        except TypeError:
            continue
    return PaddleOCR(**base)


def get_engine():
    global _engine
    if _engine is None:
        with _lock:
            if _engine is None:
                _engine = _build()
    return _engine


def _as_list(v):
    if v is None:
        return []
    if isinstance(v, np.ndarray):
        return v.tolist()
    return list(v)


def _get(res, key):
    if isinstance(res, dict):
        return res.get(key)
    return getattr(res, key, None)


def run(img_bgr):
    """PaddleOCR 실행 → [{text, score, box:[[x,y]x4] int}] 반환."""
    results = get_engine().predict(img_bgr)
    detections = []
    for res in results or []:
        texts = _as_list(_get(res, "rec_texts"))
        scores = _as_list(_get(res, "rec_scores"))
        polys = _get(res, "rec_polys")
        if polys is None:
            polys = _get(res, "dt_polys")
        polys = _as_list(polys)
        for i, text in enumerate(texts):
            if i >= len(polys):
                break
            box = [[int(round(float(p[0]))), int(round(float(p[1])))] for p in polys[i]]
            score = float(scores[i]) if i < len(scores) else 0.0
            detections.append({"text": text, "score": score, "box": box})
    return detections


def engine_name():
    return f"PaddleOCR {getattr(paddleocr, '__version__', '3.x')} (PP-OCRv5)"
