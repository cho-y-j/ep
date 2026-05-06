-- ============================================================
-- Equipment 컬럼 확장 + 이력 테이블 5종
-- ============================================================

-- equipment 추가 컬럼
ALTER TABLE equipment
    ADD COLUMN code VARCHAR(64),                       -- 장비 코드 (예: EQ-2024-001)
    ADD COLUMN serial_number VARCHAR(128),             -- 제조번호 (HCEZ350VJ0001234)
    ADD COLUMN usage_hours INTEGER,                    -- 누적 사용 시간
    ADD COLUMN weight_kg INTEGER,                      -- 장비 중량(kg)
    ADD COLUMN bucket_capacity NUMERIC(8, 2),          -- 버킷 용량(㎥)
    ADD COLUMN insurance_expiry DATE,                  -- 보험 만료일
    ADD COLUMN operating_hours INTEGER NOT NULL DEFAULT 0,  -- 누적 가동 시간
    ADD COLUMN idle_hours INTEGER NOT NULL DEFAULT 0,       -- 누적 대기 시간
    ADD COLUMN downtime_hours INTEGER NOT NULL DEFAULT 0;   -- 누적 비가동 시간

CREATE UNIQUE INDEX idx_equipment_code ON equipment(code) WHERE code IS NOT NULL;

-- ------------------------------------------------------------
-- 점검 이력 (정기 점검, 안전 점검 등)
-- ------------------------------------------------------------
CREATE TABLE equipment_inspection_history (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    inspected_at DATE NOT NULL,
    inspector VARCHAR(100),
    title VARCHAR(255) NOT NULL,                       -- 정기 안전 점검 등
    result VARCHAR(32) NOT NULL,                       -- PASS | ATTENTION | FAIL
    note TEXT,
    next_inspection_at DATE,                           -- 다음 점검 예정일
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_eq_inspection_equipment ON equipment_inspection_history(equipment_id, inspected_at DESC);

-- ------------------------------------------------------------
-- 가동 이력 (작업 단위 가동 기록)
-- ------------------------------------------------------------
CREATE TABLE equipment_operation_history (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,                                -- null = 진행 중
    site_name VARCHAR(100),                            -- 서울 A현장 등
    description VARCHAR(255),                          -- 토공 구간 작업
    utilization_pct INTEGER,                           -- 그 작업 가동률
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',     -- RUNNING | DONE | IDLE | BROKEN
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_eq_operation_equipment ON equipment_operation_history(equipment_id, started_at DESC);

-- ------------------------------------------------------------
-- 위치 이력 (GPS / 사이트 이동 기록)
-- ------------------------------------------------------------
CREATE TABLE equipment_location_history (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    recorded_at TIMESTAMP NOT NULL,
    location_name VARCHAR(255) NOT NULL,               -- 서울 A현장 토공 2구역
    note VARCHAR(255),                                 -- GPS 정상, 작업 종료 등
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_eq_location_equipment ON equipment_location_history(equipment_id, recorded_at DESC);

-- ------------------------------------------------------------
-- 정비 이력 (부품 교체, 수리)
-- ------------------------------------------------------------
CREATE TABLE equipment_maintenance_history (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    maintained_at DATE NOT NULL,
    maintainer VARCHAR(100),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    cost BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_eq_maintenance_equipment ON equipment_maintenance_history(equipment_id, maintained_at DESC);

-- ------------------------------------------------------------
-- 메모 (관리자/현장 노트)
-- ------------------------------------------------------------
CREATE TABLE equipment_notes (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    author_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_eq_notes_equipment ON equipment_notes(equipment_id, created_at DESC);

-- ------------------------------------------------------------
-- 시드: 기존 equipment에 mock 값 채우기
-- ------------------------------------------------------------
UPDATE equipment SET
    code = 'EQ-2024-' || LPAD(id::text, 3, '0'),
    serial_number = CASE
        WHEN category = 'EXCAVATOR' THEN 'HCEZ350VJ' || LPAD(id::text, 7, '0')
        WHEN category = 'CRANE' THEN 'TC700-' || LPAD(id::text, 6, '0')
        ELSE 'SN-' || LPAD(id::text, 8, '0')
    END,
    usage_hours = 1256 + (id * 117) % 800,
    weight_kg = CASE
        WHEN category = 'EXCAVATOR' THEN 34200
        WHEN category = 'CRANE' THEN 28000
        WHEN category = 'WHEEL_LOADER' THEN 18500
        ELSE 12000
    END,
    bucket_capacity = CASE
        WHEN category = 'EXCAVATOR' THEN 1.20
        WHEN category = 'WHEEL_LOADER' THEN 2.00
        ELSE NULL
    END,
    insurance_expiry = DATE '2025-12-31',
    operating_hours = 980,
    idle_hours = 220,
    downtime_hours = 56;

-- 점검 이력 (각 장비당 2건)
INSERT INTO equipment_inspection_history (equipment_id, inspected_at, inspector, title, result, note, next_inspection_at)
SELECT id, DATE '2026-04-10', '김민수 기사', '정기 안전 점검', 'PASS', '이상 없음', DATE '2026-07-10' FROM equipment;
INSERT INTO equipment_inspection_history (equipment_id, inspected_at, inspector, title, result, note, next_inspection_at)
SELECT id, DATE '2026-03-01', '관리자', '보험 증권 확인', 'PASS', '갱신 완료', NULL FROM equipment;

-- 가동 이력 (3건씩)
INSERT INTO equipment_operation_history (equipment_id, started_at, ended_at, site_name, description, utilization_pct, status)
SELECT id, TIMESTAMP '2026-05-03 08:00', TIMESTAMP '2026-05-03 17:00', '서울 A현장', '토공 구간 작업', 78, 'DONE' FROM equipment;
INSERT INTO equipment_operation_history (equipment_id, started_at, ended_at, site_name, description, utilization_pct, status)
SELECT id, TIMESTAMP '2026-05-02 09:00', TIMESTAMP '2026-05-02 15:30', '서울 A현장', '상차 지원', 86, 'DONE' FROM equipment;
INSERT INTO equipment_operation_history (equipment_id, started_at, ended_at, site_name, description, utilization_pct, status)
SELECT id, TIMESTAMP '2026-05-01 13:00', TIMESTAMP '2026-05-01 17:00', '서울 A현장', '대기', 0, 'IDLE' FROM equipment;

-- 위치 이력 (3건씩)
INSERT INTO equipment_location_history (equipment_id, recorded_at, location_name, note)
SELECT id, TIMESTAMP '2026-05-04 09:20', '서울 A현장 장비 주차장', 'GPS 정상' FROM equipment;
INSERT INTO equipment_location_history (equipment_id, recorded_at, location_name, note)
SELECT id, TIMESTAMP '2026-05-03 17:10', '서울 A현장 토공 2구역', '작업 종료' FROM equipment;
INSERT INTO equipment_location_history (equipment_id, recorded_at, location_name, note)
SELECT id, TIMESTAMP '2026-05-01 07:45', '반입 게이트', '반입 완료' FROM equipment;

-- 정비 이력 (1건)
INSERT INTO equipment_maintenance_history (equipment_id, maintained_at, maintainer, title, description, cost)
SELECT id, DATE '2026-02-15', '센터 A 정비팀', '엔진 오일 교환', '필터 동시 교체', 180000 FROM equipment;

-- 메모 (2건)
INSERT INTO equipment_notes (equipment_id, content)
SELECT id, '점검일 다가옴, 부품 재고 확인 필요' FROM equipment;
INSERT INTO equipment_notes (equipment_id, content)
SELECT id, '버킷 마모 진행. 다음 정비 시 교체 예정.' FROM equipment;
