import type { EquipmentCategory } from './equipment';
import type { PersonRole } from './person';

export type QuotationRequestType = 'EQUIPMENT' | 'MANPOWER';
export type QuotationStatus = 'DRAFT' | 'SENT' | 'CLOSED' | 'CANCELLED';
export type QuotationTargetStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'FINAL_ACCEPTED' | 'EXPIRED';
export type QuotationMode = 'OPEN_BID' | 'TARGETED';

export const QUOTATION_STATUS_LABEL: Record<QuotationStatus, string> = {
  DRAFT: '임시저장',
  SENT: '응답 대기',
  CLOSED: '완료',
  CANCELLED: '취소됨',
};

export const QUOTATION_TARGET_STATUS_LABEL: Record<QuotationTargetStatus, string> = {
  PENDING: '응답 대기',
  ACCEPTED: '공급사 수락',
  REJECTED: '공급사 거부',
  FINAL_ACCEPTED: '최종 채택',
  EXPIRED: '만료',
};

// 백엔드 Jackson SNAKE_CASE 라 응답은 snake_case.
export type QuotationCandidateEquipment = {
  id: number;
  vehicle_no?: string | null;
  model?: string | null;
  manufacturer?: string | null;
  year?: number | null;
  category: EquipmentCategory;
  serial_number?: string | null;
  has_photo: boolean;
  current_site_id?: number | null;
  current_site_name?: string | null;
};

export type QuotationCandidateGroup = {
  supplier_id: number;
  supplier_name: string;
  equipments: QuotationCandidateEquipment[];
};

export type QuotationManpowerPerson = {
  id: number;
  name: string;
  job_title?: string | null;
  phone?: string | null;
  employee_no?: string | null;
  roles: PersonRole[];
  has_photo: boolean;
};

export type QuotationManpowerCandidateGroup = {
  supplier_id: number;
  supplier_name: string;
  persons: QuotationManpowerPerson[];
};

export type QuotationTargetItem = {
  id: number;
  supplier_company_id: number;
  supplier_company_name: string;
  equipment_id?: number | null;
  equipment_label?: string | null;
  person_id?: number | null;
  person_label?: string | null;
  status: QuotationTargetStatus;
  responded_by_user_id?: number | null;
  responded_at?: string | null;
  response_note?: string | null;
  finalized_by_user_id?: number | null;
  finalized_at?: string | null;
  finalized_to_work_plan_id?: number | null;
  finalized_to_wpe_id?: number | null;
};

export type QuotationRequestResponse = {
  id: number;
  site_id: number;
  site_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  requested_by_user_id: number;
  requested_by_user_name?: string | null;
  on_behalf_of_bp_company_id?: number | null;
  work_period_start: string;
  work_period_end: string;
  request_type?: QuotationRequestType;
  equipment_category?: EquipmentCategory | null;
  manpower_role?: PersonRole | null;
  spec_text?: string | null;
  proposed_daily_rate?: number | null;
  proposed_monthly_rate?: number | null;
  count: number;
  notes?: string | null;
  status: QuotationStatus;
  mode?: QuotationMode | null;
  client_org_id?: number | null;
  work_location_text?: string | null;
  created_at: string;
  updated_at: string;
  targets?: QuotationTargetItem[] | null;
};

// POST body — Spring 의 record 매핑은 camelCase 받음 (요청은 SNAKE_CASE 안 적용).
// 실제로는 .yml 의 property-naming-strategy 가 응답+요청 양쪽 적용. 일관성 위해 snake 로 보냄.
// (테스트로 둘 다 매칭되는지 확인 필요. 아니면 camelCase 로 변경.)
export type CreateQuotationPayload = {
  site_id: number;
  work_period_start: string;
  work_period_end: string;
  request_type?: QuotationRequestType;
  equipment_category?: EquipmentCategory;
  manpower_role?: PersonRole;
  spec_text?: string;
  proposed_daily_rate?: number;
  proposed_monthly_rate?: number;
  count?: number;
  notes?: string;
  on_behalf_of_bp_company_id?: number;
  targets: { supplier_company_id: number; equipment_id?: number; person_id?: number }[];
};

export type RespondQuotationPayload = {
  accept: boolean;
  note?: string;
};

/** 현장 묶음 응답 — UI에서 "견적 1건" 으로 보이는 단위. */
export type QuotationBundleResponse = {
  bundle_id?: string | null;
  site_id: number;
  site_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  requested_by_user_id: number;
  requested_by_user_name?: string | null;
  on_behalf_of_bp_company_id?: number | null;
  work_period_start: string;
  work_period_end: string;
  notes?: string | null;
  aggregate_status: QuotationStatus;
  total_targets: number;
  responded_count: number;
  accepted_count: number;
  finalized_count: number;
  /** OPEN_BID 견적의 받은 제안 수 (전체). TARGETED 묶음은 0. */
  proposal_count: number;
  /** OPEN_BID 미선정 제안 수 (SUBMITTED + PENDING_REVIEW). */
  pending_proposal_count: number;
  first_work_plan_id?: number | null;
  created_at: string;
  updated_at: string;
  items: QuotationRequestResponse[];
};
