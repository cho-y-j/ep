-- ============================================================
-- audit_logs.before_json / after_json 을 jsonb 에서 text 로 단순화.
-- JSONB 의 인덱싱/검색 이점은 이번 단계에서 필요 없고, JPA String 매핑이
-- jsonb 컬럼에 직접 들어가면 PostgreSQL 이 타입 캐스팅 에러를 낸다.
-- 향후 검색/인덱싱이 필요해지면 다시 jsonb 로 복귀하고 AttributeConverter 를 도입한다.
-- ============================================================

ALTER TABLE audit_logs
    ALTER COLUMN before_json TYPE TEXT USING before_json::text,
    ALTER COLUMN after_json  TYPE TEXT USING after_json::text;
