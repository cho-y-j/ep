-- 장비공급사의 하위공급사(재하도급) — 1단계 자기참조 계층.
-- 부모(EQUIPMENT 공급사)가 독립 로그인 자식 공급사(EQUIPMENT/MANPOWER)를 둘 수 있다.
-- 읽기만 부모→자식 단방향 확장. 쓰기는 각 회사 본인 고정.
ALTER TABLE companies ADD COLUMN parent_company_id BIGINT REFERENCES companies(id);
CREATE INDEX idx_companies_parent ON companies(parent_company_id);

-- 4-a 재배분: 부모가 자식 자원을 자기 배차로 끌어와 부모 명의로 발송.
-- supplier_company_id = 대외 명의(부모), sub_supplier_company_id = 실소유 자식(본인 자원이면 NULL).
ALTER TABLE quotation_dispatched_equipments ADD COLUMN sub_supplier_company_id BIGINT;
ALTER TABLE quotation_dispatched_persons ADD COLUMN sub_supplier_company_id BIGINT;
