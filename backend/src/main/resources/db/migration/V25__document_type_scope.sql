-- S-11: document_types 에 카테고리/역할 매핑 추가.
-- NULL = 해당 owner_type 의 모든 sub-type 에 적용 (예: PERSON 모든 역할).
-- CSV 문자열 = 명시한 sub-type 만 적용. 예: 'CRANE,AERIAL_LIFT'.

ALTER TABLE document_types
    ADD COLUMN applies_to_categories     TEXT,
    ADD COLUMN applies_to_person_roles   TEXT;

COMMENT ON COLUMN document_types.applies_to_categories
    IS 'EQUIPMENT owner_type 일 때 적용할 EquipmentCategory CSV. NULL = 모든 카테고리. 예: ''CRANE,AERIAL_LIFT''';
COMMENT ON COLUMN document_types.applies_to_person_roles
    IS 'PERSON owner_type 일 때 적용할 PersonRole CSV. NULL = 모든 역할. 예: ''OPERATOR''';

-- 시드 매핑 — 기존 document_types 의 카테고리/역할 명시
UPDATE document_types SET applies_to_person_roles = 'OPERATOR'
    WHERE applies_to = 'PERSON' AND name = '운전면허증';
UPDATE document_types SET applies_to_person_roles = 'OPERATOR'
    WHERE applies_to = 'PERSON' AND name = '화물운송자격증';
-- 안전인증서 (KCs) 는 크레인/고소작업차 위주
UPDATE document_types SET applies_to_categories = 'CRANE,AERIAL_LIFT'
    WHERE applies_to = 'EQUIPMENT' AND name = '안전인증서';
-- 그 외는 NULL (= 모든 카테고리/역할 적용)
