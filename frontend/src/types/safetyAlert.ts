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
  // S5' 3등급 + 확인응답(ack).
  severity?: string | null;
  acknowledged_at?: string | null;
  ack_person_id?: number | null;
  escalated_at?: string | null;
  // P5-W2/W3 대응체인 골든타임 + 응답자 + BLE 릴레이 위치 보강.
  peer_notified_at?: string | null;
  first_response_at?: string | null;
  peer_escalated_at?: string | null;
  relayed_at?: string | null;
  relay_lat?: number | null;
  relay_lng?: number | null;
  responder_count?: number | null;
  responders?: SafetyAlertResponder[] | null;
  created_at: string;
};

export type SafetyAlertResponder = {
  person_id: number;
  name: string | null;
  created_at: string;
};

/** S5' 확인응답 대상 kind(작업자 수신 알림). SOS(emergency·fall)는 관리자 응답 흐름이라 ack 대상 아님. */
export const ACK_KINDS = new Set(['wind_stop', 'heat', 'rest']);

export type AckState = 'acknowledged' | 'escalated' | 'pending' | 'na';

/** 알림의 확인응답 상태 — 관제 ack 컬럼·미확인 필터용. severity 미지정(레거시)/비대상 kind = na. */
export function ackState(r: {
  kind: string;
  severity?: string | null;
  acknowledged_at?: string | null;
  escalated_at?: string | null;
}): AckState {
  const ackable = ACK_KINDS.has(r.kind) && r.severity != null && r.severity !== 'NORMAL';
  if (!ackable) return 'na';
  if (r.acknowledged_at) return 'acknowledged';
  if (r.escalated_at) return 'escalated';
  return 'pending';
}

export const SEVERITY_LABEL: Record<string, string> = {
  EMERGENCY: '긴급',
  CAUTION: '주의',
  NORMAL: '일반',
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
  fall_detected: '낙상 감지',
  abnormal_vital: '바이탈이상',
  vital_anomaly: '생체 이상',
  watch_offline: '신호 두절',
  manual: '수동호출',
  heat: '폭염알림',
  rest: '휴식알림',
  wind_stop: '강풍중지',
};
