"""FastAPI 진입점.

POST /ocr : multipart(필드명 image) 이미지/PDF → 전처리 → OCR → 읽기순서 재조립.
GET  /health : 상태.
"""

import io
import json
from contextlib import asynccontextmanager
from typing import Optional

import cv2
import fitz
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from . import config, document_align, ocr_engine, preprocess
from .reading_order import reconstruct


@asynccontextmanager
async def lifespan(app):
    # 웜업: 기동 시 모델을 메모리에 올리고 더미 추론 1회 → 첫 실요청도 즉시(모델로드 지연 제거).
    # 서비스는 상시 대기(persistent)로 두고 이 웜업은 부팅 시 1회만 발생.
    try:
        ocr_engine.run(np.full((320, 640, 3), 255, np.uint8))
    except Exception:
        pass
    yield


app = FastAPI(title="paddle-ocr", version="0.1.0", lifespan=lifespan)


def _load_image(data: bytes, filename: Optional[str], content_type: Optional[str]) -> np.ndarray:
    """이미지/PDF 바이트 → BGR ndarray. PDF 는 첫 페이지를 PDF_DPI 로 렌더."""
    is_pdf = (content_type == "application/pdf"
              or (filename or "").lower().endswith(".pdf")
              or data[:5] == b"%PDF-")
    if is_pdf:
        doc = fitz.open(stream=data, filetype="pdf")
        try:
            page = doc.load_page(0)
            zoom = config.PDF_DPI / 72.0
            pix = page.get_pixmap(matrix=fitz.Matrix(zoom, zoom), alpha=False)
            rgb = np.frombuffer(pix.samples, np.uint8).reshape(pix.height, pix.width, pix.n)
            return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
        finally:
            doc.close()

    img = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        from PIL import Image  # cv2 가 못 읽는 포맷 폴백
        pil = Image.open(io.BytesIO(data)).convert("RGB")
        img = cv2.cvtColor(np.array(pil), cv2.COLOR_RGB2BGR)
    return img


@app.get("/health")
def health():
    return {"status": "ok", "engine": ocr_engine.engine_name(),
            "lang": config.OCR_LANG, "gpu": False}


@app.post("/ocr")
async def ocr(
    image: UploadFile = File(...),
    grayscale: Optional[bool] = None,
    binarize: Optional[bool] = None,
    binarize_method: Optional[str] = None,
    deskew: Optional[bool] = None,
    denoise: Optional[bool] = None,
    max_side: Optional[int] = None,
):
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    try:
        img = _load_image(data, image.filename, image.content_type)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"cannot decode image: {e}")
    if img is None:
        raise HTTPException(status_code=400, detail="invalid image")

    cfg = config.resolve_preprocess(
        grayscale=grayscale, binarize=binarize, binarize_method=binarize_method,
        deskew=deskew, denoise=denoise, max_side=max_side,
    )
    img = preprocess.apply(img, cfg)
    detections = ocr_engine.run(img)
    full_text, lines = reconstruct(detections)
    return {"fullText": full_text, "lines": lines}


@app.post("/extract-regions")
async def extract_regions(
    image: UploadFile = File(...),
    template: str = Form(...),
    corners: Optional[str] = Form(None),
    return_warped: Optional[bool] = Form(False),
):
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    try:
        img = _load_image(data, image.filename, image.content_type)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"cannot decode image: {e}")
    try:
        tmpl = json.loads(template)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"invalid template json: {e}")
    pts = None
    if corners:
        try:
            pts = json.loads(corners)
        except Exception:
            pts = None
    return document_align.extract_regions(img, tmpl, pts, bool(return_warped))


@app.post("/detect-corners")
async def detect_corners(image: UploadFile = File(...)):
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="empty file")
    try:
        img = _load_image(data, image.filename, image.content_type)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"cannot decode image: {e}")
    return document_align.detect_corners(img)
