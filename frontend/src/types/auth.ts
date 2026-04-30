export type Role =
  | 'ADMIN'
  | 'BP'
  | 'EQUIPMENT_SUPPLIER'
  | 'MANPOWER_SUPPLIER'
  | 'WORKER';

export type UserResponse = {
  id: number;
  email: string;
  name: string;
  phone?: string | null;
  role: Role;
  company_id?: number | null;
  is_company_admin: boolean;
  enabled: boolean;
  created_at: string;
};

export type TokenResponse = {
  access_token: string;
  refresh_token: string;
  token_type: 'Bearer';
  expires_in: number;
};

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: '시스템 관리자',
  BP: '발주사',
  EQUIPMENT_SUPPLIER: '장비공급사',
  MANPOWER_SUPPLIER: '인력공급사',
  WORKER: '작업자',
};

export const SIGNUP_ROLES: Role[] = ['BP', 'EQUIPMENT_SUPPLIER', 'MANPOWER_SUPPLIER', 'WORKER'];
