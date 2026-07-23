-- R3 조합(장비+교대조 조종원) 투입 요청 — 요청 시점 조합 스냅샷.
-- 장비 행 = 자기 equipment id, 조종원 행 = 그 장비 id, 단독(비조합) 요청 = NULL.
-- 원장(equipment_default_operators)의 사후 변경이 과거 요청 기록을 오염시키지 않게 행에 고정.
-- V55 원생성 스타일에 맞춰 FK 미사용.
ALTER TABLE field_deployment_requests
    ADD COLUMN combo_equipment_id BIGINT;

CREATE INDEX idx_field_dep_combo_equipment ON field_deployment_requests(combo_equipment_id);
