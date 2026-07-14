# paddle-ocr — 로컬 PaddleOCR(PP-OCRv5) 마이크로서비스

verify-api 의 구글 비전 OCR 을 대체/보완하기 위한 **로컬 CPU OCR** 서비스.
박스+텍스트 리스트를 **구글 비전과 동일한 읽기순서**로 재조립해 `fullText` 를 만든다.
verify-api Java 파서(`OcrExtractController`)가 `fullText` 정규식으로 그대로 동작하도록
하는 것이 목표(파서 무변경).

이번 단계(Wave 0)는 **파이썬 서비스 신설**만 포함한다. verify-api/backend Java 는 무변경이며,
이후 별도 Wave 에서 Java 어댑터가 이 서비스를 호출한다.

## 전제
- **CPU 전용.** GTX 1060 은 Pascal 세대라 PP-OCRv5 GPU 추론이 불가 → `paddle.set_device("cpu")` 로 CPU 강제.
- paddleocr 3.x + paddlepaddle 3.x(CPU 빌드, PyPI 기본).

## 실행

### 로컬 (uvicorn)
```bash
cd external/verify-server/paddle-ocr
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
# 첫 기동/최초 요청 시 PP-OCRv5 모델 자동 다운로드(네트워크 필요)
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 도커
```bash
cd external/verify-server/paddle-ocr
docker build -t paddle-ocr .          # 빌드 시 모델 사전 다운로드(오프라인 기동 대비)
docker run --rm -p 8000:8000 paddle-ocr
```

## 엔드포인트

### `GET /health`
```json
{ "status": "ok", "engine": "PaddleOCR 3.x (PP-OCRv5)", "lang": "korean", "gpu": false }
```

### `POST /ocr`
- `multipart/form-data`, 파일 필드명 **`image`** (이미지 또는 PDF). PDF 는 **첫 페이지**를 `PDF_DPI`(기본 200)로 렌더.
- 응답:
```json
{
  "fullText": "자동차등록증\n자동차등록번호 : 12가 3456\n차명 : 포터Ⅱ\n...",
  "lines": [
    { "text": "자동차등록증", "score": 0.99, "box": [[x,y],[x,y],[x,y],[x,y]] },
    { "text": "자동차등록번호 : 12가 3456", "score": 0.98, "box": [[x,y],[x,y],[x,y],[x,y]] }
  ]
}
```

`curl` 예:
```bash
curl -s -F "image=@/path/to/vehicle_reg.jpg" http://localhost:8000/ocr | jq .fullText
# 전처리 A/B (요청별 쿼리로 덮어쓰기)
curl -s -F "image=@sample.jpg" "http://localhost:8000/ocr?binarize=true&binarize_method=adaptive"
```

## /ocr 처리 흐름
1. **로드**: 이미지 디코드(cv2, 실패 시 PIL 폴백) 또는 PDF 첫 페이지 렌더(PyMuPDF, 200 DPI) → BGR.
2. **전처리(OpenCV)**: 해상도정규화 → (그레이) → 노이즈제거 → deskew → 이진화 → BGR 복원. (플래그, 아래)
3. **OCR**: PaddleOCR(PP-OCRv5, CPU) `predict` → `rec_texts` / `rec_polys` / `rec_scores`.
4. **읽기순서 재조립**: y-밴드 클러스터 → 밴드 내 x오름차순 → 밴드 y오름차순 → `fullText`.
5. 응답 `{ fullText, lines }`.

## 읽기순서 알고리즘 (`app/reading_order.py`)
구글 비전 `textAnnotations[0].description` 와 같은 **위→아래·왼→오** 순서를 재현:
1. 각 박스의 세로중심 `cy` 오름차순 정렬.
2. **y-밴드 클러스터**: 직전 밴드 평균 `cy` 와의 차이가 `0.5 × max(두 박스 높이)` 이내면
   같은 줄로 묶음(= 세로로 50% 이상 겹치면 같은 줄).
3. 밴드 내부는 `x`(왼쪽 좌표) 오름차순.
4. **밴드 안은 공백, 밴드 사이는 `\n`** 으로 join → `fullText`.

`lines` 배열도 동일한 읽기순서(박스 단위)로 나열되어, 이후 Java 어댑터가
`textAnnotations`(마스킹용 boundingPoly)로 매핑하기 쉽게 한다.

## 전처리 플래그
환경변수로 기본값, `/ocr` 쿼리 파라미터로 요청별 덮어쓰기. (쿼리가 우선)

| 기능 | 환경변수 | 기본 | 쿼리 파라미터 |
|---|---|---|---|
| 그레이스케일 | `PREPROCESS_GRAYSCALE` | `true` | `grayscale` |
| 이진화 | `PREPROCESS_BINARIZE` | `false` | `binarize` |
| 이진화 방식 | `PREPROCESS_BINARIZE_METHOD` | `otsu` | `binarize_method` (`otsu`\|`adaptive`) |
| deskew(기울기보정) | `PREPROCESS_DESKEW` | `false` | `deskew` |
| 노이즈제거 | `PREPROCESS_DENOISE` | `false` | `denoise` |
| 해상도정규화(긴변 상한) | `PREPROCESS_MAX_SIDE` | `0`(off) | `max_side` |
| PDF 렌더 DPI | `PDF_DPI` | `200` | — |
| 인식 언어 | `OCR_LANG` | `korean` | — |

**이진화 기본 OFF**: PP-OCRv5 는 딥러닝 인식이라 이진화가 오히려 정확도를 떨어뜨릴 수 있다.
스캔 품질이 낮은 문서에서 A/B 로만 켠다.

## 정합 검증 (사용자 서버)
목표: PaddleOCR 의 `fullText` 로 verify-api 파서 정규식이 구글 비전 때와 동일하게 통과하는지.

1. 서비스 기동 후 샘플(자동차등록증/운전면허증/화물운송자격증/사업자등록증)로 `/ocr` 호출,
   `fullText` 를 눈으로 확인 — **라벨과 값이 같은 줄에 공백으로**, 줄바꿈이 자연스러운지.
2. 파서 정규식 통과 스팟체크(대표 패턴, `OcrExtractController` 기준):
   - 자동차등록증: `fullText` 에 `자동차등록번호` 가 **한 토큰으로 연속**되어야 함
     (`text.indexOf("자동차등록번호")`), `차\s*명\s*[:：]?\s*(...)` 이 같은 줄에서 매칭.
   - 운전면허증: `(\d{2})-(\d{2})-(\d{6})-(\d{2})`, 주민번호 `(\d{6})[\-\s](\d{7})`.
   - 사업자등록증: `등록\s*번호\s*[:：]?\s*(\d{3})-(\d{2})-(\d{5})`.
   - 화물운송자격증: `자격증\s*번호`, `생년월일`.
3. 실패 시: 대개 **줄 묶임/순서** 문제 → 밴드 허용치나 join 규칙 조정, 또는 저품질 스캔이면
   `binarize`/`deskew`/`denoise` 를 A/B 로 시도.

> 참고: 구글 비전 출력형식은 `verify-api .../dto/kosha/OCRData.java`(fullText + textAnnotations)
> 를, 파서는 `.../controller/OcrExtractController.java` 를 보라.

## verify-api 연동(후속 Wave, 참고)
Java 어댑터가 이 서비스를 호출하도록 붙일 때, docker-compose 에 서비스 추가 후
`app-network` 상에서 `http://paddle-ocr:8000/ocr` 로 접근하면 된다. (이번 단계에서는 미변경)
