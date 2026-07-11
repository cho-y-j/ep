export type NotificationResponse = {
  id: number;
  target_user_id?: number | null;
  target_company_id?: number | null;
  site_id?: number | null;
  type: string;
  title: string;
  message: string;
  link_type?: string | null;
  link_id?: number | null;
  read_at?: string | null;
  created_at: string;
};

export const NOTIFICATION_TYPE_LABEL: Record<string, string> = {
  DOCUMENT_REJECTED: '서류 반려',
  DOCUMENT_OCR_REVIEW: 'OCR 검토 필요',
  DOCUMENT_VERIFIED: '서류 검증 완료',
  DOCUMENT_EXPIRING: '서류 만료 임박',
  DOCUMENT_EXPIRED: '서류 만료',
  DOCUMENT_REVIEW: '서류 심사',
  ASSIGNMENT_OVERRIDDEN: '강제 배치',
  SUB_SUPPLIER_SIGNUP: '하위 공급사 가입 신청',
};

export type ReviewItemResponse = {
  id: number;
  document_type_id: number;
  document_type_name: string;
  owner_type: 'PERSON' | 'EQUIPMENT' | 'COMPANY';
  owner_id: number;
  owner_name?: string | null;
  owner_supplier_id?: number | null;
  owner_supplier_name?: string | null;
  file_name: string;
  expiry_date?: string | null;
  verification_status: 'PENDING' | 'VERIFIED' | 'REJECTED' | 'OCR_REVIEW_REQUIRED';
  rejected_reason?: string | null;
  verification_result?: string | null;
  extracted_data?: string | null;
  created_at: string;
};
