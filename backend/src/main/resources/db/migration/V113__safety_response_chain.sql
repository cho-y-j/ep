-- P5-W2/W3 서버 대응체인 + BLE 릴레이 수신 + 골든타임 타임라인 (특허 §5.5·5.7·5.8).
-- ① safety_alert_responses: 근접 동료 [제가 갑니다] 응답 기록(alert×person 1행, 멱등).
-- ② field_safety_alerts 골든타임 열: peer_notified_at(t1 동료통보)·first_response_at(t2 최초응답)·
--    peer_escalated_at(60초 무응답 확대 1회 마커).
-- ③ 릴레이 위치 보강 열: relayed_at·relay_lat/lng — 제3자 폰 BLE 대리중계로 피재자 추정 위치 보강.
-- 무회귀: 기존 행은 신규 열 전부 NULL(=대응체인 미발동) → 기존 로직 불변. 컬럼만 추가.

CREATE TABLE safety_alert_responses (
    id         BIGSERIAL PRIMARY KEY,
    alert_id   BIGINT NOT NULL REFERENCES field_safety_alerts(id) ON DELETE CASCADE,
    person_id  BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    response   VARCHAR(16) NOT NULL,       -- GOING (현재 유일값 — [제가 갑니다]).
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (alert_id, person_id)           -- 동료 1인 1응답(중복 탭 멱등).
);
CREATE INDEX idx_safety_alert_responses_alert ON safety_alert_responses (alert_id);

ALTER TABLE field_safety_alerts
    ADD COLUMN peer_notified_at  TIMESTAMP,          -- 근접 동료 통보 시각(골든타임 t1).
    ADD COLUMN first_response_at TIMESTAMP,          -- 최초 [제가 갑니다] 시각(t2 — 최초 1회만).
    ADD COLUMN peer_escalated_at TIMESTAMP,          -- 60초 무응답 → 현장 전체 확대 + 관리자 통보(1회 마커).
    ADD COLUMN relayed_at        TIMESTAMP,          -- 최근 BLE 대리중계 수신 시각(제3자 폰).
    ADD COLUMN relay_lat         DOUBLE PRECISION,   -- 중계자 위치(피재자 추정 위치 보강).
    ADD COLUMN relay_lng         DOUBLE PRECISION;
