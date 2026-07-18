export type RateType = 'DAILY' | 'MONTHLY';
export type SignStatus = 'UNSIGNED' | 'SIGNED' | 'PHOTO';

export type DailyWorkLog = {
  id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  site_id?: number | null;
  site_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  contract_id?: number | null;
  equipment_id?: number | null;
  equipment_label?: string | null;
  person_id?: number | null;
  person_name?: string | null;
  work_date: string;
  work_content?: string | null;
  work_location?: string | null;
  rate_type: RateType;
  ot_early: number;
  ot_lunch: number;
  ot_evening: number;
  ot_night: number;
  ot_overnight: number;
  start_time?: string | null;
  end_time?: string | null;
  memo?: string | null;
  sign_status: SignStatus;
  bp_signed_at?: string | null;
  has_sign_image: boolean;
  has_slip_photo: boolean;
  created_at: string;
};

export type LedgerRow = {
  id: number;
  work_date: string;
  work_content?: string | null;
  ot_early: number;
  ot_lunch: number;
  ot_evening: number;
  ot_night: number;
  ot_overnight: number;
  sign_status: SignStatus;
  memo?: string | null;
};

export type Ledger = {
  period: string;
  start_date: string;
  end_date: string;
  settlement_day?: number | null;
  site_id?: number | null;
  site_name?: string | null;
  equipment_id?: number | null;
  equipment_label?: string | null;
  person_id?: number | null;
  person_name?: string | null;
  contract_id?: number | null;
  rate_type: RateType;
  base_rate?: number | null;
  ot_rates?: { early: number | null; lunch: number | null; evening: number | null; night: number | null; overnight: number | null } | null;
  rows: LedgerRow[];
  totals: {
    work_days: number;
    ot_early_hours: number;
    ot_lunch_hours: number;
    ot_evening_hours: number;
    ot_night_hours: number;
    ot_overnight_hours: number;
    base_amount?: number | null;
    ot_amount?: number | null;
    total_amount?: number | null;
  };
};

/** OT 5분류 + 고정 시간대(§3.6.3 발견②). key = DailyWorkLog / LedgerRow 필드 접미. */
export const OT_COLS = [
  { key: 'early', label: '조출', time: '05-07' },
  { key: 'lunch', label: '점심', time: '12-13' },
  { key: 'evening', label: '연장', time: '17-19' },
  { key: 'night', label: '야간', time: '19-21:30' },
  { key: 'overnight', label: '철야', time: '21-06' },
] as const;

export const SIGN_BADGE: Record<SignStatus, { text: string; cls: string }> = {
  UNSIGNED: { text: '미서명', cls: 'bg-slate-100 text-slate-500' },
  SIGNED: { text: '서명완료', cls: 'bg-emerald-100 text-emerald-700' },
  PHOTO: { text: '사진갈음', cls: 'bg-blue-100 text-blue-700' },
};

export function money(n?: number | null): string {
  return n != null ? n.toLocaleString() + '원' : '-';
}

/** 두 날짜(YYYY-MM-DD) 사이 모든 날짜 배열. 월간 원장이 빈 날짜행까지 그리기 위함. TZ-safe(UTC 연산). */
export function eachDate(start: string, end: string): string[] {
  const out: string[] = [];
  const [sy, sm, sd] = start.split('-').map(Number);
  const [ey, em, ed] = end.split('-').map(Number);
  const cur = new Date(Date.UTC(sy, sm - 1, sd));
  const last = new Date(Date.UTC(ey, em - 1, ed));
  while (cur <= last) {
    const m = String(cur.getUTCMonth() + 1).padStart(2, '0');
    const d = String(cur.getUTCDate()).padStart(2, '0');
    out.push(`${cur.getUTCFullYear()}-${m}-${d}`);
    cur.setUTCDate(cur.getUTCDate() + 1);
  }
  return out;
}
