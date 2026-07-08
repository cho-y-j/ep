-- V33: 공개입찰 모드 + ClientOrg(원청기관) + 자원 ClientOrg 이력
--
-- 변경 요지
--   1) client_orgs 신규 테이블 — 삼성/SK/현대 등 원청 본사. ADMIN 이 관리.
--   2) equipment_client_org_history / person_client_org_history — 자원이 어느 원청 현장에 언제 들어갔는지.
--      source = ADMIN(수동 등록) / WORK_PLAN(작업계획서 STARTED 시 자동).
--   3) quotation_proposals — 공개입찰에서 공급사가 자유 제안. 기존 quotation_request_targets 는 지정배차 유지.
--   4) quotation_requests 컬럼 — mode(OPEN_BID/TARGETED), client_org_id, work_location_text, site_id nullable.
--
-- 호환: 기존 견적 데이터는 mode=TARGETED 로 처리. site_id 가 있는 행은 그대로, 없는 신규 OPEN_BID 만 nullable 활용.

-- ── 1) client_orgs ───────────────────────────────────────────────
CREATE TABLE client_orgs (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(32)  NOT NULL UNIQUE,
    note        TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_client_orgs_name ON client_orgs (name);

INSERT INTO client_orgs (name, code) VALUES
    ('삼성',   'SAMSUNG'),
    ('SK',     'SK'),
    ('현대',   'HYUNDAI'),
    ('LG',     'LG'),
    ('포스코', 'POSCO'),
    ('GS',     'GS');

-- ── 2) 자원-ClientOrg 이력 ──────────────────────────────────────
-- source: ADMIN | WORK_PLAN
-- period_end NULL 가능 (진행중 또는 기간 미상)
CREATE TABLE equipment_client_org_history (
    id              BIGSERIAL PRIMARY KEY,
    equipment_id    BIGINT       NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    client_org_id   BIGINT       NOT NULL REFERENCES client_orgs(id) ON DELETE RESTRICT,
    period_start    DATE         NOT NULL,
    period_end      DATE,
    source          VARCHAR(16)  NOT NULL,
    source_ref_id   BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_eq_co_hist_equipment      ON equipment_client_org_history (equipment_id);
CREATE INDEX idx_eq_co_hist_client_org     ON equipment_client_org_history (client_org_id);
CREATE INDEX idx_eq_co_hist_equipment_org  ON equipment_client_org_history (equipment_id, client_org_id);

CREATE TABLE person_client_org_history (
    id              BIGSERIAL PRIMARY KEY,
    person_id       BIGINT       NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    client_org_id   BIGINT       NOT NULL REFERENCES client_orgs(id) ON DELETE RESTRICT,
    period_start    DATE         NOT NULL,
    period_end      DATE,
    source          VARCHAR(16)  NOT NULL,
    source_ref_id   BIGINT,
    created_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_pp_co_hist_person      ON person_client_org_history (person_id);
CREATE INDEX idx_pp_co_hist_client_org  ON person_client_org_history (client_org_id);
CREATE INDEX idx_pp_co_hist_person_org  ON person_client_org_history (person_id, client_org_id);

-- ── 3) quotation_requests 확장 ───────────────────────────────────
-- 기존 row 는 mode='TARGETED' 로 채워서 호환 유지.
ALTER TABLE quotation_requests
    ADD COLUMN mode               VARCHAR(16),
    ADD COLUMN client_org_id      BIGINT REFERENCES client_orgs(id) ON DELETE RESTRICT,
    ADD COLUMN work_location_text TEXT;

UPDATE quotation_requests SET mode = 'TARGETED' WHERE mode IS NULL;
ALTER TABLE quotation_requests ALTER COLUMN mode SET NOT NULL;
ALTER TABLE quotation_requests ALTER COLUMN mode SET DEFAULT 'TARGETED';

-- OPEN_BID 는 site 없이 가능하므로 site_id nullable 로 완화.
ALTER TABLE quotation_requests ALTER COLUMN site_id DROP NOT NULL;

CREATE INDEX idx_quotation_requests_mode          ON quotation_requests (mode);
CREATE INDEX idx_quotation_requests_client_org_id ON quotation_requests (client_org_id);

-- ── 4) quotation_proposals ──────────────────────────────────────
-- 공개입찰에서 공급사가 자기 자원 + 단가 제출.
-- status: SUBMITTED | FINAL_ACCEPTED | REJECTED | WITHDRAWN
-- 한 공급사가 같은 견적에 N개 제안 가능 (자기 보유 자원 수만큼).
CREATE TABLE quotation_proposals (
    id                  BIGSERIAL PRIMARY KEY,
    request_id          BIGINT       NOT NULL REFERENCES quotation_requests(id) ON DELETE CASCADE,
    supplier_company_id BIGINT       NOT NULL REFERENCES companies(id) ON DELETE RESTRICT,
    proposed_by_user_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    equipment_id        BIGINT       REFERENCES equipment(id) ON DELETE RESTRICT,
    person_id           BIGINT       REFERENCES persons(id) ON DELETE RESTRICT,
    daily_rate          INTEGER,
    monthly_rate        INTEGER,
    note                TEXT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'SUBMITTED',
    finalized_by_user_id    BIGINT,
    finalized_at            TIMESTAMP,
    finalized_to_work_plan_id BIGINT,
    finalized_to_wpe_id     BIGINT,
    finalized_to_wpp_id     BIGINT,
    rejected_at             TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- 같은 (request, supplier, equipment) 또는 (request, supplier, person) 중복 차단
    CONSTRAINT uq_proposal_equipment UNIQUE (request_id, supplier_company_id, equipment_id),
    CONSTRAINT uq_proposal_person    UNIQUE (request_id, supplier_company_id, person_id),
    CONSTRAINT ck_proposal_resource_one CHECK (
        (equipment_id IS NOT NULL AND person_id IS NULL) OR
        (equipment_id IS NULL AND person_id IS NOT NULL)
    )
);
CREATE INDEX idx_qp_request   ON quotation_proposals (request_id);
CREATE INDEX idx_qp_supplier  ON quotation_proposals (supplier_company_id);
CREATE INDEX idx_qp_status    ON quotation_proposals (status);

-- ── 5) work_plan_equipment/person 에 source_proposal_id 추가 ────
ALTER TABLE work_plan_equipment
    ADD COLUMN source_proposal_id BIGINT REFERENCES quotation_proposals(id) ON DELETE SET NULL;
ALTER TABLE work_plan_persons
    ADD COLUMN source_proposal_id BIGINT REFERENCES quotation_proposals(id) ON DELETE SET NULL;
