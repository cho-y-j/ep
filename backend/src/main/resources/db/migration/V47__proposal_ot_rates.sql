-- V47: QuotationProposal 에 OT 단가 (일대/월대) 컬럼 추가
ALTER TABLE quotation_proposals
    ADD COLUMN IF NOT EXISTS ot_daily_rate INTEGER,
    ADD COLUMN IF NOT EXISTS ot_monthly_rate INTEGER;
