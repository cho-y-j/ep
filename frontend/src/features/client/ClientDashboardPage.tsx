import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import MiniBarChart from '../dashboard/MiniBarChart';
import HourlyBars from './HourlyBars';
import { ackState } from '../../types/safetyAlert';
import type { AlertItem, ClientSiteOverview, ClientSiteSummary, ExpiringItem, SupplierItem } from './types';

export default function ClientDashboardPage() {
  const [sites, setSites] = useState<ClientSiteSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [overview, setOverview] = useState<ClientSiteOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get<ClientSiteSummary[]>('/api/client/sites')
      .then((r) => {
        if (cancelled) return;
        setSites(r.data);
        if (r.data.length > 0) setSelectedId(r.data[0].site_id);
        else setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof AxiosError ? err.response?.data?.message ?? '현장 목록을 불러오지 못했습니다' : '오류');
        setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const loadOverview = useCallback((siteId: number) => {
    setLoading(true);
    setError(null);
    api.get<ClientSiteOverview>(`/api/client/sites/${siteId}/overview`)
      .then((r) => setOverview(r.data))
      .catch((err) => setError(err instanceof AxiosError ? err.response?.data?.message ?? '현장 관제 정보를 불러오지 못했습니다' : '오류'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (selectedId != null) loadOverview(selectedId);
  }, [selectedId, loadOverview]);

  const orgName = overview?.client_org_name ?? '원청';

  return (
    <AppShell breadcrumb={[{ label: '원청 관제' }]}>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-950">{orgName} 현장 통합 관제</h1>
          <p className="mt-1 text-sm text-slate-500">내 원청 현장의 투입·출근·안전·서류 리스크를 한눈에 봅니다 (읽기 전용).</p>
        </div>
        <div className="flex items-center gap-2">
          {sites.length > 0 && (
            <select
              value={selectedId ?? ''}
              onChange={(e) => setSelectedId(Number(e.target.value))}
              className="input bg-white"
            >
              {sites.map((s) => (
                <option key={s.site_id} value={s.site_id}>
                  {s.name}{s.bp_company_name ? ` · ${s.bp_company_name}` : ''}
                </option>
              ))}
            </select>
          )}
          <Link to="/safety-reports" className="inline-flex items-center rounded-lg border border-brand-200 bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-700 hover:bg-brand-100">
            안전관리 이행 보고서
          </Link>
          <span className="inline-flex items-center rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-500">읽기 전용</span>
        </div>
      </div>

      {error && (
        <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>
      )}

      {!error && sites.length === 0 && !loading && (
        <div className="card py-12 text-center text-sm text-slate-400">
          연결된 현장이 없습니다. 관리자에게 현장의 원청 연결을 요청하세요.
        </div>
      )}

      {loading && !overview && (
        <div className="card py-12 text-center text-sm text-slate-400">불러오는 중...</div>
      )}

      {overview && (
        <div className="space-y-5">
          {/* 요약 카드 */}
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
            <StatCard label="참여 공급사" value={overview.suppliers.length} unit="개사" />
            <StatCard
              label="투입 장비"
              value={overview.equipment.total}
              unit="대"
              sub={`배치 ${overview.equipment.assigned} · 가용 ${overview.equipment.available} · 고장 ${overview.equipment.broken}`}
            />
            <StatCard label="투입 인원" value={overview.attendance.deployed_person_count} unit="명" />
            <StatCard
              label="오늘 출근"
              value={overview.attendance.attended_today}
              unit="명"
              sub={`현재 체크인 ${overview.attendance.currently_checked_in}명`}
              tone="emerald"
            />
            <StatCard
              label="미해결 안전알림"
              value={overview.unresolved_alert_count}
              unit="건"
              tone={overview.unresolved_alert_count > 0 ? 'rose' : 'slate'}
            />
            <StatCard
              label="만료 임박 서류 (D-30)"
              value={overview.expiring_d30_count}
              unit="건"
              tone={overview.expiring_d30_count > 0 ? 'amber' : 'slate'}
            />
          </div>

          {/* 혼잡도 v1 */}
          <div>
            <div className="mb-2 flex items-center gap-2">
              <h2 className="text-sm font-bold text-slate-800">시간대별 혼잡도 (v1)</h2>
              <span className="text-[11px] text-slate-400">현장 단위 · 구역 드릴다운 미지원(정직 표기)</span>
            </div>
            <div className="grid gap-3 lg:grid-cols-2">
              <HourlyBars title="오늘 시간대별 재실 인원" subtitle="출근 세션 기준" data={overview.congestion.today_by_hour} />
              <HourlyBars title="이번주 평균" subtitle="요일 평균(재실 인원)" data={overview.congestion.week_avg_by_hour} tone="emerald" />
            </div>
          </div>

          {/* 장비 상태 + 참여 공급사 + 일일점검 */}
          <div className="grid gap-3 lg:grid-cols-3">
            <MiniBarChart
              title="투입 장비 상태"
              data={[
                { label: '배치', value: overview.equipment.assigned, tone: 'emerald' },
                { label: '가용', value: overview.equipment.available, tone: 'slate' },
                { label: '고장', value: overview.equipment.broken, tone: 'rose' },
              ]}
              emptyText="투입 장비가 없습니다"
            />
            <SuppliersCard suppliers={overview.suppliers} />
            <DailyInspectionCard
              done={overview.daily_inspection.done_today}
              target={overview.daily_inspection.equipment_target}
              legalDone={overview.daily_inspection.legal_done}
              legalTarget={overview.daily_inspection.legal_target}
            />
          </div>

          {/* 안전알림 + 만료 리스크 */}
          <div className="grid gap-3 lg:grid-cols-2">
            <AlertsCard alerts={overview.recent_alerts} total={overview.unresolved_alert_count} />
            <ExpiringCard docs={overview.expiring_docs} total={overview.expiring_d30_count} />
          </div>
        </div>
      )}
    </AppShell>
  );
}

function StatCard({ label, value, unit, sub, tone = 'brand' }: {
  label: string; value: number; unit?: string; sub?: string; tone?: 'brand' | 'emerald' | 'rose' | 'amber' | 'slate';
}) {
  const valueColor: Record<string, string> = {
    brand: 'text-brand-700', emerald: 'text-emerald-600', rose: 'text-rose-600', amber: 'text-amber-600', slate: 'text-slate-800',
  };
  return (
    <div className="card">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold tabular-nums ${valueColor[tone]}`}>
        {value}<span className="ml-1 text-sm font-medium text-slate-400">{unit}</span>
      </p>
      {sub && <p className="mt-1 text-[11px] text-slate-500">{sub}</p>}
    </div>
  );
}

function SuppliersCard({ suppliers }: { suppliers: SupplierItem[] }) {
  return (
    <section className="card">
      <h2 className="mb-3 text-sm font-bold text-slate-900">참여 공급사</h2>
      {suppliers.length === 0 ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">참여 공급사가 없습니다</div>
      ) : (
        <ul className="space-y-2">
          {suppliers.map((s) => (
            <li key={s.company_id} className="flex items-center justify-between gap-2">
              <span className="truncate text-sm text-slate-700">{s.name ?? `#${s.company_id}`}</span>
              <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500">{s.type}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function DailyInspectionCard({ done, target, legalDone, legalTarget }: { done: number; target: number; legalDone: number; legalTarget: number }) {
  const pct = target > 0 ? Math.round((done / target) * 100) : 0;
  const legalPct = legalTarget > 0 ? Math.round((legalDone / legalTarget) * 100) : 0;
  return (
    <section className="card">
      <h2 className="mb-3 text-sm font-bold text-slate-900">일일점검 <span className="text-xs font-medium text-slate-400">(2트랙)</span></h2>
      {target === 0 ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">점검 대상 장비가 없습니다</div>
      ) : (
        <div className="space-y-3">
          <div>
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-slate-600">조종원 일일점검</span>
              <span className="tabular-nums font-bold text-slate-800">{done} / {target}대</span>
            </div>
            <div className="mt-1 h-2 overflow-hidden rounded bg-slate-100">
              <div className={`h-full rounded ${pct >= 100 ? 'bg-emerald-500' : 'bg-brand-500'}`} style={{ width: `${pct}%` }} />
            </div>
          </div>
          <div>
            <div className="flex items-center justify-between text-xs">
              <span className="font-medium text-slate-600">법정점검 (안전점검원 NFC)</span>
              <span className="tabular-nums font-bold text-slate-800">{legalDone} / {legalTarget}대</span>
            </div>
            <div className="mt-1 h-2 overflow-hidden rounded bg-slate-100">
              <div className={`h-full rounded ${legalPct >= 100 ? 'bg-emerald-500' : 'bg-indigo-500'}`} style={{ width: `${legalPct}%` }} />
            </div>
          </div>
        </div>
      )}
    </section>
  );
}

const ALERT_TONE: Record<string, string> = {
  EMERGENCY: 'bg-rose-100 text-rose-700',
  DANGER: 'bg-rose-100 text-rose-700',
  WARNING: 'bg-amber-100 text-amber-700',
  INFO: 'bg-slate-100 text-slate-600',
};

/** S5' 확인응답 표시 — 확인/미확인/미확인!(에스컬레이션). ack 대상 아니면 표시 안 함. */
function AckIndicator({ a }: { a: AlertItem }) {
  const st = ackState(a);
  if (st === 'acknowledged') return <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold text-emerald-700">확인</span>;
  if (st === 'escalated') return <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-bold text-rose-700">미확인!</span>;
  if (st === 'pending') return <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-bold text-amber-700">미확인</span>;
  return null;
}

function AlertsCard({ alerts, total }: { alerts: AlertItem[]; total: number }) {
  return (
    <section className="card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-slate-900">미해결 안전알림</h2>
        {total > 0 && <span className="rounded-full bg-rose-600 px-2 py-0.5 text-xs font-bold text-white">{total}</span>}
      </div>
      {alerts.length === 0 ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">미해결 알림이 없습니다</div>
      ) : (
        <ul className="space-y-2">
          {alerts.map((a) => (
            <li key={a.id} className="flex items-start gap-2">
              <span className={`mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[10px] font-bold ${ALERT_TONE[a.level] ?? ALERT_TONE.INFO}`}>{a.level}</span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm text-slate-700">
                  <span className="font-semibold">{a.kind}</span>
                  {a.person_name ? ` · ${a.person_name}` : ''}
                </p>
                {a.message && <p className="truncate text-xs text-slate-500">{a.message}</p>}
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1">
                <span className="text-[11px] tabular-nums text-slate-400">{a.created_at?.slice(11, 16)}</span>
                <AckIndicator a={a} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function ExpiringCard({ docs, total }: { docs: ExpiringItem[]; total: number }) {
  return (
    <section className="card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-bold text-slate-900">만료 임박 서류 (D-30)</h2>
        {total > 0 && <span className="rounded-full bg-amber-500 px-2 py-0.5 text-xs font-bold text-white">{total}</span>}
      </div>
      {docs.length === 0 ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">만료 임박 서류가 없습니다</div>
      ) : (
        <ul className="space-y-2">
          {docs.map((d, i) => (
            <li key={i} className="flex items-center justify-between gap-2">
              <span className="truncate text-sm text-slate-700">
                {d.owner_label}
                <span className="ml-1 text-[11px] text-slate-400">{d.owner_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
              </span>
              <span className="shrink-0 text-xs tabular-nums">
                <span className="text-slate-500">{d.expiry_date}</span>
                <span className={`ml-2 font-bold ${d.d_day <= 7 ? 'text-rose-600' : 'text-amber-600'}`}>
                  {d.d_day < 0 ? '만료' : `D-${d.d_day}`}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
