import type { EquipmentCategory } from './equipment';

export type WorkPlanStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'IN_PROGRESS'
  | 'DONE'
  | 'CANCELLED';

export type ComplianceStatus = 'OK' | 'WARNING' | 'BLOCKED' | 'OVERRIDDEN';

export type WorkPlanEquipmentResponse = {
  id: number;
  equipment_id: number;
  equipment_name?: string | null;
  category?: EquipmentCategory | null;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  purpose?: string | null;
  note?: string | null;
  daily_rate?: number | null;
  monthly_rate?: number | null;
  source_quotation_target_id?: number | null;
  created_at: string;
};

export type WorkPlanPersonResponse = {
  id: number;
  person_id: number;
  person_name?: string | null;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  equipment_id?: number | null;
  role?: string | null;
  note?: string | null;
  created_at: string;
};

export type ComplianceCheckResponse = {
  id: number;
  target_type: 'EQUIPMENT' | 'PERSON';
  target_id: number;
  status: ComplianceStatus;
  reason?: string | null;
  checked_at: string;
  override_by?: number | null;
  override_reason?: string | null;
};

export type WorkPlanResponse = {
  id: number;
  site_id: number;
  site_name?: string | null;
  bp_company_id: number;
  bp_company_name?: string | null;
  work_date: string;
  start_time?: string | null;
  end_time?: string | null;
  title: string;
  work_location?: string | null;
  description?: string | null;
  status: WorkPlanStatus;
  created_by?: number | null;
  submitted_at?: string | null;
  submitted_by?: number | null;
  approved_at?: string | null;
  approved_by?: number | null;
  cancelled_at?: string | null;
  cancel_reason?: string | null;
  created_at: string;
  updated_at: string;
  // P1c: L2 교체로 대체 생성된 계획서면 원본 id (이력 연결).
  cloned_from_id?: number | null;
  equipment?: WorkPlanEquipmentResponse[] | null;
  persons?: WorkPlanPersonResponse[] | null;
  compliance_checks?: ComplianceCheckResponse[] | null;
  // P1a 기반①: 저장된 워크시트 폼 상태 (132 필드 + roleAssign + 첨부 선택). 상세 응답만.
  form_values?: Record<string, any> | null;
};

export type WorkPlanPage = {
  content: WorkPlanResponse[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};

export type CreateWorkPlanPayload = {
  site_id: number;
  work_date: string;
  start_time?: string;
  end_time?: string;
  title: string;
  work_location?: string;
  description?: string;
};

export type UpdateWorkPlanPayload = Partial<Omit<CreateWorkPlanPayload, 'site_id'>>;

export type AddEquipmentPayload = {
  equipment_id: number;
  purpose?: string;
  note?: string;
  override?: boolean;
  override_reason?: string;
};

export type AddPersonPayload = {
  person_id: number;
  equipment_id?: number;
  role?: string;
  note?: string;
  override?: boolean;
  override_reason?: string;
};

export const WORK_PLAN_STATUS_LABEL: Record<WorkPlanStatus, string> = {
  DRAFT: '작성중',
  SUBMITTED: '제출됨',
  APPROVED: '승인됨',
  IN_PROGRESS: '진행중',
  DONE: '완료',
  CANCELLED: '취소됨',
};

export const COMPLIANCE_STATUS_LABEL: Record<ComplianceStatus, string> = {
  OK: '정상',
  WARNING: '주의',
  BLOCKED: '차단',
  OVERRIDDEN: '강제진행',
};
