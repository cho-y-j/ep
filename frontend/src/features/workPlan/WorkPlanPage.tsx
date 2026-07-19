import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import {
  WORK_PLAN_STATUS_LABEL,
  type WorkPlanPage as WorkPlanPageType,
  type WorkPlanStatus,
} from '../../types/workPlan';

type SortKey = 'id_desc' | 'id_asc' | 'date_desc' | 'date_asc';

/** /work-plans — DRAFT (작성 중) 작업계획서 목록. + 새 작성 버튼만. */
export default function WorkPlanPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [data, setData] = useState<WorkPlanPageType | null>(null);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [siteFilter, setSiteFilter] = useState('');
  const [sort, setSort] = useState<SortKey>('id_desc');

  const canCreate = user?.role === 'ADMIN' || user?.role === 'BP';

  useEffect(() => {
    let cancelled = false;
    api.get<WorkPlanPageType>('/api/work-plans', { params: { page: 0, size: 100 } })
      .then((r) => { if (!cancelled) setData(r.data); })
      .catch(() => { if (!cancelled) setData(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const allDrafts = useMemo(
    () => (data?.content ?? []).filter((wp) => wp.status === 'DRAFT'),
    [data],
  );
  const siteOptions = useMemo(() => {
    const m = new Map<string, string>();
    allDrafts.forEach((wp) => { if (wp.site_name) m.set(wp.site_name, wp.site_name); });
    return [...m.values()].map((v) => ({ value: v, label: v }));
  }, [allDrafts]);

  const qLower = q.trim().toLowerCase();
  const drafts = useMemo(() => {
    const rows = allDrafts.filter((wp) => {
      if (siteFilter && (wp.site_name ?? '') !== siteFilter) return false;
      if (qLower) {
        const hay = `${wp.title} ${wp.site_name ?? ''} ${wp.bp_company_name ?? ''}`.toLowerCase();
        if (!hay.includes(qLower)) return false;
      }
      return true;
    });
    return [...rows].sort((a, b) => {
      switch (sort) {
        case 'id_asc': return a.id - b.id;
        case 'date_desc': return (b.work_date ?? '').localeCompare(a.work_date ?? '');
        case 'date_asc': return (a.work_date ?? '').localeCompare(b.work_date ?? '');
        default: return b.id - a.id;
      }
    });
  }, [allDrafts, siteFilter, qLower, sort]);

  const activeFilterCount = [q, siteFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setSiteFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '작업 계획서' }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <PageHeader
          title="작업 계획서"
          subtitle="3-step 으로 새로 작성하거나 작성 중인 계획서를 이어서 편집합니다. 사인 5건 + 점검 회신까지 마치고 제출하세요."
          actions={canCreate ? (
            <button type="button"
                    onClick={() => navigate('/work-plans/new')}
                    className="btn-primary text-base px-5 py-2.5">
              + 새 작업계획서 (3-Step)
            </button>
          ) : undefined}
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '제목·현장·BP사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
          sort={
            <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)}
                    className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
              <option value="id_desc">최신 등록순</option>
              <option value="id_asc">오래된 등록순</option>
              <option value="date_desc">작업일 늦은순</option>
              <option value="date_asc">작업일 빠른순</option>
            </select>
          }
        >
          <FilterSelect value={siteFilter} onChange={setSiteFilter} placeholder="현장 전체" options={siteOptions} />
        </FilterBar>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : allDrafts.length === 0 ? (
          <div className="card p-10 text-center">
            <p className="text-sm text-slate-500 mb-3">작성 중인 작업계획서가 없습니다.</p>
            {canCreate && (
              <button type="button" onClick={() => navigate('/work-plans/new')} className="btn-primary">
                + 새 작업계획서 만들기
              </button>
            )}
          </div>
        ) : drafts.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 계획서가 없습니다.</div>
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
