-- 인원 출/퇴근 세션. 모바일 체크인/체크아웃 기반.
-- 한 세션 = 하나의 출퇴근 페어. 점심·휴식으로 끊었다 들어오면 새 세션.
CREATE TABLE attendance_sessions (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL,
    work_plan_id BIGINT NOT NULL,
    check_in_at TIMESTAMP NOT NULL,
    check_out_at TIMESTAMP,
    check_in_photo_doc_id BIGINT,
    check_out_photo_doc_id BIGINT,
    check_in_lat DOUBLE PRECISION,
    check_in_lng DOUBLE PRECISION,
    check_out_lat DOUBLE PRECISION,
    check_out_lng DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_att_person ON attendance_sessions(person_id, check_in_at DESC);
CREATE INDEX idx_att_wp ON attendance_sessions(work_plan_id, check_in_at DESC);
-- 같은 person+wp 에 미완료 세션 1개만 가능
CREATE UNIQUE INDEX idx_att_open_session_unique
  ON attendance_sessions(person_id, work_plan_id)
  WHERE check_out_at IS NULL;
