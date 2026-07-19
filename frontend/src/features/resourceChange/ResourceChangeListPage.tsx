import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { CHANGE_KIND_LABEL, type ResourceChangeRequestResponse } from '../../types/resourceChange';

/** 업체변경 신청서 v0 목록 (L2a). 공급사=본인 발신, BP=본인 앞, ADMIN=전체. */
export default function ResourceChangeListPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ResourceChangeRequestResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  useEffect(() => {
    api.get<ResourceChangeRequestResponse[]>('/api/resource-change-requests')
      .then((r) => setRows(r.data))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, []);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => rows.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (qLower) {
      const before = r.old_label ?? r.old_operator_name ?? '';
      const after = r.new_label ?? r.new_operator_name ?? '';
      const hay = `${r.site_name ?? ''} ${before} ${after} ${r.supplier_name ?? ''} ${r.bp_name ?? ''} ${CHANGE_KIND_LABEL[r.change_kind]}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [rows, statusFilter, qLower]);

  const activeFilterCount = [q, statusFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '업체변경 신청서' }]}>
      <div className="mx-auto max-w-5xl space-y-4">
        <PageHeader
          title="업체변경 신청서"
          subtitle="같은 현장 기검증 자원 교체 신청 (임의양식 v0)"
          actions={
            <button type="button" onClick={() => navigate('/resource-change-requests/new')} className="btn-primary">+ 신규 작성</button>
          }
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '현장·자원·업체 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체"
            options={[{ value: 'DRAFT', label: '작성중' }, { value: 'CONFIRMED', label: '확정' }]} />
        </FilterBar>

        <div className="card overflow-x-auto">
          {loading ? (
            <p className="text-sm text-slate-400">불러오는 중...</p>
          ) : rows.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-400">
              작성된 업체변경 신청서가 없습니다. <button type="button" onClick={() => navigate('/resource-change-requests/new')} className="font-semibold text-blue-600 underline">신규 작성</button>
            </div>
          ) : filtered.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-400">조건에 맞는 신청서가 없습니다.</div>
          ) : (
            <table className="w-full text-sm">
              <thead className="text-left text-xs text-slate-500">
                <tr>
                  <th className="py-2 font-semibold">#</th>
                  <th className="py-2 font-semibold">변경구분</th>
                  <th className="py-2 font-semibold">현장</th>
                  <th className="py-2 font-semibold">변경 전 → 후</th>
                  <th className="py-2 font-semibold">판정</th>
                  <th className="py-2 font-semibold">적용일</th>
                  <th className="py-2 font-semibold">작성일</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((r) => {
                  const before = r.old_label ?? r.old_operator_name ?? '-';
                  const after = r.new_label ?? r.new_operator_name ?? '-';
                  const snap = r.l3_snapshot;
                  return (
                    <tr key={r.id} onClick={() => navigate(`/resource-change-requests/${r.id}`)} className="cursor-pointer hover:bg-slate-50">
                      <td className="py-2.5 font-semibold text-slate-700">#{r.id}</td>
                      <td className="py-2.5">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-semibold text-slate-700">{CHANGE_KIND_LABEL[r.change_kind]}</span>
                      </td>
                      <td className="py-2.5 text-slate-600">{r.site_name ?? '-'}</td>
                      <td className="py-2.5 text-slate-700">{before} <span className="text-slate-300">→</span> <span className="font-semibold">{after}</span></td>
                      <td className="py-2.5">
                        {snap == null ? (
                          <span className="text-xs text-slate-400">-</span>
                        ) : snap.ready ? (
                          <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-bold text-emerald-800">✓ 가능</span>
                        ) : (
                          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-bold text-amber-800">! 부족 {snap.blocks.length}건</span>
                        )}
                      </td>
                      <td className="py-2.5 text-slate-600">{r.apply_date ?? '-'}</td>
                      <td className="py-2.5 text-slate-500">{r.created_at.slice(0, 10)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </AppShell>
  );
}
