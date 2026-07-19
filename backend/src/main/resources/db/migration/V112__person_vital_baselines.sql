-- P5-W1 개인 맞춤 자기학습 판정 — 서버 2차 판정용 개인 심박 대역 + 학습 보정 상태(1인 1행).
-- field_baselines(V64, 워치 온디바이스 EMA 동기화)와 별개: 이 표는 서버가 raw readings 로
-- 재학습(drift 추종)하는 대역 + 자가취소/실제사건 피드백으로 개인 임계를 자동 보정하는 상태.
-- rest/work 대역 = 정상 상태(state NORMAL) HR 분위수(p5/p50/p90). adjust_pct = 개인 보정(±캡):
--   자가취소(오탐) 누적 → 완화(+), 관리자 실제사건 → 강화(-). work_hr_high×(1+adjust_pct/100)=실효 상한.
-- 낙상·SOS·데드맨은 보정 대상 아님(민감도 저하 금지) — 코드에서 kind 로 격리.

CREATE TABLE person_vital_baselines (
    person_id     BIGINT PRIMARY KEY REFERENCES persons(id) ON DELETE CASCADE,
    rest_hr_low   INTEGER,          -- 안정 심박 대역 하한(p5).
    rest_hr_high  INTEGER,          -- 안정 심박 대역 상한(p50).
    work_hr_low   INTEGER,          -- 작업 심박 대역 하한(p50).
    work_hr_high  INTEGER,          -- 작업 심박 대역 상한(p90) — 지속 고심박 판정 기준.
    sample_count  INTEGER NOT NULL DEFAULT 0,    -- 학습에 쓰인 정상 readings 수.
    learned_at    TIMESTAMP,        -- 마지막 대역 산출 시각(NULL=미학습).
    adjust_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,  -- 개인 보정 %(-10 ~ +20). +=완화, -=강화.
    fp_count      INTEGER NOT NULL DEFAULT 0,    -- 오탐(자가취소) 누적.
    tp_count      INTEGER NOT NULL DEFAULT 0,    -- 실제사건(관리자 확인) 누적.
    updated_at    TIMESTAMP NOT NULL DEFAULT now()
);
