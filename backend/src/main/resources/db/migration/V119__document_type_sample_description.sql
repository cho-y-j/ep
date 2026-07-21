-- 서류 수집 '샘플 보기': 서류종류별 촬영/제출 안내 설명글.
-- V116 의 샘플 이미지와 독립 — 사진만/글만/둘 다 자유 조합. NULL = 설명 미등록.
ALTER TABLE document_types
    ADD COLUMN sample_description TEXT;
