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

export function rolesAllowedFor(companyType: CompanyType): PersonRole[] {
  switch (companyType) {
    case 'EQUIPMENT': return ['OPERATOR'];
    case 'MANPOWER': return ['WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER'];
    // BP 회사도 자체 인원 보유 가능 (직속 운전수/현장소장/지휘자 등 모든 역할)
    case 'BP': return ['OPERATOR', 'WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER'];
  }
}
