-- ============================================================
-- Phase S-8.5: assignment 자동 해제를 위한 추적 컬럼
-- 작업계획서 시작 시 자동 생성된 배치만 완료/취소 시 자동 해제하기 위해
-- triggered_by_work_plan_id 컬럼을 추가한다.
-- 수동 배치(AssignmentService.assignEquipment 등) 는 NULL 로 유지.
-- ============================================================

ALTER TABLE equipment_site_assignments
    ADD COLUMN triggered_by_work_plan_id BIGINT REFERENCES work_plans(id) ON DELETE SET NULL;

ALTER TABLE person_site_assignments
    ADD COLUMN triggered_by_work_plan_id BIGINT REFERENCES work_plans(id) ON DELETE SET NULL;

CREATE INDEX idx_eqa_triggered_by ON equipment_site_assignments(triggered_by_work_plan_id)
    WHERE triggered_by_work_plan_id IS NOT NULL;
CREATE INDEX idx_pa_triggered_by ON person_site_assignments(triggered_by_work_plan_id)
    WHERE triggered_by_work_plan_id IS NOT NULL;
