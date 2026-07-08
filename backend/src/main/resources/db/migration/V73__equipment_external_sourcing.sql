-- 장비 외부 조달 여부 — 우리 공급사 장비(false) / 외부에서 가져온 장비(true).
ALTER TABLE equipment ADD COLUMN is_external BOOLEAN NOT NULL DEFAULT FALSE;

-- 외부 조달 장비용 사업자등록증 서류타입. 후순위(sort_order 큼) + 첨부만(필수X, 배차 차단X).
INSERT INTO document_types
    (name, applies_to, has_expiry, requires_verification, sort_order, active,
     required, blocks_assignment, default_valid_months,
     ocr_enabled, ocr_extract_type, ocr_expiry_field_key,
     verify_endpoint, required_fields)
VALUES
    ('사업자등록증(외부장비)', 'EQUIPMENT', false, false, 900, true,
     false, false, NULL,
     false, NULL, NULL,
     NULL, NULL);
