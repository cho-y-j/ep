-- ============================================================
-- Phase S-8.2: 작업계획서 프리셋 (자주 쓰는 양식 9개 슬롯)
-- 사용자별로 1~9 슬롯에 양식을 저장해두고 새 plan 작성 시 시드.
-- payload_json 에 헤더 필드(start_time/end_time/work_location/description) 만 담는다 — site/work_date/title 은 적용 시 사용자 입력.
-- ============================================================

CREATE TABLE work_plan_presets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 1 AND 9),
    name VARCHAR(80) NOT NULL,
    payload_json TEXT NOT NULL,          -- 헤더 + 자원 templates(JSON 배열). 자세한 schema 는 백엔드 DTO 참고.
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, slot)               -- 사용자별 슬롯 1~9 unique
);

CREATE INDEX idx_wpp_user ON work_plan_presets(user_id);
