-- R2 조합(장비+교대조 조종원) 일괄 발행 — 발행 시점 조합 스냅샷.
-- 장비 행 = 자기 equipment id, 조종원 행 = 그 장비 id, 단독(비조합) 발행 = NULL.
-- 원장(equipment_default_operators)의 사후 변경이 과거 발행 기록을 오염시키지 않게 행에 고정.
ALTER TABLE resource_check_requests
    ADD COLUMN combo_equipment_id BIGINT REFERENCES equipment(id) ON DELETE SET NULL;

CREATE INDEX ix_rcr_combo_equipment ON resource_check_requests (combo_equipment_id);
