-- 서류 수집 링크: 차량주인/사람에게 공개 토큰 링크를 보내 필수/선택 서류를 무로그인 업로드받고,
-- 모이면 정해진 순서대로 PDF로 합쳐 이메일 발송한다.

CREATE TABLE document_collection_request (
    id                  BIGSERIAL PRIMARY KEY,
    token               VARCHAR(64) NOT NULL UNIQUE,
    token_expires_at    TIMESTAMP NOT NULL,
    owner_type          VARCHAR(16) NOT NULL,        -- EQUIPMENT / PERSON
    owner_id            BIGINT NOT NULL,
    supplier_company_id BIGINT,                       -- 작성자 회사 scope
    created_by          BIGINT,
    title               VARCHAR(150),
    recipient_name      VARCHAR(100),
    recipient_phone     VARCHAR(32),
    recipient_email     VARCHAR(255),
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN / SUBMITTED / SENT / CANCELLED
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    submitted_at        TIMESTAMP,
    sent_at             TIMESTAMP
);
CREATE INDEX idx_doc_collect_owner ON document_collection_request (owner_type, owner_id);
CREATE INDEX idx_doc_collect_supplier ON document_collection_request (supplier_company_id);

CREATE TABLE document_collection_item (
    id                   BIGSERIAL PRIMARY KEY,
    request_id           BIGINT NOT NULL REFERENCES document_collection_request (id) ON DELETE CASCADE,
    document_type_id     BIGINT NOT NULL,
    required             BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order           INT NOT NULL DEFAULT 0,            -- PDF 병합/표시 순서
    uploaded_document_id BIGINT,                            -- 업로드되면 채움
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (request_id, document_type_id)
);
CREATE INDEX idx_doc_collect_item_req ON document_collection_item (request_id);

-- 수집 대상 서류 타입 시드 (이름 없으면 추가). sort_order = 작업계획서 병합 순서.
-- 필수: 비파괴 → 안전점검 → 갑부 → 차량서류 / 선택: 알루미늄 → 유해위험기구.
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '비파괴검사 보고서(MT/UT)', 'EQUIPMENT', false, false, 510, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '비파괴검사 보고서(MT/UT)');
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '안전점검표', 'EQUIPMENT', false, false, 520, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '안전점검표');
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '갑부', 'EQUIPMENT', false, false, 530, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '갑부');
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '차량서류', 'EQUIPMENT', false, false, 540, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '차량서류');
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '알루미늄 시험성적서', 'EQUIPMENT', false, false, 550, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '알루미늄 시험성적서');
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, active, required, blocks_assignment, default_valid_months, ocr_enabled, ocr_extract_type, ocr_expiry_field_key, verify_endpoint, required_fields)
SELECT '유해위험기구 검사서', 'EQUIPMENT', false, false, 560, true, false, false, NULL, false, NULL, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE name = '유해위험기구 검사서');
