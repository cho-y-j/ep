-- V99: 일일 작업확인서(daily_work_logs) — 하루 1건(작업내용·위치·구분·OT 5분류 시간).
-- P0.5b §3.6.3: 일일 전표(장비업체 보관) 디지털화 → BP 인앱 서명 or 전표 사진 갈음(단독모드).
-- 월간 원장·작업자 내 작업내역은 이 테이블의 뷰. OT 5분류 = 조출·점심·연장·야간·철야(고정 시간대).
CREATE TABLE daily_work_logs (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_company_id BIGINT       NOT NULL,
    site_id             BIGINT,
    site_name           VARCHAR(255),           -- 현장 미지정 시 텍스트 폴백.
    bp_company_id       BIGINT,                 -- 서명 주체(단독모드면 NULL).
    contract_id         BIGINT,                 -- 단가 연결(정산 원천). NULL 가능.
    equipment_id        BIGINT,
    person_id           BIGINT,                 -- 운전원/작업자.
    work_date           DATE         NOT NULL,
    work_content        VARCHAR(500),
    work_location       VARCHAR(255),
    rate_type           VARCHAR(16)  NOT NULL,  -- DAILY | MONTHLY (계약 있으면 상속).
    ot_early            NUMERIC(4,1) NOT NULL DEFAULT 0,  -- 조출(05-07).
    ot_lunch            NUMERIC(4,1) NOT NULL DEFAULT 0,  -- 점심(12-13).
    ot_evening          NUMERIC(4,1) NOT NULL DEFAULT 0,  -- 연장(17-19).
    ot_night            NUMERIC(4,1) NOT NULL DEFAULT 0,  -- 야간(19-21:30).
    ot_overnight        NUMERIC(4,1) NOT NULL DEFAULT 0,  -- 철야(21-06).
    start_time          TIME,
    end_time            TIME,
    memo                TEXT,                   -- 연장작업 내용 등.
    sign_status         VARCHAR(16)  NOT NULL DEFAULT 'UNSIGNED',  -- UNSIGNED | SIGNED | PHOTO
    bp_signed_by        BIGINT,
    bp_signed_at        TIMESTAMP,
    sign_image          BYTEA,                  -- BP 서명 캔버스 PNG.
    slip_photo_key      VARCHAR(255),           -- 종이 전표 사진(단독모드 갈음).
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_dwl_supplier  ON daily_work_logs (supplier_company_id, work_date);
CREATE INDEX idx_dwl_bp        ON daily_work_logs (bp_company_id, sign_status);
CREATE INDEX idx_dwl_equipment ON daily_work_logs (equipment_id, work_date);
CREATE INDEX idx_dwl_person    ON daily_work_logs (person_id, work_date);
