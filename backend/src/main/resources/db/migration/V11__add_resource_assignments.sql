-- ============================================================
-- Phase S-2: 자원 현장 배치 + 배치 이력
-- equipment / persons 에 현재 배치 정보 캐시
-- equipment_site_assignments / person_site_assignments 이력 테이블 추가
-- ============================================================

-- ------------------------------------------------------------
-- equipment 현재 배치 정보
-- ------------------------------------------------------------
ALTER TABLE equipment
    ADD COLUMN current_site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    ADD COLUMN assignment_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | ASSIGNED | BROKEN
    ADD COLUMN last_assigned_at TIMESTAMP;

CREATE INDEX idx_equipment_current_site ON equipment(current_site_id) WHERE current_site_id IS NOT NULL;
CREATE INDEX idx_equipment_assignment_status ON equipment(assignment_status);

-- ------------------------------------------------------------
-- persons 현재 배치 정보
-- (V9의 status 컬럼은 고용상태(WORKING/VACATION/RETIRED)로 그대로 두고
--  배치 상태는 별도 컬럼 assignment_status 로 관리한다)
-- ------------------------------------------------------------
ALTER TABLE persons
    ADD COLUMN current_site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    ADD COLUMN assignment_status VARCHAR(32) NOT NULL DEFAULT 'OFF_DUTY',  -- ON_DUTY | OFF_DUTY | INACTIVE
    ADD COLUMN last_assigned_at TIMESTAMP;

CREATE INDEX idx_persons_current_site ON persons(current_site_id) WHERE current_site_id IS NOT NULL;
CREATE INDEX idx_persons_assignment_status ON persons(assignment_status);

-- ------------------------------------------------------------
-- 장비 배치 이력
-- ------------------------------------------------------------
CREATE TABLE equipment_site_assignments (
    id BIGSERIAL PRIMARY KEY,
    equipment_id BIGINT NOT NULL REFERENCES equipment(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    assigned_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    released_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note VARCHAR(255),
    release_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_eq_assign_equipment ON equipment_site_assignments(equipment_id, assigned_at DESC);
CREATE INDEX idx_eq_assign_site ON equipment_site_assignments(site_id, assigned_at DESC);
-- 활성(미해제) 배치는 자원당 1건만 허용
CREATE UNIQUE INDEX idx_eq_assign_active_unique
    ON equipment_site_assignments(equipment_id)
    WHERE released_at IS NULL;

-- ------------------------------------------------------------
-- 인원 배치 이력
-- ------------------------------------------------------------
CREATE TABLE person_site_assignments (
    id BIGSERIAL PRIMARY KEY,
    person_id BIGINT NOT NULL REFERENCES persons(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES sites(id),
    assigned_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    assigned_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    released_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note VARCHAR(255),
    release_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_person_assign_person ON person_site_assignments(person_id, assigned_at DESC);
CREATE INDEX idx_person_assign_site ON person_site_assignments(site_id, assigned_at DESC);
CREATE UNIQUE INDEX idx_person_assign_active_unique
    ON person_site_assignments(person_id)
    WHERE released_at IS NULL;
