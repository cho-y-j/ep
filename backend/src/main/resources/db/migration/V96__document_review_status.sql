-- V96: 서류 심사 봉투 상태머신 — BP 원스톱 심사(웹 승인/반려).
-- 기존 봉투는 status='PENDING'(심사중) 으로 백필된다(DEFAULT).
ALTER TABLE document_reviews
    ADD COLUMN status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN rejected_reason VARCHAR(255),
    ADD COLUMN acted_by        BIGINT REFERENCES users(id),
    ADD COLUMN acted_at        TIMESTAMP;
