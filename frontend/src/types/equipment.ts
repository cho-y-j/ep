export type EquipmentCategory =
  | 'EXCAVATOR'
  | 'WHEEL_LOADER'
  | 'CRANE'
  | 'FORKLIFT'
  | 'DOZER'
  | 'GRADER'
  | 'AERIAL_LIFT'
  | 'PUMP_TRUCK'
  | 'ATTACHMENT'
  | 'ROLLER'
  | 'PILE_DRIVER'
  | 'CONCRETE_MIXER_TRUCK'
  | 'ASPHALT_FINISHER'
  | 'CRUSHER'
  | 'AIR_COMPRESSOR'
  | 'BORING_MACHINE'
  | 'TOWER_CRANE'
  | 'DUMP_TRUCK'
  | 'SKID_LOADER'
  | 'TRAILER'
  | 'WING_BODY'
  | 'CARGO_TRUCK'
  | 'FREIGHT_TRUCK'
  | 'TANK_LORRY'
  | 'LADDER_TRUCK';

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
  // S4'(P3a) 가동시간 기반 정비 — 누적 가동시간 / 현재 현장 정비주기(null=비활성) / 도래 여부.
  cumulative_work_hours: number;
  maintenance_interval_hours?: number | null;
  maintenance_due: boolean;
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
  ROLLER: '롤러',
  PILE_DRIVER: '항타·항발기',
  CONCRETE_MIXER_TRUCK: '콘크리트믹서트럭',
  ASPHALT_FINISHER: '아스팔트피니셔',
  CRUSHER: '쇄석기',
  AIR_COMPRESSOR: '공기압축기',
  BORING_MACHINE: '천공기',
  TOWER_CRANE: '타워크레인',
  DUMP_TRUCK: '덤프트럭',
  SKID_LOADER: '스키드로더',
  TRAILER: '트레일러',
  WING_BODY: '윙바디',
  CARGO_TRUCK: '카고트럭',
  FREIGHT_TRUCK: '화물차',
  TANK_LORRY: '탱크로리',
  LADDER_TRUCK: '사다리차',
};

/**
 * 종류 code → 표시 라벨. 어드민이 추가한 새 코드는 정적 맵에 없으므로 code 자체를 폴백으로 반환.
 * 새 코드의 한글 이름은 useEquipmentTypes() 훅(런타임 /api/equipment-types)으로 해석.
 */
export function equipmentCategoryLabel(code: string | null | undefined): string {
  if (!code) return '';
  return EQUIPMENT_CATEGORY_LABEL[code as EquipmentCategory] ?? code;
}

// 표시 순서: 건설기계(등록증) → 차량(자동차등록증) → 기타.
export const EQUIPMENT_CATEGORIES: EquipmentCategory[] = [
  'EXCAVATOR', 'WHEEL_LOADER', 'CRANE', 'FORKLIFT', 'DOZER', 'GRADER', 'PUMP_TRUCK',
  'ROLLER', 'PILE_DRIVER', 'CONCRETE_MIXER_TRUCK', 'ASPHALT_FINISHER', 'CRUSHER',
  'AIR_COMPRESSOR', 'BORING_MACHINE', 'TOWER_CRANE', 'DUMP_TRUCK', 'SKID_LOADER',
  'AERIAL_LIFT', 'TRAILER', 'WING_BODY', 'CARGO_TRUCK', 'FREIGHT_TRUCK', 'TANK_LORRY', 'LADDER_TRUCK',
  'ATTACHMENT',
];
