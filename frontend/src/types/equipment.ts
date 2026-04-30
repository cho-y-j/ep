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

export type EquipmentResponse = {
  id: number;
  supplier_id: number;
  vehicle_no?: string | null;
  category: EquipmentCategory;
  model?: string | null;
  manufacturer?: string | null;
  year?: number | null;
  has_photo: boolean;
  created_at: string;
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
