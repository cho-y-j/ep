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
  sender_label?: string | null;
  read_at?: string | null;
  created_at: string;
};

export const NOTIFICATION_TYPE_LABEL: Record<string, string> = {
  DOCUMENT_REJECTED: '서류 반려',
  DOCUMENT_OCR_REVIEW: 'OCR 검토 필요',
  DOCUMENT_VERIFIED: '서류 검증 완료',
  DOCUMENT_EXPIRING: '서류 만료 임박',
  DOCUMENT_EXPIRED: '서류 만료',
  DOCUMENT_EXPIRY_EXTRACTED: '만료일 자동 추출',
  DOCUMENT_RENEWED: '서류 갱신',
  DOCUMENT_REVIEW: '서류 심사',
  DOCUMENT_REVIEW_RESULT: '서류 심사 결과',
  SUPPLEMENT_REQUESTED: '서류 보완 요청',
  SUPPLEMENT_RESOLVED: '서류 보완 완료',
  QUOTATION_RECEIVED: '견적 요청 수신',
  QUOTATION_RESPONDED: '견적 응답',
  QUOTATION_FINALIZED: '견적 최종 수락',
  QUOTATION_REJECTED: '견적 거절',
  QUOTATION_DISPATCH: '견적서 도착',
  FIELD_DEPLOYMENT_REQUESTED: '투입 요청',
  FIELD_DEPLOYMENT_REVIEWED: '투입 요청 처리',
  RESOURCE_ONBOARDING_REQUESTED: '소급 승인 요청',
  RESOURCE_ONBOARDING_APPROVED: '소급 승인 완료',
  RESOURCE_CHECK_REQUEST: '점검 요청',
  RESOURCE_CHECK_SUBMITTED: '점검 회신',
  RESOURCE_CHECK_REJECTED: '점검 반려',
  ASSIGNMENT_OVERRIDDEN: '강제 배치',
  WORK_PLAN_RESOURCE_REPLACED: '자원 교체',
  DAILY_INSPECTION_INCOMPLETE: '일일점검 미완',
  EQUIPMENT_MAINTENANCE_DUE: '정비 도래',
  EQUIPMENT_DUE: '장비 만료 임박',
  WIND_STOP: '강풍 작업중지',
  WIND_CLEARED: '강풍 해제',
  SAFETY_ACK_MISSING: '안전알림 미확인',
  ISSUE_REPORT: '이슈 신고',
  DAILY_WORK_LOG_SIGNED: '일일 확인서 서명',
  MONTHLY_STATEMENT_READY: '월 거래내역서',
  SUB_SUPPLIER_SIGNUP: '하위 공급사 가입 신청',
  COLLECTION_SUBMITTED: '서류 제출 완료',
};

/** P4d: 알림 유형 필터 그룹 (6그룹). 선택 시 해당 type 목록을 서버에 전달. */
export type NotificationGroupKey = 'safety' | 'document' | 'quotation' | 'field' | 'settlement' | 'etc';

export const NOTIFICATION_GROUPS: { key: NotificationGroupKey; label: string; types: string[] }[] = [
  { key: 'safety', label: '안전', types: ['WIND_STOP', 'WIND_CLEARED', 'SAFETY_ACK_MISSING', 'DAILY_INSPECTION_INCOMPLETE', 'EQUIPMENT_MAINTENANCE_DUE', 'EQUIPMENT_DUE'] },
  { key: 'document', label: '서류', types: ['DOCUMENT_REJECTED', 'DOCUMENT_OCR_REVIEW', 'DOCUMENT_VERIFIED', 'DOCUMENT_EXPIRING', 'DOCUMENT_EXPIRED', 'DOCUMENT_EXPIRY_EXTRACTED', 'DOCUMENT_RENEWED', 'DOCUMENT_REVIEW', 'DOCUMENT_REVIEW_RESULT', 'SUPPLEMENT_REQUESTED', 'SUPPLEMENT_RESOLVED', 'COLLECTION_SUBMITTED'] },
  { key: 'quotation', label: '견적·계약', types: ['QUOTATION_RECEIVED', 'QUOTATION_RESPONDED', 'QUOTATION_FINALIZED', 'QUOTATION_REJECTED', 'QUOTATION_DISPATCH'] },
  { key: 'field', label: '투입·현장', types: ['FIELD_DEPLOYMENT_REQUESTED', 'FIELD_DEPLOYMENT_REVIEWED', 'RESOURCE_ONBOARDING_REQUESTED', 'RESOURCE_ONBOARDING_APPROVED', 'RESOURCE_CHECK_REQUEST', 'RESOURCE_CHECK_SUBMITTED', 'RESOURCE_CHECK_REJECTED', 'ASSIGNMENT_OVERRIDDEN', 'WORK_PLAN_RESOURCE_REPLACED', 'ISSUE_REPORT'] },
  { key: 'settlement', label: '정산', types: ['DAILY_WORK_LOG_SIGNED', 'MONTHLY_STATEMENT_READY'] },
  { key: 'etc', label: '기타', types: ['SUB_SUPPLIER_SIGNUP'] },
];

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
