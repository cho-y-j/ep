import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';

type DeploymentRow = {
  equipment_id: number;
  vehicle_no?: string | null;
  model?: string | null;
  category: string;
  external: boolean;
  owner_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  deploy_count: number;
};

const catLabel = (c: string) => EQUIPMENT_CATEGORY_LABEL[c as EquipmentCategory] ?? c;
const equipName = (r: DeploymentRow) => r.vehicle_no || r.model || `장비 #${r.equipment_id}`;

export default function EquipmentDeploymentStatsPage() {
  const [rows, setRows] = useState<DeploymentRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [q, setQ] = useState('');
  const [cat, setCat] = useState('');
  const [bp, setBp] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api.get<DeploymentRow[]>('/api/equipment-stats/deployments')
      .then((r) => { if (!cancelled) { setRows(r.data); setError(null); } })
      .catch(() => { if (!cancelled) setError('통계를 불러오지 못했습니다'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // 필터 옵션 — 로드된 통계에서 파생(종류·발주사).
  const catOptions = useMemo(() => {
    const m = new Map<string, string>();
    rows.forEach((r) => { if (r.category) m.set(r.category, catLabel(r.category)); });
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [rows]);
  const bpOptions = useMemo(() => {
    const m = new Map<string, string>();
    rows.forEach((r) => m.set(String(r.bp_company_id ?? 'none'), r.bp_company_name ?? '(발주사 미지정)'));
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [rows]);

  const qLower = q.trim().toLowerCase();
  const filteredRows = useMemo(() => rows.filter((r) => {
    if (cat && r.category !== cat) return false;
    if (bp && String(r.bp_company_id ?? 'none') !== bp) return false;
    if (qLower) {
      const hay = `${equipName(r)} ${r.model ?? ''} ${catLabel(r.category)} ${r.owner_name ?? ''} ${r.bp_company_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [rows, cat, bp, qLower]);

  const totalCount = filteredRows.reduce((s, r) => s + r.deploy_count, 0);
  const equipCount = new Set(filteredRows.map((r) => r.equipment_id)).size;
  const companyCount = new Set(filteredRows.map((r) => r.bp_company_id ?? 0)).size;
  const activeFilterCount = [q, cat, bp].filter(Boolean).length;

  // BP 회사별 그룹 (합계 내림차순) — "이 회사에 어떤 차가 몇 번"
  const groups = useMemo(() => {
    const m = new Map<string, { name: string; rows: DeploymentRow[]; total: number }>();
    for (const r of filteredRows) {
      const key = String(r.bp_company_id ?? 'none');
      if (!m.has(key)) m.set(key, { name: r.bp_company_name ?? '(발주사 미지정)', rows: [], total: 0 });
      const g = m.get(key)!;
      g.rows.push(r);
      g.total += r.deploy_count;
    }
    const arr = Array.from(m.values());
    arr.forEach((g) => g.rows.sort((a, b) => b.deploy_count - a.deploy_count));
    arr.sort((a, b) => b.total - a.total);
    return arr;
  }, [filteredRows]);

  return (
    <AppShell breadcrumb={[{ label: '장비 투입 통계' }]}>
      <div className="space-y-5">
        <PageHeader
          title="장비 투입 통계"
          subtitle="내 장비가 어느 발주사(BP) 작업계획서에 몇 번 투입됐는지 — 취소·초안 제외."
        />

        <div className="grid grid-cols-3 gap-3">
          <StatCard label="총 투입 건수" value={totalCount} />
          <StatCard label="장비 수" value={equipCount} />
          <StatCard label="거래 발주사" value={companyCount} />
        </div>

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '장비·소유주·발주사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={() => { setQ(''); setCat(''); setBp(''); }}
        >
          <FilterSelect value={cat} onChange={setCat} placeholder="종류 전체" options={catOptions} />
          <FilterSelect value={bp} onChange={setBp} placeholder="발주사 전체" options={bpOptions} />
        </FilterBar>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : error ? (
          <p className="text-sm text-red-600">{error}</p>
        ) : groups.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">
            {rows.length === 0 ? '투입 이력이 없습니다.' : '조건에 맞는 투입 이력이 없습니다.'}
          </div>
        ) : (
          <div className="space-y-4">
            {groups.map((g, i) => (
              <div key={i} className="card p-0 overflow-hidden">
                <div className="flex items-center justify-between bg-slate-50 px-5 py-3 border-b border-slate-200">
                  <span className="font-bold text-slate-900">{g.name}</span>
                  <span className="text-sm text-slate-500">합계 <strong className="text-brand-700">{g.total}</strong>건</span>
                </div>
                <table className="w-full text-sm">
                  <thead className="text-left text-xs text-slate-500">
                    <tr>
                      <th className="px-5 py-2 font-semibold">장비</th>
                      <th className="px-3 py-2 font-semibold">종류</th>
                      <th className="px-3 py-2 font-semibold">소유주</th>
                      <th className="px-5 py-2 font-semibold text-right">투입 횟수</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {g.rows.map((r) => (
                      <tr key={r.equipment_id} className="hover:bg-slate-50/60">
                        <td className="px-5 py-2 font-medium text-slate-800">
                          {equipName(r)}
                          {r.vehicle_no && r.model ? <span className="ml-1.5 text-xs text-slate-400">{r.model}</span> : null}
                        </td>
                        <td className="px-3 py-2 text-slate-600">{catLabel(r.category)}</td>
                        <td className="px-3 py-2">
                          {r.external ? (
                            <span className="inline-flex items-center gap-1.5">
                              <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-amber-100 text-amber-800">외부</span>
                              <span className="text-xs text-slate-600">{r.owner_name || '소유주 미입력'}</span>
                            </span>
                          ) : (
                            <span className="px-1.5 py-0.5 rounded text-[11px] font-semibold bg-slate-100 text-slate-500">내부</span>
                          )}
                        </td>
                        <td className="px-5 py-2 text-right tabular-nums font-bold text-slate-900">{r.deploy_count}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="card p-4">
      <span className="block text-sm text-slate-500">{label}</span>
      <span className="mt-1 block text-3xl font-bold text-slate-900 tabular-nums">{value}</span>
    </div>
  );
}
