-- V102: 업체변경 신청서 v0 (L2a, §3.6·§7). 실양식 수령 전 임의양식 v0 — 현장/적용일/변경구분/
-- 변경 전후 표(자동채움)/사유/신규자원 L3 확인(deploy-check 스냅샷)/신청·확인 서명(후속).
-- 서명 수집은 후속 — 본 테이블은 신청서 데이터 + 신청 시점 L3 판정 스냅샷만 보관.
CREATE TABLE resource_change_requests (
    id                  BIGSERIAL PRIMARY KEY,
    site_id             BIGINT,
    site_name           VARCHAR(255),
    bp_company_id       BIGINT,
    bp_name             VARCHAR(255),
    supplier_company_id BIGINT       NOT NULL,
    change_kind         VARCHAR(16)  NOT NULL,   -- EQUIPMENT | OPERATOR | COMPANY
    old_equipment_id    BIGINT,
    new_equipment_id    BIGINT,
    old_person_id       BIGINT,
    new_person_id       BIGINT,
    old_label           VARCHAR(255),            -- 변경 전 표시 라벨(장비명/조종원명/업체명).
    new_label           VARCHAR(255),
    old_vehicle_no      VARCHAR(64),
    new_vehicle_no      VARCHAR(64),
    old_operator_name   VARCHAR(120),
    new_operator_name   VARCHAR(120),
    old_contact         VARCHAR(64),
    new_contact         VARCHAR(64),
    reason              TEXT,
    apply_date          DATE,
    l3_snapshot         JSONB,                   -- 신청 시점 신규자원 deploy-check 결과.
    work_plan_id        BIGINT,                  -- 계획서 연계 시(선택).
    status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',   -- DRAFT | CONFIRMED
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_rcr_supplier ON resource_change_requests (supplier_company_id, id DESC);
CREATE INDEX idx_rcr_bp ON resource_change_requests (bp_company_id, id DESC);
