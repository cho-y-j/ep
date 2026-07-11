import { useCallback, useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import CollapsibleSection from '../../components/ui/CollapsibleSection';
import { api } from '../../lib/api';
import { formatWon } from '../../lib/format';

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

  return (
    <AppShell breadcrumb={[{ label: '투입 정산' }]}>
      <div className="space-y-5">
        <div>
          <h1 className="text-xl font-bold text-slate-900">투입 정산</h1>
          <p className="mt-1 text-sm text-slate-500">
            소유자(본인/협력사)별로 근무일수 기준 정산 금액을 요약합니다.
          </p>
          <p className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
            월대 = (월대 ÷ 25) × 근무일수 + OT, 일대 = 일대 × 근무일수 + OT. 근무일수·OT일수는 투입 관리에서 입력합니다(미입력이면 금액 미산출). 미입력 시 작업확인서(서명완료) 기준 자동 집계. 수수료 미표기.
          </p>
        </div>

        <div className="card flex flex-wrap items-end gap-3 p-4">
          <label className="text-xs text-slate-500">시작
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
              className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm" />
          </label>
          <label className="text-xs text-slate-500">종료
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
              className="mt-1 block rounded border border-slate-300 px-2 py-1 text-sm" />
          </label>
          <button onClick={() => void load(from, to)} className="btn-primary px-4 py-1.5 text-sm">조회</button>
          {(from || to) && (
            <button onClick={() => { setFrom(''); setTo(''); void load('', ''); }}
              className="text-xs text-slate-500 underline">전체</button>
          )}
        </div>

        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : !data || data.owners.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">정산할 투입 내역이 없습니다.</div>
        ) : (
          <>
            <div className="card flex items-center justify-between p-5">
              <span className="text-sm font-semibold text-slate-700">전체 합계</span>
              <span className="text-2xl font-bold text-slate-900 tabular-nums">{formatWon(data.grand_total)}</span>
            </div>

            {data.owners.map((o) => (
              <CollapsibleSection
                key={o.owner_company_id}
                title={o.owner_company_name}
                defaultOpen={o.is_self}
                status={o.is_self
                  ? <span className="rounded-full bg-brand-100 px-2 py-0.5 text-[11px] font-semibold text-brand-700">본인</span>
                  : <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[11px] font-semibold text-slate-600">협력사</span>}
                summary={`${o.item_count}건 · ${formatWon(o.total_amount)}`}
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
          {owner.items.map((it) => <ItemRow key={it.dispatch_id} it={it} onSave={onSave} />)}
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

function ItemRow({ it, onSave }: {
  it: SettlementItem;
  onSave: (it: SettlementItem, wd: number | null, od: number | null) => Promise<void>;
}) {
  const initWd = it.settlement_work_days != null ? String(it.settlement_work_days) : '';
  const initOd = it.settlement_ot_days != null ? String(it.settlement_ot_days) : '';
  const [wd, setWd] = useState(initWd);
  const [od, setOd] = useState(initOd);
  const [saving, setSaving] = useState(false);
  const dirty = wd !== initWd || od !== initOd;

  const save = async () => {
    setSaving(true);
    try {
      await onSave(it, wd === '' ? null : Number(wd), od === '' ? null : Number(od));
    } finally {
      setSaving(false);
    }
  };

  return (
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
      </td>
    </tr>
  );
}
