-- P3a 안전 웨이브 1차 (§3.4·§5) — 현장 안전설정(법정 가드) + S1 강풍 전이 상태 + S4' 정비 알림 상태.
-- 무회귀 원칙: 설정 행이 없는 현장은 HeatStage 하드코딩(법정 기본값) 그대로 동작.

-- 1) 현장별 안전설정 (site_id UNIQUE) — 온도 4단계·휴식·무더위시간대·풍속 중지·점검 게이트·정비 주기.
--    법정 완화 금지 가드는 서버(SiteSafetySettingsService)에서 저장 전 검증.
CREATE TABLE site_safety_settings (
    id                             BIGSERIAL PRIMARY KEY,
    site_id                        BIGINT           NOT NULL UNIQUE,
    temp_caution                   DOUBLE PRECISION NOT NULL DEFAULT 31,   -- 주의(법정 상한).
    temp_warning                   DOUBLE PRECISION NOT NULL DEFAULT 33,   -- 경고=휴식 의무.
    temp_danger                    DOUBLE PRECISION NOT NULL DEFAULT 35,   -- 고강도제한.
    temp_extreme                   DOUBLE PRECISION NOT NULL DEFAULT 38,   -- 중지.
    rest_interval_min              INT              NOT NULL DEFAULT 120,  -- 휴식 간격(법정 상한).
    rest_duration_min              INT              NOT NULL DEFAULT 20,   -- 휴식 시간(법정 하한).
    midday_start_hour              INT              NOT NULL DEFAULT 14,   -- 무더위 시간대 시작.
    midday_end_hour                INT              NOT NULL DEFAULT 17,   -- 무더위 시간대 끝.
    wind_stop_mps                  DOUBLE PRECISION NOT NULL DEFAULT 10,   -- 풍속 작업중지 임계(법정 상한).
    enforce_daily_inspection_gate  BOOLEAN          NOT NULL DEFAULT false,-- S3: 일일점검 미완 작업시작 차단.
    maintenance_interval_hours     INT,                                    -- S4': 정비 가동시간 주기(NULL=비활성).
    updated_by                     BIGINT,
    created_at                     TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMP        NOT NULL DEFAULT now()
);

-- 2) S1 강풍 작업중지 전이 상태 (site_id UNIQUE) — 초과 진입 1회·해제 1회 스팸 방지 + 증거(진입/해제 시각).
CREATE TABLE site_wind_states (
    id          BIGSERIAL PRIMARY KEY,
    site_id     BIGINT           NOT NULL UNIQUE,
    active      BOOLEAN          NOT NULL DEFAULT false,  -- 현재 강풍 작업중지 상태 여부.
    wind_mps    DOUBLE PRECISION,                         -- 마지막 판정 풍속.
    entered_at  TIMESTAMP,                                -- 최근 초과 진입 시각.
    cleared_at  TIMESTAMP,                                -- 최근 해제 시각.
    updated_at  TIMESTAMP        NOT NULL DEFAULT now()
);

-- 3) S4' 정비 알림 상태 — 가동시간 누적이 이 값 + 주기 를 넘으면 1회 알림 후 이 값을 누적치로 갱신.
ALTER TABLE equipment ADD COLUMN maintenance_alert_hours INT NOT NULL DEFAULT 0;
