-- 점검 회신용 document_type 은 사진만 받으면 되도록 만료일 필수 풀기.
UPDATE document_types
SET has_expiry = false
WHERE name IN (
    '자동차 안전점검 결과서',
    '건강검진 결과서',
    '안전교육 이수증',
    '점검 회신 - 기타 (장비)',
    '점검 회신 - 기타 (인원)'
);
