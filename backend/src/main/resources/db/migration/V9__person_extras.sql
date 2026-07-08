-- ============================================================
-- Person 컬럼 확장: 사원번호 / 직종 / 소속(팀) / 자격 / 주소 /
-- 이메일 / 입사일 / 상태 / 고용형태
-- ============================================================

ALTER TABLE persons
    ADD COLUMN employee_no VARCHAR(64),                  -- 사원번호 (P2024-0721)
    ADD COLUMN job_title VARCHAR(100),                   -- 직종 (굴착기 기사)
    ADD COLUMN team VARCHAR(100),                        -- 현장 내 소속 팀 (토목 2팀)
    ADD COLUMN qualification VARCHAR(255),               -- 자격증 (굴착기운전기능사)
    ADD COLUMN address VARCHAR(255),                     -- 주소
    ADD COLUMN email VARCHAR(255),                       -- 이메일
    ADD COLUMN hired_at DATE,                            -- 입사일
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'WORKING',     -- WORKING | VACATION | RETIRED
    ADD COLUMN employment_type VARCHAR(32) NOT NULL DEFAULT 'DIRECT'; -- DIRECT | SUBCONTRACT

CREATE UNIQUE INDEX idx_person_employee_no ON persons(employee_no) WHERE employee_no IS NOT NULL;

-- ------------------------------------------------------------
-- 시드: 기존 persons 에 mock 값 채우기
-- ------------------------------------------------------------
UPDATE persons SET
    employee_no = 'P2024-' || LPAD(id::text, 4, '0'),
    job_title = CASE
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'OPERATOR') THEN '굴착기 기사'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'SIGNALER') THEN '신호수'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'INSPECTOR') THEN '점검원'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'WORK_DIRECTOR') THEN '작업지휘자'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'GUIDE') THEN '유도원'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'FIRE_WATCH') THEN '화기감시자'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'SITE_MANAGER') THEN '현장소장'
        ELSE '작업자'
    END,
    team = CASE (id % 6)
        WHEN 0 THEN '토목 2팀'
        WHEN 1 THEN '기계팀'
        WHEN 2 THEN '안전관리팀'
        WHEN 3 THEN '자재팀'
        WHEN 4 THEN '전기팀'
        ELSE '철골팀'
    END,
    qualification = CASE
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'OPERATOR') THEN '굴착기운전기능사'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'SIGNALER') THEN '신호수 자격증'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'INSPECTOR') THEN '건설안전기사'
        WHEN EXISTS (SELECT 1 FROM person_roles pr WHERE pr.person_id = persons.id AND pr.role = 'SITE_MANAGER') THEN '건설안전기술사'
        ELSE '안전보건교육 수료'
    END,
    address = CASE (id % 4)
        WHEN 0 THEN '경기도 수원시 장안구'
        WHEN 1 THEN '서울특별시 강남구'
        WHEN 2 THEN '인천광역시 부평구'
        ELSE '경기도 성남시 분당구'
    END,
    email = LOWER(REPLACE(name, ' ', '')) || '.' || id::text || '@email.com',
    hired_at = COALESCE(created_at::date, DATE '2024-03-11'),
    status = CASE (id % 7)
        WHEN 5 THEN 'VACATION'
        WHEN 6 THEN 'RETIRED'
        ELSE 'WORKING'
    END,
    employment_type = CASE (id % 3)
        WHEN 0 THEN 'DIRECT'
        ELSE 'SUBCONTRACT'
    END;
