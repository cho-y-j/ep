-- 공급사가 BP사에 보낸 "서류 심사" — 이메일에 더해 BP사 계정 수신함에서도 조회/다운로드.
-- 봉투(document_reviews) 1건 = 한 번의 발송. 자원(document_review_items) N건.

CREATE TABLE document_reviews (
    id                   BIGSERIAL    PRIMARY KEY,
    supplier_company_id  BIGINT       NOT NULL REFERENCES companies(id),  -- 발신(공급사)
    bp_company_id        BIGINT       NOT NULL REFERENCES companies(id),  -- 수신(BP)
    message              TEXT,
    sent_by              BIGINT       NOT NULL REFERENCES users(id),
    sent_at              TIMESTAMP    NOT NULL DEFAULT now(),
    read_at              TIMESTAMP
);

CREATE TABLE document_review_items (
    id           BIGSERIAL    PRIMARY KEY,
    review_id    BIGINT       NOT NULL REFERENCES document_reviews(id) ON DELETE CASCADE,
    owner_type   VARCHAR(20)  NOT NULL,
    owner_id     BIGINT       NOT NULL,
    label        VARCHAR(200) NOT NULL,
    doc_count    INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_dri_owner_type CHECK (owner_type IN ('EQUIPMENT', 'PERSON'))
);

CREATE INDEX ix_dr_bp        ON document_reviews (bp_company_id, id DESC);
CREATE INDEX ix_dri_review   ON document_review_items (review_id);
