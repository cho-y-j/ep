-- V49: 단가 4행(일대/OT일대/월대/OT월대) 각각의 비고
-- DispatchedEquipment(견적 응답) + QuotationProposal(공개입찰 응찰) 둘 다.

ALTER TABLE quotation_dispatched_equipments
    ADD COLUMN IF NOT EXISTS daily_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ot_daily_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS monthly_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ot_monthly_note VARCHAR(255);

ALTER TABLE quotation_proposals
    ADD COLUMN IF NOT EXISTS daily_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ot_daily_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS monthly_note VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ot_monthly_note VARCHAR(255);
