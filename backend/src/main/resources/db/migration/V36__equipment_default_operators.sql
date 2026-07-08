-- V36: 장비별 기본 조종원 매칭 (우선순위 N명)
-- 장비공급사가 자기 장비에 평소 매칭되는 OPERATOR 인원을 미리 등록.
-- 견적/작업계획서 생성 시 자동 prefill 용도.

CREATE TABLE equipment_default_operators (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    priority INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (equipment_id, person_id)
);

CREATE INDEX idx_equipment_default_operators_eq
    ON equipment_default_operators (equipment_id, priority);
