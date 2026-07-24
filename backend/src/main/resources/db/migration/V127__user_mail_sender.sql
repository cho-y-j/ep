-- 사용자별 발송 메일 계정 — 심사 메일을 본인 메일(네이버 SMTP 등)로 발송하기 위한 설정.
-- 미등록 사용자는 종전대로 시스템 기본 계정으로 발송(보낸사람 표시명만 본인/회사).
-- password_enc: 앱 비밀번호를 AES-GCM 으로 암호화한 값(평문 저장 금지). name: 보낸사람 표시명(NULL 이면 사용자명).
ALTER TABLE users
    ADD COLUMN mail_sender_email        VARCHAR(255),
    ADD COLUMN mail_sender_password_enc VARCHAR(512),
    ADD COLUMN mail_sender_name         VARCHAR(100);
