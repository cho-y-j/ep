-- V35: quotation_requests 에 bp_company_id 직접 컬럼 추가.
-- 이전 V33 까지는 BP 회사를 Site.bp_company_id (TARGETED) 또는 onBehalfOf/requestedBy (OPEN_BID) 로 매번 유추.
-- 분기 로직이 ensureCanView/list/listBundles 등 모든 곳에 흩어져서 OPEN_BID 누락 버그 다발.
-- 컬럼 직접 보유로 단일 쿼리 통일.

ALTER TABLE quotation_requests
    ADD COLUMN bp_company_id BIGINT REFERENCES companies(id);

-- backfill: TARGETED 는 site.bp_company_id
UPDATE quotation_requests qr
   SET bp_company_id = s.bp_company_id
  FROM sites s
 WHERE s.id = qr.site_id
   AND qr.bp_company_id IS NULL;

-- OPEN_BID: onBehalfOf 우선
UPDATE quotation_requests
   SET bp_company_id = on_behalf_of_bp_company_id
 WHERE bp_company_id IS NULL
   AND on_behalf_of_bp_company_id IS NOT NULL;

-- 마지막 fallback: requester user 의 company
UPDATE quotation_requests qr
   SET bp_company_id = u.company_id
  FROM users u
 WHERE u.id = qr.requested_by_user_id
   AND qr.bp_company_id IS NULL;

CREATE INDEX idx_quotation_requests_bp_company_id ON quotation_requests (bp_company_id);
