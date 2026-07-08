-- ============================================================
-- Phase S-3: 감사 로그 (audit_logs)
-- 누가(어떤 회사) 어떤 데이터(target_type/id)에 어떤 액션을 했는지 추적.
-- 알림(notifications), 도메인 이력(equipment_site_assignments 등) 과 분리한다.
-- ============================================================

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(32),
    actor_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT,
    target_company_id BIGINT REFERENCES companies(id) ON DELETE SET NULL,
    site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    before_json JSONB,
    after_json JSONB,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 권한별 조회 패턴: actor_company_id / target_company_id / site_id 로 필터.
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_logs_actor_company ON audit_logs(actor_company_id, created_at DESC);
CREATE INDEX idx_audit_logs_target_company ON audit_logs(target_company_id, created_at DESC);
CREATE INDEX idx_audit_logs_site ON audit_logs(site_id, created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs(action, created_at DESC);
CREATE INDEX idx_audit_logs_target ON audit_logs(target_type, target_id);
