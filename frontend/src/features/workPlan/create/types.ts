import type { PersonRole } from '../../../types/person';

export type RoleKey = 'operator' | 'supervisor' | 'signalman' | 'firewatch' | 'signaler';

export interface RequiredRoleDef {
  key: RoleKey;
  label: string;
  personRole: PersonRole;
  serverRole: string;
  required: boolean;
}

export const REQUIRED_ROLES: RequiredRoleDef[] = [
  { key: 'operator',   label: '조종원',     personRole: 'OPERATOR',      serverRole: '조종원',     required: true },
  { key: 'supervisor', label: '작업지휘자', personRole: 'WORK_DIRECTOR', serverRole: '작업지휘자', required: false },
  { key: 'signalman',  label: '유도원',     personRole: 'GUIDE',         serverRole: '유도원',     required: false },
  { key: 'firewatch',  label: '화기감시자', personRole: 'FIRE_WATCH',    serverRole: '화기감시자', required: false },
  { key: 'signaler',   label: '신호수',     personRole: 'SIGNALER',      serverRole: '신호수',     required: false },
];

export type RoleAssign = Record<RoleKey, number[]>;

export const EMPTY_ROLE_ASSIGN: RoleAssign = {
  operator: [],
  supervisor: [],
  signalman: [],
  firewatch: [],
  signaler: [],
};

export interface DocPreviewTarget {
  docId: number;
  category: string;
  mimeType: string;
  ownerName: string;
  originalName: string;
}
