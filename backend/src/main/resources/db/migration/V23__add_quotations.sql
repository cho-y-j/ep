-- S-10: 장비 견적 요청 도메인.
-- BP (또는 ADMIN 대행) 가 사이트의 ACTIVE 참여 EQUIPMENT_SUPPLIER 들의 가용 장비 중에서 골라 가격 제안과 함께 발송.
-- 공급사 별 (equipment, supplier) 쌍 = 1 target row. 공급사가 yes/no 응답. BP/ADMIN 가 최종 수락 시 WorkPlan 자원으로 반영.

CREATE TABLE quotation_requests (
    id                          BIGSERIAL PRIMARY KEY,
    site_id                     BIGINT NOT NULL REFERENCES sites(id) ON DELETE CASCADE,
    requested_by_user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    -- ADMIN 대행 시 어느 BP 컨텍스트로 만들었는지. BP 본인 직접 작성 시 NULL.
    on_behalf_of_bp_company_id  BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    work_period_start           DATE NOT NULL,
    work_period_end             DATE NOT NULL,
    equipment_category          VARCHAR(32) NOT NULL,
    spec_text                   TEXT,
    proposed_daily_rate         INTEGER,
    proposed_monthly_rate       INTEGER,
    count                       INTEGER NOT NULL DEFAULT 1,
    notes                       TEXT,
    status                      VARCHAR(16) NOT NULL DEFAULT 'SENT',  -- DRAFT/SENT/CLOSED/CANCELLED
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_qreq_site ON quotation_requests (site_id);
CREATE INDEX idx_qreq_status ON quotation_requests (status);
CREATE INDEX idx_qreq_requested_by ON quotation_requests (requested_by_user_id);
CREATE INDEX idx_qreq_bp ON quotation_requests (on_behalf_of_bp_company_id);

CREATE TABLE quotation_request_targets (
    id                          BIGSERIAL PRIMARY KEY,
    request_id                  BIGINT NOT NULL REFERENCES quotation_requests(id) ON DELETE CASCADE,
    supplier_company_id         BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    equipment_id                BIGINT REFERENCES equipment(id) ON DELETE SET NULL,
    -- target lifecycle: PENDING → ACCEPTED/REJECTED → FINAL_ACCEPTED (BP/ADMIN 가 최종 채택)
    status                      VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    responded_by_user_id        BIGINT REFERENCES users(id) ON DELETE SET NULL,
    responded_at                TIMESTAMP,
    response_note               TEXT,
    finalized_by_user_id        BIGINT REFERENCES users(id) ON DELETE SET NULL,
    finalized_at                TIMESTAMP,
    finalized_to_work_plan_id   BIGINT REFERENCES work_plans(id) ON DELETE SET NULL,
    finalized_to_wpe_id         BIGINT REFERENCES work_plan_equipment(id) ON DELETE SET NULL,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (request_id, supplier_company_id, equipment_id)
);

CREATE INDEX idx_qrt_request ON quotation_request_targets (request_id);
CREATE INDEX idx_qrt_supplier ON quotation_request_targets (supplier_company_id);
CREATE INDEX idx_qrt_status ON quotation_request_targets (status);
CREATE INDEX idx_qrt_equipment ON quotation_request_targets (equipment_id);

-- work_plan_equipment 에 가격 + 견적 source 추적 컬럼 추가 (자원 추가 시 가격 즉시 저장).
ALTER TABLE work_plan_equipment
    ADD COLUMN daily_rate                INTEGER,
    ADD COLUMN monthly_rate              INTEGER,
    ADD COLUMN source_quotation_target_id BIGINT REFERENCES quotation_request_targets(id) ON DELETE SET NULL;

COMMENT ON COLUMN work_plan_equipment.daily_rate IS '견적 수락 시 결정된 일대 단가 (원)';
COMMENT ON COLUMN work_plan_equipment.monthly_rate IS '견적 수락 시 결정된 월대 단가 (원)';
COMMENT ON COLUMN work_plan_equipment.source_quotation_target_id IS '이 자원 행이 어느 견적 target finalize 로부터 만들어졌는지 추적 (NULL = 직접 추가)';
