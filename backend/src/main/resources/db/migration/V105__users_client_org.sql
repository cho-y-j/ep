-- P2c: 원청(SK) 역할 신설. users.role 은 varchar(32) 저장이라 enum 값 'CLIENT' 추가에 컬럼 변경 불필요.
-- CLIENT 사용자는 회사(company_id) 대신 원청(client_org_id)에 소속 — 관제 범위 스코프.
ALTER TABLE users ADD COLUMN client_org_id BIGINT;
