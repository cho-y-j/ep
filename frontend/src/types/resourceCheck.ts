export type ResourceCheckType = 'VEHICLE_SAFETY' | 'HEALTH_CHECK' | 'SAFETY_TRAINING' | 'OTHER';
export type ResourceCheckStatus = 'REQUESTED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type ResourceOwnerType = 'EQUIPMENT' | 'PERSON';

export const CHECK_TYPE_LABEL: Record<ResourceCheckType, string> = {
  VEHICLE_SAFETY: '자동차 안전점검',
  HEALTH_CHECK: '건강검진',
  SAFETY_TRAINING: '안전교육',
  OTHER: '기타',
};

export const CHECK_STATUS_LABEL: Record<ResourceCheckStatus, string> = {
  REQUESTED: '회신 대기',
  SUBMITTED: '검토 대기',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  CANCELLED: '취소됨',
};

export const CHECK_STATUS_CHIP_CLS: Record<ResourceCheckStatus, string> = {
  REQUESTED: 'bg-amber-100 text-amber-800',
  SUBMITTED: 'bg-blue-100 text-blue-800',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-rose-100 text-rose-800',
  CANCELLED: 'bg-slate-200 text-slate-600',
};

export type ResourceCheckResponse = {
  id: number;
  work_plan_id?: number | null;
  owner_type: ResourceOwnerType;
  owner_id: number;
  owner_label: string;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  bp_company_id: number;
  check_type: ResourceCheckType;
  due_date?: string | null;
  notes?: string | null;
  status: ResourceCheckStatus;
  document_id?: number | null;
  issued_at: string;
  submitted_at?: string | null;
  reviewed_at?: string | null;
  review_note?: string | null;
  /** R2 조합 스냅샷 — 같은 값 행을 목록에서 조합 묶음으로 그룹핑(단독 발행=null/생략). */
  combo_equipment_id?: number | null;
  combo_equipment_label?: string | null;
};
