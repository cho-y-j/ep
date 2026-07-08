-- 한 번 발송으로 묶이는 견적들을 같은 bundle_id 로 그룹화.
-- 한 사이트에 장비 1건 + 인력 N건을 한 마법사에서 발송 → 같은 UUID 공유.
-- 목록에서 묶음 카드로 표시, 모두 finalize 되면 같은 WorkPlan 으로 자동 매칭.

ALTER TABLE quotation_requests
    ADD COLUMN bundle_id UUID;

CREATE INDEX idx_quotation_request_bundle ON quotation_requests(bundle_id) WHERE bundle_id IS NOT NULL;
