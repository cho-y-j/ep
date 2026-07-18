-- P5-W0 워치 데드맨 감시 — 워커별 워치 최신 상태(1인 1행).
-- "침묵이 정상, 무수신 자체가 신호": last_seen_at 으로 데드맨(30분 무수신) 판정,
-- battery/worn 으로 오경보 구분(벗음)·선제 조치·착용률 증거, state 로 관제 상태등.
-- 채워지는 경로: POST /api/field-auth/sensor upsert(폰 배치 중계 + 워치 직접 5분 폴백 둘 다).
-- deadman_alert_id: 열린 데드맨 경보 마커 — 수신 재개 시 자동 resolve, 재발 방지 가드.

CREATE TABLE worker_watch_states (
    person_id        BIGINT PRIMARY KEY,
    last_seen_at     TIMESTAMP,
    battery          INTEGER,          -- 잔량 %(0~100). NULL=미보고.
    worn             BOOLEAN,          -- 오프바디(착용) 감지. FALSE=벗음, NULL=미보고.
    state            VARCHAR(16),      -- GREEN | YELLOW | RED (서버가 센서 상태에서 파생).
    hr               INTEGER,          -- 최근 바이탈 요약.
    spo2             INTEGER,
    body_temp        NUMERIC(4,1),
    site_id          BIGINT,
    bp_company_id    BIGINT,
    deadman_alert_id BIGINT,           -- 열린 데드맨 경보 id(수신 재개 시 resolve 대상).
    updated_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_worker_watch_states_site ON worker_watch_states (site_id);
CREATE INDEX idx_worker_watch_states_last_seen ON worker_watch_states (last_seen_at);
