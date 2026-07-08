-- V43
-- 공급사가 견적 발송 시 차량과 함께 보낼 인원(운전수/오퍼레이터/작업자) + 인당 단가.
-- 장비 배차(quotation_dispatched_equipments)와 동일 패턴. 같은 견적에 같은 인원 중복 send 차단.
CREATE TABLE quotation_dispatched_persons (
    id                    BIGSERIAL PRIMARY KEY,
    quotation_request_id  BIGINT NOT NULL REFERENCES quotation_requests(id),
    supplier_company_id   BIGINT NOT NULL REFERENCES companies(id),
    person_id             BIGINT NOT NULL REFERENCES persons(id),
    daily_price           BIGINT,
    monthly_price         BIGINT,
    notes                 TEXT,
    sent_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_by               BIGINT REFERENCES users(id),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dispatched_request_person UNIQUE (quotation_request_id, person_id)
);
CREATE INDEX idx_dispatched_person_request ON quotation_dispatched_persons(quotation_request_id);
CREATE INDEX idx_dispatched_person_supplier ON quotation_dispatched_persons(supplier_company_id);
