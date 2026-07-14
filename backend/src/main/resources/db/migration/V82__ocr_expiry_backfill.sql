-- ============================================================
-- V82: 서류 만료일 로컬 OCR 비동기 백필 대상 지정.
--
-- 정기검사증(EQUIPMENT, has_expiry=TRUE, verify_endpoint=NULL) 에
-- ocr_enabled=TRUE + ocr_extract_type='EQUIPMENT_REGISTRATION' 를 부여한다.
-- 업로드 직후 전용 스레드가 로컬 PaddleOCR 로 검사유효기간을 추출해
-- expiry_date(NULL 일 때만) 를 자동 백필한다.
--
-- verify_endpoint 보유 타입(운전면허/화물/안전교육/사업자)은 건드리지 않는다 —
-- 그쪽은 Google Vision 즉시 진위확인 경로(AutoVerifyTrigger)가 담당한다.
--
-- 멱등: UPDATE ... WHERE 라 반복 실행해도 같은 결과.
-- ============================================================
UPDATE document_types SET
    ocr_enabled = TRUE,
    ocr_extract_type = 'EQUIPMENT_REGISTRATION'
    WHERE applies_to = 'EQUIPMENT' AND name = '정기검사증';
