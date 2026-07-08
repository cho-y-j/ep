import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import type { WorkPlanPage as WorkPlanPageType, WorkPlanResponse } from '../../types/workPlan';
import type { ResourceCheckResponse } from '../../types/resourceCheck';
import { WorkPlanStatusBadge } from './WorkPlanPage';

type Counts = { total: number; approved: number; open: number };

/** /work-plans/pending — 사인 완료 후 제출된(SUBMITTED) 계획서 + 점검 요청 진행 상태. 등록순. */
export default function WorkPlanPendingPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<WorkPlanResponse[]>([]);
  const [checkCounts, setCheckCounts] = useState<Record<number, Counts>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<WorkPlanPageType>('/api/work-plans', { params: { page: 0, size: 200 } }).then((r) => r.data),
      api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list').then((r) => r.data).catch(() => []),
    ]).then(([wpPage, checks]) => {
      if (cancelled) return;
      const pending = wpPage.content
        .filter((wp) => wp.status === 'SUBMITTED')
        .sort((a, b) => b.id - a.id);
      setItems(pending);

      const counts: Record<number, Counts> = {};
      checks.forEach((c) => {
        if (!c.work_plan_id) return;
        const cur = counts[c.work_plan_id] ?? { total: 0, approved: 0, open: 0 };
        cur.total += 1;
        if (c.status === 'APPROVED') cur.approved += 1;
        else if (c.status === 'REQUESTED' || c.status === 'SUBMITTED' || c.status === 'REJECTED') cur.open += 1;
        counts[c.work_plan_id] = cur;
      });
      setCheckCounts(counts);
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  return (
    <AppShell breadcrumb={[{ label: '투입 대기' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">투입 대기</h1>
          <p className="mt-1 text-sm text-slate-500">
            사인 5건 완료 + 제출된 계획서. 자동차 안전점검·건강검진·안전교육 회신을 모두 받으면 현장 투입 단계로 넘어갑니다.
          </p>
        </header>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : items.length === 0 ? (
          <div className="card p-10 text-center text-sm text-slate-400">
            대기 중인 계획서가 없습니다. 작성·사인·제출이 끝난 계획서가 여기로 옵니다.
          </div>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">#</th>
                  <th className="px-4 py-3 font-semibold">작업일</th>
                  <th className="px-4 py-3 font-semibold">제목</th>
                  <th className="px-4 py-3 font-semibold">현장</th>
                  <th className="px-4 py-3 font-semibold">점검 진행</th>
                  <th className="px-4 py-3 font-semibold">제출 시점</th>
                  <th className="px-4 py-3 font-semibold">상태</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((wp) => {
                  const c = checkCounts[wp.id] ?? { total: 0, approved: 0, open: 0 };
                  return (
                    <tr key={wp.id} onClick={() => navigate(`/work-plans/${wp.id}`)}
                        className="cursor-pointer hover:bg-slate-50">
                      <td className="px-4 py-3 text-xs text-slate-400 tabular-nums">#{wp.id}</td>
                      <td className="px-4 py-3 text-slate-900 font-semibold tabular-nums">{wp.work_date}</td>
                      <td className="px-4 py-3 text-slate-900">{wp.title}</td>
                      <td className="px-4 py-3 text-slate-700">{wp.site_name ?? wp.work_location ?? '-'}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {c.total === 0 ? (
                            <span className="text-xs text-slate-400">요청 없음</span>
                          ) : (
                            <>
                              <span className="text-xs font-semibold text-slate-700 tabular-nums">
                                {c.approved}/{c.total}
                              </span>
                              {c.open > 0 && (
                                <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-amber-100 text-amber-800">
                                  대기 {c.open}
                                </span>
                              )}
                              {c.approved === c.total && (
                                <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-100 text-emerald-800">
                                  완료
                                </span>
                              )}
                            </>
                          )}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-500 tabular-nums">
                        {wp.submitted_at ? new Date(wp.submitted_at).toLocaleDateString('ko-KR') : '-'}
                      </td>
                      <td className="px-4 py-3"><WorkPlanStatusBadge status={wp.status} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  );
}
