-- BP → 공급사: 자원별(차량/인원) 점검 요청 (자동차 안전점검, 건강검진, 안전교육 등)
-- 공급사가 점검 받은 후 서류 첨부 회신 → BP 승인 → 자원 "투입 대기"
CREATE TABLE resource_check_requests (
    id                   BIGSERIAL    PRIMARY KEY,
    work_plan_id         BIGINT       REFERENCES work_plans(id) ON DELETE SET NULL,
    owner_type           VARCHAR(20)  NOT NULL,
    owner_id             BIGINT       NOT NULL,
    supplier_company_id  BIGINT       NOT NULL REFERENCES companies(id),
    bp_company_id        BIGINT       NOT NULL REFERENCES companies(id),
    check_type           VARCHAR(30)  NOT NULL,
    due_date             DATE,
    notes                TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED',
    document_id          BIGINT       REFERENCES documents(id) ON DELETE SET NULL,
    issued_by            BIGINT       NOT NULL REFERENCES users(id),
    issued_at            TIMESTAMP    NOT NULL DEFAULT now(),
    submitted_at         TIMESTAMP,
    reviewed_by          BIGINT       REFERENCES users(id),
    reviewed_at          TIMESTAMP,
    review_note          TEXT,
    CONSTRAINT ck_rcr_owner_type CHECK (owner_type IN ('EQUIPMENT', 'PERSON')),
    CONSTRAINT ck_rcr_check_type CHECK (check_type IN ('VEHICLE_SAFETY', 'HEALTH_CHECK', 'SAFETY_TRAINING', 'OTHER')),
    CONSTRAINT ck_rcr_status     CHECK (status IN ('REQUESTED', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE INDEX ix_rcr_supplier_status   ON resource_check_requests (supplier_company_id, status);
CREATE INDEX ix_rcr_bp_status         ON resource_check_requests (bp_company_id, status);
CREATE INDEX ix_rcr_owner             ON resource_check_requests (owner_type, owner_id);
CREATE INDEX ix_rcr_work_plan         ON resource_check_requests (work_plan_id);
