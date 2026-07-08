export type InspectionTarget = 'VEHICLE' | 'PERSON';
export type InspectionKind = 'VEHICLE_INSPECTION' | 'ENTRY_CHECK';
export type InspectionStatus = 'PENDING' | 'SENT' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';

export const KIND_LABEL: Record<InspectionKind, string> = {
  VEHICLE_INSPECTION: '차량검사 (사전)',
  ENTRY_CHECK: '입소검사 (당일)',
};
export const STATUS_LABEL: Record<InspectionStatus, string> = {
  PENDING: '등록',
  SENT: '통보 완료',
  CONFIRMED: '공급사 확인',
  COMPLETED: '검사 완료',
  CANCELLED: '취소',
};
export const STATUS_CHIP: Record<InspectionStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-700',
  SENT: 'bg-blue-100 text-blue-700',
  CONFIRMED: 'bg-amber-100 text-amber-700',
  COMPLETED: 'bg-emerald-100 text-emerald-700',
  CANCELLED: 'bg-rose-100 text-rose-700',
};

export type InspectionResponse = {
  id: number;
  site_id: number;
  site_name?: string | null;
  supplier_company_id?: number | null;
  supplier_company_name?: string | null;
  target_type: InspectionTarget;
  target_id: number;
  target_label: string;
  kind: InspectionKind;
  scheduled_at: string;
  duration_minutes?: number | null;
  status: InspectionStatus;
  sent_at?: string | null;
  confirmed_at?: string | null;
  completed_at?: string | null;
  result_notes?: string | null;
  created_at: string;
};

export type CreateInspectionPayload = {
  site_id: number;
  target_type: InspectionTarget;
  target_id: number;
  kind: InspectionKind;
  scheduled_at: string;
  duration_minutes?: number | null;
  supplier_company_id?: number | null;
  inspector_id?: number | null;
};
