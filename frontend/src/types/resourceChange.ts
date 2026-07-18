export type ResourceChangeKind = 'EQUIPMENT' | 'OPERATOR' | 'COMPANY';
export type ResourceChangeStatus = 'DRAFT' | 'CONFIRMED';

export const CHANGE_KIND_LABEL: Record<ResourceChangeKind, string> = {
  EQUIPMENT: '장비',
  OPERATOR: '조종원',
  COMPANY: '업체',
};

/** l3_snapshot 판정 블록(신청 시점 deploy-check). */
export type L3Block = { kind: string; label: string; detail?: string | null };
export type L3Snapshot = { ready: boolean; blocks: L3Block[]; checkedAt?: string };

export type ResourceChangeRequestResponse = {
  id: number;
  site_id?: number | null;
  site_name?: string | null;
  bp_company_id?: number | null;
  bp_name?: string | null;
  supplier_company_id: number;
  supplier_name?: string | null;
  change_kind: ResourceChangeKind;
  old_equipment_id?: number | null;
  new_equipment_id?: number | null;
  old_person_id?: number | null;
  new_person_id?: number | null;
  old_label?: string | null;
  new_label?: string | null;
  old_vehicle_no?: string | null;
  new_vehicle_no?: string | null;
  old_operator_name?: string | null;
  new_operator_name?: string | null;
  old_contact?: string | null;
  new_contact?: string | null;
  reason?: string | null;
  apply_date?: string | null;
  l3_snapshot?: L3Snapshot | null;
  work_plan_id?: number | null;
  status: ResourceChangeStatus;
  created_at: string;
};

export type CreateResourceChangePayload = {
  change_kind: ResourceChangeKind;
  site_id?: number;
  site_name?: string;
  bp_company_id?: number;
  supplier_company_id?: number;
  old_equipment_id?: number;
  new_equipment_id?: number;
  old_person_id?: number;
  new_person_id?: number;
  reason?: string;
  apply_date?: string;
  work_plan_id?: number;
};
