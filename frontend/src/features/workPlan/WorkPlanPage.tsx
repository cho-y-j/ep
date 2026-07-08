import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import {
  WORK_PLAN_STATUS_LABEL,
  type WorkPlanPage as WorkPlanPageType,
  type WorkPlanStatus,
} from '../../types/workPlan';

/** /work-plans — DRAFT (작성 중) 작업계획서 목록. + 새 작성 버튼만. */
export default function WorkPlanPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [data, setData] = useState<WorkPlanPageType | null>(null);
  const [loading, setLoading] = useState(true);

  const canCreate = user?.role === 'ADMIN' || user?.role === 'BP';

  useEffect(() => {
    let cancelled = false;
    api.get<WorkPlanPageType>('/api/work-plans', { params: { page: 0, size: 100 } })
      .then((r) => { if (!cancelled) setData(r.data); })
      .catch(() => { if (!cancelled) setData(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const drafts = (data?.content ?? [])
    .filter((wp) => wp.status === 'DRAFT')
    .sort((a, b) => b.id - a.id);

  return (
    <AppShell breadcrumb={[{ label: '작업 계획서' }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold text-slate-950">작업 계획서</h1>
            <p className="mt-1 text-sm text-slate-500">
              3-step 으로 새로 작성하거나 작성 중인 계획서를 이어서 편집합니다. 사인 5건 + 점검 회신까지 마치고 제출하세요.
            </p>
          </div>
          {canCreate && (
            <button type="button"
                    onClick={() => navigate('/work-plans/new')}
                    className="btn-primary text-base px-5 py-2.5">
              + 새 작업계획서 (3-Step)
            </button>
          )}
        </div>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : drafts.length === 0 ? (
          <div className="card p-10 text-center">
            <p className="text-sm text-slate-500 mb-3">작성 중인 작업계획서가 없습니다.</p>
            {canCreate && (
              <button type="button" onClick={() => navigate('/work-plans/new')} className="btn-primary">
                + 새 작업계획서 만들기
              </button>
            )}
          </div>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-semibold">작업일</th>
                  <th className="px-4 py-3 font-semibold">제목</th>
                  <th className="px-4 py-3 font-semibold">현장</th>
                  <th className="px-4 py-3 font-semibold">BP사</th>
                  <th className="px-4 py-3 font-semibold">상태</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {drafts.map((wp) => (
                  <tr key={wp.id} onClick={() => navigate(`/work-plans/${wp.id}`)}
                      className="cursor-pointer hover:bg-slate-50">
                    <td className="px-4 py-3 text-slate-900 font-semibold tabular-nums">{wp.work_date}</td>
                    <td className="px-4 py-3 text-slate-900">{wp.title}</td>
                    <td className="px-4 py-3 text-slate-700">{wp.site_name ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-500">{wp.bp_company_name ?? '-'}</td>
                    <td className="px-4 py-3"><WorkPlanStatusBadge status={wp.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </AppShell>
  );
}

export function WorkPlanStatusBadge({ status }: { status: WorkPlanStatus }) {
  const tone =
    status === 'DRAFT' ? 'bg-slate-100 text-slate-600 ring-slate-200'
    : status === 'SUBMITTED' ? 'bg-amber-50 text-amber-700 ring-amber-200'
    : status === 'APPROVED' || status === 'IN_PROGRESS' ? 'bg-brand-50 text-brand-700 ring-brand-200'
    : status === 'DONE' ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    : 'bg-rose-50 text-rose-700 ring-rose-200';
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${tone}`}>{WORK_PLAN_STATUS_LABEL[status]}</span>;
}
