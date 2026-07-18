import { useEffect, useState, type ReactNode } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';

type SiteRow = { id: number; name: string };

type SafetySettings = {
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
  default_maintenance_hours: number;
};

/** 현장 안전설정(§3.4) — 현장 선택 → 폼(법정 배지·완화 금지 가드) → 저장. BP(자기 현장)·ADMIN. */
export default function SafetySettingsPage() {
  const { user } = useAuth();
  const allowed = user?.role === 'ADMIN' || user?.role === 'BP';

  const [sites, setSites] = useState<SiteRow[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [data, setData] = useState<SafetySettings | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!allowed) return;
    api.get<SiteRow[]>('/api/sites')
      .then((r) => {
        setSites(r.data);
        if (r.data.length > 0) setSiteId(r.data[0].id);
      })
      .catch(() => setSites([]));
  }, [allowed]);

  useEffect(() => {
    if (siteId == null) { setData(null); return; }
    api.get<SafetySettings>(`/api/sites/${siteId}/safety-settings`)
      .then((r) => setData(r.data))
      .catch((e) => {
        const err = e as AxiosError<{ message?: string }>;
        toast.error(err.response?.data?.message ?? '안전설정을 불러올 수 없습니다');
        setData(null);
      });
  }, [siteId]);

  function set<K extends keyof SafetySettings>(key: K, value: SafetySettings[K]) {
    setData((d) => (d ? { ...d, [key]: value } : d));
  }

  async function save() {
    if (!data || siteId == null) return;
    setSaving(true);
    try {
      const { data: saved } = await api.put<SafetySettings>(`/api/sites/${siteId}/safety-settings`, {
        temp_caution: data.temp_caution,
        temp_warning: data.temp_warning,
        temp_danger: data.temp_danger,
        temp_extreme: data.temp_extreme,
        rest_interval_min: data.rest_interval_min,
        rest_duration_min: data.rest_duration_min,
        midday_start_hour: data.midday_start_hour,
        midday_end_hour: data.midday_end_hour,
        wind_stop_mps: data.wind_stop_mps,
        enforce_daily_inspection_gate: data.enforce_daily_inspection_gate,
        maintenance_interval_hours: data.maintenance_interval_hours,
      });
      setData(saved);
      toast.success('안전설정을 저장했습니다');
    } catch (e) {
      const err = e as AxiosError<{ message?: string }>;
      toast.error(err.response?.data?.message ?? '저장에 실패했습니다');
    } finally {
      setSaving(false);
    }
  }

  const legalBadge = (text: string) => (
    <span className="ml-2 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-[11px] font-medium text-slate-500">
      {text}
    </span>
  );

  if (!allowed) {
    return (
      <AppShell>
        <div className="p-6 text-slate-600">접근 권한이 없습니다.</div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="p-6 space-y-5">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">안전 설정</h1>
          <p className="mt-1 text-sm text-slate-500">
            현장별 폭염 단계·휴식·풍속 작업중지·일일점검 게이트·정비 주기를 설정합니다.
            법정 기준보다 <span className="font-medium text-rose-600">완화(느슨하게)는 불가</span>하며 강화(더 엄격)만 가능합니다.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <label className="text-sm font-medium text-slate-700">현장</label>
          <select
            value={siteId ?? ''}
            onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : null)}
            className="rounded border border-slate-300 px-3 py-1.5 text-sm min-w-[220px]"
          >
            {sites.length === 0 && <option value="">현장 없음</option>}
            {sites.map((s) => (
              <option key={s.id} value={s.id}>{s.name}</option>
            ))}
          </select>
          {data && (
            <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
              data.configured ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-500'
            }`}>
              {data.configured ? '저장된 설정' : '기본값(법정) 표시 중'}
            </span>
          )}
        </div>

        {!data ? (
          <div className="rounded border border-dashed border-slate-300 p-10 text-center text-slate-500">
            현장을 선택하세요.
          </div>
        ) : (
          <div className="space-y-5">
            {/* 폭염 4단계 임계온도 */}
            <section className="rounded border border-slate-200 bg-white p-4">
              <h2 className="text-sm font-semibold text-slate-800 mb-1">폭염 단계 임계온도 (℃)</h2>
              <p className="text-xs text-slate-500 mb-3">체감온도 기준. 낮출수록(강화) 더 빨리 경보합니다. 법정 상한 초과 불가.</p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <NumField label="주의" value={data.temp_caution} step={0.5}
                  onChange={(v) => set('temp_caution', v)} badge={legalBadge(`법정 ≤ ${data.legal_temp_caution}`)} />
                <NumField label="경고(휴식)" value={data.temp_warning} step={0.5}
                  onChange={(v) => set('temp_warning', v)} badge={legalBadge(`법정 ≤ ${data.legal_temp_warning}`)} />
                <NumField label="위험" value={data.temp_danger} step={0.5}
                  onChange={(v) => set('temp_danger', v)} badge={legalBadge(`법정 ≤ ${data.legal_temp_danger}`)} />
                <NumField label="중지" value={data.temp_extreme} step={0.5}
                  onChange={(v) => set('temp_extreme', v)} badge={legalBadge(`법정 ≤ ${data.legal_temp_extreme}`)} />
              </div>
            </section>

            {/* 휴식 + 무더위 시간대 */}
            <section className="rounded border border-slate-200 bg-white p-4">
              <h2 className="text-sm font-semibold text-slate-800 mb-3">휴식 · 무더위 시간대</h2>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <NumField label="휴식 간격(분)" value={data.rest_interval_min} step={5}
                  onChange={(v) => set('rest_interval_min', v)} badge={legalBadge(`법정 ≤ ${data.legal_rest_interval}`)} />
                <NumField label="휴식 시간(분)" value={data.rest_duration_min} step={5}
                  onChange={(v) => set('rest_duration_min', v)} badge={legalBadge(`법정 ≥ ${data.legal_rest_duration}`)} />
                <NumField label="무더위 시작(시)" value={data.midday_start_hour} step={1}
                  onChange={(v) => set('midday_start_hour', v)} />
                <NumField label="무더위 끝(시)" value={data.midday_end_hour} step={1}
                  onChange={(v) => set('midday_end_hour', v)} />
              </div>
            </section>

            {/* 강풍 작업중지 */}
            <section className="rounded border border-slate-200 bg-white p-4">
              <h2 className="text-sm font-semibold text-slate-800 mb-1">강풍 작업중지 (S1)</h2>
              <p className="text-xs text-slate-500 mb-3">풍속이 이 값을 초과하면 옥외 작업중지 경보. 낮출수록(강화) 더 빨리 중지. 법정 상한 초과 불가.</p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <NumField label="풍속 중지 임계(m/s)" value={data.wind_stop_mps} step={0.5}
                  onChange={(v) => set('wind_stop_mps', v)} badge={legalBadge(`법정 ≤ ${data.legal_wind_stop}`)} />
              </div>
            </section>

            {/* 일일점검 게이트 + 정비 주기 */}
            <section className="rounded border border-slate-200 bg-white p-4">
              <h2 className="text-sm font-semibold text-slate-800 mb-3">작업 시작 · 정비</h2>
              <label className="flex items-start gap-2 text-sm text-slate-700">
                <input type="checkbox" className="mt-0.5" checked={data.enforce_daily_inspection_gate}
                  onChange={(e) => set('enforce_daily_inspection_gate', e.target.checked)} />
                <span>
                  <span className="font-medium">일일점검 미완 시 작업시작 차단 (S3)</span>
                  <span className="block text-xs text-slate-500">
                    체크: 당일 조종원 일일점검이 없는 장비는 작업시작 400 차단 / 해제: 시작 허용하되 경고 알림.
                  </span>
                </span>
              </label>
              <div className="mt-3 grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div>
                  <label className="block text-xs font-medium text-slate-600 mb-1">
                    정비 주기(가동시간, h)
                    {legalBadge(`기본 ${data.default_maintenance_hours}`)}
                  </label>
                  <input
                    type="number" min={1} step={10}
                    value={data.maintenance_interval_hours ?? ''}
                    placeholder="비활성"
                    onChange={(e) => set('maintenance_interval_hours', e.target.value === '' ? null : Number(e.target.value))}
                    className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                  />
                  <p className="mt-1 text-[11px] text-slate-400">비우면 정비 알림 비활성 (S4').</p>
                </div>
              </div>
            </section>

            <div className="flex items-center gap-3">
              <button
                onClick={save} disabled={saving}
                className="rounded bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              >
                {saving ? '저장 중…' : '저장'}
              </button>
              <span className="text-xs text-slate-400">저장 시 법정 완화 여부를 서버가 검증합니다.</span>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}

function NumField({ label, value, step, onChange, badge }: {
  label: string;
  value: number;
  step: number;
  onChange: (v: number) => void;
  badge?: ReactNode;
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-slate-600 mb-1">{label}{badge}</label>
      <input
        type="number" step={step}
        value={value}
        onChange={(e) => onChange(e.target.value === '' ? 0 : Number(e.target.value))}
        className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
      />
    </div>
  );
}
