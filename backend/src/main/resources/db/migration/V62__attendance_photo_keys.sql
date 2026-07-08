-- 출/퇴근 사진 파일 키. 사진은 storage 에 저장되고 key 만 attendance_sessions 에 보관.
ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS check_in_photo_key VARCHAR(255);
ALTER TABLE attendance_sessions ADD COLUMN IF NOT EXISTS check_out_photo_key VARCHAR(255);
