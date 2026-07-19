import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { PageHeader } from '../../components/ui';
import MiniBarChart, { type BarDatum } from '../dashboard/MiniBarChart';
import { useAuth } from '../auth/AuthContext';
import {
  defaultPeriod,
  formatElapsed,
  SEVERITY_CHIP,
  SEVERITY_LABEL,
  timeOnly,
  type EmergencyResponseSummary,
  type EmergencyTimeline,
  type SafetyReport,
  type TimelineAlert,
} from '../../types/safetyReport';

type SiteOpt = { id: number; name: string };

/**
 * P3d 안전관리 이행 보고서 — 현장·기간 선택 → 증거사슬 요약/타임라인/미이행/현행기준.
 * ADMIN·BP·CLIENT 공용(현장 목록 원천만 역할별로 다름). [인쇄]는 별도 인쇄뷰(새 탭).
 */
export default function SafetyReportPage() {
  const { user } = useAuth();
  const isClient = user?.role === 'CLIENT';

  const [sites, setSites] = useState<SiteOpt[]>([]);
  const [siteId, setSiteId] = useState<number | null>(null);
  const [period] = useState(defaultPeriod(30));
  const [from, setFrom] = useState(period.from);
  const [to, setTo] = useState(period.to);
  const [report, setReport] = useState<SafetyReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const url = isClient ? '/api/client/sites' : '/api/sites';
    api.get<Array<{ id?: number; site_id?: number; name: string }>>(url)
      .then((r) => {
        const opts = r.data.map((s) => ({ id: (s.id ?? s.site_id) as number, name: s.name }));
        setSites(opts);
        if (opts.length > 0) setSiteId(opts[0].id);
      })
      .catch(() => setSites([]));
  }, [isClient]);

  useEffect(() => {
    if (siteId == null) return;
    setLoading(true);
    setError(null);
    api.get<SafetyReport>('/api/safety-reports', { params: { siteId, from, to } })
      .then((r) => setReport(r.data))
      .catch((e) => setError(e instanceof AxiosError ? (e.response?.data?.message ?? '불러오기 실패') : '불러오기 실패'))
      .finally(() => setLoading(false));
  }, [siteId, from, to]);

  function openPrint() {
    if (siteId == null) return;
    window.open(`/safety-reports/print?siteId=${siteId}&from=${from}&to=${to}`, '_blank');
  }

  return (
    <AppShell breadcrumb={[{ label: '안전' }, { label: '이행 보고서' }]}>
      <PageHeader
        title="안전관리 이행 보고서"
        subtitle="현장·기간의 고지·확인·점검·서명 이력을 일괄 집계합니다. 사고·감사 시 증빙으로 제출하세요."
        actions={
          <button type="button" onClick={openPrint} disabled={!report} className="btn-primary disabled:opacity-50">
            인쇄 / PDF
          </button>
        }
      />

      {/* 조회 조건 */}
      <div className="card mb-5 flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm text-slate-600">
          현장
          <select value={siteId ?? ''} onChange={(e) => setSiteId(Number(e.target.value))} className="input bg-white max-w-xs">
            {sites.length === 0 && <option value="">현장 없음</option>}
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          기간
          <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} className="input bg-white" />
        </label>
        <span className="text-slate-400">~</span>
        <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} className="input bg-white" />
      </div>

      {error && <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</div>}
      {loading && !report && <div className="card py-12 text-center text-sm text-slate-400">불러오는 중...</div>}
      {!error && sites.length === 0 && !loading && (
        <div className="card py-12 text-center text-sm text-slate-400">조회할 현장이 없습니다.</div>
      )}

      {report && <ReportBody report={report} />}
    </AppShell>
  );
}

function ReportBody({ report }: { report: SafetyReport }) {
  const a = report.alert_summary;
  const ins = report.inspection_summary;
  const w = report.work_compliance_summary;

  const ackRateBars: BarDatum[] = useMemo(() =>
    report.timeline
      .filter((d) => d.alerts.some((x) => x.needs_ack))
      .map((d) => {
        const need = d.alerts.filter((x) => x.needs_ack).length;
        const ack = d.alerts.filter((x) => x.needs_ack && x.acknowledged_at).length;
        const rate = need > 0 ? Math.round((ack / need) * 100) : 0;
        return { label: d.date.slice(5), value: rate, tone: rate >= 100 ? 'emerald' : 'amber' } as BarDatum;
      }), [report.timeline]);

  const alertCountBars: BarDatum[] = useMemo(() =>
    report.timeline
      .filter((d) => d.alerts.length > 0)
      .map((d) => ({ label: d.date.slice(5), value: d.alerts.length, tone: 'brand' } as BarDatum)), [report.timeline]);

  return (
    <div className="space-y-6">
      {/* ① 요약 */}
      <section>
        <h2 className="mb-2 text-sm font-bold text-slate-800">① 요약</h2>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
          <StatCard label="안전알림" value={a.total} unit="건" sub={`확인대상 ${a.ack_needed} · 에스컬 ${a.escalated_count}`} tone={a.escalated_count > 0 ? 'rose' : 'brand'} />
          <StatCard label="알림 확인율" value={a.ack_rate_pct ?? '-'} unit={a.ack_rate_pct == null ? '' : '%'} sub={`평균 확인 ${a.avg_ack_minutes == null ? '-' : `${a.avg_ack_minutes}분`}`} tone={ackTone(a.ack_rate_pct)} />
          <StatCard label="법정점검" value={ins.legal_days} unit="일" sub={`총 ${ins.legal_total}건 · NFC ${ins.nfc_rate_pct == null ? '-' : `${ins.nfc_rate_pct}%`}`} tone="emerald" />
          <StatCard label="조종원 점검" value={ins.operator_days} unit="일" sub={`총 ${ins.operator_total}건 · 대상 ${ins.target_equipment}대`} tone="emerald" />
          <StatCard label="계획서 서명완결" value={w.plan_fully_signed} unit={`/ ${w.plan_total}건`} sub="5인 전원 서명" tone={w.plan_total > 0 && w.plan_fully_signed < w.plan_total ? 'amber' : 'emerald'} />
          <StatCard label="확인서 서명율" value={w.log_sign_rate_pct ?? '-'} unit={w.log_sign_rate_pct == null ? '' : '%'} sub={`서명 ${w.log_signed} / ${w.log_total}건`} tone={ackTone(w.log_sign_rate_pct)} />
        </div>
      </section>

      {/* 차트 */}
      <div className="grid gap-3 lg:grid-cols-2">
        <MiniBarChart title="일자별 안전알림 건수" data={alertCountBars} emptyText="기간 내 안전알림이 없습니다" />
        <MiniBarChart title="일자별 알림 확인율(%)" data={ackRateBars} emptyText="확인 대상 알림이 없습니다" />
      </div>

      {/* 긴급 대응 이력 (개인 응급 골든타임) */}
      {report.emergency_response.total > 0 && <EmergencyResponseSection er={report.emergency_response} />}

      {/* ② 타임라인 */}
      <section>
        <h2 className="mb-2 text-sm font-bold text-slate-800">② 일자별 타임라인 <span className="font-normal text-slate-400">(발송 → 확인 사슬)</span></h2>
        {report.timeline.length === 0 ? (
          <div className="card py-8 text-center text-sm text-slate-400">기간 내 안전 활동 기록이 없습니다.</div>
        ) : (
          <div className="space-y-2">
            {report.timeline.map((d) => (
              <div key={d.date} className="card">
                <div className="mb-2 flex flex-wrap items-center gap-2 border-b border-slate-100 pb-2">
                  <span className="text-sm font-bold text-slate-800">{d.date}</span>
                  {d.legal_inspections > 0 && <Tag tone="emerald">법정점검 {d.legal_inspections}</Tag>}
                  {d.operator_inspections > 0 && <Tag tone="emerald">조종원점검 {d.operator_inspections}</Tag>}
                  {d.wind_event && (
                    <Tag tone="rose">
                      강풍{d.wind_event.wind_mps != null ? ` ${d.wind_event.wind_mps}m/s` : ''}
                      {d.wind_event.entered_at ? ` 진입 ${timeOnly(d.wind_event.entered_at)}` : ''}
                      {d.wind_event.cleared_at ? ` / 해제 ${timeOnly(d.wind_event.cleared_at)}` : ''}
                    </Tag>
                  )}
                </div>
                {d.alerts.length === 0 ? (
                  <p className="text-xs text-slate-400">점검·이벤트 기록(알림 없음)</p>
                ) : (
                  <ul className="space-y-1.5">
                    {d.alerts.map((al) => <AlertRow key={al.id} a={al} />)}
                  </ul>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* ③ 미이행 */}
      <section>
        <h2 className="mb-2 text-sm font-bold text-slate-800">③ 미이행 목록</h2>
        <div className="grid gap-3 lg:grid-cols-2">
          <ListCard title="미확인 안전알림" count={report.noncompliance.unacknowledged_alerts.length} tone="rose"
            empty="미확인 알림이 없습니다">
            {report.noncompliance.unacknowledged_alerts.map((al) => (
              <li key={al.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate text-slate-700">{al.kind_label}</span>
                <span className="shrink-0 text-xs tabular-nums text-slate-400">
                  {al.created_at.slice(5, 16).replace('T', ' ')}
                  {al.escalated_at && <span className="ml-1 font-bold text-rose-600">에스컬</span>}
                </span>
              </li>
            ))}
          </ListCard>

          <ListCard title="점검 없는 작업 예정일" count={report.noncompliance.uninspected_work_days.length} tone="amber"
            empty="작업 예정일 점검 누락이 없습니다">
            {report.noncompliance.uninspected_work_days.map((d) => (
              <li key={d} className="text-sm text-slate-700">{d} <span className="text-xs text-slate-400">— 법정·조종원 점검 기록 없음</span></li>
            ))}
          </ListCard>

          <ListCard title="서명 미완결 작업계획서" count={report.noncompliance.unsigned_plans.length} tone="amber"
            empty="서명 미완결 계획서가 없습니다">
            {report.noncompliance.unsigned_plans.map((p) => (
              <li key={p.work_plan_id} className="text-sm">
                <span className="text-slate-700">{p.work_date} · {p.title ?? `계획서 #${p.work_plan_id}`}</span>
                <div className="text-xs text-amber-700">미서명: {p.pending_roles.join(', ')}</div>
              </li>
            ))}
          </ListCard>

          <ListCard title="미서명 일일 확인서" count={report.noncompliance.unsigned_logs.length} tone="amber"
            empty="미서명 확인서가 없습니다">
            {report.noncompliance.unsigned_logs.map((l) => (
              <li key={l.id} className="flex items-center justify-between gap-2 text-sm">
                <span className="truncate text-slate-700">{l.label}</span>
                <span className="shrink-0 text-xs tabular-nums text-slate-400">{l.work_date}</span>
              </li>
            ))}
          </ListCard>
        </div>
      </section>

      {/* ④ 현행 안전 기준 */}
      <section>
        <h2 className="mb-2 text-sm font-bold text-slate-800">④ 현행 안전 기준
          <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-medium text-slate-500">
            {report.standard.configured ? '현장 설정' : '법정 기본값'}
          </span>
        </h2>
        <StandardTable s={report.standard} />
      </section>

      <p className="text-[11px] text-slate-400">
        발행 {report.generated_at.slice(0, 16).replace('T', ' ')} · 발행자 {report.generated_by ?? '-'} ·
        서명 이미지는 유무·건수만 표기(개인정보 최소화).
      </p>
    </div>
  );
}

function EmergencyResponseSection({ er }: { er: EmergencyResponseSummary }) {
  return (
    <section>
      <h2 className="mb-2 text-sm font-bold text-slate-800">
        긴급 대응 이력 <span className="font-normal text-slate-400">(개인 응급 SOS·낙상·BLE 중계 · 골든타임)</span>
      </h2>
      <div className="mb-3 grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="긴급 발생" value={er.total} unit="건" sub={`대응체인 발동 ${er.chain_activated}`} tone="rose" />
        <StatCard label="동료 응답" value={er.responded} unit="건" sub={`60초 무응답 확대 ${er.escalated_count}`} tone={er.escalated_count > 0 ? 'amber' : 'emerald'} />
        <StatCard label="평균 최초응답" value={er.avg_first_response_seconds == null ? '-' : formatElapsed(Math.round(er.avg_first_response_seconds))} sub="통보 → 응답" tone="brand" />
        <StatCard label="BLE 대리중계" value={er.relayed_count} unit="건" sub="통신음영 보완" tone="slate" />
      </div>
      <div className="space-y-2">
        {er.timelines.map((t) => <EmergencyRow key={t.alert_id} t={t} />)}
      </div>
    </section>
  );
}

function EmergencyRow({ t }: { t: EmergencyTimeline }) {
  const steps: string[] = [`감지 ${timeOnly(t.detected_at)}`];
  if (t.peer_notified_at) steps.push(`통보 ${timeOnly(t.peer_notified_at)}`);
  if (t.first_response_at) {
    steps.push(`응답 ${timeOnly(t.first_response_at)}${t.response_elapsed_seconds != null ? ` (${formatElapsed(t.response_elapsed_seconds)})` : ''}`);
  }
  if (t.resolved_at) steps.push(`해제 ${timeOnly(t.resolved_at)}`);
  return (
    <div className="card flex flex-wrap items-center gap-2 text-sm">
      <span className="font-semibold text-slate-700">{t.kind_label}</span>
      {t.relayed && <Tag tone="amber">BLE중계</Tag>}
      {t.escalated && <Tag tone="rose">현장확대</Tag>}
      {t.responder_count > 0 && (
        <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold text-emerald-700">{t.responder_count}명 응답</span>
      )}
      {t.peer_notified_at && !t.first_response_at && !t.resolved_at && (
        <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-bold text-rose-700">응답 없음</span>
      )}
      <span className="text-xs text-slate-500">{steps.join(' → ')}</span>
    </div>
  );
}

function AlertRow({ a }: { a: TimelineAlert }) {
  const sev = a.severity ?? 'NORMAL';
  const chain = a.needs_ack
    ? (a.acknowledged_at
        ? `발송 ${timeOnly(a.created_at)} → 확인 ${timeOnly(a.acknowledged_at)} (${formatElapsed(a.ack_elapsed_seconds)})`
        : `발송 ${timeOnly(a.created_at)} → 미확인`)
    : `발송 ${timeOnly(a.created_at)}`;
  return (
    <li className="flex flex-wrap items-center gap-2 text-sm">
      <span className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${SEVERITY_CHIP[sev] ?? SEVERITY_CHIP.NORMAL}`}>{SEVERITY_LABEL[sev] ?? sev}</span>
      <span className="font-semibold text-slate-700">{a.kind_label}</span>
      <span className="text-xs text-slate-500">{chain}</span>
      {a.needs_ack && !a.acknowledged_at && <span className="rounded bg-rose-100 px-1.5 py-0.5 text-[10px] font-bold text-rose-700">미확인{a.escalated_at ? '!' : ''}</span>}
      {a.resolved && <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold text-emerald-700">조치완료</span>}
    </li>
  );
}

function StandardTable({ s }: { s: SafetyReport['standard'] }) {
  const rows: Array<{ label: string; val: string; legal: string }> = [
    { label: '주의 임계', val: `${s.temp_caution}℃`, legal: `법정 ${s.legal_temp_caution}℃` },
    { label: '휴식(경고) 임계', val: `${s.temp_warning}℃`, legal: `법정 ${s.legal_temp_warning}℃` },
    { label: '위험 임계', val: `${s.temp_danger}℃`, legal: `법정 ${s.legal_temp_danger}℃` },
    { label: '작업중지 임계', val: `${s.temp_extreme}℃`, legal: `법정 ${s.legal_temp_extreme}℃` },
    { label: '휴식 간격', val: `${s.rest_interval_min}분`, legal: `법정 ${s.legal_rest_interval}분 이하` },
    { label: '휴식 시간', val: `${s.rest_duration_min}분`, legal: `법정 ${s.legal_rest_duration}분 이상` },
    { label: '무더위 시간대', val: `${s.midday_start_hour}~${s.midday_end_hour}시`, legal: '옥외작업 중지 권고' },
    { label: '강풍 작업중지', val: `${s.wind_stop_mps}m/s`, legal: `법정 ${s.legal_wind_stop}m/s 이하` },
    { label: '일일점검 게이트', val: s.enforce_daily_inspection_gate ? '차단' : '경고', legal: 'S3' },
    { label: '정비 알림 주기', val: s.maintenance_interval_hours == null ? '비활성' : `${s.maintenance_interval_hours}h`, legal: 'S4′' },
  ];
  return (
    <div className="card overflow-x-auto">
      <table className="w-full text-sm">
        <tbody>
          {rows.map((r) => (
            <tr key={r.label} className="border-b border-slate-100 last:border-0">
              <td className="py-1.5 pr-4 text-slate-500">{r.label}</td>
              <td className="py-1.5 pr-4 font-semibold text-slate-800 tabular-nums">{r.val}</td>
              <td className="py-1.5 text-xs text-slate-400">{r.legal}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function StatCard({ label, value, unit, sub, tone = 'brand' }: {
  label: string; value: number | string; unit?: string; sub?: string; tone?: 'brand' | 'emerald' | 'rose' | 'amber' | 'slate';
}) {
  const color: Record<string, string> = {
    brand: 'text-brand-700', emerald: 'text-emerald-600', rose: 'text-rose-600', amber: 'text-amber-600', slate: 'text-slate-800',
  };
  return (
    <div className="card">
      <p className="text-xs font-semibold text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-bold tabular-nums ${color[tone]}`}>
        {value}{unit && <span className="ml-1 text-sm font-medium text-slate-400">{unit}</span>}
      </p>
      {sub && <p className="mt-1 text-[11px] text-slate-500">{sub}</p>}
    </div>
  );
}

function Tag({ children, tone }: { children: ReactNode; tone: 'emerald' | 'rose' | 'amber' }) {
  const cls: Record<string, string> = {
    emerald: 'bg-emerald-100 text-emerald-700',
    rose: 'bg-rose-100 text-rose-700',
    amber: 'bg-amber-100 text-amber-700',
  };
  return <span className={`rounded px-2 py-0.5 text-[11px] font-semibold ${cls[tone]}`}>{children}</span>;
}

function ListCard({ title, count, tone, empty, children }: {
  title: string; count: number; tone: 'rose' | 'amber'; empty: string; children: ReactNode;
}) {
  const badge = tone === 'rose' ? 'bg-rose-600' : 'bg-amber-500';
  return (
    <section className="card">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-900">{title}</h3>
        {count > 0 && <span className={`rounded-full px-2 py-0.5 text-xs font-bold text-white ${badge}`}>{count}</span>}
      </div>
      {count === 0 ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">{empty}</div>
      ) : (
        <ul className="space-y-1.5">{children}</ul>
      )}
    </section>
  );
}

function ackTone(pct: number | null): 'emerald' | 'amber' | 'rose' | 'slate' {
  if (pct == null) return 'slate';
  if (pct >= 100) return 'emerald';
  if (pct >= 80) return 'amber';
  return 'rose';
}
