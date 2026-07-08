-- 작업확인서 출근 인증 사진 첨부 (현장 인원/차량 인증 샷).
-- documents 도메인 활용 (Document.id 참조).
ALTER TABLE work_confirmations
    ADD COLUMN attendance_photo_doc_id BIGINT;
CREATE INDEX idx_wc_attendance_photo ON work_confirmations(attendance_photo_doc_id);
