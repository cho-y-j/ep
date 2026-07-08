-- ============================================================
-- Phase S-8.4: OnlyOffice 인플레이스 편집 — work_plans 의 현재 DOCX 키.
-- 첫 편집 시 템플릿에서 생성해 storage 에 저장하고 key 기록. 이후 콜백에서 같은 key 덮어쓰기.
-- ============================================================

ALTER TABLE work_plans ADD COLUMN current_docx_key VARCHAR(255);
ALTER TABLE work_plans ADD COLUMN current_docx_template_id BIGINT REFERENCES docx_templates(id) ON DELETE SET NULL;
