-- V44: 견적서 엑셀 양식 통합
-- 1) 회사 프로필 확장 — 견적서 우측 "공급자" 박스용
-- 2) users.show_in_quote / quote_display_order — 담당자 노출 제어
-- 3) quotation_dispatched_equipments.ot_daily_price / ot_monthly_price — 4칸 단가

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS business_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS business_category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS business_subcategory VARCHAR(200),
    ADD COLUMN IF NOT EXISTS ceo_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS fax VARCHAR(32);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS show_in_quote BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS quote_display_order INT;

ALTER TABLE quotation_dispatched_equipments
    ADD COLUMN IF NOT EXISTS ot_daily_price BIGINT,
    ADD COLUMN IF NOT EXISTS ot_monthly_price BIGINT;
