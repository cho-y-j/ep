-- V97: 계약(Contract) — 단가의 원천. 공급사가 직접 등록(견적 없이도), BP 는 자기 앞 계약 조회.
-- P0.5a §3.3.1: BP↔공급사·현장·장비종류/규격·기본단가(일대/월대)+OT 5분류 단가·기간·계약서 파일.
CREATE TABLE contracts (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_company_id BIGINT       NOT NULL,
    bp_company_id       BIGINT,                 -- 회사 가입 BP 지정 시. 단독모드면 NULL + bp_name.
    bp_name             VARCHAR(255),           -- 미가입 BP 이름만 (단독모드 텍스트 폴백).
    site_id             BIGINT,
    site_name           VARCHAR(255),           -- 현장 미지정 시 텍스트 폴백.
    title               VARCHAR(255),
    equipment_desc      VARCHAR(500),           -- 장비 종류·규격 텍스트.
    rate_type           VARCHAR(16)  NOT NULL,  -- DAILY | MONTHLY
    base_rate           BIGINT,                 -- 기본단가(일대/월대), 원.
    rate_early          BIGINT,                 -- OT 조출.
    rate_lunch          BIGINT,                 -- OT 점심.
    rate_evening        BIGINT,                 -- OT 연장.
    rate_night          BIGINT,                 -- OT 야간.
    rate_overnight      BIGINT,                 -- OT 철야.
    start_date          DATE,
    end_date            DATE,
    file_key            VARCHAR(255),           -- 계약서 스캔 첨부(FileStorage key).
    memo                TEXT,
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_contracts_supplier ON contracts (supplier_company_id);
CREATE INDEX idx_contracts_bp       ON contracts (bp_company_id);
