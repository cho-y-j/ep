-- 자원 점검 회신용 document_type 시드 (중복 시 skip).
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, blocks_assignment, active)
SELECT * FROM (VALUES
    ('자동차 안전점검 결과서', 'EQUIPMENT', true,  false, 900, false, true),
    ('점검 회신 - 기타 (장비)', 'EQUIPMENT', false, false, 901, false, true),
    ('건강검진 결과서',         'PERSON',    true,  false, 900, false, true),
    ('안전교육 이수증',         'PERSON',    true,  false, 901, false, true),
    ('점검 회신 - 기타 (인원)', 'PERSON',    false, false, 902, false, true)
) AS v(name, applies_to, has_expiry, requires_verification, sort_order, blocks_assignment, active)
ON CONFLICT (name, applies_to) DO NOTHING;
