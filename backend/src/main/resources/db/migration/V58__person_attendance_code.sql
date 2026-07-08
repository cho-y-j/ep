-- 근무자 출퇴근 코드 — 앱이 이 6자리 코드로 자동 입장.
ALTER TABLE persons ADD COLUMN attendance_code VARCHAR(8);
UPDATE persons SET attendance_code = LPAD(id::TEXT, 6, '0') WHERE attendance_code IS NULL;
ALTER TABLE persons ALTER COLUMN attendance_code SET NOT NULL;
ALTER TABLE persons ADD CONSTRAINT persons_att_code_unique UNIQUE (attendance_code);
CREATE INDEX idx_person_att_code ON persons(attendance_code);
