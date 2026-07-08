import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../types/equipment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../types/person';

/**
 * 사업자등록번호 자동 하이픈: ###-##-#####
 * 입력에서 숫자만 뽑아 최대 10자리로 자르고 3-2-5 패턴으로 포맷.
 */
export function formatBusinessNumber(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 10);
  if (digits.length <= 3) return digits;
  if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
}

/**
 * 휴대폰 번호 자동 하이픈: ###-####-####
 */
export function formatPhone(value: string): string {
  const digits = value.replace(/\D/g, '').slice(0, 11);
  if (digits.length <= 3) return digits;
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
}

/** 금액 표시: 1234567 → "1,234,567원", null/undefined → "-" */
export function formatWon(n: number | null | undefined): string {
  return n != null ? n.toLocaleString('ko-KR') + '원' : '-';
}

/**
 * 자원 sub label 한국어 매핑 — 장비는 카테고리, 인원은 역할(콤마결합) 분해 후 매핑.
 * COMPANY 등 그 외 owner type 은 null.
 */
export function formatOwnerSubLabel(ownerType: string, raw: string | null | undefined): string | null {
  if (!raw) return null;
  if (ownerType === 'EQUIPMENT') {
    return EQUIPMENT_CATEGORY_LABEL[raw as EquipmentCategory] ?? raw;
  }
  if (ownerType === 'PERSON') {
    const parts = raw.split(',').map((r) => r.trim()).filter(Boolean);
    if (!parts.length) return null;
    return parts.map((r) => PERSON_ROLE_LABEL[r as PersonRole] ?? r).join(', ');
  }
  return null;
}
