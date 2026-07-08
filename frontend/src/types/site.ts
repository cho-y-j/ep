import type { CompanyResponse, CompanyType } from './auth';

export type SiteStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED';
export type SiteParticipantType = 'EQUIPMENT_SUPPLIER' | 'MANPOWER_SUPPLIER';
export type SiteParticipantStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED';

export type SiteParticipantResponse = {
  id: number;
  site_id: number;
  company_id: number;
  company_name?: string | null;
  company_type?: CompanyType | null;
  participant_type: SiteParticipantType;
  status: SiteParticipantStatus;
  added_at: string;
};

export type SiteResponse = {
  id: number;
  bp_company_id: number;
  bp_company_name?: string | null;
  name: string;
  code?: string | null;
  address?: string | null;
  detail_address?: string | null;
  start_date?: string | null;
  end_date?: string | null;
  status: SiteStatus;
  latitude?: number | null;
  longitude?: number | null;
  polygon_geojson?: string | null;
  map_zoom?: number | null;
  participant_count: number;
  created_at: string;
  updated_at: string;
  participants?: SiteParticipantResponse[] | null;
};

export type CreateSitePayload = {
  bp_company_id?: number;
  name: string;
  code?: string;
  address?: string;
  detail_address?: string;
  start_date?: string;
  end_date?: string;
  latitude?: number | null;
  longitude?: number | null;
  polygon_geojson?: string | null;
  map_zoom?: number | null;
};

export type UpdateSitePayload = CreateSitePayload & {
  status: SiteStatus;
};

export type SupplierCompany = CompanyResponse;

export const SITE_STATUS_LABEL: Record<SiteStatus, string> = {
  ACTIVE: '진행중',
  PAUSED: '일시중지',
  COMPLETED: '완료',
  ARCHIVED: '보관',
};

export const SITE_PARTICIPANT_TYPE_LABEL: Record<SiteParticipantType, string> = {
  EQUIPMENT_SUPPLIER: '장비공급사',
  MANPOWER_SUPPLIER: '인력공급사',
};
