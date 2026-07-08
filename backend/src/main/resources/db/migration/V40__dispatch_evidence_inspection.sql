-- V40
-- 1) quotation_dispatched_equipments — 선정 후 공급사가 보내기로 한 차량 + 단가. 멱등.
-- 2) quotation_comparison_snapshots — 선정 시점 비교 증거 동결.
-- 3) safety_inspections — 차량검사 / 입소검사 도메인. BP 등록 → 공급사 통보 → 완료.

-- ============================================================
-- 1) 선정 통보 받은 공급사가 어떤 차를 보낼지 + 차마다 단가.
-- 같은 견적에 같은 장비 중복 send 차단 (UNIQUE).
-- ============================================================
CREATE TABLE quotation_dispatched_equipments (
    id                    BIGSERIAL PRIMARY KEY,
    quotation_request_id  BIGINT NOT NULL REFERENCES quotation_requests(id),
    supplier_company_id   BIGINT NOT NULL REFERENCES companies(id),
    equipment_id          BIGINT NOT NULL REFERENCES equipment(id),
    daily_price           BIGINT,
    monthly_price         BIGINT,
    notes                 TEXT,
    sent_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_by               BIGINT REFERENCES users(id),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dispatched_request_equipment UNIQUE (quotation_request_id, equipment_id)
);
CREATE INDEX idx_dispatched_request ON quotation_dispatched_equipments(quotation_request_id);
CREATE INDEX idx_dispatched_supplier ON quotation_dispatched_equipments(supplier_company_id);

-- ============================================================
-- 2) 선정 시점 비교 증거 동결.
-- snapshot_json: [{supplier_id, supplier_name, daily_price, monthly_price, note, submitted_at}, ...]
-- ============================================================
CREATE TABLE quotation_comparison_snapshots (
    id                    BIGSERIAL PRIMARY KEY,
    quotation_request_id  BIGINT NOT NULL REFERENCES quotation_requests(id),
    selected_proposal_id  BIGINT REFERENCES quotation_proposals(id),
    selected_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    selected_by           BIGINT REFERENCES users(id),
    snapshot_json         TEXT NOT NULL,
    selection_reason      TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_snapshot_request ON quotation_comparison_snapshots(quotation_request_id);

-- ============================================================
-- 3) 안전점검 도메인
-- target_type: VEHICLE (장비) | PERSON (인원)
-- kind: VEHICLE_INSPECTION (차량검사, 며칠 사전) | ENTRY_CHECK (입소검사, 인원/시간)
-- status: PENDING → SENT → CONFIRMED → COMPLETED, CANCELLED
-- ============================================================
CREATE TABLE safety_inspections (
    id                BIGSERIAL PRIMARY KEY,
    site_id           BIGINT NOT NULL REFERENCES sites(id),
    supplier_company_id BIGINT REFERENCES companies(id),
    target_type       VARCHAR(32) NOT NULL,
    target_id         BIGINT NOT NULL,
    kind              VARCHAR(32) NOT NULL,
    scheduled_at      TIMESTAMP NOT NULL,
    duration_minutes  INT,
    inspector_id      BIGINT REFERENCES users(id),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sent_at           TIMESTAMP,
    confirmed_at      TIMESTAMP,
    completed_at      TIMESTAMP,
    result_notes      TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by        BIGINT REFERENCES users(id),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_safety_site ON safety_inspections(site_id);
CREATE INDEX idx_safety_target ON safety_inspections(target_type, target_id);
CREATE INDEX idx_safety_supplier ON safety_inspections(supplier_company_id);
