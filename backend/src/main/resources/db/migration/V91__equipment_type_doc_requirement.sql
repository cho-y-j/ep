-- V91: 장비 종류 × 서류 요구 junction.
-- 3상태: 행 존재 + required=true → 필수, 행 존재 + required=false → 선택, 행 없음 → 해당없음.
-- 기존 글로벌 required + applies_to_categories 의 한계(종류별 필수/선택 분리 불가)를 해소.
CREATE TABLE equipment_type_doc_requirement (
    equipment_type_code VARCHAR(32) NOT NULL,
    document_type_id    BIGINT      NOT NULL,
    required            BOOLEAN     NOT NULL,
    PRIMARY KEY (equipment_type_code, document_type_id)
);

-- 백필: 기존 컴플라이언스(ComplianceService.matches + document_types.required)와 동일한
-- 적용/필수 집합을 재현한다.
--   각 active EQUIPMENT document_type t 에 대해:
--     · applies_to_categories 가 NULL/빈값 → 모든 equipment_type.code 에 (code, t.id, t.required)
--     · CSV → CSV 각 코드 중 equipment_type 마스터에 존재하는 코드에만 (code, t.id, t.required)
INSERT INTO equipment_type_doc_requirement (equipment_type_code, document_type_id, required)
SELECT et.code, dt.id, dt.required
  FROM document_types dt
  CROSS JOIN equipment_type et
 WHERE dt.applies_to = 'EQUIPMENT' AND dt.active = TRUE
   AND (dt.applies_to_categories IS NULL OR btrim(dt.applies_to_categories) = '')
UNION
SELECT et.code, dt.id, dt.required
  FROM document_types dt
  CROSS JOIN LATERAL regexp_split_to_table(dt.applies_to_categories, ',') AS raw_code
  JOIN equipment_type et ON et.code = btrim(raw_code)
 WHERE dt.applies_to = 'EQUIPMENT' AND dt.active = TRUE
   AND dt.applies_to_categories IS NOT NULL AND btrim(dt.applies_to_categories) <> '';
