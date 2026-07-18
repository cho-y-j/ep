-- P3b S5' 안전알림 3등급 + 확인응답(ack) 루프 (§5) — FieldSafetyAlert 에 등급·확인·에스컬레이션 증거 컬럼.
-- 안전 증거 사슬: 발송(created_at) → 확인(acknowledged_at·ack_person_id) → 미확인 재알림/관제(escalated_at).
-- 무회귀: 기존 행은 severity NULL(=일반) → ack 대상 아님. 컬럼만 추가(기존 로직 불변).

ALTER TABLE field_safety_alerts
    ADD COLUMN severity        VARCHAR(16),   -- EMERGENCY | CAUTION | NORMAL (NULL=레거시=일반).
    ADD COLUMN acknowledged_at TIMESTAMP,     -- 작업자 [확인] 탭 시각(인지 증거).
    ADD COLUMN ack_person_id   BIGINT,        -- 확인한 작업자(본인).
    ADD COLUMN escalated_at    TIMESTAMP;     -- 5분 미확인 → 재알림 1회 + 관제 표시 시각.
