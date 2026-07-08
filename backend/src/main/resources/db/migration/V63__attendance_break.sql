-- 출근 중 휴식 추적. break_start_at 이 있으면 휴식 중. break_minutes 는 누적 휴식 시간(분).
ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS break_start_at TIMESTAMP;
ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS break_minutes INTEGER NOT NULL DEFAULT 0;
