-- ============================================================
-- Phase S-8.3: DOCX 템플릿 (작업계획서 출력용)
-- - company_id NULL = 전역(ADMIN 관리), NOT NULL = BP 회사 전용 템플릿
-- - file_key 는 Storage 추상화 키 (LocalDiskStorage 기준 파일 경로)
-- - placeholder 는 {key} 문법, 지원 키는 백엔드 WorkPlanDocxExporter 참고
-- ============================================================

CREATE TABLE docx_templates (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(32) NOT NULL,         -- WORK_PLAN
    company_id BIGINT REFERENCES companies(id) ON DELETE CASCADE, -- NULL = 전역
    name VARCHAR(120) NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    file_size BIGINT,
    uploaded_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_docx_templates_scope ON docx_templates(target_type, company_id);
