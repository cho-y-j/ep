-- V38: work_confirmations 단위를 인원(Person) 기반으로 변경
--
-- 기존 단위: (work_plan_id, work_date, issuing_supplier_company_id) UNIQUE — 공급사 회사 1건
-- 변경 단위: (work_plan_id, person_id) UNIQUE — 인원 1명 = 1건
--   장비공급사 운전수든 인력공급사 일용직이든 동일하게 인원당 1건 발급.
--   작업계획서가 1일 단위라 work_date 는 wp.workDate 와 사실상 동일.
--
-- skep 미운영이라 기존 행은 삭제 (NOT NULL 컬럼 추가 위해).

DELETE FROM work_confirmations;

-- 기존 자동생성 3-column UNIQUE 제거 (이름 자동생성이라 가변)
DO $$
DECLARE cname TEXT;
BEGIN
    FOR cname IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'work_confirmations'::regclass
          AND contype = 'u'
          AND array_length(conkey, 1) = 3
    LOOP
        EXECUTE 'ALTER TABLE work_confirmations DROP CONSTRAINT ' || quote_ident(cname);
    END LOOP;
END $$;

ALTER TABLE work_confirmations
    ADD COLUMN person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE RESTRICT;

ALTER TABLE work_confirmations
    ADD CONSTRAINT work_confirmations_wp_person_unique UNIQUE (work_plan_id, person_id);

CREATE INDEX idx_work_confirmations_person ON work_confirmations(person_id, work_date DESC);
