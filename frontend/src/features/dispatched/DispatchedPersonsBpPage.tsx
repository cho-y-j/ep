import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { formatWon } from '../../lib/format';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';

type Row = {
  id: number;
  quotation_request_id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  person_id: number;
  person_label?: string | null;
  job_title?: string | null;
  daily_price?: number | null;
  monthly_price?: number | null;
  sent_at?: string | null;
};

export default function DispatchedPersonsBpPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');
  const [supplierFilter, setSupplierFilter] = useState('');
  const [titleFilter, setTitleFilter] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.get<Row[]>('/api/bp-dispatched/persons')
      .then((r) => { if (!cancelled) setRows(r.data); })
      .catch(() => { if (!cancelled) setRows([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const supplierOptions = useMemo(() => {
    const m = new Map<string, string>();
    rows.forEach((r) => m.set(String(r.supplier_company_id), r.supplier_company_name ?? '#' + r.supplier_company_id));
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [rows]);
  const titleOptions = useMemo(() => {
    const s = new Set<string>();
    rows.forEach((r) => { if (r.job_title) s.add(r.job_title); });
    return [...s].map((t) => ({ value: t, label: t }));
  }, [rows]);
  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => rows.filter((r) => {
    if (supplierFilter && String(r.supplier_company_id) !== supplierFilter) return false;
    if (titleFilter && r.job_title !== titleFilter) return false;
    if (qLower) {
      const hay = `${r.person_label ?? ''} ${r.supplier_company_name ?? ''} ${r.job_title ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [rows, supplierFilter, titleFilter, qLower]);
  const activeFilterCount = [q, supplierFilter, titleFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setSupplierFilter(''); setTitleFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '투입 인원' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <PageHeader
          title="투입 인원"
          subtitle="견적 응답으로 받은 인원 — 작업계획서에 투입하거나 투입 중인 인원을 모아봅니다."
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '이름 / 공급사 / 직책 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
          trailing={<span className="text-xs text-slate-500">총 {filtered.length}건</span>}
        >
          <FilterSelect value={supplierFilter} onChange={setSupplierFilter} placeholder="공급사 전체" options={supplierOptions} />
          <FilterSelect value={titleFilter} onChange={setTitleFilter} placeholder="직책 전체" options={titleOptions} />
        </FilterBar>

        {loading ? (
          <div className="text-sm text-slate-400">불러오는 중…</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">
            {rows.length === 0
              ? '받은 인원이 없습니다. 견적 요청 → 공급사 응답을 받아야 표시됩니다.'
              : '조건에 맞는 인원이 없습니다.'}
          </div>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-semibold">이름</th>
                  <th className="px-3 py-2 font-semibold">직책</th>
                  <th className="px-3 py-2 font-semibold">공급사</th>
                  <th className="px-3 py-2 font-semibold text-right">일대</th>
                  <th className="px-3 py-2 font-semibold text-right">월대</th>
                  <th className="px-3 py-2 font-semibold">받은 시점</th>
                  <th className="px-3 py-2 font-semibold">견적</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filtered.map((r) => (
                  <tr key={r.id}>
                    <td className="px-3 py-2">
                      <Link to={`/persons/${r.person_id}`} className="font-semibold text-slate-900 hover:text-brand-700">
                        {r.person_label}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-xs text-slate-600">{r.job_title ?? '-'}</td>
                    <td className="px-3 py-2 text-slate-700">{r.supplier_company_name ?? '#' + r.supplier_company_id}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{formatWon(r.daily_price)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{formatWon(r.monthly_price)}</td>
                    <td className="px-3 py-2 text-xs text-slate-500 tabular-nums">
                      {r.sent_at ? new Date(r.sent_at).toLocaleDateString('ko-KR') : '-'}
                    </td>
                    <td className="px-3 py-2">
                      <Link to={`/quotations/${r.quotation_request_id}`} className="text-xs text-blue-600 hover:underline">
                        #{r.quotation_request_id} →
                      </Link>
                    </td>
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
