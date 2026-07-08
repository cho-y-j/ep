-- V28: 일별 작업확인서
--
-- 워크플로우:
--   1. 장비공급사(or 인력공급사) 사용자가 작업한 날에 BP에 작업확인서 요청 (생성, PENDING)
--   2. 공급사측 사인 (장비공급사=운전수 Person, 인력공급사=계정 User) + BP측 사인. 순서 무관.
--   3. 둘 다 SIGNED 면 COMPLETED.
--   4. 한쪽 사인된 후 내용 수정 시 다이얼로그로 사용자가 선택 (무효화 or 유지).
--
-- 단위: WorkPlan × work_date × issuing_supplier_company_id UNIQUE (하루에 같은 공급사 1건만)

CREATE TABLE work_confirmations (
    id BIGSERIAL PRIMARY KEY,
    work_plan_id BIGINT NOT NULL REFERENCES work_plans(id) ON DELETE CASCADE,
    work_date DATE NOT NULL,
    issuing_supplier_company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    -- 'EQUIPMENT' (장비공급사 — 운전수 사인) / 'MANPOWER' (인력공급사 — 계정 사인)
    issuing_supplier_type VARCHAR(20) NOT NULL CHECK (issuing_supplier_type IN ('EQUIPMENT', 'MANPOWER')),
    bp_company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,

    -- 작업 내용
    work_content TEXT,
    remarks TEXT,

    -- 시간 4단 (구간 문자열 + decimal hours)
    morning_time VARCHAR(64),
    morning_hours NUMERIC(4,2),
    afternoon_time VARCHAR(64),
    afternoon_hours NUMERIC(4,2),
    overtime_time VARCHAR(64),
    overtime_hours NUMERIC(4,2),
    night_time VARCHAR(64),
    night_hours NUMERIC(4,2),
    total_hours NUMERIC(5,2),

    -- 공급사측 사인 (장비공급사면 person_id, 인력공급사면 user_id 중 하나)
    supplier_signer_name VARCHAR(100),
    supplier_signer_person_id BIGINT REFERENCES persons(id) ON DELETE SET NULL,
    supplier_signer_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    supplier_signature_png BYTEA,
    supplier_signed_at TIMESTAMP,

    -- BP측 사인
    bp_signer_name VARCHAR(100),
    bp_signer_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    bp_signature_png BYTEA,
    bp_signed_at TIMESTAMP,

    -- PENDING : 생성됨, 사인 대기 (둘 다 또는 한쪽 사인 안 됨)
    -- COMPLETED : 양쪽 사인 완료
    -- CANCELLED : 사용자 취소
    -- INVALIDATED : 수정으로 인해 사인 무효화 됨 (재사인 필요)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','COMPLETED','CANCELLED','INVALIDATED')),

    created_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (work_plan_id, work_date, issuing_supplier_company_id)
);

CREATE INDEX idx_work_confirmations_work_plan ON work_confirmations(work_plan_id, work_date DESC);
CREATE INDEX idx_work_confirmations_supplier ON work_confirmations(issuing_supplier_company_id, work_date DESC);
CREATE INDEX idx_work_confirmations_bp ON work_confirmations(bp_company_id, work_date DESC);
CREATE INDEX idx_work_confirmations_status ON work_confirmations(status);
