CREATE TABLE document_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    applies_to VARCHAR(16) NOT NULL,            -- PERSON | EQUIPMENT
    has_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    requires_verification BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (name, applies_to)
);

CREATE INDEX idx_document_types_applies_to ON document_types(applies_to);

CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    document_type_id BIGINT NOT NULL REFERENCES document_types(id) ON DELETE RESTRICT,
    owner_type VARCHAR(16) NOT NULL,            -- PERSON | EQUIPMENT
    owner_id BIGINT NOT NULL,
    file_key VARCHAR(255) NOT NULL,             -- storage key (예: 2026/04/uuid.bin)
    file_name VARCHAR(255) NOT NULL,            -- 원본 파일명
    file_size BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expiry_date DATE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_documents_owner ON documents(owner_type, owner_id);
CREATE INDEX idx_documents_type ON documents(document_type_id);
CREATE INDEX idx_documents_expiry ON documents(expiry_date) WHERE expiry_date IS NOT NULL;

-- Seed: 기본 DocumentType. ADMIN이 추후 추가/비활성 가능.
-- PERSON 서류
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order) VALUES
    ('운전면허증',          'PERSON', TRUE,  TRUE,  10),
    ('신분증',              'PERSON', FALSE, TRUE,  20),
    ('안전교육 이수증',     'PERSON', TRUE,  FALSE, 30),
    ('건강진단서',          'PERSON', TRUE,  FALSE, 40),
    ('자격증',              'PERSON', FALSE, FALSE, 50),
    ('기타',                'PERSON', FALSE, FALSE, 99);

-- EQUIPMENT 서류
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order) VALUES
    ('자동차등록증',        'EQUIPMENT', FALSE, TRUE,  10),
    ('정기검사증',          'EQUIPMENT', TRUE,  FALSE, 20),
    ('보험증권',            'EQUIPMENT', TRUE,  FALSE, 30),
    ('안전인증서',          'EQUIPMENT', TRUE,  FALSE, 40),
    ('점검표',              'EQUIPMENT', TRUE,  FALSE, 50),
    ('기타',                'EQUIPMENT', FALSE, FALSE, 99);
