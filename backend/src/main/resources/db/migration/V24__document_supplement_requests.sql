-- S-11: 서류 보완 요청 도메인.
-- BP/ADMIN 가 작업계획서 작성 전 단계에서 사이트 자원의 서류 빠짐/만료/REJECTED 발견 시
-- 공급사에게 보완 요청 발송. 공급사가 갱신 서류 업로드 시 자동 RESOLVED.

CREATE TABLE document_supplement_requests (
    id                          BIGSERIAL PRIMARY KEY,
    requester_user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    requester_role              VARCHAR(16) NOT NULL,                -- BP / ADMIN
    target_supplier_company_id  BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    target_owner_type           VARCHAR(16) NOT NULL,                -- PERSON / EQUIPMENT / COMPANY
    target_owner_id             BIGINT NOT NULL,
    document_type_id            BIGINT NOT NULL REFERENCES document_types(id) ON DELETE CASCADE,
    -- 컨텍스트: 어떤 사이트/작업계획서 준비 중에 발생했는지
    context_site_id             BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    context_work_plan_id        BIGINT REFERENCES work_plans(id) ON DELETE SET NULL,
    reason                      TEXT,
    status                      VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN / RESOLVED / CANCELLED
    -- 갱신된 서류 row 추적 — 공급사가 같은 (owner, document_type) 새 서류 업로드 시 자동 매칭
    resolved_doc_id             BIGINT REFERENCES documents(id) ON DELETE SET NULL,
    resolved_at                 TIMESTAMP,
    cancelled_at                TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dsr_supplier ON document_supplement_requests (target_supplier_company_id);
CREATE INDEX idx_dsr_owner ON document_supplement_requests (target_owner_type, target_owner_id);
CREATE INDEX idx_dsr_doc_type ON document_supplement_requests (document_type_id);
CREATE INDEX idx_dsr_status ON document_supplement_requests (status);
CREATE INDEX idx_dsr_site ON document_supplement_requests (context_site_id);
CREATE INDEX idx_dsr_work_plan ON document_supplement_requests (context_work_plan_id);
CREATE INDEX idx_dsr_requester ON document_supplement_requests (requester_user_id);

COMMENT ON TABLE document_supplement_requests IS 'S-11: 작업계획서 준비 단계 서류 보완 요청. 공급사가 갱신 서류 업로드 시 자동 RESOLVED.';
