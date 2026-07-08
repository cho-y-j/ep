-- 폭염/근로기준법 휴식 알림 중복 방지: 마지막 휴식 알림(또는 휴식 종료) 기준 시각.
ALTER TABLE attendance_sessions ADD COLUMN last_rest_alert_at TIMESTAMP;
