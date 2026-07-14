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

/** 이 자원에 맞춰 종류를 필수/선택/기타로 그룹핑. */
export function groupDocTypes(
  types: DocumentTypeResponse[],
  ownerType: OwnerType,
  roles?: string[],
  category?: string,
): DocTypeGroups {
  const matched = types.filter((t) => ownerMatches(t, ownerType, roles, category));
  return {
    required: matched.filter((t) => t.required),
    optional: matched.filter((t) => !t.required),
    etc: types.filter((t) => !ownerMatches(t, ownerType, roles, category)),
  };
}
