-- V101: L2 자원 교체 이력 연결(§3.6). replace-resource 로 새 계획서가 원본을 대체할 때
-- 새 계획서에 원본 id 를 남겨 교체 이력을 추적한다(원본은 자동 종료).
ALTER TABLE work_plans ADD COLUMN cloned_from_id BIGINT;
