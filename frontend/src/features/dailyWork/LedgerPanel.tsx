import { useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import LedgerView from './LedgerView';
import type { DailyWorkLog, Ledger } from './types';

function thisMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

/** 월간 원장 조회 패널 — 로그에 등장한 자원/현장에서 선택 → 정산주기 원장. 공급사·BP 공용. */
export default function LedgerPanel({ logs }: { logs: DailyWorkLog[] }) {
  const resources = useMemo(() => {
    const map = new Map<string, { key: string; type: 'EQ' | 'PP'; id: number; label: string; siteId: number | null }>();
    for (const l of logs) {
      if (l.equipment_id != null) {
        const k = `EQ:${l.equipment_id}`;
        if (!map.has(k)) map.set(k, { key: k, type: 'EQ', id: l.equipment_id, label: `[장비] ${l.equipment_label ?? l.equipment_id}`, siteId: l.site_id ?? null });
      } else if (l.person_id != null) {
        const k = `PP:${l.person_id}`;
        if (!map.has(k)) map.set(k, { key: k, type: 'PP', id: l.person_id, label: `[작업자] ${l.person_name ?? l.person_id}`, siteId: l.site_id ?? null });
      }
    }
    return [...map.values()];
  }, [logs]);

  const sites = useMemo(() => {
    const map = new Map<number, string>();
    for (const l of logs) if (l.site_id != null) map.set(l.site_id, l.site_name ?? `현장 #${l.site_id}`);
    return [...map.entries()].map(([id, name]) => ({ id, name }));
  }, [logs]);

  const [resourceKey, setResourceKey] = useState('');
  const [siteId, setSiteId] = useState<number | ''>('');
  const [period, setPeriod] = useState(thisMonth());
  const [ledger, setLedger] = useState<Ledger | null>(null);
  const [busy, setBusy] = useState(false);

  const selected = resources.find((r) => r.key === resourceKey);

  const run = async () => {
    if (!selected) { toast.error('장비 또는 작업자를 선택하세요'); return; }
    setBusy(true);
    try {
      const params: Record<string, unknown> = { period };
      if (selected.type === 'EQ') params.equipmentId = selected.id;
      else params.personId = selected.id;
      const sid = siteId === '' ? selected.siteId : siteId;
      if (sid != null) params.siteId = sid;
      const { data } = await api.get<Ledger>('/api/daily-work-logs/ledger', { params });
      setLedger(data);
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '원장 조회에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="card p-4 flex flex-wrap items-end gap-3">
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">장비 / 작업자 <span className="text-rose-500">*</span></span>
          <select value={resourceKey} onChange={(e) => { setResourceKey(e.target.value); setSiteId(''); }} className="input mt-1 w-56">
            <option value="">선택</option>
            {resources.map((r) => <option key={r.key} value={r.key}>{r.label}</option>)}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">현장 (정산주기 기준)</span>
          <select value={siteId} onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : '')} className="input mt-1 w-48">
            <option value="">자원 현장 자동</option>
            {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">기준월</span>
          <input type="month" value={period} onChange={(e) => setPeriod(e.target.value)} className="input mt-1 w-40" />
        </label>
        <button onClick={run} disabled={busy || !selected} className="btn-primary disabled:opacity-50">
          {busy ? '조회 중…' : '원장 조회'}
        </button>
      </div>

      {resources.length === 0 ? (
        <div className="card p-8 text-center text-sm text-slate-400">
          일일 확인서가 등록되면 자원별 월간 원장을 조회할 수 있습니다.
        </div>
      ) : ledger ? (
        <LedgerView ledger={ledger} />
      ) : (
        <div className="card p-8 text-center text-sm text-slate-400">
          장비/작업자와 기준월을 선택하고 <b>원장 조회</b>를 누르세요.
        </div>
      )}
    </div>
  );
}
