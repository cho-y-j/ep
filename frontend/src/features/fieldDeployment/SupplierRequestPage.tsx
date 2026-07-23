import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect, useTableSort } from '../../components/ui';
import MoneyInput from '../../components/MoneyInput';
import { useAuth } from '../auth/AuthContext';
import { FD_STATUS_LABEL, type FieldDeploymentResponse, type ResourceType } from '../../types/fieldDeployment';

const STATUS_CHIP: Record<string, string> = {
  REQUESTED: 'bg-amber-100 text-amber-800',
  ACCEPTED: 'bg-blue-100 text-blue-800',
  ACTIVE: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-rose-100 text-rose-800',
  COMPLETED: 'bg-slate-200 text-slate-600',
  CANCELLED: 'bg-slate-200 text-slate-500',
};

type DispatchedRow = {
  key: string;               // 후보 행 식별 — 배차행 D:<배차id> / 보유자원 O:<TYPE>:<자원id>
  dispatched: boolean;       // 배차 이력 뱃지 — false 면 자기(+자식) 보유 자원(배차 없이도 요청 가능)
  resource_type: ResourceType;
  resource_id: number;
  resource_label: string;
  bp_company_id?: number;
  bp_company_name?: string | null;
  quotation_request_id?: number;
  daily_price?: number | null;
  monthly_price?: number | null;
  ot_daily_price?: number | null;
  ot_monthly_price?: number | null;
  sent_at?: string | null;
};

type RateRow = { daily: string; monthly: string; ot: string; night: string };

export default function FieldDeploymentSupplierPage() {
  const { user } = useAuth();
  const isEquipmentSupplier = user?.role === 'EQUIPMENT_SUPPLIER';
  const [candidates, setCandidates] = useState<DispatchedRow[]>([]);
  const [sent, setSent] = useState<FieldDeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const [bpFilter, setBpFilter] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  // 세트 보드 [투입 요청] → ?equipment=<id> 프리필: 그 장비 선택 + 조합 다이얼로그 자동 오픈.
  const [searchParams, setSearchParams] = useSearchParams();
  const [prefillCombo, setPrefillCombo] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [eqRes, ppRes, ownEq, ownPp, sentRes] = await Promise.all([
        isEquipmentSupplier
          ? api.get<any[]>('/api/bp-dispatched/supplier-equipment').then((r) => r.data).catch(() => [])
          : Promise.resolve([]),
        !isEquipmentSupplier
          ? api.get<any[]>('/api/bp-dispatched/supplier-persons').then((r) => r.data).catch(() => [])
          : Promise.resolve([]),
        // 자기(+직속 자식) 보유 자원 — 배차행이 없는 신규 세트도 투입 요청 후보에 오르게(기존 스코프 API 재사용).
        isEquipmentSupplier
          ? api.get<any[]>('/api/equipment').then((r) => r.data ?? []).catch(() => [])
          : Promise.resolve([]),
        !isEquipmentSupplier
          ? api.get<{ content: any[] }>('/api/persons', { params: { size: 100 } })
              .then((r) => r.data.content ?? []).catch(() => [])
          : Promise.resolve([]),
        api.get<FieldDeploymentResponse[]>('/api/field-deployments/supplier').then((r) => r.data).catch(() => []),
      ]);
      const rows: DispatchedRow[] = isEquipmentSupplier
        ? eqRes.map((d) => ({
            key: `D:${d.id}`, dispatched: true, resource_type: 'EQUIPMENT',
            resource_id: d.equipment_id, resource_label: d.equipment_label ?? '#' + d.equipment_id,
            bp_company_name: d.bp_company_name, bp_company_id: d.bp_company_id,
            quotation_request_id: d.quotation_request_id,
            daily_price: d.daily_price, monthly_price: d.monthly_price,
            ot_daily_price: d.ot_daily_price, ot_monthly_price: d.ot_monthly_price,
            sent_at: d.sent_at,
          }))
        : ppRes.map((d) => ({
            key: `D:${d.id}`, dispatched: true, resource_type: 'PERSON',
            resource_id: d.person_id, resource_label: d.person_label ?? '#' + d.person_id,
            bp_company_name: d.bp_company_name, bp_company_id: d.bp_company_id,
            quotation_request_id: d.quotation_request_id,
            daily_price: d.daily_price, monthly_price: d.monthly_price, sent_at: d.sent_at,
          }));
      // 배차행에 이미 있는 자원은 제외(중복 제거) — 배차행이 BP·단가 정보를 가지므로 우선.
      const dispatchedKeys = new Set(rows.map((r) => `${r.resource_type}:${r.resource_id}`));
      const ownRows: DispatchedRow[] = isEquipmentSupplier
        ? ownEq.filter((e) => !dispatchedKeys.has(`EQUIPMENT:${e.id}`)).map((e) => ({
            key: `O:EQUIPMENT:${e.id}`, dispatched: false, resource_type: 'EQUIPMENT' as ResourceType,
            resource_id: e.id, resource_label: e.vehicle_no || e.model || `장비 #${e.id}`,
          }))
        : ownPp.filter((p) => !dispatchedKeys.has(`PERSON:${p.id}`)).map((p) => ({
            key: `O:PERSON:${p.id}`, dispatched: false, resource_type: 'PERSON' as ResourceType,
            resource_id: p.id, resource_label: p.name ?? `인원 #${p.id}`,
          }));
      setCandidates([...rows, ...ownRows]);
      setSent(sentRes);
      setSelected(new Set());
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, [isEquipmentSupplier]);

  // ?equipment=<id> 프리필 — 로드 완료 후 1회: 해당 장비 후보 선택 + 조합 다이얼로그 자동 오픈.
  useEffect(() => {
    if (loading) return;
    const eq = searchParams.get('equipment');
    if (!eq) return;
    const row = candidates.find((c) => c.resource_type === 'EQUIPMENT' && c.resource_id === Number(eq));
    if (row) {
      setSelected(new Set([row.key]));
      setPrefillCombo(true);
      setDialogOpen(true);
    }
    const next = new URLSearchParams(searchParams);
    next.delete('equipment');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  // BP사 필터 옵션 (후보에서 distinct)
  const bpOptions = useMemo(() => {
    const m = new Map<number, string>();
    for (const c of candidates) if (c.bp_company_id) m.set(c.bp_company_id, c.bp_company_name ?? '#' + c.bp_company_id);
    return Array.from(m.entries());
  }, [candidates]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return candidates.filter((c) => {
      if (bpFilter && String(c.bp_company_id ?? '') !== bpFilter) return false;
      if (!q) return true;
      return (c.resource_label?.toLowerCase().includes(q)) || (c.bp_company_name ?? '').toLowerCase().includes(q);
    });
  }, [candidates, search, bpFilter]);

  const activeFilterCount = [search, bpFilter].filter(Boolean).length;
  const resetFilters = () => { setSearch(''); setBpFilter(''); };

  const candSort = useTableSort<'resource' | 'bp' | 'daily' | 'monthly' | 'sentAt' | 'quote'>();
  const sortedCandidates = candSort.apply(filtered, (r, key) => {
    switch (key) {
      case 'resource': return r.resource_label;
      case 'bp': return r.bp_company_name;
      case 'daily': return r.daily_price;
      case 'monthly': return r.monthly_price;
      case 'sentAt': return r.sent_at;
      case 'quote': return r.quotation_request_id;
    }
  });
  const sentSort = useTableSort<'resource' | 'bp' | 'start' | 'rate' | 'status'>();
  const sortedSent = sentSort.apply(sent, (r, key) => {
    switch (key) {
      case 'resource': return r.resource_label;
      case 'bp': return r.bp_company_name;
      case 'start': return r.start_date;
      case 'rate': return r.daily_price ?? r.monthly_price ?? r.ot_price ?? r.night_price;
      case 'status': return FD_STATUS_LABEL[r.status];
    }
  });

  const selectedRows = useMemo(() => candidates.filter((c) => selected.has(c.key)), [candidates, selected]);

  const toggle = (key: string) => setSelected((prev) => {
    const next = new Set(prev);
    if (next.has(key)) next.delete(key); else next.add(key);
    return next;
  });
  const allFilteredSelected = filtered.length > 0 && filtered.every((c) => selected.has(c.key));
  const toggleAllFiltered = () => setSelected((prev) => {
    const next = new Set(prev);
    if (allFilteredSelected) filtered.forEach((c) => next.delete(c.key));
    else filtered.forEach((c) => next.add(c.key));
    return next;
  });

  const cancel = async (id: number) => {
    if (!confirm('이 요청을 취소하시겠어요?')) return;
    try {
      await api.post(`/api/field-deployments/${id}/cancel`, {});
      toast.success('취소됨');
      void load();
    } catch (e: any) { toast.error(e?.response?.data?.message ?? '실패'); }
  };

  return (
    <AppShell breadcrumb={[{ label: '현장 투입 요청' }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <PageHeader
          title="현장 투입 요청"
          subtitle="배차(수락)된 자원과 보유 자원을 선택하고, 일대·월대·OT·야간 단가를 넣어 한 번에 BP로 투입 요청을 보냅니다."
        />

        <section>
          <h2 className="font-bold text-slate-900 mb-2">
            투입 후보 자원 ({filtered.length}{filtered.length !== candidates.length ? `/${candidates.length}` : ''})
          </h2>
          <FilterBar
            search={{ value: search, onChange: setSearch, placeholder: '자원·BP 검색' }}
            activeFilterCount={activeFilterCount}
            onReset={resetFilters}
          >
            <FilterSelect value={bpFilter} onChange={setBpFilter} placeholder="전체 BP사"
              options={bpOptions.map(([id, name]) => ({ value: String(id), label: name }))} />
          </FilterBar>

          {loading ? <div className="text-sm text-slate-400">불러오는 중…</div>
           : candidates.length === 0 ? (
            <div className="card p-8 text-center text-sm text-slate-400">
              투입 후보가 없습니다. 자원(장비·인원)을 등록하거나 견적 응답을 발송하세요.
            </div>
          ) : (
            <div className="card overflow-x-auto p-0">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th className="px-3 py-2 w-9">
                      <input type="checkbox" checked={allFilteredSelected} onChange={toggleAllFiltered} />
                    </th>
                    <th className="px-3 py-2">{candSort.header('resource', '자원')}</th>
                    <th className="px-3 py-2">{candSort.header('bp', '받은 BP사')}</th>
                    <th className="px-3 py-2 text-right">{candSort.header('daily', '일대')}</th>
                    <th className="px-3 py-2 text-right">{candSort.header('monthly', '월대')}</th>
                    <th className="px-3 py-2">{candSort.header('sentAt', '발송 시점')}</th>
                    <th className="px-3 py-2">{candSort.header('quote', '견적')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sortedCandidates.map((r) => (
                    <tr key={r.key} className={selected.has(r.key) ? 'bg-brand-50/40' : 'cursor-pointer'}
                        onClick={() => toggle(r.key)}>
                      <td className="px-3 py-2" onClick={(e) => e.stopPropagation()}>
                        <input type="checkbox" checked={selected.has(r.key)} onChange={() => toggle(r.key)} />
                      </td>
                      <td className="px-3 py-2 font-semibold">
                        <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                        {r.resource_label}
                        {r.dispatched && (
                          <span className="ml-1.5 px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-blue-100 text-blue-700">
                            배차 이력
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2 text-slate-700">
                        {r.bp_company_name ?? (r.bp_company_id != null ? '#' + r.bp_company_id : '-')}
                      </td>
                      <td className="px-3 py-2 text-right tabular-nums text-xs">{r.daily_price != null ? r.daily_price.toLocaleString() : '-'}</td>
                      <td className="px-3 py-2 text-right tabular-nums text-xs">{r.monthly_price != null ? r.monthly_price.toLocaleString() : '-'}</td>
                      <td className="px-3 py-2 text-xs text-slate-500 tabular-nums">
                        {r.sent_at ? new Date(r.sent_at).toLocaleDateString('ko-KR') : '-'}
                      </td>
                      <td className="px-3 py-2 text-xs text-blue-600">
                        {r.quotation_request_id != null ? '#' + r.quotation_request_id : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {selected.size > 0 && (
            <div className="sticky bottom-3 mt-3 flex items-center justify-between gap-3 card px-4 py-2.5 shadow-lg border-brand-200">
              <div className="text-sm text-slate-600"><b className="text-slate-900">{selected.size}</b>건 선택됨</div>
              <div className="flex gap-2">
                <button onClick={() => setSelected(new Set())} className="px-3 py-1.5 text-sm text-slate-500 hover:text-slate-900">선택 해제</button>
                <button onClick={() => setDialogOpen(true)}
                        className="px-4 py-1.5 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700">
                  선택 {selected.size}건 투입 요청
                </button>
              </div>
            </div>
          )}
        </section>

        <section>
          <h2 className="font-bold text-slate-900 mb-2">보낸 투입 요청 ({sent.length})</h2>
          {sent.length === 0 ? (
            <div className="card p-6 text-center text-xs text-slate-400">아직 발송한 요청이 없습니다.</div>
          ) : (
            <div className="card overflow-x-auto p-0">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th className="px-3 py-2">{sentSort.header('resource', '자원')}</th>
                    <th className="px-3 py-2">{sentSort.header('bp', 'BP사')}</th>
                    <th className="px-3 py-2">{sentSort.header('start', '시작일')}</th>
                    <th className="px-3 py-2">{sentSort.header('rate', '단가 (일/월/OT/야)')}</th>
                    <th className="px-3 py-2">{sentSort.header('status', '상태')}</th>
                    <th className="px-3 py-2"></th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sortedSent.map((r) => (
                    <tr key={r.id}>
                      <td className="px-3 py-2 font-semibold">
                        <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                        {r.resource_label}
                      </td>
                      <td className="px-3 py-2">{r.bp_company_name ?? '#' + r.bp_company_id}</td>
                      <td className="px-3 py-2 text-xs tabular-nums">{r.start_date ?? '-'}</td>
                      <td className="px-3 py-2 text-[11px] tabular-nums text-slate-500">{rateSummary(r)}</td>
                      <td className="px-3 py-2">
                        <span className={`px-1.5 py-0.5 rounded text-[11px] font-semibold ${STATUS_CHIP[r.status] ?? ''}`}>
                          {FD_STATUS_LABEL[r.status]}
                        </span>
                        {r.review_note && r.status === 'REJECTED' && (
                          <div className="mt-1 text-[11px] text-rose-700">메모: {r.review_note}</div>
                        )}
                      </td>
                      <td className="px-3 py-2">
                        {r.status === 'REQUESTED' && (
                          <button onClick={() => cancel(r.id)} className="text-xs text-slate-500 hover:text-rose-700">취소</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        {dialogOpen && (
          <BatchRequestDialog rows={selectedRows} sentRows={sent} initialCombo={prefillCombo}
                              onClose={() => { setDialogOpen(false); setPrefillCombo(false); }}
                              onSaved={() => { setDialogOpen(false); setPrefillCombo(false); void load(); }} />
        )}
      </div>
    </AppShell>
  );
}

function rateSummary(r: FieldDeploymentResponse): string {
  const vals = [r.daily_price, r.monthly_price, r.ot_price, r.night_price];
  if (vals.every((v) => v == null)) return '-';
  return vals.map((v) => (v != null ? v.toLocaleString() : '-')).join(' / ');
}

function BatchRequestDialog({ rows, sentRows, initialCombo, onClose, onSaved }:
  { rows: DispatchedRow[]; sentRows: FieldDeploymentResponse[]; initialCombo?: boolean;
    onClose: () => void; onSaved: () => void }) {
  const [startDate, setStartDate] = useState('');
  const [note, setNote] = useState('');
  const [targetSiteId, setTargetSiteId] = useState<number | ''>('');
  const [sites, setSites] = useState<Array<{ id: number; name: string }>>([]);
  // R3 조합 모드(장비 1건 선택 시): 교대조 조종원 자동 로드 → combo API 1회 호출. 끄면 단건 발송 기존 그대로.
  const comboEligible = rows.length === 1 && rows[0].resource_type === 'EQUIPMENT';
  const [comboMode, setComboMode] = useState(initialCombo === true && comboEligible);
  const [comboOperators, setComboOperators] = useState<{ person_id: number; person_name?: string | null }[]>([]);
  const [comboChecked, setComboChecked] = useState<Set<number>>(new Set());
  const [opRates, setOpRates] = useState<Record<number, RateRow>>({});
  const [rates, setRates] = useState<Record<string, RateRow>>(() => {
    const init: Record<string, RateRow> = {};
    for (const r of rows) {
      init[r.key] = {
        daily: r.daily_price != null ? String(r.daily_price) : '',
        monthly: r.monthly_price != null ? String(r.monthly_price) : '',
        // OT 단가는 원천(배차 장비)에 일대 OT·월대 OT 둘 다 있으나 폼은 OT 단일 필드 → 일대 OT 우선, 없으면 월대 OT.
        ot: r.ot_daily_price != null ? String(r.ot_daily_price)
          : r.ot_monthly_price != null ? String(r.ot_monthly_price) : '',
        night: '',
      };
    }
    return init;
  });
  const [busy, setBusy] = useState(false);
  // 배차 이력 없는 보유 자원은 받는 BP 가 정해져 있지 않다 — 공통 BP 선택으로 채운다(배차행은 자기 BP 유지).
  const needsBpPick = rows.some((r) => !r.bp_company_id);
  const [bpOptions, setBpOptions] = useState<Array<{ id: number; name: string }>>([]);
  const [fallbackBp, setFallbackBp] = useState<number | ''>('');
  const bpOf = (r: DispatchedRow): number | null =>
    r.bp_company_id ?? (fallbackBp === '' ? null : fallbackBp);

  useEffect(() => {
    api.get<any[]>('/api/sites').then((r) => setSites((r.data ?? []).map((s) => ({ id: s.id, name: s.name }))))
      .catch(() => setSites([]));
  }, []);

  useEffect(() => {
    if (!needsBpPick) return;
    api.get<any[]>('/api/companies/bp-list')
      .then((r) => setBpOptions((r.data ?? []).map((c) => ({ id: c.id, name: c.name }))))
      .catch(() => setBpOptions([]));
  }, [needsBpPick]);

  // R3: 조합 모드 켜면 그 장비의 교대조 조종원 로드(기존 배치 API) — 전원 기본 선택, 개별 해제 가능.
  useEffect(() => {
    if (!comboMode || !comboEligible) return;
    const equipmentId = rows[0].resource_id;
    let alive = true;
    api.post<{ results: Array<{ equipment_id: number; operators: Array<{ person_id: number; person_name?: string | null }> }> }>(
      '/api/equipment/default-operators', { equipment_ids: [equipmentId] })
      .then((res) => {
        if (!alive) return;
        const ops = res.data.results.find((x) => x.equipment_id === equipmentId)?.operators ?? [];
        setComboOperators(ops);
        setComboChecked(new Set(ops.map((o) => o.person_id)));
      })
      .catch(() => { if (alive) { setComboOperators([]); setComboChecked(new Set()); } });
    return () => { alive = false; };
  }, [comboMode, comboEligible, rows]);

  const setRate = (rowKey: string, key: keyof RateRow, val: string) =>
    setRates((prev) => ({ ...prev, [rowKey]: { ...prev[rowKey], [key]: val } }));

  const setOpRate = (personId: number, key: keyof RateRow, val: string) =>
    setOpRates((prev) => ({
      ...prev,
      [personId]: { ...(prev[personId] ?? { daily: '', monthly: '', ot: '', night: '' }), [key]: val },
    }));

  const toggleOperator = (personId: number) => setComboChecked((prev) => {
    const next = new Set(prev);
    if (next.has(personId)) next.delete(personId); else next.add(personId);
    return next;
  });

  const num = (s: string) => (s && Number(s) > 0 ? Number(s) : null);

  // 기배차 경고(차단 아님) — 이미 진행 중(수락 대기/현장 운영) 투입 요청이 있는 선택 자원.
  // 판단은 이미 로드된 보낸 요청 목록(/api/field-deployments/supplier) 재사용 — 추가 API 없음.
  const inProgressKeys = useMemo(() => new Set(
    sentRows.filter((s) => s.status === 'REQUESTED' || s.status === 'ACTIVE')
      .map((s) => `${s.resource_type}:${s.resource_id}`)), [sentRows]);
  const inProgressLabels = useMemo(() => {
    const labels: string[] = [];
    for (const r of rows) {
      if (inProgressKeys.has(`${r.resource_type}:${r.resource_id}`)) labels.push(r.resource_label);
    }
    if (comboMode) {
      for (const o of comboOperators) {
        if (comboChecked.has(o.person_id) && inProgressKeys.has(`PERSON:${o.person_id}`)) {
          labels.push(o.person_name ?? `인원 #${o.person_id}`);
        }
      }
    }
    return labels;
  }, [rows, comboMode, comboOperators, comboChecked, inProgressKeys]);

  // 통계 — 일대/월대 건수 + 합계
  const stats = useMemo(() => {
    let dailyCount = 0, monthlyCount = 0, dailySum = 0, monthlySum = 0;
    for (const r of rows) {
      const rt = rates[r.key]; if (!rt) continue;
      const d = num(rt.daily), m = num(rt.monthly);
      if (d) { dailyCount++; dailySum += d; }
      if (m) { monthlyCount++; monthlySum += m; }
    }
    return { dailyCount, monthlyCount, dailySum, monthlySum };
  }, [rows, rates]);

  const missingBp = rows.filter((r) => !bpOf(r));

  // R3 조합 발송 — combo API 1회 호출(단일 트랜잭션·전 행 combo_equipment_id 스냅샷).
  const submitCombo = async () => {
    const r = rows[0];
    const bpId = r ? bpOf(r) : null;
    if (!bpId) { toast.error('받는 BP사를 선택하세요'); return; }
    const opIds = comboOperators.filter((o) => comboChecked.has(o.person_id)).map((o) => o.person_id);
    const rt = rates[r.key] ?? { daily: '', monthly: '', ot: '', night: '' };
    setBusy(true);
    try {
      const res = await api.post<FieldDeploymentResponse[]>('/api/field-deployments/combo', {
        bp_company_id: bpId,
        equipment_id: r.resource_id,
        operator_person_ids: opIds,
        target_site_id: targetSiteId === '' ? null : targetSiteId,
        start_date: startDate || null,
        note: note || null,
        equipment_prices: {
          daily_price: num(rt.daily), monthly_price: num(rt.monthly),
          ot_price: num(rt.ot), night_price: num(rt.night),
        },
        operator_prices: opIds.map((pid) => {
          const or = opRates[pid] ?? { daily: '', monthly: '', ot: '', night: '' };
          return {
            person_id: pid,
            daily_price: num(or.daily), monthly_price: num(or.monthly),
            ot_price: num(or.ot), night_price: num(or.night),
          };
        }),
      });
      toast.success(`조합 투입 요청 ${res.data.length}건 발송`);
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '발송 실패');
    } finally { setBusy(false); }
  };

  const submit = async () => {
    if (rows.length === 0) return;
    const sendable = rows.filter((r) => bpOf(r));
    if (sendable.length === 0) { toast.error('받는 BP사를 선택하세요'); return; }
    setBusy(true);
    const results = await Promise.allSettled(
      sendable.map((r) => {
        const rt = rates[r.key] ?? { daily: '', monthly: '', ot: '', night: '' };
        return api.post('/api/field-deployments', {
          bp_company_id: bpOf(r),
          resource_type: r.resource_type,
          resource_id: r.resource_id,
          target_site_id: targetSiteId === '' ? null : targetSiteId,
          start_date: startDate || null,
          note: note || null,
          daily_price: num(rt.daily),
          monthly_price: num(rt.monthly),
          ot_price: num(rt.ot),
          night_price: num(rt.night),
        });
      }),
    );
    setBusy(false);
    const ok = results.filter((x) => x.status === 'fulfilled').length;
    const fail = results.length - ok;
    if (ok > 0) {
      toast.success(`${ok}건 발송됨`
        + (fail > 0 ? ` · ${fail}건 실패` : '')
        + (missingBp.length ? ` · BP없음 ${missingBp.length}건 제외` : ''));
      onSaved();
    } else {
      toast.error('발송 실패');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-3xl max-h-[90vh] flex flex-col">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">현장 투입 요청 — {rows.length}건</h3>
        </div>
        <div className="px-5 py-4 space-y-4 text-sm overflow-y-auto">
          {/* R3 조합 모드 — 장비 1건 선택 시에만 노출 */}
          {comboEligible && (
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-800 cursor-pointer">
              <input type="checkbox" checked={comboMode} onChange={(e) => setComboMode(e.target.checked)} />
              <span>조합으로 요청</span>
              <span className="font-normal text-xs text-slate-500">장비+교대조 조종원 일괄</span>
            </label>
          )}

          {/* 기배차 경고 — 차단 없음(이중 계상 노출 완화) */}
          {inProgressLabels.length > 0 && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
              이미 진행 중(수락 대기·현장 운영)인 투입 요청이 있는 자원입니다: {inProgressLabels.join(', ')}.
              이중 요청 여부를 확인하세요.
            </div>
          )}

          {/* 받는 BP사 — 배차 이력 없는 보유 자원 포함 시(배차행은 자기 BP 유지, 빈 자원만 이 값 적용) */}
          {needsBpPick && (
            <div>
              <label className="text-xs font-semibold text-slate-500">
                받는 BP사 <span className="font-normal text-slate-400">— 배차 이력 없는 자원에 적용</span>
              </label>
              <select value={fallbackBp} onChange={(e) => setFallbackBp(e.target.value ? Number(e.target.value) : '')}
                      className="input mt-1 w-full">
                <option value="">BP사 선택</option>
                {bpOptions.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
          )}

          {/* 공통 */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-500">투입 현장 (희망)</label>
              <select value={targetSiteId} onChange={(e) => setTargetSiteId(e.target.value ? Number(e.target.value) : '')}
                      className="input mt-1 w-full">
                <option value="">미지정 (BP 가 배치)</option>
                {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-500">시작 예정일</label>
              <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="input mt-1 w-full" />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-500">공통 메모</label>
              <input value={note} onChange={(e) => setNote(e.target.value)} className="input mt-1 w-full" placeholder="예: 5/1 부터 투입" />
            </div>
          </div>

          {/* 통계 — 일대/월대 */}
          <div className="flex flex-wrap gap-4 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
            <span>일대 <b className="text-slate-900">{stats.dailyCount}</b>건 · 합계 <b className="text-slate-900">{stats.dailySum.toLocaleString()}</b>원</span>
            <span>월대 <b className="text-slate-900">{stats.monthlyCount}</b>건 · 합계 <b className="text-slate-900">{stats.monthlySum.toLocaleString()}</b>원</span>
            {missingBp.length > 0 && <span className="text-rose-600">받는 BP사 미지정 {missingBp.length}건 (발송 제외)</span>}
          </div>

          {/* 자원별 단가 */}
          <div className="space-y-2">
            {rows.map((r) => {
              const rt = rates[r.key] ?? { daily: '', monthly: '', ot: '', night: '' };
              const bpId = bpOf(r);
              const noBp = !bpId;
              const bpLabel = r.bp_company_name
                ?? (r.bp_company_id != null ? '#' + r.bp_company_id
                  : bpId != null ? (bpOptions.find((c) => c.id === bpId)?.name ?? '#' + bpId)
                  : 'BP사 선택 필요');
              return (
                <div key={r.key} className={`rounded-lg border p-2.5 ${noBp ? 'border-rose-200 bg-rose-50/40' : 'border-slate-200'}`}>
                  <div className="font-semibold text-slate-800 text-sm mb-1.5">
                    <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                    {r.resource_label}
                    <span className="ml-2 text-xs text-slate-400">→ {bpLabel}</span>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    <RateField label="일대" value={rt.daily} onChange={(v) => setRate(r.key, 'daily', v)} />
                    <RateField label="월대" value={rt.monthly} onChange={(v) => setRate(r.key, 'monthly', v)} />
                    <RateField label="OT" value={rt.ot} onChange={(v) => setRate(r.key, 'ot', v)} />
                    <RateField label="야간" value={rt.night} onChange={(v) => setRate(r.key, 'night', v)} />
                  </div>
                </div>
              );
            })}
          </div>

          {/* R3 조합(교대조) 조종원 — 요청 대상 개별 해제 + 행별 단가 */}
          {comboMode && (
            <div className="rounded-lg border border-indigo-200 bg-indigo-50/40 px-3 py-2 space-y-2">
              <span className="text-xs font-semibold text-slate-500">조합(교대조) 조종원 — 요청 대상 선택 · 행별 단가</span>
              {comboOperators.length === 0 ? (
                <div className="text-xs text-slate-400">이 장비에 등록된 조종원이 없습니다 (장비만 요청).</div>
              ) : comboOperators.map((o) => {
                const or = opRates[o.person_id] ?? { daily: '', monthly: '', ot: '', night: '' };
                const checked = comboChecked.has(o.person_id);
                return (
                  <div key={o.person_id} className="rounded-lg border border-slate-200 bg-white p-2.5">
                    <label className="flex items-center gap-2 text-sm font-semibold text-slate-800 cursor-pointer">
                      <input type="checkbox" checked={checked} onChange={() => toggleOperator(o.person_id)} />
                      <span className="text-[10px] font-normal text-slate-500">인원</span>
                      <span>{o.person_name ?? `인원 #${o.person_id}`}</span>
                    </label>
                    {checked && (
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mt-1.5">
                        <RateField label="일대" value={or.daily} onChange={(v) => setOpRate(o.person_id, 'daily', v)} />
                        <RateField label="월대" value={or.monthly} onChange={(v) => setOpRate(o.person_id, 'monthly', v)} />
                        <RateField label="OT" value={or.ot} onChange={(v) => setOpRate(o.person_id, 'ot', v)} />
                        <RateField label="야간" value={or.night} onChange={(v) => setOpRate(o.person_id, 'night', v)} />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={comboMode ? submitCombo : submit} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '발송 중…'
              : comboMode ? `조합 발송 (장비 1 + 조종원 ${comboChecked.size})`
              : `${rows.length}건 발송`}
          </button>
        </div>
      </div>
    </div>
  );
}

function RateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block">
      <span className="text-[11px] text-slate-500">{label}</span>
      <MoneyInput className="input mt-0.5 w-full text-sm" value={value} showKorean={false}
                  onChange={(v) => onChange(v === '' ? '' : String(v))} />
    </label>
  );
}
