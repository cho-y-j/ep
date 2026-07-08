-- 보완요청 "기타" — 사용자가 자유 텍스트로 요청할 때 사용할 document_type.
-- 실제 회신은 reason 필드에 사용자가 적은 서류명을 담아 보낸다.
INSERT INTO document_types (name, applies_to, has_expiry, requires_verification, sort_order, blocks_assignment, active)
SELECT * FROM (VALUES
    ('기타 서류 요청 (장비)', 'EQUIPMENT', false, false, 990, false, true),
    ('기타 서류 요청 (인원)', 'PERSON',    false, false, 990, false, true)
) AS v(name, applies_to, has_expiry, requires_verification, sort_order, blocks_assignment, active)
ON CONFLICT (name, applies_to) DO NOTHING;
