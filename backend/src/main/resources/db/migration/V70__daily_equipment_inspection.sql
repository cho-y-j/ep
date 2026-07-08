-- 차량/장비 일상점검(시업점검) — 조종원이 매일 제출. items=체크리스트 JSON.
CREATE TABLE daily_equipment_inspections (
    id                     BIGSERIAL PRIMARY KEY,
    equipment_id           BIGINT NOT NULL,
    inspected_by_person_id BIGINT,
    inspect_date           DATE NOT NULL,
    items                  TEXT,
    photo_key              VARCHAR(255),
    notes                  TEXT,
    overall                VARCHAR(20),
    created_at             TIMESTAMP NOT NULL
);
CREATE INDEX ix_daily_eq_insp_equipment ON daily_equipment_inspections(equipment_id, inspect_date DESC);

-- 차량관리 만료/교체 due (정기검사/오일/등록). 임박 시 스케줄러가 공급사에 알림.
ALTER TABLE equipment
    ADD COLUMN inspection_due_date DATE,
    ADD COLUMN oil_change_due_date DATE,
    ADD COLUMN registration_expiry DATE;
