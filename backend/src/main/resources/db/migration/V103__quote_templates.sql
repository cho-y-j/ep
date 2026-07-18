-- V103: 견적 템플릿(단가표) — 공급사가 미리 등록해 두는 5분류 단가표.
-- P2b §3.3: 라인 N행 = 장비종류·규격 + 기본단가(일대/월대) + OT 5분류 단가(조출·점심·연장·야간·철야).
-- 발송(내 견적 발송) 화면에서 불러와 발송 내용에 삽입. rows 는 JSONB 패스스루(백엔드 미해석).
CREATE TABLE quote_templates (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_company_id BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    memo                TEXT,
    rows                JSONB        NOT NULL DEFAULT '[]',  -- [{equipment_desc,rate_type,base_rate,rate_early,rate_lunch,rate_evening,rate_night,rate_overnight,note}]
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_quote_templates_supplier ON quote_templates (supplier_company_id);
