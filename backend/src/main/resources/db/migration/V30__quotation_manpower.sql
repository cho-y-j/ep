-- 견적 요청에 인력공급 타입 추가
-- request_type: EQUIPMENT (기본), MANPOWER
-- manpower_role: PersonRole (WORK_DIRECTOR, GUIDE, SIGNALER, FIRE_WATCH, SITE_MANAGER, INSPECTOR, OPERATOR) — request_type=MANPOWER 일 때만 사용
-- equipment_category: nullable 로 완화 (request_type=MANPOWER 일 때는 NULL)

ALTER TABLE quotation_requests
    ADD COLUMN request_type VARCHAR(16) NOT NULL DEFAULT 'EQUIPMENT';

ALTER TABLE quotation_requests
    ADD COLUMN manpower_role VARCHAR(32);

ALTER TABLE quotation_requests
    ALTER COLUMN equipment_category DROP NOT NULL;

-- 정합성 — 둘 중 하나는 채워져 있어야 함
ALTER TABLE quotation_requests
    ADD CONSTRAINT quotation_request_type_consistent
    CHECK (
        (request_type = 'EQUIPMENT' AND equipment_category IS NOT NULL AND manpower_role IS NULL)
        OR
        (request_type = 'MANPOWER' AND manpower_role IS NOT NULL AND equipment_category IS NULL)
    );

CREATE INDEX idx_quotation_request_type ON quotation_requests(request_type);

-- target 에 person_id 추가 (MANPOWER 견적의 제안 인원)
ALTER TABLE quotation_request_targets
    ADD COLUMN person_id BIGINT REFERENCES persons(id) ON DELETE SET NULL;

CREATE INDEX idx_quotation_request_targets_person ON quotation_request_targets(person_id) WHERE person_id IS NOT NULL;

-- 기존 uniqueConstraint 는 (request_id, supplier_company_id, equipment_id) 라 person_id 만 다른 경우 충돌. 인덱스 교체.
ALTER TABLE quotation_request_targets DROP CONSTRAINT IF EXISTS quotation_request_targets_request_id_supplier_company_id_eq_key;
CREATE UNIQUE INDEX uniq_quotation_target_per_resource ON quotation_request_targets
  (request_id, supplier_company_id, COALESCE(equipment_id, 0), COALESCE(person_id, 0));
