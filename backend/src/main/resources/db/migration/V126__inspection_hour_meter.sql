-- 조종원 일상점검에 가동시간(아워미터)·운행거리 입력 추가. 둘 다 선택(NULL 허용) — 표시·이력 축적 전용.
-- 기존 누적 가동시간(작업확인서 기반)·정비도래 로직과 무관(정본화 안 함, 소스 분리).
ALTER TABLE daily_equipment_inspections
    ADD COLUMN hour_meter  NUMERIC,
    ADD COLUMN odometer_km NUMERIC;
