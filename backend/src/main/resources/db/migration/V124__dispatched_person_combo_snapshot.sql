-- R4 조합(장비+교대조 조종원) 배차 — 배차 시점 조합 스냅샷.
-- 조종원 행이 같은 견적에 함께 배차된 장비를 가리킴(단독 배차=NULL). quotation_dispatched_equipments 는 무변경.
-- 원장(equipment_default_operators)의 사후 변경이 과거 배차 기록을 오염시키지 않게 행에 고정. V43 FK 스타일.
ALTER TABLE quotation_dispatched_persons
    ADD COLUMN combo_equipment_id BIGINT REFERENCES equipment(id);

CREATE INDEX idx_dispatched_person_combo ON quotation_dispatched_persons(combo_equipment_id);
