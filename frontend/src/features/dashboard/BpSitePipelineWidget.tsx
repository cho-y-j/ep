import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';

type SitePipeline = {
  site_id: number;
  site_name: string;
  status: string;
  resource_checks: { pending: number; approved: number };
  safety_inspections: { scheduled: number; completed: number };
  field_deployments: { requested: number; active: number };
  pending_signatures: number;
  documents: { progress_pct: number; ready: boolean };
};

/** B3: BP 현장별 파이프라인 위젯 — GET /api/dashboards/bp/site-pipeline.
 *  현장 카드마다 점검/검사/투입/서명대기/서류% 카운트를 가로로 배치, 각 단계 클릭 시 해당 화면으로 딥링크.
 *  신규 엔드포인트 미배포(404)·현장 없음이면 위젯 자체를 숨긴다 (ReadinessWidget 패턴). */
export default function BpSitePipelineWidget() {
  const [sites, setSites] = useState<SitePipeline[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.get<{ sites: SitePipeline[] }>('/api/dashboards/bp/site-pipeline')
      .then((r) => { if (!cancelled) setSites(r.data.sites ?? []); })
      .catch(() => { if (!cancelled) setSites(null); })
      .finally(() => {});
    return () => { cancelled = true; };
  }, []);

  if (!sites || sites.length === 0) return null;

  return (
    <section className="card p-4">
      <h2 className="font-bold text-slate-900 mb-3">현장별 파이프라인</h2>
      <div className="space-y-2.5">
        {sites.map((s) => {
          const stages = [
            { label: '점검', main: s.resource_checks.pending, sub: `승인 ${s.resource_checks.approved}`, to: '/resource-checks/bp' },
            { label: '검사', main: s.safety_inspections.scheduled, sub: `완료 ${s.safety_inspections.completed}`, to: '/safety-inspections' },
            { label: '투입', main: s.field_deployments.requested, sub: `활성 ${s.field_deployments.active}`, to: '/field-deployments/bp' },
            { label: '서명대기', main: s.pending_signatures, sub: undefined as string | undefined, to: '/work-plans' },
            { label: '서류', main: `${s.documents.progress_pct}%`, sub: s.documents.ready ? '준비완료' : undefined, to: '/document-management' },
          ];
          return (
            <div key={s.site_id} className="rounded-lg border border-slate-200 p-3">
              <div className="flex items-center gap-2 mb-2">
                <Link to={`/sites/${s.site_id}`} className="font-semibold text-slate-900 hover:text-brand-700 truncate">
                  {s.site_name}
                </Link>
                <span className={`inline-flex px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                  s.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700'
                    : s.status === 'PAUSED' ? 'bg-amber-100 text-amber-700'
                      : 'bg-slate-100 text-slate-600'
                }`}>
                  {s.status === 'ACTIVE' ? '진행중' : s.status === 'PAUSED' ? '일시중지' : s.status === 'COMPLETED' ? '완료' : '보관'}
                </span>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
                {stages.map((st) => (
                  <Link
                    key={st.label}
                    to={st.to}
                    className="rounded-lg border border-slate-200 bg-white px-2.5 py-2 hover:border-brand-300 hover:bg-brand-50/40 transition"
                  >
                    <div className="text-[11px] text-slate-500">{st.label}</div>
                    <div className="text-base font-bold text-slate-900 tabular-nums">{st.main}</div>
                    {st.sub && <div className="text-[10px] text-slate-400">{st.sub}</div>}
                  </Link>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
