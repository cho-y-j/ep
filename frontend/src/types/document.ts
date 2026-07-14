export type OwnerType = 'PERSON' | 'EQUIPMENT' | 'COMPANY';

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'OCR_REVIEW_REQUIRED';

export const VERIFICATION_STATUS_LABEL: Record<VerificationStatus, string> = {
  PENDING: '확인 대기',
  VERIFIED: '검증 완료',
  REJECTED: '반려',
  OCR_REVIEW_REQUIRED: 'OCR 검토 필요',
};

export type DocumentTypeResponse = {
  id: number;
  name: string;
  applies_to: OwnerType;
  has_expiry: boolean;
  requires_verification: boolean;
  sort_order: number;
  active: boolean;
  // V14 정책 / 검증 라우팅 필드
  required: boolean;
  blocks_assignment: boolean;
  default_valid_months?: number | null;
  ocr_enabled: boolean;
  ocr_extract_type?: string | null;
  ocr_expiry_field_key?: string | null;
  verify_endpoint?: string | null;
  required_fields?: string | null;        // JSON 배열 문자열
  applies_to_person_roles?: string | null;  // PersonRole CSV, null=모든 역할
  applies_to_categories?: string | null;    // EquipmentCategory CSV, null=모든 카테고리
  ocr_region_template?: string | null;      // 영역-크롭 OCR 템플릿 JSON, null=미사용 (정렬/영역OCR 분기 기준)
};

export type DocumentResponse = {
  id: number;
  document_type_id: number;
  document_type_name: string;
  document_type_has_expiry: boolean;
  owner_type: OwnerType;
  owner_id: number;
  file_name: string;
  file_size: number;
  content_type: string;
  expiry_date?: string | null;
  verified: boolean;
  // V14 검증 필드
  verification_status: VerificationStatus;
  verified_by?: number | null;
  verified_at?: string | null;
  rejected_reason?: string | null;
  previous_document_id?: number | null;
  verification_result?: string | null;    // JSON 문자열
  extracted_data?: string | null;         // JSON 문자열
  created_at: string;
};

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** 만료까지 며칠 남았는지. expired면 음수, 만료일 없으면 null */
export function daysUntilExpiry(expiryDate?: string | null): number | null {
  if (!expiryDate) return null;
  const exp = new Date(expiryDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffMs = exp.getTime() - today.getTime();
  return Math.floor(diffMs / (1000 * 60 * 60 * 24));
}

/** required_fields JSON 문자열 → string[]. 파싱 실패 시 빈 배열. */
export function parseRequiredFields(s?: string | null): string[] {
  if (!s) return [];
  try {
    const arr = JSON.parse(s);
    return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string') : [];
  } catch {
    return [];
  }
}
