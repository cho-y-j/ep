-- S-9-B: skep 원본 작업계획서 워크시트 132 필드 + 역할 배정 + 첨부 선택 상태 저장
ALTER TABLE work_plans
    ADD COLUMN form_values JSONB,
    ADD COLUMN equipment_supplier_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    ADD COLUMN manpower_supplier_company_id  BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    ADD COLUMN current_equipment_id BIGINT REFERENCES equipment(id) ON DELETE SET NULL;

COMMENT ON COLUMN work_plans.form_values
    IS 'skep 워크시트 schema (lib/worksheet/schema.ts) 132 필드 + role_assign + 선택 첨부 ID. 키 형식 자유.';

CREATE INDEX IF NOT EXISTS idx_work_plans_equipment_supplier
    ON work_plans (equipment_supplier_company_id);
CREATE INDEX IF NOT EXISTS idx_work_plans_manpower_supplier
    ON work_plans (manpower_supplier_company_id);
