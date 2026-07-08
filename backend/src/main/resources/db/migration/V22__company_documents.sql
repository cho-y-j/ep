-- S-9-G: 회사 단위 서류 (사업자 등록증, 통장 사본, 건설업 등록증 등)
-- documents.owner_type 에 'COMPANY' 가능하도록 (enum 이 아니라 varchar 라 추가 작업 없음 — 시드만)
-- 단, 외래키 cascade 정의는 owner_type 별로 분기되지 않으므로, 회사 삭제 시 documents 도 정리 필요.
-- 회사는 cascade delete 안 하므로 (운영상 회사 archive), 별도 정합성 로직은 백엔드에서 책임.

-- 사업자 등록증 시드
-- - applies_to=COMPANY, required=true, blocks_assignment=true (사업자등록증 없으면 자원 배치 차단)
-- - has_expiry=false (사업자등록증 자체는 만료 없음. 단, 폐업 상태 변경은 NTS 재검증으로 감지)
-- - ocr_enabled=true, ocr_extract_type=BUSINESS (verify-api 의 BUSINESS extract)
-- - verify_endpoint=NTS_BIZ (자동으로 국세청 사업자등록상태 조회 호출)
-- - required_fields: OCR 결과 검증에 필요한 키
INSERT INTO document_types
    (name, applies_to, has_expiry, requires_verification, sort_order, active,
     required, blocks_assignment, default_valid_months,
     ocr_enabled, ocr_extract_type, ocr_expiry_field_key,
     verify_endpoint, required_fields)
VALUES
    ('사업자 등록증', 'COMPANY', false, true, 100, true,
     true, true, NULL,
     true, 'BUSINESS', NULL,
     'NTS_BIZ', '["biz_no","start_date","owner_name"]')
ON CONFLICT DO NOTHING;

-- 추가 회사 서류 (운영자가 필요 시 활성)
INSERT INTO document_types
    (name, applies_to, has_expiry, requires_verification, sort_order, active,
     required, blocks_assignment, default_valid_months,
     ocr_enabled, ocr_extract_type, ocr_expiry_field_key,
     verify_endpoint, required_fields)
VALUES
    ('통장 사본', 'COMPANY', false, false, 110, true, false, false, NULL, false, NULL, NULL, NULL, NULL),
    ('건설업 등록증', 'COMPANY', true, true, 120, true, false, false, 60, false, NULL, NULL, NULL, NULL),
    ('4대보험 가입증명원', 'COMPANY', true, true, 130, true, false, false, 1, false, NULL, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;
