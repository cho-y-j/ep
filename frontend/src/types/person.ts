import type { CompanyType } from './auth';

export type PersonRole =
  | 'OPERATOR'
  | 'WORK_DIRECTOR'
  | 'GUIDE'
  | 'FIRE_WATCH'
  | 'SIGNALER'
  | 'INSPECTOR'
  | 'SITE_MANAGER';

export type PersonResponse = {
  id: number;
  supplier_id: number;
  name: string;
  birth?: string | null;
  phone?: string | null;
  roles: PersonRole[];
  has_photo: boolean;
  created_at: string;
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

export function rolesAllowedFor(companyType: CompanyType): PersonRole[] {
  switch (companyType) {
    case 'EQUIPMENT': return ['OPERATOR'];
    case 'MANPOWER': return ['WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER'];
    case 'BP': return [];
  }
}
