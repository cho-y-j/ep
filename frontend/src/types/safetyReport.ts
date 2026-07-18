// P3d 안전관리 이행 보고서 — 백엔드 SafetyReportDtos 와 1:1 (전역 snake_case).

export type AlertSummary = {
  total: number;
  ack_needed: number;
  acknowledged: number;
  ack_rate_pct: number | null;
  avg_ack_minutes: number | null;
  escalated_count: number;
};

export type InspectionSummary = {
  legal_total: number;
  legal_days: number;
  nfc_verified: number;
  nfc_rate_pct: number | null;
  operator_total: number;
  operator_days: number;
  target_equipment: number;
};

export type WorkComplianceSummary = {
  plan_total: number;
  plan_fully_signed: number;
  log_total: number;
  log_signed: number;
  log_sign_rate_pct: number | null;
};

export type TimelineAlert = {
  id: number;
  kind: string;
  kind_label: string;
  severity: string | null;
  level: string | null;
  created_at: string;
  acknowledged_at: string | null;
  ack_elapsed_seconds: number | null;
  escalated_at: string | null;
  resolved: boolean;
  needs_ack: boolean;
};

export type WindEvent = {
  entered_at: string | null;
  cleared_at: string | null;
  wind_mps: number | null;
};

export type TimelineDay = {
  date: string;
  alerts: TimelineAlert[];
  legal_inspections: number;
  operator_inspections: number;
  wind_event: WindEvent | null;
};

export type UnsignedPlan = { work_plan_id: number; work_date: string; title: string | null; pending_roles: string[] };
export type UnsignedLog = { id: number; work_date: string; label: string };

export type Noncompliance = {
  unacknowledged_alerts: TimelineAlert[];
  uninspected_work_days: string[];
  unsigned_plans: UnsignedPlan[];
  unsigned_logs: UnsignedLog[];
};

export type SafetyStandard = {
  site_id: number;
  configured: boolean;
  temp_caution: number;
  temp_warning: number;
  temp_danger: number;
  temp_extreme: number;
  rest_interval_min: number;
  rest_duration_min: number;
  midday_start_hour: number;
  midday_end_hour: number;
  wind_stop_mps: number;
  enforce_daily_inspection_gate: boolean;
  maintenance_interval_hours: number | null;
  legal_temp_caution: number;
  legal_temp_warning: number;
  legal_temp_danger: number;
  legal_temp_extreme: number;
  legal_rest_interval: number;
  legal_rest_duration: number;
  legal_wind_stop: number;
};

export type SafetyReport = {
  site_id: number;
  site_name: string | null;
  site_code: string | null;
  bp_company_name: string | null;
  client_org_name: string | null;
  from: string;
  to: string;
  generated_at: string;
  generated_by: string | null;
  alert_summary: AlertSummary;
  inspection_summary: InspectionSummary;
  work_compliance_summary: WorkComplianceSummary;
  timeline: TimelineDay[];
  noncompliance: Noncompliance;
  standard: SafetyStandard;
};

export const SEVERITY_LABEL: Record<string, string> = {
  EMERGENCY: '긴급',
  CAUTION: '주의',
  NORMAL: '일반',
};

export const SEVERITY_CHIP: Record<string, string> = {
  EMERGENCY: 'bg-rose-100 text-rose-700',
  CAUTION: 'bg-amber-100 text-amber-700',
  NORMAL: 'bg-slate-100 text-slate-600',
};

/** 발송→확인 소요(초) → 사람이 읽는 문자열. */
export function formatElapsed(seconds: number | null): string {
  if (seconds == null) return '-';
  if (seconds < 60) return `${seconds}초`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return s === 0 ? `${m}분` : `${m}분 ${s}초`;
}

/** ISO datetime → HH:MM (시각만). */
export function timeOnly(iso: string | null): string {
  return iso ? iso.slice(11, 16) : '-';
}

/** 기본 조회 기간 = 최근 N일(오늘 포함). from/to 를 yyyy-MM-dd 로 반환. */
export function defaultPeriod(days = 30): { from: string; to: string } {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - (days - 1));
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { from: iso(from), to: iso(to) };
}
