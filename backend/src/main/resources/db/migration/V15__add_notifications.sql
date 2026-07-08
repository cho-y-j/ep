-- ============================================================
-- Phase S-4 단계 4: 알림(notifications) 도메인
-- audit_logs(시스템 감사) 와 분리. 사용자에게 보여주는 알림.
-- ============================================================

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    target_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    target_company_id BIGINT REFERENCES companies(id) ON DELETE CASCADE,
    site_id BIGINT REFERENCES sites(id) ON DELETE SET NULL,
    type VARCHAR(64) NOT NULL,            -- DOCUMENT_REJECTED | DOCUMENT_OCR_REVIEW | DOCUMENT_EXPIRING | ASSIGNMENT_OVERRIDDEN ...
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    link_type VARCHAR(32),                -- DOCUMENT | EQUIPMENT | PERSON | SITE | WORK_PLAN
    link_id BIGINT,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_created ON notifications(target_user_id, created_at DESC)
    WHERE target_user_id IS NOT NULL;
CREATE INDEX idx_notifications_company_created ON notifications(target_company_id, created_at DESC)
    WHERE target_company_id IS NOT NULL;
CREATE INDEX idx_notifications_unread ON notifications(target_user_id) WHERE read_at IS NULL;
