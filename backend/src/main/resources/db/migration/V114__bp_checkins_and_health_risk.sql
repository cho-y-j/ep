-- P5-W4 뇌심혈관 건강 3겹 (§4-C) — 1겹 혈압 체크인 게이트 + 2겹 고위험군 태깅 기반.
-- 무회귀 원칙: 신규 테이블 + 기존 테이블 컬럼 추가만(기본값 보유) → 기존 로직 불변.

-- 1) 혈압 체크인 기록 — 현장 커프 혈압계(수기 대행) 또는 워치/BLE. verdict 는 서버가 임계로 계산.
--    BLOCK 이어도 출근 하드차단은 없음(권고+관리자 통보). 이 행 자체가 "측정·조치 권고" 증거사슬.
CREATE TABLE bp_checkins (
    id          BIGSERIAL PRIMARY KEY,
    person_id   BIGINT      NOT NULL,
    site_id     BIGINT,                                 -- 측정 현장(자가 체크인 시 미상 가능).
    sys         INT         NOT NULL,                   -- 수축기(mmHg).
    dia         INT         NOT NULL,                   -- 이완기(mmHg).
    pulse       INT,                                    -- 맥박(선택).
    method      VARCHAR(16) NOT NULL DEFAULT 'MANUAL',  -- MANUAL(커프 수기) | BLE.
    verdict     VARCHAR(16) NOT NULL,                   -- OK | CAUTION | BLOCK (서버 계산).
    measured_at TIMESTAMP   NOT NULL,                   -- 측정 시각(입력 시각과 다를 수 있음).
    created_by  BIGINT,                                 -- 대행 입력한 사용자(자가 체크인이면 NULL).
    created_at  TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_bp_checkins_person ON bp_checkins (person_id, measured_at);
CREATE INDEX idx_bp_checkins_site   ON bp_checkins (site_id, measured_at);

-- 2) 현장 혈압 임계 4열 — 법정 아님(자유 설정). 완화 금지 가드 대상 아님(폭염/풍속과 별개).
--    기본 160/100(주의)·180/110(차단권고). 행 없는 현장은 이 기본값으로 판정(BpThresholds.defaults).
ALTER TABLE site_safety_settings
    ADD COLUMN bp_caution_sys INT NOT NULL DEFAULT 160,
    ADD COLUMN bp_caution_dia INT NOT NULL DEFAULT 100,
    ADD COLUMN bp_block_sys   INT NOT NULL DEFAULT 180,
    ADD COLUMN bp_block_dia   INT NOT NULL DEFAULT 110;

-- 3) 고위험군 태깅 — 건강검진 서류 기반 수동 태깅(ADMIN·BP). HIGH = 워치 정책 YELLOW 상향 + 혈압 체크인 필수 대상.
ALTER TABLE persons
    ADD COLUMN health_risk_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL';  -- NORMAL | CAUTION | HIGH.
