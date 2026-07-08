-- 워치 별도 FCM 토큰 — 폰과 다른 디바이스라 푸시 분리.
ALTER TABLE persons ADD COLUMN watch_fcm_token VARCHAR(512);
ALTER TABLE persons ADD COLUMN watch_fcm_token_updated_at TIMESTAMP;
