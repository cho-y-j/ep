export type AlertLevel = 'info' | 'caution' | 'warning' | 'danger';

export type SafetyAlertResponse = {
  id: number;
  person_id: number;
  person_name: string | null;
  person_phone?: string | null;
  person_has_photo?: boolean | null;
  kind: string;
  level: AlertLevel | string;
  message: string | null;
  hr: number | null;
  spo2: number | null;
  body_temp: number | null;
  lat: number | null;
  lng: number | null;
  site_id: number | null;
  work_plan_id: number | null;
  resolved: boolean;
  resolved_at: string | null;
  created_at: string;
};

export const LEVEL_LABEL: Record<string, string> = {
  info: '정보',
  caution: '주의',
  warning: '경고',
  danger: '위험',
};

export const LEVEL_BADGE: Record<string, string> = {
  info: 'bg-slate-100 text-slate-700',
  caution: 'bg-amber-100 text-amber-700',
  warning: 'bg-orange-100 text-orange-700',
  danger: 'bg-rose-100 text-rose-700 ring-1 ring-rose-300',
};

export const KIND_LABEL: Record<string, string> = {
  emergency: '긴급호출',
  fall: '낙상감지',
  abnormal_vital: '바이탈이상',
  manual: '수동호출',
  heat: '폭염알림',
  rest: '휴식알림',
};
