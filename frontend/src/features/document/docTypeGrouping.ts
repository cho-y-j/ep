import type { DocumentTypeResponse, OwnerType } from '../../types/document';

/** 이 자원(역할/카테고리)에 서류종류가 적용되는지 — 백엔드 ComplianceService.matches 와 동일 규칙. */
export function ownerMatches(t: DocumentTypeResponse, ownerType: OwnerType, roles?: string[], category?: string): boolean {
  if (ownerType === 'EQUIPMENT') {
    const csv = t.applies_to_categories;
    if (!csv) return true;
    if (!category) return false;
    return csv.split(',').map((s) => s.trim()).includes(category);
  }
  if (ownerType === 'PERSON') {
    const csv = t.applies_to_person_roles;
    if (!csv) return true;
    if (!roles || roles.length === 0) return false;
    return csv.split(',').some((c) => roles.includes(c.trim()));
  }
  return true; // COMPANY
}

export type DocTypeGroups = {
  required: DocumentTypeResponse[];
  optional: DocumentTypeResponse[];
  etc: DocumentTypeResponse[];
};

/** 이 자원에 맞춰 종류를 필수/선택/기타로 그룹핑.
 *  reqByTypeId 가 주어지면(EQUIPMENT 종류×서류 junction) 그것으로 판정:
 *  키 존재=적용, 값 true=필수 / false=선택, 키 없음=해당없음(기타). 없으면 기존 CSV/글로벌 required. */
export function groupDocTypes(
  types: DocumentTypeResponse[],
  ownerType: OwnerType,
  roles?: string[],
  category?: string,
  reqByTypeId?: Map<number, boolean>,
): DocTypeGroups {
  if (reqByTypeId) {
    return {
      required: types.filter((t) => reqByTypeId.get(t.id) === true),
      optional: types.filter((t) => reqByTypeId.get(t.id) === false),
      etc: types.filter((t) => !reqByTypeId.has(t.id)),
    };
  }
  const matched = types.filter((t) => ownerMatches(t, ownerType, roles, category));
  return {
    required: matched.filter((t) => t.required),
    optional: matched.filter((t) => !t.required),
    etc: types.filter((t) => !ownerMatches(t, ownerType, roles, category)),
  };
}
