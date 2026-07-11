-- V80: 선정(OPEN_BID finalize) 직후 만들어지는 "배차 초안".
-- 선정된 제안의 자원(장비/인원)+응찰단가를 별도 테이블에 담아둔다.
-- 공급사가 "확인 후 발송" 하면 기존 send() 를 그대로 호출해 실제 dispatched 행을 만든다.
-- 격리 목적: 정산/작업계획서 자동추가/PDF/Excel/투입목록/서류묶음/갱신알림 등 기존 dispatched 리더는
--            이 별도 테이블을 읽지 않으므로 confirm 전까지 초안이 어디에도 나타나지 않는다.
-- resource_type: EQUIPMENT | PERSON,  status: DRAFT | CONFIRMED | DISCARDED.
-- 단가만 응찰(자원 미지정)한 EQUIPMENT 초안은 equipment_id NULL 허용(V45 선례). MANPOWER 단가-only 는 초안 미생성.
CREATE TABLE quotation_dispatch_drafts (
    id                    BIGSERIAL PRIMARY KEY,
    quotation_request_id  BIGINT NOT NULL REFERENCES quotation_requests(id),
    supplier_company_id   BIGINT NOT NULL REFERENCES companies(id),
    resource_type         VARCHAR(16) NOT NULL,
    equipment_id          BIGINT REFERENCES equipment(id),
    person_id             BIGINT REFERENCES persons(id),
    daily_price           BIGINT,
    monthly_price         BIGINT,
    ot_daily_price        BIGINT,
    ot_monthly_price      BIGINT,
    notes                 TEXT,
    source_proposal_id    BIGINT REFERENCES quotation_proposals(id),
    status                VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dispatch_draft_request ON quotation_dispatch_drafts(quotation_request_id);
CREATE INDEX idx_dispatch_draft_supplier ON quotation_dispatch_drafts(supplier_company_id);
