-- V48: 자원(차량/인원) 필수 제약 완화 — 단가만 응찰 허용. 둘 다 동시 지정만 금지.
ALTER TABLE quotation_proposals
    DROP CONSTRAINT IF EXISTS ck_proposal_resource_one;
ALTER TABLE quotation_proposals
    ADD CONSTRAINT ck_proposal_resource_at_most_one
        CHECK (NOT (equipment_id IS NOT NULL AND person_id IS NOT NULL));
