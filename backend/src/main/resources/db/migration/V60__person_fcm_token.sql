-- persons 에 FCM 디바이스 토큰 컬럼 추가. 같은 person 이 폰 교체 시 갱신되며, NULL 이면 미등록.
ALTER TABLE persons ADD COLUMN fcm_token VARCHAR(512);
ALTER TABLE persons ADD COLUMN fcm_token_updated_at TIMESTAMP;
