-- BP/ADMIN(웹 로그인 사용자) 모바일 앱 FCM 토큰 — 작업자 현장 문제알림(인원/장비) 푸시 수신용.
ALTER TABLE users ADD COLUMN fcm_token VARCHAR(512);
