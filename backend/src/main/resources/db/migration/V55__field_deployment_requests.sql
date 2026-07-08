-- 현장 투입 요청 (공급사 → BP)
-- 견적 수락된 자원을 공급사가 BP 에게 "지금 보낼게요" 요청 → BP 수락 → ACTIVE.
CREATE TABLE field_deployment_requests (
    id BIGSERIAL PRIMARY KEY,
    supplier_company_id BIGINT NOT NULL,
    bp_company_id BIGINT NOT NULL,
    resource_type VARCHAR(20) NOT NULL,
    resource_id BIGINT NOT NULL,
    target_site_id BIGINT,
    start_date DATE,
    note TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    requested_by_user_id BIGINT NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_by_user_id BIGINT,
    reviewed_at TIMESTAMP,
    review_note TEXT,
    activated_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT field_dep_status_check
        CHECK (status IN ('REQUESTED','ACCEPTED','REJECTED','ACTIVE','COMPLETED','CANCELLED')),
    CONSTRAINT field_dep_resource_check
        CHECK (resource_type IN ('EQUIPMENT','PERSON'))
);
CREATE INDEX idx_field_dep_supplier ON field_deployment_requests(supplier_company_id, status);
CREATE INDEX idx_field_dep_bp ON field_deployment_requests(bp_company_id, status);
CREATE INDEX idx_field_dep_resource ON field_deployment_requests(resource_type, resource_id);
