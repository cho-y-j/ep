-- 출근(check-in)과 별개의 '작업시간' — 현장 관리자(ADMIN/BP)가 지정·수정. 휴식 알림 기준.
ALTER TABLE attendance_sessions
    ADD COLUMN work_start_at TIMESTAMP,
    ADD COLUMN work_end_at   TIMESTAMP;
