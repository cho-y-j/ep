-- V98: 기통과 소급 + 구두승인 — 현장 즉시 도입(§3.8).
-- 공급사가 이미 투입 중인 자원을 현장 단위로 신고 → BP 소급 일괄 승인(REQUESTED→APPROVED)
-- 또는 공급사 구두승인(VERBAL) 즉시 확정. 확정 시 기존 ResourceCheck 승인행 자동 생성(서비스 훅).
CREATE TABLE site_resource_onboardings (
    id                  BIGSERIAL PRIMARY KEY,
    supplier_company_id BIGINT       NOT NULL,
    site_id             BIGINT,
    site_name           VARCHAR(255),           -- 현장 미지정 시 텍스트 폴백.
    bp_company_id       BIGINT,                 -- 구두모드에서 미지정 가능(NULL).
    owner_type          VARCHAR(20)  NOT NULL,  -- EQUIPMENT | PERSON
    owner_id            BIGINT       NOT NULL,
    inspection_date     DATE,                   -- 반입검사 완료일(선택).
    education_date      DATE,                   -- 안전교육 완료일(선택).
    health_date         DATE,                   -- 건강검진 완료일(선택).
    mode                VARCHAR(16)  NOT NULL,  -- REQUESTED | APPROVED | VERBAL
    verbal_approver     VARCHAR(255),           -- 구두승인자명.
    verbal_at           TIMESTAMP,
    memo                TEXT,
    requested_by        BIGINT,
    requested_at        TIMESTAMP    NOT NULL DEFAULT now(),
    approved_by         BIGINT,
    approved_at         TIMESTAMP
);

CREATE INDEX idx_sro_owner ON site_resource_onboardings (site_id, owner_type, owner_id);
