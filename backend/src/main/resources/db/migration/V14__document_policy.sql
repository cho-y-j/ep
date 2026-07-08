-- ============================================================
-- Phase S-4: 서류 정책 강화 + 검증 필드 정리
--
-- document_types:
--   required, blocks_assignment, default_valid_months — 정책
--   ocr_enabled, ocr_extract_type, ocr_expiry_field_key — verify-api OCR 라우팅
--   verify_endpoint — main-api 정부 API 라우팅
--   required_fields — 검증/등록 시 채워야 하는 필드 목록 (JSON 배열 문자열)
--
-- documents:
--   verification_status — PENDING | VERIFIED | REJECTED | OCR_REVIEW_REQUIRED
--   verified_by, verified_at, rejected_reason
--   previous_document_id — 갱신(재업로드) 시 직전 문서 연결
--   verification_result — verify-api 응답 원본 JSON 문자열
--   extracted_data — OCR 추출 + 사용자 보충 입력 합본 JSON 문자열
-- ============================================================

-- ------------------------------------------------------------
-- document_types
-- ------------------------------------------------------------
ALTER TABLE document_types
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocks_assignment BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN default_valid_months INTEGER,
    ADD COLUMN ocr_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ocr_extract_type VARCHAR(64),       -- LICENSE | BUSINESS | CARGO | KOSHA | EQUIPMENT_REGISTRATION | null
    ADD COLUMN ocr_expiry_field_key VARCHAR(100),
    ADD COLUMN verify_endpoint VARCHAR(64),        -- RIMS_LICENSE | CARGO_LICENSE | KOSHA | NTS_BIZ | null
    ADD COLUMN required_fields TEXT;               -- JSON 배열 문자열 ["license_no","name", ...]

-- 정책 적용. has_expiry=TRUE 인 서류는 보수적으로 blocks_assignment=TRUE.
-- requires_verification 도 자동 검증 대상에 맞춰 갱신.

-- PERSON
UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 24,
    ocr_enabled = TRUE,  ocr_extract_type = 'LICENSE',
    ocr_expiry_field_key = 'expiry_date',
    verify_endpoint = 'RIMS_LICENSE',
    requires_verification = TRUE,
    required_fields = '["license_no","name","license_condition_code"]'
    WHERE applies_to = 'PERSON' AND name = '운전면허증';

UPDATE document_types SET
    required = TRUE,  blocks_assignment = FALSE, default_valid_months = NULL,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '[]'
    WHERE applies_to = 'PERSON' AND name = '신분증';

-- "안전교육 이수증" 을 KOSHA 안전보건교육으로 매핑한다 (한국 건설현장 실무).
UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 12,
    ocr_enabled = TRUE,  ocr_extract_type = 'KOSHA',
    ocr_expiry_field_key = 'completion_date',
    verify_endpoint = 'KOSHA',
    requires_verification = TRUE,
    required_fields = '[]'
    WHERE applies_to = 'PERSON' AND name = '안전교육 이수증';

UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 12,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '["expiry_date"]'
    WHERE applies_to = 'PERSON' AND name = '건강진단서';

UPDATE document_types SET
    required = FALSE, blocks_assignment = FALSE, default_valid_months = NULL,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '[]'
    WHERE applies_to = 'PERSON' AND name = '자격증';

UPDATE document_types SET
    required = FALSE, blocks_assignment = FALSE, default_valid_months = NULL,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '[]'
    WHERE applies_to = 'PERSON' AND name = '기타';

-- 신규: 화물운송자격증 (PERSON)
INSERT INTO document_types
    (name, applies_to, has_expiry, requires_verification, sort_order, active,
     required, blocks_assignment, default_valid_months,
     ocr_enabled, ocr_extract_type, ocr_expiry_field_key,
     verify_endpoint, required_fields)
VALUES
    ('화물운송자격증', 'PERSON', TRUE, TRUE, 25, TRUE,
     FALSE, FALSE, NULL,
     TRUE, 'CARGO', 'expiry_date',
     'CARGO_LICENSE', '["license_no","name","birth_date"]')
ON CONFLICT (name, applies_to) DO NOTHING;

-- EQUIPMENT
-- 자동차등록증: OCR로 차량번호/소유자 추출만. 정부 검증 API 없음.
UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = NULL,
    ocr_enabled = TRUE,  ocr_extract_type = 'EQUIPMENT_REGISTRATION',
    ocr_expiry_field_key = NULL,
    verify_endpoint = NULL,
    requires_verification = FALSE,
    required_fields = '["vehicle_no"]'
    WHERE applies_to = 'EQUIPMENT' AND name = '자동차등록증';

UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 12,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '["expiry_date"]'
    WHERE applies_to = 'EQUIPMENT' AND name = '정기검사증';

UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 12,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '["expiry_date"]'
    WHERE applies_to = 'EQUIPMENT' AND name = '보험증권';

UPDATE document_types SET
    required = TRUE,  blocks_assignment = TRUE,  default_valid_months = 24,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '["expiry_date"]'
    WHERE applies_to = 'EQUIPMENT' AND name = '안전인증서';

UPDATE document_types SET
    required = FALSE, blocks_assignment = FALSE, default_valid_months = 12,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '["expiry_date"]'
    WHERE applies_to = 'EQUIPMENT' AND name = '점검표';

UPDATE document_types SET
    required = FALSE, blocks_assignment = FALSE, default_valid_months = NULL,
    ocr_enabled = FALSE, requires_verification = FALSE,
    required_fields = '[]'
    WHERE applies_to = 'EQUIPMENT' AND name = '기타';

-- ------------------------------------------------------------
-- documents
-- ------------------------------------------------------------
ALTER TABLE documents
    ADD COLUMN verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN verified_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN verified_at TIMESTAMP,
    ADD COLUMN rejected_reason VARCHAR(255),
    ADD COLUMN previous_document_id BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    ADD COLUMN verification_result TEXT,            -- verify-api 응답 원본 JSON
    ADD COLUMN extracted_data TEXT;                 -- OCR 결과 + 사용자 보충 입력 JSON

-- 기존 verified=true 였던 row 는 VERIFIED 로 마이그레이션.
UPDATE documents SET verification_status = 'VERIFIED' WHERE verified = TRUE;

CREATE INDEX idx_documents_verification_status ON documents(verification_status);
CREATE INDEX idx_documents_previous ON documents(previous_document_id) WHERE previous_document_id IS NOT NULL;
