export type EquipmentCategory =
  | 'EXCAVATOR'
  | 'WHEEL_LOADER'
  | 'CRANE'
  | 'FORKLIFT'
  | 'DOZER'
  | 'GRADER'
  | 'AERIAL_LIFT'
  | 'PUMP_TRUCK'
  | 'ATTACHMENT';

import type { EquipmentAssignmentStatus } from './assignment';

export type EquipmentResponse = {
  id: number;
  supplier_id: number;
  supplier_name?: string | null;
  vehicle_no?: string | null;
  category: EquipmentCategory;
  model?: string | null;
  manufacturer?: string | null;
  year?: number | null;
  is_external?: boolean;
  vehicle_owner_name?: string | null;
  vehicle_owner_business_no?: string | null;
  operator_person_id?: number | null;
  has_photo: boolean;
  expiring_count: number;
  // 신규 (V8)
  code?: string | null;
  serial_number?: string | null;
  usage_hours?: number | null;
  weight_kg?: number | null;
  bucket_capacity?: number | string | null;
  insurance_expiry?: string | null;
  // V70 차량관리 due
  inspection_due_date?: string | null;
  oil_change_due_date?: string | null;
  registration_expiry?: string | null;
  operating_hours: number;
  idle_hours: number;
  downtime_hours: number;
  utilization_pct?: number | null;
  // V11 배치 정보
  current_site_id?: number | null;
  current_site_name?: string | null;
  assignment_status?: EquipmentAssignmentStatus;
  last_assigned_at?: string | null;
  // ---
  created_at: string;
  updated_at?: string | null;
};

export const EQUIPMENT_CATEGORY_LABEL: Record<EquipmentCategory, string> = {
  EXCAVATOR: '굴삭기',
  WHEEL_LOADER: '휠로더',
  CRANE: '크레인',
  FORKLIFT: '지게차',
  DOZER: '도저',
  GRADER: '그레이더',
  AERIAL_LIFT: '고소작업차',
  PUMP_TRUCK: '펌프카',
  ATTACHMENT: '어태치먼트',
};

export const EQUIPMENT_CATEGORIES: EquipmentCategory[] = [
  'EXCAVATOR', 'WHEEL_LOADER', 'CRANE', 'FORKLIFT', 'DOZER',
  'GRADER', 'AERIAL_LIFT', 'PUMP_TRUCK', 'ATTACHMENT',
];
