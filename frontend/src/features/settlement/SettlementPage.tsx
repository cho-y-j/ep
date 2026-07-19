import { useCallback, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import CollapsibleSection from '../../components/ui/CollapsibleSection';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { formatWon } from '../../lib/format';

type OtBreakdown = {
  contract_id?: number | null;
  early_hours: number; early_amount: number;
  lunch_hours: number; lunch_amount: number;
  evening_hours: number; evening_amount: number;
  night_hours: number; night_amount: number;
  overnight_hours: number; overnight_amount: number;
  total_ot_amount: number;
  log_count: number;
};
type SettlementItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  dispatch_id: number;
  resource_id?: number | null;
  resource_label: string;
  quotation_request_id: number;
  site_id?: number | null;
  site_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  work_period_start?: string | null;
  work_period_end?: string | null;
  period_days: number;
  daily_price?: number | null;
  ot_daily_price?: number | null;
  monthly_price?: number | null;
  ot_monthly_price?: number | null;
  amount_basis?: 'MONTHLY' | 'DAILY' | null;
  amount?: number | null;
  supplier_company_id: number;
  dispatched_by_parent: boolean;
  sent_at?: string | null;
  settlement_work_days?: number | null;
  settlement_ot_days?: number | null;
  base_amount?: number | null;
  ot_amount?: number | null;
  site_settlement_day?: number | null;
  derived_work_days?: number | null;
  derived_ot_days?: number | null;
  work_days_source?: 'MANUAL' | 'DERIVED' | null;
  source_kind?: 'DISPATCH' | 'DEPLOYMENT' | null;
  ot_breakdown?: OtBreakdown | null;
};
type OwnerSettlement = {
  owner_company_id: number;
  owner_company_name: string;
  is_self: boolean;
  items: SettlementItem[];
  total_amount: number;
  item_count: number;
};
type SettlementSummaryResponse = {
  owners: OwnerSettlement[];
  grand_total: number;
};

export default function SettlementPage() {
  const [data, setData] = useState<SettlementSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  // 클라이언트 필터 — 이미 로드된 데이터를 좁힘(서버 재조회는 기간만).
  const [q, setQ] = useState('');
  const [siteFilter, setSiteFilter] = useState('');
  const [ownerFilter, setOwnerFilter] = useState('');
  const [bpFilter, setBpFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  const load = useCallback((f: string, t: string) => {
    setLoading(true);
    const params: Record<string, string> = {};
    if (f) params.from = f;
    if (t) params.to = t;
    return api.get<SettlementSummaryResponse>('/api/settlements/summary', { params })
      .then((res) => { setData(res.data); setError(null); })
      .catch((err) => setError(err instanceof AxiosError ? (err.response?.data?.message ?? '불러오기 실패') : '불러오기 실패'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { void load('', ''); }, [load]);

  const saveQuantity = async (it: SettlementItem, workDays: number | null, otDays: number | null) => {
    await api.patch(`/api/settlements/dispatch/${it.resource_type}/${it.dispatch_id}/quantity`,
      { work_days: workDays, ot_days: otDays });
    await load(from, to);
  };

  const downloadStatement = async (fmt: 'pdf' | 'xlsx') => {
    const params: Record<string, string> = { format: fmt };
    if (from) params.from = from;
    if (to) params.to = to;
    try {
      const res = await api.get('/api/settlements/statement', { params, responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      const period = from || to ? `${from || ''}~${to || ''}` : '전체';
      a.download = `거래내역서_${period}.${fmt}`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      setError('거래내역서 다운로드에 실패했습니다');
    }
  };

  // 필터 옵션 — 로드된 데이터에서 파생(현장·소유사·발주사).
  const { siteOptions, ownerOptions, bpOptions } = useMemo(() => {
    const sites = new Map<string, string>();
    const owners = new Map<string, string>();
    const bps = new Map<string, string>();
    data?.owners.forEach((o) => {
      owners.set(String(o.owner_company_id), o.owner_company_name);
      o.items.forEach((it) => {
        if (it.site_id != null) sites.set(String(it.site_id), it.site_name ?? `현장 #${it.site_id}`);
        if (it.bp_company_id != null) bps.set(String(it.bp_company_id), it.bp_company_name ?? `발주사 #${it.bp_company_id}`);
      });
    });
    const toOpts = (m: Map<string, string>) => [...m.entries()].map(([value, label]) => ({ value, label }));
    return { siteOptions: toOpts(sites), ownerOptions: toOpts(owners), bpOptions: toOpts(bps) };
  }, [data]);

  const qLower = q.trim().toLowerCase();
  // 행(자원) 단위 필터가 걸리면 소계·합계를 보이는 행 기준으로 재계산(소유사만 좁힐 땐 서버 합계 유지 → 기존 계산 무회귀).
  const itemFiltersActive = !!(siteFilter || bpFilter || typeFilter || qLower);
  const view = useMemo(() => {
    if (!data) return null;
    const owners = data.owners
      .filter((o) => !ownerFilter || String(o.owner_company_id) === ownerFilter)
      .map((o) => {
        const items = itemFiltersActive
          ? o.items.filter((it) => {
              if (siteFilter && String(it.site_id ?? '') !== siteFilter) return false;
              if (bpFilter && String(it.bp_company_id ?? '') !== bpFilter) return false;
              if (typeFilter && it.resource_type !== typeFilter) return false;
              if (qLower) {
                const hay = `${it.resource_label} ${it.site_name ?? ''} ${it.bp_company_name ?? ''}`.toLowerCase();
                if (!hay.includes(qLower)) return false;
              }
              return true;
            })
          : o.items;
        const total_amount = itemFiltersActive
          ? items.reduce((s, it) => s + (it.amount ?? 0), 0)
          : o.total_amount;
        return { ...o, items, total_amount };
      })
      .filter((o) => o.items.length > 0);
    const grand_total = itemFiltersActive || ownerFilter
      ? owners.reduce((s, o) => s + o.total_amount, 0)
      : data.grand_total;
    return { owners, grand_total };
  }, [data, ownerFilter, siteFilter, bpFilter, typeFilter, qLower, itemFiltersActive]);

  const activeFilterCount =
    [q, siteFilter, ownerFilter, bpFilter, typeFilter].filter(Boolean).length + (from || to ? 1 : 0);
  const resetFilters = () => {
    setQ(''); setSiteFilter(''); setOwnerFilter(''); setBpFilter(''); setTypeFilter('');
    setFrom(''); setTo(''); void load('', '');
  };

  return (
    <AppShell breadcrumb={[{ label: '투입 정산' }]}>
      <div className="space-y-5">
        <PageHeader
          title="투입 정산"
          subtitle="소유자(본인/협력사)별로 근무일수 기준 정산 금액을 요약합니다."
          actions={
            <>
              <button onClick={() => void downloadStatement('pdf')} className="btn-ghost text-sm">거래내역서 PDF</button>
              <button onClick={() => void downloadStatement('xlsx')} className="btn-ghost text-sm">Excel</button>
            </>
          }
        />

        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
          월대 = (월대 ÷ 25) × 근무일수 + OT, 일대 = 일대 × 근무일수 + OT. 근무일수·OT일수는 투입 관리에서 입력합니다(미입력이면 금액 미산출). 미입력 시 작업확인서(서명완료) 기준 자동 집계. 수수료 미표기.
        </p>

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원명·현장·발주사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={siteFilter} onChange={setSiteFilter} placeholder="현장 전체" options={siteOptions} />
          <FilterSelect value={ownerFilter} onChange={setOwnerFilter} placeholder="소유사 전체" options={ownerOptions} />
          <FilterSelect value={bpFilter} onChange={setBpFilter} placeholder="발주사(BP) 전체" options={bpOptions} />
          <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="자원종류 전체"
            options={[{ value: 'EQUIPMENT', label: '장비' }, { value: 'PERSON', label: '인원' }]} />
          <div className="flex items-center gap-1">
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="input w-auto" />
            <span className="text-xs text-slate-400">~</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="input w-auto" />
            <button onClick={() => void load(from, to)} className="btn-ghost text-sm">기간 조회</button>
          </div>
        </FilterBar>

        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : !data || data.owners.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">정산할 투입 내역이 없습니다.</div>
        ) : !view || view.owners.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 정산 내역이 없습니다.</div>
        ) : (
          <>
            <div className="card flex items-center justify-between p-5">
              <span className="text-sm font-semibold text-slate-700">전체 합계</span>
              <span className="text-2xl font-bold text-slate-900 tabular-nums">{formatWon(view.grand_total)}</span>
            </div>

            {view.owners.map((o) => (
              <CollapsibleSection
                key={o.owner_company_id}
                title={o.owner_company_name}
                defaultOpen={o.is_self}
                status={o.is_self
                  ? <span className="rounded-full bg-brand-100 px-2 py-0.5 text-[11px] font-semibold text-brand-700">본인</span>
                  : <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">협력사</span>}
                summary={`${o.items.length}건 · ${formatWon(o.total_amount)}`}
              >
                <OwnerTable owner={o} onSave={saveQuantity} />
              </CollapsibleSection>
            ))}
          </>
        )}
      </div>
    </AppShell>
  );
}

function OwnerTable({ owner, onSave }: {
  owner: OwnerSettlement;
  onSave: (it: SettlementItem, wd: number | null, od: number | null) => Promise<void>;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200 bg-slate-50 text-left text-xs text-slate-500">
          <tr>
            <th className="px-3 py-2.5 font-semibold">자원</th>
            <th className="px-3 py-2.5 font-semibold">현장 · 발주</th>
            <th className="px-3 py-2.5 font-semibold">계약기간</th>
            <th className="px-3 py-2.5 font-semibold text-right">단가</th>
            <th className="px-3 py-2.5 font-semibold text-center">근무 / OT일수</th>
            <th className="px-3 py-2.5 font-semibold text-right">금액</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {owner.items.map((it) => (
            <ItemRow key={`${it.source_kind ?? 'D'}:${it.resource_type}:${it.dispatch_id}`} it={it} onSave={onSave} />
          ))}
        </tbody>
        <tfoot>
          <tr className="border-t border-slate-200 bg-slate-50">
            <td className="px-3 py-2.5 text-sm font-semibold text-slate-700" colSpan={5}>소계</td>
            <td className="px-3 py-2.5 text-right text-sm font-bold text-slate-900 tabular-nums">{formatWon(owner.total_amount)}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

/** OT 5분류 컬럼 — [시간키, 금액키, 라벨]. */
const OT_COLS: Array<[keyof OtBreakdown, keyof OtBreakdown, string]> = [
  ['early_hours', 'early_amount', '조출'],
  ['lunch_hours', 'lunch_amount', '점심'],
  ['evening_hours', 'evening_amount', '연장'],
  ['night_hours', 'night_amount', '야간'],
  ['overnight_hours', 'overnight_amount', '철야'],
];

function fmtHours(h?: number): string {
  return String(Number(h ?? 0));
}

function ItemRow({ it, onSave }: {
  it: SettlementItem;
  onSave: (it: SettlementItem, wd: number | null, od: number | null) => Promise<void>;
}) {
  const initWd = it.settlement_work_days != null ? String(it.settlement_work_days) : '';
  const initOd = it.settlement_ot_days != null ? String(it.settlement_ot_days) : '';
  const [wd, setWd] = useState(initWd);
  const [od, setOd] = useState(initOd);
  const [saving, setSaving] = useState(false);
  const [showOt, setShowOt] = useState(false);
  const dirty = wd !== initWd || od !== initOd;
  const isDeployment = it.source_kind === 'DEPLOYMENT';
  const ot = it.ot_breakdown;

  const save = async () => {
    setSaving(true);
    try {
      await onSave(it, wd === '' ? null : Number(wd), od === '' ? null : Number(od));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
    <tr>
      <td className="px-3 py-2.5">
        <div className="flex items-center gap-1.5">
          <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
            it.resource_type === 'EQUIPMENT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
            {it.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
          </span>
          <span className="font-medium text-slate-900">{it.resource_label}</span>
          {it.dispatched_by_parent && (
            <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-800">부모 대행</span>
          )}
          {isDeployment && (
            <span className="rounded bg-violet-100 px-1.5 py-0.5 text-[10px] font-semibold text-violet-700">투입요청</span>
          )}
        </div>
      </td>
      <td className="px-3 py-2.5 text-slate-600">
        <div className="truncate">
          {it.site_name ?? '-'}
          {it.site_settlement_day != null && <span className="ml-1 text-[10px] text-slate-400">정산일 {it.site_settlement_day}</span>}
        </div>
        <div className="text-xs text-slate-400 truncate">{it.bp_company_name ?? '-'}</div>
      </td>
      <td className="px-3 py-2.5 text-xs text-slate-600 tabular-nums whitespace-nowrap">
        {it.work_period_start ?? '?'} ~ {it.work_period_end ?? '?'}
        <span className="ml-1 text-slate-400">({it.period_days}일)</span>
      </td>
      <td className="px-3 py-2.5 text-right text-xs text-slate-600 tabular-nums whitespace-nowrap">
        {it.amount_basis === 'MONTHLY'
          ? <div>월대 {formatWon(it.monthly_price)}</div>
          : it.amount_basis === 'DAILY'
            ? <div>일대 {formatWon(it.daily_price)}</div>
            : <div className="text-slate-400">단가 미입력</div>}
        {it.ot_monthly_price != null && <div className="text-slate-400">OT월 {formatWon(it.ot_monthly_price)}</div>}
        {it.ot_daily_price != null && <div className="text-slate-400">OT일 {formatWon(it.ot_daily_price)}</div>}
      </td>
      <td className="px-3 py-2.5 text-center whitespace-nowrap">
        {isDeployment ? (
          <span className="text-[11px] text-slate-400">현장 투입요청</span>
        ) : (
          <>
            <div className="flex items-center justify-center gap-1">
              <input type="number" min={0} value={wd} onChange={(e) => setWd(e.target.value)} placeholder="근무"
                className="w-14 rounded border border-slate-300 px-1.5 py-1 text-xs text-right" />
              <span className="text-slate-300">/</span>
              <input type="number" min={0} value={od} onChange={(e) => setOd(e.target.value)} placeholder="OT"
                className="w-12 rounded border border-slate-300 px-1.5 py-1 text-xs text-right" />
              {dirty && (
                <button onClick={() => void save()} disabled={saving}
                  className="rounded bg-brand-600 px-2 py-1 text-[11px] font-semibold text-white disabled:opacity-50">
                  {saving ? '…' : '저장'}
                </button>
              )}
            </div>
            {it.work_days_source === 'DERIVED' && (
              <div className="mt-1 text-[10px] text-emerald-600">
                자동 {it.derived_work_days}일{(it.derived_ot_days ?? 0) > 0 && ` · OT ${it.derived_ot_days}일`}
              </div>
            )}
          </>
        )}
      </td>
      <td className="px-3 py-2.5 text-right font-semibold text-slate-900 tabular-nums whitespace-nowrap">
        {it.amount != null ? (
          <>
            <div>{formatWon(it.amount)}</div>
            {(it.ot_amount ?? 0) > 0 && (
              <div className="text-[10px] font-normal text-slate-400">기본 {formatWon(it.base_amount)} + OT {formatWon(it.ot_amount)}</div>
            )}
          </>
        ) : <span className="text-slate-400 font-normal">근무일수 미입력</span>}
        {ot && (
          <button type="button" onClick={() => setShowOt((v) => !v)}
            className="mt-1 block ml-auto text-[10px] font-semibold text-brand-600 hover:underline">
            OT 5분류 {formatWon(ot.total_ot_amount)} {showOt ? '▴' : '▾'}
          </button>
        )}
      </td>
    </tr>
    {showOt && ot && (
      <tr className="bg-brand-50/40">
        <td colSpan={6} className="px-3 py-2">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px]">
            <span className="font-semibold text-slate-600">일일 확인서 OT (인정 {ot.log_count}건)</span>
            {OT_COLS.map(([hk, ak, label]) => (
              <span key={label} className="text-slate-500">
                {label} <b className="text-slate-700">{fmtHours(ot[hk] as number)}h</b>
                <span className="text-slate-400"> · {formatWon(ot[ak] as number)}</span>
              </span>
            ))}
            <span className="ml-auto font-semibold text-slate-800">OT 합계 {formatWon(ot.total_ot_amount)}</span>
          </div>
        </td>
      </tr>
    )}
    </>
  );
}
