-- ============================================================
-- Phase S-5: 작업계획서 도메인
-- BP 사가 자기 현장에 참여 중인 공급사 자원으로 작업계획서를 만든다.
-- 생성 당시의 서류 컴플라이언스 상태를 work_plan_compliance_checks 에 스냅샷.
-- ============================================================

CREATE TABLE work_plans (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    bp_company_id BIGINT NOT NULL REFERENCES companies(id),
    work_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    title VARCHAR(150) NOT NULL,
    work_location VARCHAR(255),
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',  -- DRAFT | SUBMITTED | APPROVED | IN_PROGRESS | DONE | CANCELLED
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    submitted_at TIMESTAMP,
    submitted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_at TIMESTAMP,
    approved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    cancelled_at TIMESTAMP,
    cancelled_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    cancel_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_work_plans_site ON work_plans(site_id, work_date DESC);
CREATE INDEX idx_work_plans_bp ON work_plans(bp_company_id, work_date DESC);
CREATE INDEX idx_work_plans_status ON work_plans(status);
CREATE INDEX idx_work_plans_work_date ON work_plans(work_date);

-- 작업계획서 ↔ 장비
CREATE TABLE work_plan_equipment (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id),
    supplier_company_id BIGINT NOT NULL REFERENCES companies(id),
    purpose VARCHAR(100),
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (work_plan_id, equipment_id)
);

CREATE INDEX idx_wpe_supplier ON work_plan_equipment(supplier_company_id);
CREATE INDEX idx_wpe_equipment ON work_plan_equipment(equipment_id);

-- 작업계획서 ↔ 인원 (선택적으로 장비에 매칭)
CREATE TABLE work_plan_persons (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES persons(id),
    supplier_company_id BIGINT NOT NULL REFERENCES companies(id),
    equipment_id BIGINT REFERENCES equipment(id),  -- 이 인원이 어떤 장비 담당인지 (조종원/신호수 등)
    role VARCHAR(32),                               -- OPERATOR / SIGNALER / GUIDE ...
    note VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (work_plan_id, person_id)
);

CREATE INDEX idx_wpp_supplier ON work_plan_persons(supplier_company_id);
CREATE INDEX idx_wpp_person ON work_plan_persons(person_id);
CREATE INDEX idx_wpp_equipment ON work_plan_persons(equipment_id) WHERE equipment_id IS NOT NULL;

-- 작업계획서 자원 추가/생성 시점의 서류 컴플라이언스 스냅샷
CREATE TABLE work_plan_compliance_checks (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    target_type VARCHAR(16) NOT NULL,      -- EQUIPMENT | PERSON
    target_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,           -- OK | WARNING | BLOCKED | OVERRIDDEN
    reason VARCHAR(255),
    checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    override_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    override_reason VARCHAR(255)
);

CREATE INDEX idx_wpcc_plan ON work_plan_compliance_checks(work_plan_id, checked_at DESC);
CREATE INDEX idx_wpcc_target ON work_plan_compliance_checks(target_type, target_id);
