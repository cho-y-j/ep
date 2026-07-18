// 원청(client_org) 통합 관제 허브 — 백엔드 ClientControlDtos 와 1:1 (전역 snake_case).

export type ClientSiteSummary = {
  site_id: number;
  name: string;
  code?: string | null;
  bp_company_name?: string | null;
  status?: string | null;
  participant_count: number;
  deployed_person_count: number;
  currently_checked_in: number;
  unresolved_alert_count: number;
};

export type SupplierItem = { company_id: number; name?: string | null; type?: string | null };
export type EquipmentStatusCount = { total: number; assigned: number; available: number; broken: number };
export type AttendanceSummary = { deployed_person_count: number; attended_today: number; currently_checked_in: number };
export type Congestion = { today_by_hour: number[]; week_avg_by_hour: number[] };
export type DailyInspection = { equipment_target: number; done_today: number; legal_target: number; legal_done: number };
export type AlertItem = {
  id: number; kind: string; level: string; message?: string | null;
  person_name?: string | null; created_at: string;
  // S5' 확인응답(ack) 여부.
  severity?: string | null; acknowledged_at?: string | null; escalated_at?: string | null;
};
export type ExpiringItem = { owner_type: string; owner_label: string; expiry_date: string; d_day: number };

export type ClientSiteOverview = {
  site_id: number;
  name: string;
  code?: string | null;
  address?: string | null;
  bp_company_name?: string | null;
  client_org_name?: string | null;
  status?: string | null;
  start_date?: string | null;
  end_date?: string | null;
  suppliers: SupplierItem[];
  equipment: EquipmentStatusCount;
  attendance: AttendanceSummary;
  congestion: Congestion;
  daily_inspection: DailyInspection;
  unresolved_alert_count: number;
  recent_alerts: AlertItem[];
  expiring_d30_count: number;
  expiring_docs: ExpiringItem[];
};
