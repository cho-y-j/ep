import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { formatWon } from '../../lib/format';
import AppShell from '../../components/layout/AppShell';

type Row = {
  id: number;
  quotation_request_id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  equipment_id: number;
  equipment_label?: string | null;
  equipment_category?: string | null;
  daily_price?: number | null;
  ot_daily_price?: number | null;
  monthly_price?: number | null;
  ot_monthly_price?: number | null;
  sent_at?: string | null;
};

export default function DispatchedEquipmentBpPage() {
  const [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(true);
  const [q, setQ] = useState('');

  useEffect(() => {
    let cancelled = false;
    api.get<Row[]>('/api/bp-dispatched/equipment')
      .then((r) => { if (!cancelled) setRows(r.data); })
      .catch(() => { if (!cancelled) setRows([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const filtered = q.trim()
    ? rows.filter((r) => {
        const hay = `${r.equipment_label ?? ''} ${r.supplier_company_name ?? ''} ${r.equipment_category ?? ''}`.toLowerCase();
        return hay.includes(q.toLowerCase());
      })
    : rows;

  return (
    <AppShell breadcrumb={[{ label: '투입 장비' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">투입 장비</h1>
          <p className="mt-1 text-sm text-slate-500">
            견적 응답으로 받은 차량 — 작업계획서에 투입하거나 투입 중인 장비를 모아봅니다.
          </p>
        </header>

        <div className="flex items-center gap-2">
          <input type="text" value={q} onChange={(e) => setQ(e.target.value)}
                 placeholder="장비명 / 공급사 / 카테고리 검색"
                 className="flex-1 max-w-md px-3 py-2 text-sm border border-slate-300 rounded" />
          <span className="text-xs text-slate-500">총 {filtered.length}건</span>
        </div>

        {loading ? (
          <div className="text-sm text-slate-400">불러오는 중…</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">
            받은 차량이 없습니다. 견적 요청 → 공급사 응답을 받아야 표시됩니다.
          </div>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-semibold">장비</th>
                  <th className="px-3 py-2 font-semibold">카테고리</th>
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
                      <Link to={`/equipment/${r.equipment_id}`} className="font-semibold text-slate-900 hover:text-brand-700">
                        {r.equipment_label}
                      </Link>
                    </td>
                    <td className="px-3 py-2 text-xs text-slate-600">{r.equipment_category ?? '-'}</td>
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
