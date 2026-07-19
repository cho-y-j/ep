import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';

type ResourceType = 'EQUIPMENT' | 'PERSON';
type Onboarding = {
  id: number;
  supplier_company_name?: string | null;
  owner_type: ResourceType;
  owner_id: number;
  owner_label: string;
  site_name?: string | null;
  mode: 'REQUESTED' | 'APPROVED' | 'VERBAL';
  inspection_date?: string | null;
  education_date?: string | null;
  health_date?: string | null;
  verbal_approver?: string | null;
  requested_at?: string | null;
  approved_at?: string | null;
};

const MODE_LABEL: Record<Onboarding['mode'], { text: string; cls: string }> = {
  REQUESTED: { text: '소급 승인 대기', cls: 'bg-amber-100 text-amber-800' },
  APPROVED: { text: '소급 승인', cls: 'bg-emerald-100 text-emerald-800' },
  VERBAL: { text: '구두승인', cls: 'bg-blue-100 text-blue-800' },
};

function dates(o: Onboarding): string {
  const parts: string[] = [];
  if (o.inspection_date) parts.push(`반입검사 ${o.inspection_date}`);
  if (o.education_date) parts.push(`교육 ${o.education_date}`);
  if (o.health_date) parts.push(`검진 ${o.health_date}`);
  return parts.join(' · ');
}

export default function BpOnboardingApprovalPage() {
  const [rows, setRows] = useState<Onboarding[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  // 클라이언트 필터 — 두 섹션(대기·완료) 카드를 좁힘. 선택·일괄승인은 보이는 대기 건 기준.
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<Onboarding[]>('/api/resource-onboardings/bp');
      setRows(data ?? []);
      setSelected(new Set());
    } catch {
      setRows([]);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const pending = useMemo(() => rows.filter((r) => r.mode === 'REQUESTED'), [rows]);
  const done = useMemo(() => rows.filter((r) => r.mode !== 'REQUESTED'), [rows]);

  const qLower = q.trim().toLowerCase();
  const match = (r: Onboarding) => {
    if (typeFilter && r.owner_type !== typeFilter) return false;
    if (qLower) {
      const hay = `${r.owner_label} ${r.supplier_company_name ?? ''} ${r.site_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  };
  const filteredPending = pending.filter(match);
  const filteredDone = done.filter(match);
  const activeFilterCount = [q, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setTypeFilter(''); };

  const toggle = (id: number) => setSelected((p) => { const n = new Set(p); n.has(id) ? n.delete(id) : n.add(id); return n; });
  // 선택·일괄승인은 보이는(필터된) 대기 건 기준으로만 동작.
  const selectedPending = filteredPending.filter((r) => selected.has(r.id));
  const allSelected = filteredPending.length > 0 && filteredPending.every((r) => selected.has(r.id));
  const toggleAll = () => setSelected((p) => {
    const allSel = filteredPending.length > 0 && filteredPending.every((r) => p.has(r.id));
    const n = new Set(p);
    if (allSel) filteredPending.forEach((r) => n.delete(r.id)); else filteredPending.forEach((r) => n.add(r.id));
    return n;
  });

  const approve = async () => {
    const ids = selectedPending.map((r) => r.id);
    if (ids.length === 0) { toast.error('승인할 건을 선택하세요'); return; }
    setBusy(true);
    const results = await Promise.allSettled(ids.map((id) => api.post(`/api/resource-onboardings/${id}/approve`, {})));
    setBusy(false);
    const ok = results.filter((x) => x.status === 'fulfilled').length;
    const fail = results.length - ok;
    if (ok > 0) {
      toast.success(`${ok}건 소급 승인 완료` + (fail > 0 ? ` · ${fail}건 실패` : ''));
      void load();
    } else {
      toast.error('승인에 실패했습니다');
    }
  };

  return (
    <AppShell breadcrumb={[{ label: '소급 승인' }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <PageHeader
          title="기투입 소급 승인"
          subtitle="공급사가 이미 투입 중이라고 신고한 자원입니다. 확인 후 일괄 승인하면 반입검사·건강검진·안전교육 통과가 자동 반영됩니다."
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원·공급사·현장 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="자원종류 전체"
            options={[{ value: 'EQUIPMENT', label: '장비' }, { value: 'PERSON', label: '인원' }]} />
        </FilterBar>

        <section>
          <div className="flex items-center justify-between mb-2">
            <h2 className="font-bold text-slate-900">승인 대기 ({pending.length})</h2>
            {pending.length > 0 && (
              <button onClick={toggleAll} className="text-xs text-slate-500 hover:text-slate-900">
                {allSelected ? '전체 해제' : '전체 선택'}
              </button>
            )}
          </div>

          {loading ? (
            <div className="text-sm text-slate-400">불러오는 중…</div>
          ) : pending.length === 0 ? (
            <div className="card p-8 text-center">
              <div className="text-3xl mb-1">✅</div>
              <div className="font-semibold text-slate-700">승인 대기 중인 건이 없습니다</div>
              <p className="mt-1 text-sm text-slate-400">공급사가 기투입 소급 요청을 보내면 여기에 표시됩니다.</p>
            </div>
          ) : filteredPending.length === 0 ? (
            <div className="card p-6 text-center text-xs text-slate-400">조건에 맞는 대기 건이 없습니다.</div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {filteredPending.map((r) => {
                const on = selected.has(r.id);
                return (
                  <button key={r.id} type="button" onClick={() => toggle(r.id)}
                          className={`text-left rounded-xl border p-3.5 transition-colors ${on ? 'border-brand-500 bg-brand-50/50 ring-1 ring-brand-200' : 'border-slate-200 bg-white hover:border-slate-300'}`}>
                    <div className="flex items-center gap-2">
                      <input type="checkbox" checked={on} readOnly className="pointer-events-none" />
                      <span className="text-[10px] text-slate-500">{r.owner_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      <span className="font-semibold text-slate-800 truncate">{r.owner_label}</span>
                      <span className={`ml-auto shrink-0 px-1.5 py-0.5 rounded text-[10px] font-semibold ${MODE_LABEL[r.mode].cls}`}>
                        {MODE_LABEL[r.mode].text}
                      </span>
                    </div>
                    <div className="mt-1 text-xs text-slate-500">
                      {(r.supplier_company_name ?? '공급사')} · {r.site_name ?? '현장 미지정'}
                    </div>
                    {dates(r) && <div className="mt-0.5 text-[11px] text-slate-400">{dates(r)}</div>}
                  </button>
                );
              })}
            </div>
          )}

          {selectedPending.length > 0 && (
            <div className="sticky bottom-3 mt-3 flex items-center justify-between gap-3 card px-4 py-2.5 shadow-lg border-brand-200">
              <div className="text-sm text-slate-600"><b className="text-slate-900">{selectedPending.length}</b>건 선택됨</div>
              <div className="flex gap-2">
                <button onClick={() => setSelected(new Set())} className="px-3 py-1.5 text-sm text-slate-500 hover:text-slate-900">선택 해제</button>
                <button onClick={approve} disabled={busy}
                        className="px-4 py-1.5 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50">
                  {busy ? '승인 중…' : `${selectedPending.length}건 일괄 승인`}
                </button>
              </div>
            </div>
          )}
        </section>

        <section>
          <h2 className="font-bold text-slate-900 mb-2">처리 완료 ({done.length})</h2>
          {done.length === 0 ? (
            <div className="card p-6 text-center text-xs text-slate-400">아직 승인/기록된 건이 없습니다.</div>
          ) : filteredDone.length === 0 ? (
            <div className="card p-6 text-center text-xs text-slate-400">조건에 맞는 건이 없습니다.</div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {filteredDone.map((r) => (
                <div key={r.id} className="card p-3.5 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="font-semibold text-slate-800 truncate">
                      <span className="text-[10px] text-slate-500 mr-1">{r.owner_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      {r.owner_label}
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500">
                      {(r.supplier_company_name ?? '공급사')} · {r.site_name ?? '현장 미지정'}
                      {r.verbal_approver ? ` · 승인자 ${r.verbal_approver}` : ''}
                    </div>
                  </div>
                  <span className={`shrink-0 px-2 py-0.5 rounded text-[11px] font-semibold ${MODE_LABEL[r.mode].cls}`}>
                    {MODE_LABEL[r.mode].text}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </AppShell>
  );
}
