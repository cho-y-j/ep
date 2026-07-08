-- 현장 투입 요청에 단가(일대/월대/OT/야간) 기록. 모두 nullable — 기존 행 영향 없음.
ALTER TABLE field_deployment_requests
    ADD COLUMN daily_price   BIGINT,
    ADD COLUMN monthly_price BIGINT,
    ADD COLUMN ot_price      BIGINT,
    ADD COLUMN night_price   BIGINT;
