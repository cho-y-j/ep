-- V46: 이행지시 (ComplianceOrder)
-- BP가 공급사에 안전점검/건강검진 등 이행 지시 발행 → 공급사 증빙 업로드 → BP 검토.

CREATE TABLE compliance_orders (
    id BIGSERIAL PRIMARY KEY,

    bp_company_id BIGINT NOT NULL REFERENCES companies(id),
    supplier_company_id BIGINT NOT NULL REFERENCES companies(id),

    -- 대상 자원
    target_type VARCHAR(16) NOT NULL,          -- 'VEHICLE' | 'PERSON'
    target_id BIGINT NOT NULL,                  -- equipment_id 또는 person_id

    -- 지시 내용
    order_type VARCHAR(32) NOT NULL,            -- 'SAFETY_INSPECTION' | 'HEALTH_CHECK' | 'OTHER'
    order_subtype VARCHAR(100),                 -- OTHER 자유텍스트 또는 상세 (예: "정기 안전점검")
    due_date DATE NOT NULL,
    request_notes TEXT,

    -- 상태 머신
    status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',  -- 'REQUESTED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED'

    -- 공급사 제출
    submitted_at TIMESTAMP,
    submission_notes TEXT,
    proof_storage_key VARCHAR(500),
    proof_filename VARCHAR(255),
    proof_content_type VARCHAR(100),

    -- BP 검토
    reviewed_at TIMESTAMP,
    reviewed_by BIGINT REFERENCES users(id),
    rejection_reason TEXT,

    -- meta
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT compliance_orders_target_type_check
        CHECK (target_type IN ('VEHICLE', 'PERSON')),
    CONSTRAINT compliance_orders_order_type_check
        CHECK (order_type IN ('SAFETY_INSPECTION', 'HEALTH_CHECK', 'OTHER')),
    CONSTRAINT compliance_orders_status_check
        CHECK (status IN ('REQUESTED', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_compliance_orders_bp ON compliance_orders(bp_company_id, status);
CREATE INDEX idx_compliance_orders_supplier ON compliance_orders(supplier_company_id, status);
CREATE INDEX idx_compliance_orders_target ON compliance_orders(target_type, target_id);
CREATE INDEX idx_compliance_orders_due ON compliance_orders(due_date) WHERE status IN ('REQUESTED', 'SUBMITTED');
