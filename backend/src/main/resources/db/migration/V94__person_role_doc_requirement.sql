-- V94: 인력 역할(PersonRole) × 서류 요구 junction. (V91 장비종류×서류의 인력판 미러)
-- 3상태: 행 존재 + required=true → 필수, 행 존재 + required=false → 선택, 행 없음 → 해당없음.
-- 기존 글로벌 required + applies_to_person_roles 의 한계(역할별 필수/선택 분리 불가)를 해소.
CREATE TABLE person_role_doc_requirement (
    person_role      VARCHAR(32) NOT NULL,
    document_type_id BIGINT      NOT NULL,
    required         BOOLEAN     NOT NULL,
    PRIMARY KEY (person_role, document_type_id)
);

-- 백필: 기존 컴플라이언스(ComplianceService.matches + document_types.required)와 동일한
-- 적용/필수 집합을 재현한다. 인력 역할은 마스터 테이블이 없어 PersonRole enum 7종을 VALUES 로 나열.
--   각 active PERSON document_type t 에 대해:
--     · applies_to_person_roles 가 NULL/빈값 → 모든 역할에 (role, t.id, t.required)
--     · CSV → CSV 각 역할 중 유효 PersonRole 에만 (role, t.id, t.required)
INSERT INTO person_role_doc_requirement (person_role, document_type_id, required)
SELECT r.role, dt.id, dt.required
  FROM document_types dt
  CROSS JOIN (VALUES
      ('OPERATOR'),('WORK_DIRECTOR'),('GUIDE'),('FIRE_WATCH'),
      ('SIGNALER'),('INSPECTOR'),('SITE_MANAGER')) AS r(role)
 WHERE dt.applies_to = 'PERSON' AND dt.active = TRUE
   AND (dt.applies_to_person_roles IS NULL OR btrim(dt.applies_to_person_roles) = '')
UNION
SELECT btrim(raw_role), dt.id, dt.required
  FROM document_types dt
  CROSS JOIN LATERAL regexp_split_to_table(dt.applies_to_person_roles, ',') AS raw_role
 WHERE dt.applies_to = 'PERSON' AND dt.active = TRUE
   AND dt.applies_to_person_roles IS NOT NULL AND btrim(dt.applies_to_person_roles) <> ''
   AND btrim(raw_role) IN
       ('OPERATOR','WORK_DIRECTOR','GUIDE','FIRE_WATCH','SIGNALER','INSPECTOR','SITE_MANAGER');
