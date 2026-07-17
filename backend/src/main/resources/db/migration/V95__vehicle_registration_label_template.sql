-- V95: 자동차등록증(id=7) OCR 템플릿을 '라벨기반 + 회전 자동보정' 버전으로 교체.
-- 근거: 신서식(2025.2.17)·90°회전 폰사진에서 고정밴드(V93)가 위치 어긋나 추출 전멸 → 라벨앵커+auto_orient 로 강건화.
--   vehicle_no·model·year = 라벨앵커(자동차등록번호/차명/최초등록일 → 옆값, 서식 버전차에 강건).
--   expiry_date          = 검사유효기간 표 밴드(date_end = 여러 날짜 중 최신 만료일).
-- 실샘플 검증(고소작업차 신서식 회전본): vehicle_no 경기99사9489 / model 호룡SKY4504N고소작업차 / year 2023 / expiry_date 2026-04-19.
UPDATE document_types SET ocr_region_template = '{"doc_type": "vehicle_registration", "name": "자동차등록증(라벨기반, 회전 자동보정)", "auto_orient": true, "ocr_long": 1653, "label_crop": [0.0, 0.45], "fields": [{"key": "vehicle_no", "label": ["자동차등록번호", "등록번호"], "exclude": ["주민", "법인"], "parser": "plate"}, {"key": "model", "label": ["차명", "차"], "left": true, "parser": "model"}, {"key": "year", "label": ["최초등록"], "parser": "year"}, {"key": "expiry_date", "box": [0.49, 0.42, 0.26, 0.17], "kind": "date_end"}]}' WHERE id = 7;  -- 자동차등록증
