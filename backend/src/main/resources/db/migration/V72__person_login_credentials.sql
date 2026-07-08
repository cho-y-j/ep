-- 작업자 앱 로그인 계정 — 공급사가 발급하는 아이디/비번. (출근코드 토큰은 그대로 사용)
ALTER TABLE persons ADD COLUMN username VARCHAR(64);
ALTER TABLE persons ADD COLUMN password_hash VARCHAR(255);
CREATE UNIQUE INDEX ux_persons_username ON persons(username) WHERE username IS NOT NULL;
