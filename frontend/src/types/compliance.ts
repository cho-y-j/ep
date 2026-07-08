import type { OwnerType } from './document';

export type ComplianceItem = {
  document_type_id: number;
  document_type_name: string;
  required: boolean;
  blocks_assignment: boolean;
  has_expiry: boolean;
  present: boolean;
  verified: boolean;
  rejected: boolean;
  ocr_review_required: boolean;
  expired: boolean;
  expiring_soon: boolean;
  document_id?: number | null;
  expiry_date?: string | null;
  open_supplement: boolean;
};

export type ResourceCompliance = {
  owner_type: OwnerType;
  owner_id: number;
  owner_name: string;
  owner_sub_label?: string | null;
  supplier_company_id?: number | null;
  supplier_company_name?: string | null;
  items: ComplianceItem[];
  required_total: number;
  required_ok: number;
  missing_count: number;
  rejected_count: number;
  expiring_count: number;
  open_supplement_count: number;
  ready_for_work_plan: boolean;
};

export type SiteCompliance = {
  site_id: number;
  site_name: string;
  bp_company_id: number;
  bp_company_name: string;
  bp_company: ResourceCompliance;
  equipments: ResourceCompliance[];
  persons: ResourceCompliance[];
  total_required_items: number;
  total_ok_items: number;
  progress_pct: number;
  ready_for_work_plan: boolean;
};

export type SupplementStatus = 'OPEN' | 'RESOLVED' | 'CANCELLED';

export type SupplementResponse = {
  id: number;
  requester_user_id: number;
  requester_user_name?: string | null;
  requester_role: string;
  target_supplier_company_id: number;
  target_supplier_company_name?: string | null;
  target_owner_type: OwnerType;
  target_owner_id: number;
  target_owner_name?: string | null;
  document_type_id: number;
  document_type_name?: string | null;
  context_site_id?: number | null;
  context_site_name?: string | null;
  context_work_plan_id?: number | null;
  reason?: string | null;
  status: SupplementStatus;
  resolved_doc_id?: number | null;
  resolved_at?: string | null;
  cancelled_at?: string | null;
  created_at: string;
};

export const SUPPLEMENT_STATUS_LABEL: Record<SupplementStatus, string> = {
  OPEN: '진행 중',
  RESOLVED: '처리 완료',
  CANCELLED: '취소됨',
};
