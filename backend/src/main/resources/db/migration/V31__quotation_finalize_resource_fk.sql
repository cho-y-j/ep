-- manpower 견적 finalize 시 work_plan_person.id 도 저장하기 위해
-- finalized_to_wpe_id 의 FK 제약 제거 (컬럼은 그대로 — wpe.id 또는 wpp.id 의 의미적 union).
-- request_type 에 따라 어느 테이블 참조인지 결정.

ALTER TABLE quotation_request_targets
    DROP CONSTRAINT IF EXISTS quotation_request_targets_finalized_to_wpe_id_fkey;
