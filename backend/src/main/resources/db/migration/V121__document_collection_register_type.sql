-- V121: 서류 수집요청 '신규 자원 등록형'.
-- 협력업체 직원이 링크에서 차량번호/이름을 입력하는 순간 장비·인력이 우리 전산에 신규 생성된다.
-- 그래서 target 은 아직 owner 가 없는(미등록) 슬롯일 수 있고, 대신 무엇을 만들지(planned_type)를 들고 있는다.
-- request 는 그 자원을 어느 협력업체 명의로 만들지(target_company_id) — 토큰이 유출돼도 그 협력업체 밖 생성 불가.
ALTER TABLE document_collection_target ALTER COLUMN owner_id DROP NOT NULL;
ALTER TABLE document_collection_target ADD COLUMN planned_type VARCHAR(32);

-- 등록형 자원의 소유 협력업체. supplier_company_id(작성자 회사)와 동일하게 FK 없는 scope 컬럼.
ALTER TABLE document_collection_request ADD COLUMN target_company_id BIGINT;

-- UNIQUE(request_id, owner_type, owner_id) 는 그대로 둔다: Postgres 는 NULL 을 서로 다르게 취급하므로
-- 미등록 슬롯(owner_id=NULL)이 같은 요청에 여러 개 있어도 충돌하지 않는다(psql 16.14 실측 확인).
