-- SafePulse 포팅: 워치 안전알림 + 센서 데이터 + 베이스라인 학습 3 테이블.

-- 1) 안전알림 (긴급/자동/주의). site_id/bp_company_id 는 권한 필터 캐시.
CREATE TABLE field_safety_alerts (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    work_plan_id BIGINT REFERENCES work_plans(id) ON DELETE SET NULL,
    site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    bp_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    kind VARCHAR(32) NOT NULL,           -- emergency / fall / hr_anomaly / spo2_low / temp_anomaly / sleep_suspected / watch_removed
    level VARCHAR(16) NOT NULL,          -- info / caution / warning / danger
    message TEXT,
    hr INTEGER,
    spo2 INTEGER,
    body_temp NUMERIC(4,1),
    stress INTEGER,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_field_safety_alerts_site_created ON field_safety_alerts(site_id, created_at DESC);
CREATE INDEX idx_field_safety_alerts_bp_created ON field_safety_alerts(bp_company_id, created_at DESC);
CREATE INDEX idx_field_safety_alerts_person_created ON field_safety_alerts(person_id, created_at DESC);
CREATE INDEX idx_field_safety_alerts_unresolved ON field_safety_alerts(resolved, created_at DESC) WHERE resolved = FALSE;

-- 2) 센서 raw 데이터 (워치가 5분마다 전송). 분석/그래프용.
CREATE TABLE field_sensor_readings (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    work_plan_id BIGINT REFERENCES work_plans(id) ON DELETE SET NULL,
    site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    hr INTEGER,
    spo2 INTEGER,
    body_temp NUMERIC(4,1),
    stress INTEGER,
    state VARCHAR(32),                   -- NORMAL / MILD_ANOMALY / WAITING_ACK / FALL_DETECTED / EMERGENCY / SLEEP_SUSPECTED / WATCH_REMOVED
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_field_sensor_person_recorded ON field_sensor_readings(person_id, recorded_at DESC);

-- 3) 개인 베이스라인 (EMA 학습값). person 당 1행.
CREATE TABLE field_baselines (
    person_id BIGINT PRIMARY KEY REFERENCES persons(id) ON DELETE CASCADE,
    hr_rest_mean NUMERIC(5,2),
    hr_rest_std NUMERIC(5,2),
    hr_active_mean NUMERIC(5,2),
    spo2_mean NUMERIC(5,2),
    spo2_std NUMERIC(5,2),
    body_temp_mean NUMERIC(4,2),
    body_temp_std NUMERIC(4,2),
    accel_baseline_mean NUMERIC(6,3),
    accel_baseline_std NUMERIC(6,3),
    alert_hr_upper NUMERIC(5,2),         -- restMean + alertRangeUpper
    alert_hr_lower NUMERIC(5,2),         -- restMean - alertRangeLower
    alert_spo2_range NUMERIC(5,2),
    samples_count INTEGER NOT NULL DEFAULT 0,
    last_learned_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
