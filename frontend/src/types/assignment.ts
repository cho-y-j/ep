import type { EquipmentCategory } from './equipment';
import type { PersonRole } from './person';

export type EquipmentAssignmentStatus = 'AVAILABLE' | 'ASSIGNED' | 'BROKEN';
export type PersonAssignmentStatus = 'ON_DUTY' | 'OFF_DUTY' | 'INACTIVE';

export const EQUIPMENT_ASSIGNMENT_STATUS_LABEL: Record<EquipmentAssignmentStatus, string> = {
  AVAILABLE: '미배치',
  ASSIGNED: '배치중',
  BROKEN: '고장',
};

export const PERSON_ASSIGNMENT_STATUS_LABEL: Record<PersonAssignmentStatus, string> = {
  ON_DUTY: '배치중',
  OFF_DUTY: '미배치',
  INACTIVE: '비활성',
};

export type AssignmentResponse = {
  id: number;
  resource_id: number;
  site_id: number;
  site_name?: string | null;
  assigned_at: string;
  released_at?: string | null;
  assigned_by?: number | null;
  released_by?: number | null;
  note?: string | null;
  release_reason?: string | null;
  active: boolean;
};

export type AssignPayload = {
  site_id: number;
  note?: string;
  /** ADMIN 만 사용 가능. 서류 미비 (DOCUMENTS_BLOCKED) 강제 진행. */
  override?: boolean;
  /** override=true 일 때 필수. audit 로그에 기록됨. */
  override_reason?: string;
};

export type ReleasePayload = {
  release_reason?: string;
};

export type EquipmentCandidateResponse = {
  id: number;
  supplier_id: number;
  supplier_name?: string | null;
  name: string;
  category: EquipmentCategory;
  code?: string | null;
  vehicle_no?: string | null;
  has_photo: boolean;
  assignment_status: EquipmentAssignmentStatus;
  current_site_id?: number | null;
  current_site_name?: string | null;
  last_assigned_at?: string | null;
  previously_used_on_site: boolean;
  currently_assigned: boolean;
  expiring_documents: number;
  missing_documents: number;
  blocked: boolean;
};

export type PersonCandidateResponse = {
  id: number;
  supplier_id: number;
  supplier_name?: string | null;
  name: string;
  roles: PersonRole[];
  employee_no?: string | null;
  job_title?: string | null;
  has_photo: boolean;
  assignment_status: PersonAssignmentStatus;
  current_site_id?: number | null;
  current_site_name?: string | null;
  last_assigned_at?: string | null;
  previously_used_on_site: boolean;
  currently_assigned: boolean;
  expiring_documents: number;
  missing_documents: number;
  blocked: boolean;
};
