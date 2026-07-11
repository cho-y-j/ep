-- 정산 재설계: 근무일수 기반 금액 + 현장별 정산일.
-- 월대 = (월대 ÷ 25) × 근무일수 + OT단가 × OT일수 / 일대 = 일대 × 근무일수 + OT단가 × OT일수.
-- 근무일수·OT일수는 출역 데이터와 배차행이 연결돼 있지 않아, 투입 관리에서 입력하는 정산용 수량을 배차행에 둔다(NULL=미입력).
ALTER TABLE quotation_dispatched_equipments ADD COLUMN settlement_work_days INT;
ALTER TABLE quotation_dispatched_equipments ADD COLUMN settlement_ot_days   INT;
ALTER TABLE quotation_dispatched_persons    ADD COLUMN settlement_work_days INT;
ALTER TABLE quotation_dispatched_persons    ADD COLUMN settlement_ot_days   INT;

-- 현장별 정산 기준일(day-of-month 1~31). NULL=미지정(말일 취급). BP/ADMIN 이 현장에서 지정.
ALTER TABLE sites ADD COLUMN settlement_day INTEGER;
