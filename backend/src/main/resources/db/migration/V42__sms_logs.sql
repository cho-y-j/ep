-- V42: SMS 발송 이력. WideShot/기타 provider 호출 결과 기록.
-- ENABLED=false 모드에서도 log 만 INSERT (실제 외부 발송 X) — 시연/디버깅 용.

CREATE TABLE sms_logs (
    id              BIGSERIAL PRIMARY KEY,
    phone           VARCHAR(32) NOT NULL,
    message         TEXT NOT NULL,
    purpose         VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider        VARCHAR(32),
    external_id     VARCHAR(128),
    error_message   TEXT,
    sent_by         BIGINT REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sms_logs_phone ON sms_logs(phone);
CREATE INDEX idx_sms_logs_created ON sms_logs(created_at DESC);
