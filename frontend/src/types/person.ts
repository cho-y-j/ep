import type { CompanyType } from './auth';
import type { PersonAssignmentStatus } from './assignment';

export type PersonRole =
  | 'OPERATOR'
  | 'WORK_DIRECTOR'
  | 'GUIDE'
  | 'FIRE_WATCH'
  | 'SIGNALER'
  | 'INSPECTOR'
  | 'SITE_MANAGER';

export type PersonStatus = 'WORKING' | 'VACATION' | 'RETIRED';
export type EmploymentType = 'DIRECT' | 'SUBCONTRACT';
export type HealthRiskLevel = 'NORMAL' | 'CAUTION' | 'HIGH';

export type PersonResponse = {
  id: number;
  supplier_id: number;
  supplier_name?: string | null;
  supplier_type?: CompanyType | null;
  name: string;
  birth?: string | null;
  phone?: string | null;
  roles: PersonRole[];
  // 신규 (V9)
  employee_no?: string | null;
  job_title?: string | null;
  team?: string | null;
  qualification?: string | null;
  address?: string | null;
  email?: string | null;
  hired_at?: string | null;
  username?: string | null;
  status: PersonStatus;
  employment_type: EmploymentType;
  // P5-W4: 건강 위험군(표시용)
  health_risk_level?: HealthRiskLevel;
  // ---
  // V11 배치 정보
  current_site_id?: number | null;
  current_site_name?: string | null;
  assignment_status?: PersonAssignmentStatus;
  last_assigned_at?: string | null;
  // ---
  has_photo: boolean;
  expiring_count: number;
  document_count: number;
  created_at: string;
  updated_at?: string | null;
};

export const PERSON_ROLE_LABEL: Record<PersonRole, string> = {
  OPERATOR: '조종원',
  WORK_DIRECTOR: '작업지휘자',
  GUIDE: '유도원',
  FIRE_WATCH: '화기감시자',
  SIGNALER: '신호수',
  INSPECTOR: '점검원',
  SITE_MANAGER: '소장',
};

export const ALL_PERSON_ROLES: PersonRole[] = [
  'OPERATOR', 'WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER',
];

export const PERSON_STATUS_LABEL: Record<PersonStatus, string> = {
  WORKING: '근무 중',
  VACATION: '휴가',
  RETIRED: '퇴사',
};

export const EMPLOYMENT_TYPE_LABEL: Record<EmploymentType, string> = {
  DIRECT: '직영',
  SUBCONTRACT: '외주',
};

export const HEALTH_RISK_LABEL: Record<HealthRiskLevel, string> = {
  NORMAL: '건강 양호',
  CAUTION: '건강 주의',
  HIGH: '건강 고위험군',
};

export const HEALTH_RISK_CHIP_CLS: Record<HealthRiskLevel, string> = {
  NORMAL: 'bg-emerald-100 text-emerald-700',
  CAUTION: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-rose-100 text-rose-700',
};

export function rolesAllowedFor(companyType: CompanyType): PersonRole[] {
  switch (companyType) {
    // 장비공급사·BP 는 모든 역할 등록 가능(조종원 외 신호수·유도원 등). 인력공급사는 인력역할만.
    case 'EQUIPMENT': return ALL_PERSON_ROLES;
    case 'MANPOWER': return ['WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER'];
    case 'BP': return ALL_PERSON_ROLES;
    // 안전점검회사 — 점검원만.
    case 'SAFETY_INSPECTION': return ['INSPECTOR'];
  }
}
