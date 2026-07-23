import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect, useTableSort } from '../../components/ui';
import ConfirmDialog from '../../components/ConfirmDialog';
import type { FieldDeploymentResponse } from '../../types/fieldDeployment';

export default function FieldDeploymentBpInbox() {
  const [items, setItems] = useState<FieldDeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [acceptingFor, setAcceptingFor] = useState<FieldDeploymentResponse | null>(null);
  const [rejectingFor, setRejectingFor] = useState<FieldDeploymentResponse | null>(null);
  // R3: [조합 일괄 수락] 대상 그룹(같은 combo_equipment_id 행들).
  const [comboAccepting, setComboAccepting] = useState<FieldDeploymentResponse[] | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkConfirm, setBulkConfirm] = useState(false);
  const [bulkBusy, setBulkBusy] = useState(false);
  // 클라이언트 필터 — 로드된 요청을 좁힘. 선택·일괄수락은 보이는(필터된) 행 기준.
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [siteFilter, setSiteFilter] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const r = await api.get<FieldDeploymentResponse[]>('/api/field-deployments/bp');
      setItems(r.data.filter((x) => x.status === 'REQUESTED'));
      setSelected(new Set());
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };
  const siteOptions = useMemo(() => {
    const m = new Map<string, string>();
    items.forEach((r) => { if (r.target_site_name) m.set(r.target_site_name, r.target_site_name); });
    return [...m.values()].map((v) => ({ value: v, label: v }));
  }, [items]);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => items.filter((r) => {
    if (typeFilter && r.resource_type !== typeFilter) return false;
    if (siteFilter && (r.target_site_name ?? '') !== siteFilter) return false;
    if (qLower) {
      const hay = `${r.resource_label ?? ''} ${r.supplier_company_name ?? ''} ${r.target_site_name ?? ''} ${r.note ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [items, typeFilter, siteFilter, qLower]);

  const activeFilterCount = [q, typeFilter, siteFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setTypeFilter(''); setSiteFilter(''); };

  // R3 조합 묶음 — 같은 combo_equipment_id 행을 장비 라벨 헤더 카드로 묶음(첫 등장 순).
  // combo_equipment_id 없는 행(singles)은 기존 테이블 렌더 그대로(무회귀).
  const comboGroups = useMemo(() => {
    const groups: FieldDeploymentResponse[][] = [];
    const seen = new Set<number>();
    for (const r of filtered) {
      if (r.combo_equipment_id == null || seen.has(r.combo_equipment_id)) continue;
      seen.add(r.combo_equipment_id);
      groups.push(filtered.filter((x) => x.combo_equipment_id === r.combo_equipment_id));
    }
    return groups;
  }, [filtered]);
  const singles = useMemo(() => filtered.filter((r) => r.combo_equipment_id == null), [filtered]);
  const sort = useTableSort<'resource' | 'supplier' | 'site' | 'start' | 'note'>();
  const sortedSingles = sort.apply(singles, (r, key) => {
    switch (key) {
      case 'resource': return r.resource_label;
      case 'supplier': return r.supplier_company_name;
      case 'site': return r.target_site_name;
      case 'start': return r.start_date;
      case 'note': return r.note;
    }
  });

  // 선택/일괄수락은 보이는(필터된) 단독 행 기준으로만 동작 — 조합 행은 [조합 일괄 수락] 이 담당.
  const allSelected = singles.length > 0 && singles.every((r) => selected.has(r.id));
  const toggleAll = () => {
    setSelected((prev) => {
      const allSel = singles.length > 0 && singles.every((r) => prev.has(r.id));
      const next = new Set(prev);
      if (allSel) singles.forEach((r) => next.delete(r.id)); else singles.forEach((r) => next.add(r.id));
      return next;
    });
  };

  // 희망 현장(target_site_id)이 있는 선택 건만 일괄 수락 대상. 없는 건은 개별 "수락 + 배치" 필요.
  const selectedRows = singles.filter((r) => selected.has(r.id));
  const bulkAcceptable = selectedRows.filter((r) => r.target_site_id != null);
  const bulkExcluded = selectedRows.filter((r) => r.target_site_id == null);

  const openBulkConfirm = () => {
    if (bulkAcceptable.length === 0) {
      toast.error('희망 현장이 지정된 요청이 없습니다. 개별로 "수락 + 배치" 하세요.');
      return;
    }
    setBulkConfirm(true);
  };

  const runBulkAccept = async () => {
    setBulkBusy(true);
    let ok = 0;
    const failed: string[] = [];
    for (const r of bulkAcceptable) {
      try {
        await api.post(`/api/field-deployments/${r.id}/accept`, { note: null, target_site_id: r.target_site_id });
        ok++;
      } catch {
        failed.push(r.resource_label ?? '#' + r.id);
      }
    }
    setBulkBusy(false);
    setBulkConfirm(false);
    if (failed.length) toast.error(`${ok}건 수락, ${failed.length}건 실패: ${failed.join(', ')}`);
    else toast.success(`${ok}건 일괄 수락 완료`);
    void load();
  };

  return (
    <AppShell breadcrumb={[{ label: '받은 투입 요청' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <PageHeader
          title="받은 투입 요청"
          subtitle='공급사가 "현장으로 보낼게요" 요청한 자원입니다. 수락하면 요청이 수락 처리되고 공급사에 알림이 전송됩니다. 실제 투입 현황은 작업계획서(서명 완료) 기준으로 반영됩니다.'
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원·공급사·현장 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="자원종류 전체"
            options={[{ value: 'EQUIPMENT', label: '장비' }, { value: 'PERSON', label: '인원' }]} />
          {siteOptions.length > 0 && (
            <FilterSelect value={siteFilter} onChange={setSiteFilter} placeholder="희망 현장 전체" options={siteOptions} />
          )}
        </FilterBar>

        {selectedRows.length > 0 && (
          <div className="card p-3 flex items-center justify-between border-amber-200 bg-amber-50/60">
            <span className="text-sm text-amber-800 font-medium">{selectedRows.length}건 선택됨</span>
            <div className="flex gap-2">
              <button type="button" onClick={() => setSelected(new Set())}
                      className="px-3 py-1.5 text-sm text-slate-600 hover:text-slate-900">선택 해제</button>
              <button type="button" disabled={bulkBusy} onClick={openBulkConfirm}
                      className="px-3 py-1.5 text-sm rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                선택 일괄 수락
              </button>
            </div>
          </div>
        )}

        {loading ? <div className="text-sm text-slate-400">불러오는 중…</div>
         : items.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">받은 요청이 없습니다.</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">조건에 맞는 요청이 없습니다.</div>
        ) : (
          <>
            {/* R3 조합 묶음 카드 — 장비 라벨 헤더 + 행별 단가 + [조합 일괄 수락]. 개별 수락/반려 병존. */}
            {comboGroups.map((group) => {
              const head = group[0];
              return (
                <div key={`combo-${head.combo_equipment_id}`}
                     className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-2 space-y-2">
                  <div className="flex items-center gap-2 px-1">
                    <span className="px-1.5 py-0.5 text-[10px] rounded-full font-semibold bg-indigo-600 text-white">조합</span>
                    <span className="text-sm font-bold text-slate-900 truncate">
                      {head.combo_equipment_label ?? `장비 #${head.combo_equipment_id}`}
                    </span>
                    <span className="text-xs text-slate-500">투입 {group.length}건</span>
                    <span className="text-xs text-slate-500">· {head.supplier_company_name ?? '#' + head.supplier_company_id}</span>
                    <div className="ml-auto">
                      <button disabled={bulkBusy} onClick={() => setComboAccepting(group)}
                              className="px-3 py-1.5 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                        조합 일괄 수락
                      </button>
                    </div>
                  </div>
                  {group.map((r) => (
                    <div key={r.id} className="rounded-lg border border-slate-200 bg-white px-3 py-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
                      <span className="font-semibold">
                        <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                        {r.resource_label}
                      </span>
                      <span className="text-[11px] tabular-nums text-slate-500">단가(일/월/OT/야) {rateSummary(r)}</span>
                      <span className="text-xs text-slate-600">희망 현장 {r.target_site_name ?? '미지정'}</span>
                      <span className="text-xs tabular-nums text-slate-600">시작 {r.start_date ?? '-'}</span>
                      {r.note && <span className="text-xs text-slate-500 max-w-[200px] truncate">{r.note}</span>}
                      <div className="ml-auto flex gap-1">
                        <button disabled={bulkBusy} onClick={() => setAcceptingFor(r)}
                                className="px-2 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                          수락 + 배치
                        </button>
                        <button disabled={bulkBusy} onClick={() => setRejectingFor(r)}
                                className="px-2 py-1 text-xs rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                          반려
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              );
            })}

            {singles.length > 0 && (
            <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 w-8">
                    <input type="checkbox" checked={allSelected} onChange={toggleAll}
                           className="rounded border-slate-300" aria-label="전체 선택" />
                  </th>
                  <th className="px-3 py-2">{sort.header('resource', '자원')}</th>
                  <th className="px-3 py-2">{sort.header('supplier', '공급사')}</th>
                  <th className="px-3 py-2">{sort.header('site', '희망 현장')}</th>
                  <th className="px-3 py-2">{sort.header('start', '시작')}</th>
                  <th className="px-3 py-2">{sort.header('note', '메모')}</th>
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sortedSingles.map((r) => (
                  <tr key={r.id}>
                    <td className="px-3 py-2">
                      <input type="checkbox" checked={selected.has(r.id)} onChange={() => toggle(r.id)}
                             className="rounded border-slate-300" aria-label="선택" />
                    </td>
                    <td className="px-3 py-2 font-semibold">
                      <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      {r.resource_label}
                    </td>
                    <td className="px-3 py-2">{r.supplier_company_name ?? '#' + r.supplier_company_id}</td>
                    <td className="px-3 py-2 text-slate-700">{r.target_site_name ?? '미지정'}</td>
                    <td className="px-3 py-2 text-xs tabular-nums">{r.start_date ?? '-'}</td>
                    <td className="px-3 py-2 text-xs text-slate-500 max-w-[200px] truncate">{r.note ?? '-'}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      <div className="flex gap-1">
                        <button disabled={bulkBusy} onClick={() => setAcceptingFor(r)}
                                className="px-2 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                          수락 + 배치
                        </button>
                        <button disabled={bulkBusy} onClick={() => setRejectingFor(r)}
                                className="px-2 py-1 text-xs rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                          반려
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
            )}
          </>
        )}

        {acceptingFor && (
          <AcceptDialog row={acceptingFor} onClose={() => setAcceptingFor(null)}
                        onSaved={() => { setAcceptingFor(null); void load(); }} />
        )}

        {rejectingFor && (
          <RejectDialog row={rejectingFor} onClose={() => setRejectingFor(null)}
                        onSaved={() => { setRejectingFor(null); void load(); }} />
        )}

        {comboAccepting && (
          <ComboAcceptDialog group={comboAccepting} onClose={() => setComboAccepting(null)}
                             onSaved={() => { setComboAccepting(null); void load(); }} />
        )}

        <ConfirmDialog
          open={bulkConfirm}
          title="선택 일괄 수락"
          message={`${bulkAcceptable.length}건을 공급사 희망 현장으로 일괄 수락합니다.`
            + (bulkExcluded.length ? `\n희망 현장이 없는 ${bulkExcluded.length}건은 제외됩니다 — 개별로 "수락 + 배치" 하세요.` : '')}
          confirmLabel="일괄 수락"
          busy={bulkBusy}
          onConfirm={runBulkAccept}
          onCancel={() => setBulkConfirm(false)}
        />
      </div>
    </AppShell>
  );
}

function rateSummary(r: FieldDeploymentResponse): string {
  const vals = [r.daily_price, r.monthly_price, r.ot_price, r.night_price];
  if (vals.every((v) => v == null)) return '-';
  return vals.map((v) => (v != null ? v.toLocaleString() : '-')).join(' / ');
}

/** R3: 조합 일괄 수락 — accept-combo 1회(전건 REQUESTED·같은 combo, 위반 시 전체 롤백). */
function ComboAcceptDialog({ group, onClose, onSaved }:
  { group: FieldDeploymentResponse[]; onClose: () => void; onSaved: () => void }) {
  const head = group[0];
  const [targetSiteId, setTargetSiteId] = useState<number | ''>(head.target_site_id ?? '');
  const [note, setNote] = useState('');
  const [sites, setSites] = useState<Array<{ id: number; name: string }>>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<any[]>('/api/sites').then((r) => setSites((r.data ?? []).map((s) => ({ id: s.id, name: s.name }))))
      .catch(() => setSites([]));
  }, []);

  const submit = async () => {
    setBusy(true);
    try {
      await api.post('/api/field-deployments/accept-combo', {
        request_ids: group.map((r) => r.id),
        note: note || null,
        target_site_id: targetSiteId === '' ? null : targetSiteId,
      });
      toast.success(`조합 ${group.length}건 일괄 수락 — 현장 운영 시작`);
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '일괄 수락 실패');
    } finally { setBusy(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">조합 일괄 수락 — {group.length}건</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            {head.combo_equipment_label ?? `장비 #${head.combo_equipment_id}`} · {head.supplier_company_name ?? '#' + head.supplier_company_id}
          </p>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          <div className="card p-2 bg-slate-50 text-xs space-y-0.5">
            {group.map((r) => (
              <div key={r.id}>
                <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                <span className="font-semibold">{r.resource_label}</span>
                <span className="ml-2 tabular-nums text-slate-500">{rateSummary(r)}</span>
              </div>
            ))}
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">배치 현장 (전 건 공통)</label>
            <select value={targetSiteId} onChange={(e) => setTargetSiteId(e.target.value ? Number(e.target.value) : '')}
                    className="input mt-1 w-full">
              <option value="">공급사 희망 유지 (미지정 가능)</option>
              {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">메모 (선택)</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2}
                      className="input mt-1 w-full" />
          </div>
          <p className="text-[11px] text-slate-400">
            전 건이 함께 수락됩니다(1건이라도 이미 처리됐으면 전체 취소). 일부만 수락하려면 개별 "수락 + 배치"를 사용하세요.
          </p>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={submit} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '처리 중…' : `${group.length}건 일괄 수락`}
          </button>
        </div>
      </div>
    </div>
  );
}

function AcceptDialog({ row, onClose, onSaved }:
  { row: FieldDeploymentResponse; onClose: () => void; onSaved: () => void }) {
  const [targetSiteId, setTargetSiteId] = useState<number | ''>(row.target_site_id ?? '');
  const [note, setNote] = useState('');
  const [sites, setSites] = useState<Array<{ id: number; name: string }>>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<any[]>('/api/sites').then((r) => setSites((r.data ?? []).map((s) => ({ id: s.id, name: s.name }))))
      .catch(() => setSites([]));
  }, []);

  const submit = async () => {
    if (!targetSiteId) { toast.error('배치할 현장을 선택하세요'); return; }
    setBusy(true);
    try {
      await api.post(`/api/field-deployments/${row.id}/accept`, {
        note: note || null,
        target_site_id: targetSiteId,
      });
      toast.success('수락 — 현장 운영 시작');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '수락 실패');
    } finally { setBusy(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">투입 요청 수락 + 현장 배치</h3>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          <div className="card p-2 bg-slate-50 text-xs">
            <div>자원: <span className="font-semibold">{row.resource_label}</span> <span className="text-slate-400">({row.resource_type === 'EQUIPMENT' ? '장비' : '인원'})</span></div>
            <div>공급사: <span className="font-semibold">{row.supplier_company_name ?? '#' + row.supplier_company_id}</span></div>
            {row.target_site_name && (
              <div>공급사 희망: <span className="font-semibold">{row.target_site_name}</span></div>
            )}
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">배치 현장 *</label>
            <select value={targetSiteId} onChange={(e) => setTargetSiteId(e.target.value ? Number(e.target.value) : '')}
                    className="input mt-1 w-full" required>
              <option value="">선택</option>
              {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <p className="text-[10px] text-slate-400 mt-1">공급사 희망과 다른 현장으로 배치할 수 있습니다.</p>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">메모 (선택)</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2}
                      className="input mt-1 w-full" />
          </div>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={submit} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '처리 중…' : '수락 + 배치'}
          </button>
        </div>
      </div>
    </div>
  );
}

function RejectDialog({ row, onClose, onSaved }:
  { row: FieldDeploymentResponse; onClose: () => void; onSaved: () => void }) {
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      await api.post(`/api/field-deployments/${row.id}/reject`, { note });
      toast.success('반려됨');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '실패');
    } finally { setBusy(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">투입 요청 반려</h3>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          <div className="card p-2 bg-slate-50 text-xs">
            <div>자원: <span className="font-semibold">{row.resource_label}</span> <span className="text-slate-400">({row.resource_type === 'EQUIPMENT' ? '장비' : '인원'})</span></div>
            <div>공급사: <span className="font-semibold">{row.supplier_company_name ?? '#' + row.supplier_company_id}</span></div>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">반려 사유 (선택)</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2}
                      className="input mt-1 w-full" />
          </div>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={submit} disabled={busy}
                  className="px-3 py-1.5 text-sm rounded bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50">
            {busy ? '처리 중…' : '반려'}
          </button>
        </div>
      </div>
    </div>
  );
}
