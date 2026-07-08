import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import type { WorkPlanPage as WorkPlanPageType, WorkPlanResponse } from '../../types/workPlan';
import { WorkPlanStatusBadge } from './WorkPlanPage';

/** /work-plans/active — 현장에 투입된 작업 (APPROVED/IN_PROGRESS/DONE). 현장별 그룹 + 장비/인원 일람. */
export default function WorkPlanActivePage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<WorkPlanResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<WorkPlanPageType>('/api/work-plans', { params: { page: 0, size: 200 } })
      .then(async (r) => {
        if (cancelled) return;
        const actives = r.data.content.filter((wp) =>
          wp.status === 'APPROVED' || wp.status === 'IN_PROGRESS' || wp.status === 'DONE');
        // list 응답에 equipment/persons 가 안 들어오면 wp/{id} 호출.
        const needsFetch = actives.some((wp) => !wp.equipment && !wp.persons);
        if (!needsFetch) { setItems(actives); return; }
        const details = await Promise.all(
          actives.map((wp) => api.get<WorkPlanResponse>(`/api/work-plans/${wp.id}`)
            .then((d) => d.data).catch(() => wp))
        );
        if (!cancelled) setItems(details);
      })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // site_name 별 그룹화 (null 인 것은 "현장 미지정")
  const groups = items.reduce<Record<string, WorkPlanResponse[]>>((acc, wp) => {
    const key = wp.site_name ?? '현장 미지정';
    (acc[key] = acc[key] ?? []).push(wp);
    return acc;
  }, {});

  return (
    <AppShell breadcrumb={[{ label: '투입 현황' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">투입 현황</h1>
          <p className="mt-1 text-sm text-slate-500">
            현장별로 어떤 장비·인원이 어떻게 투입되고 있는지 한눈에. (승인·진행·완료)
          </p>
        </header>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : items.length === 0 ? (
          <div className="card p-10 text-center text-sm text-slate-400">
            현재 진행 중인 작업이 없습니다.
          </div>
        ) : (
          <div className="space-y-4">
            {Object.entries(groups).map(([siteName, wps]) => (
              <section key={siteName} className="card p-0 overflow-hidden">
                <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
                  <h2 className="font-bold text-slate-900">{siteName}</h2>
                  <span className="text-xs text-slate-500">{wps.length}건</span>
                </div>
                <table className="w-full text-sm">
                  <thead className="text-left text-slate-500 text-xs">
                    <tr className="border-b border-slate-100">
                      <th className="px-4 py-2 font-semibold w-[90px]">#</th>
                      <th className="px-4 py-2 font-semibold w-[110px]">작업일</th>
                      <th className="px-4 py-2 font-semibold">작업</th>
                      <th className="px-4 py-2 font-semibold">장비</th>
                      <th className="px-4 py-2 font-semibold">인원</th>
                      <th className="px-4 py-2 font-semibold w-[110px]">상태</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {wps.map((wp) => (
                      <tr key={wp.id} onClick={() => navigate(`/work-plans/${wp.id}`)}
                          className="cursor-pointer hover:bg-slate-50">
                        <td className="px-4 py-3 text-xs text-slate-400 tabular-nums">#{wp.id}</td>
                        <td className="px-4 py-3 text-slate-900 font-semibold tabular-nums">{wp.work_date}</td>
                        <td className="px-4 py-3 text-slate-900">{wp.title}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {(wp.equipment ?? []).map((e) => (
                              <span key={e.id}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-slate-100 text-slate-700 text-xs">
                                <span className="font-semibold">{e.equipment_name ?? '#' + e.equipment_id}</span>
                                {e.supplier_company_name && (
                                  <span className="text-slate-500">· {e.supplier_company_name}</span>
                                )}
                              </span>
                            ))}
                            {(wp.equipment ?? []).length === 0 && <span className="text-xs text-slate-400">—</span>}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {(wp.persons ?? []).map((p) => (
                              <span key={p.id}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs">
                                <span className="font-semibold">{p.person_name ?? '#' + p.person_id}</span>
                                {p.role && <span className="text-blue-500">· {p.role}</span>}
                              </span>
                            ))}
                            {(wp.persons ?? []).length === 0 && <span className="text-xs text-slate-400">—</span>}
                          </div>
                        </td>
                        <td className="px-4 py-3"><WorkPlanStatusBadge status={wp.status} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </section>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
