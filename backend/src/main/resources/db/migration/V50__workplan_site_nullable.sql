-- 작업계획서의 현장 선택을 옵션화. 현장 미정인 채로 만들 수 있게 한다.
-- 자유 텍스트는 work_location 컬럼 사용.
ALTER TABLE work_plans ALTER COLUMN site_id DROP NOT NULL;
