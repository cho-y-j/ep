export type FieldDeploymentStatus = 'REQUESTED' | 'ACCEPTED' | 'REJECTED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type ResourceType = 'EQUIPMENT' | 'PERSON';

export const FD_STATUS_LABEL: Record<FieldDeploymentStatus, string> = {
  REQUESTED: '수락 대기',
  ACCEPTED: '수락됨',
  REJECTED: '반려됨',
  ACTIVE: '현장 운영 중',
  COMPLETED: '종료',
  CANCELLED: '취소됨',
};

export type FieldDeploymentResponse = {
  id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  bp_company_id: number;
  bp_company_name?: string | null;
  resource_type: ResourceType;
  resource_id: number;
  resource_label?: string | null;
  target_site_id?: number | null;
  target_site_name?: string | null;
  start_date?: string | null;
  note?: string | null;
  status: FieldDeploymentStatus;
  requested_at: string;
  reviewed_at?: string | null;
  review_note?: string | null;
  activated_at?: string | null;
  completed_at?: string | null;
  daily_price?: number | null;
  monthly_price?: number | null;
  ot_price?: number | null;
  night_price?: number | null;
};
