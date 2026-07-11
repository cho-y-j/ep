-- 인력 배차행 OT 단가 — 정산 OT 공식 완성(월대÷25×근무일수 + OT월단가×OT일수 / 일대×근무일수 + OT일단가×OT일수).
-- 장비(quotation_dispatched_equipments)엔 이미 있는 ot_daily_price/ot_monthly_price 를 인력에도 동일 패턴으로 추가.
-- nullable — 기존 배차행은 NULL → 정산 OT=0 유지(불변). Long ↔ BIGINT.
ALTER TABLE quotation_dispatched_persons ADD COLUMN ot_daily_price   BIGINT;
ALTER TABLE quotation_dispatched_persons ADD COLUMN ot_monthly_price BIGINT;
