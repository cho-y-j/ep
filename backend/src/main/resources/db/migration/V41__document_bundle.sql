-- V41: 공급사 → BP 서류 묶음 send 추적.
-- 차량 send (V40 dispatched_equipments) 후 별도 단계로, 공급사가 차량의 서류를 BP 에 명시적 발송.
-- 견적 1건당 공급사 1회 멱등.

CREATE TABLE quotation_document_bundles (
    id                    BIGSERIAL PRIMARY KEY,
    quotation_request_id  BIGINT NOT NULL REFERENCES quotation_requests(id),
    supplier_company_id   BIGINT NOT NULL REFERENCES companies(id),
    sent_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_by               BIGINT REFERENCES users(id),
    include_email         BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at         TIMESTAMP,
    notes                 TEXT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_bundle_request_supplier UNIQUE (quotation_request_id, supplier_company_id)
);

CREATE INDEX idx_bundle_request ON quotation_document_bundles(quotation_request_id);
CREATE INDEX idx_bundle_supplier ON quotation_document_bundles(supplier_company_id);
