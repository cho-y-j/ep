import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import type { DeployCheckResult } from '../readiness/DeployCheckCard';

type ResourceType = 'EQUIPMENT' | 'PERSON';
type Readiness = { resource_type: ResourceType; resource_id: number; label: string; ready: boolean; pending: string[] };
type OnboardMode = 'REQUESTED' | 'VERBAL';

type Onboarding = {
  id: number;
  owner_type: ResourceType;
  owner_id: number;
  owner_label: string;
  site_name?: string | null;
  bp_company_name?: string | null;
  mode: 'REQUESTED' | 'APPROVED' | 'VERBAL';
  inspection_date?: string | null;
  education_date?: string | null;
  health_date?: string | null;
  verbal_approver?: string | null;
  requested_at?: string | null;
  approved_at?: string | null;
};

type Option = { id: number; name: string };

const MODE_LABEL: Record<Onboarding['mode'], { text: string; cls: string }> = {
  REQUESTED: { text: '소급 승인 대기', cls: 'bg-amber-100 text-amber-800' },
  APPROVED: { text: '소급 승인', cls: 'bg-emerald-100 text-emerald-800' },
  VERBAL: { text: '구두승인', cls: 'bg-blue-100 text-blue-800' },
};

export default function SupplierOnboardingPage() {
  const [resources, setResources] = useState<Readiness[]>([]);
  const [history, setHistory] = useState<Onboarding[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bpOptions, setBpOptions] = useState<Option[]>([]);
  const [siteOptions, setSiteOptions] = useState<Option[]>([]);

  const [mode, setMode] = useState<OnboardMode>('REQUESTED');
  const [siteId, setSiteId] = useState<number | ''>('');
  const [siteName, setSiteName] = useState('');
  const [bpId, setBpId] = useState<number | ''>('');
  const [inspectionDate, setInspectionDate] = useState('');
  const [educationDate, setEducationDate] = useState('');
  const [healthDate, setHealthDate] = useState('');
  const [verbalApprover, setVerbalApprover] = useState('');
  const [memo, setMemo] = useState('');
  const [busy, setBusy] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [res, hist] = await Promise.all([
        api.get<Readiness[]>('/api/resources/readiness').then((r) => r.data).catch(() => []),
        api.get<Onboarding[]>('/api/resource-onboardings/supplier').then((r) => r.data).catch(() => []),
      ]);
      setResources(res ?? []);
      setHistory(hist ?? []);
      setSelected(new Set());
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);
  useEffect(() => {
    api.get<Option[]>('/api/companies/bp-list').then((r) => setBpOptions(r.data ?? [])).catch(() => setBpOptions([]));
    api.get<Option[]>('/api/sites').then((r) => setSiteOptions((r.data ?? []).map((s) => ({ id: s.id, name: s.name })))).catch(() => setSiteOptions([]));
  }, []);

  const key = (r: Readiness) => `${r.resource_type}:${r.resource_id}`;

  // L3: 현장 선택 시 그 현장 기준 사전판정으로 배지 보강 (미선택이면 아래 generic readiness 사용).
  const [deployMap, setDeployMap] = useState<Record<string, DeployCheckResult>>({});
  useEffect(() => {
    if (siteId === '' || resources.length === 0) { setDeployMap({}); return; }
    let cancelled = false;
    (async () => {
      const entries = await Promise.all(resources.map(async (r) => {
        const owner = r.resource_type === 'EQUIPMENT' ? 'equipment' : 'person';
        try {
          const res = await api.get<DeployCheckResult>(`/api/resources/${owner}/${r.resource_id}/deploy-check`, { params: { siteId } });
          return [key(r), res.data] as const;
        } catch {
          return null;
        }
      }));
      if (cancelled) return;
      const m: Record<string, DeployCheckResult> = {};
      for (const e of entries) if (e) m[e[0]] = e[1];
      setDeployMap(m);
    })();
    return () => { cancelled = true; };
  }, [siteId, resources]);

  const toggle = (k: string) => setSelected((p) => {
    const n = new Set(p); n.has(k) ? n.delete(k) : n.add(k); return n;
  });
  const selectedList = useMemo(() => resources.filter((r) => selected.has(key(r))), [resources, selected]);

  const submit = async () => {
    if (selectedList.length === 0) { toast.error('자원을 하나 이상 선택하세요'); return; }
    if (mode === 'REQUESTED' && bpId === '') { toast.error('BP 승인 요청에는 BP사를 선택하세요'); return; }
    setBusy(true);
    const results = await Promise.allSettled(selectedList.map((r) => api.post('/api/resource-onboardings', {
      owner_type: r.resource_type,
      owner_id: r.resource_id,
      site_id: siteId === '' ? null : siteId,
      site_name: siteId === '' ? (siteName.trim() || null) : null,
      bp_company_id: bpId === '' ? null : bpId,
      inspection_date: inspectionDate || null,
      education_date: educationDate || null,
      health_date: healthDate || null,
      mode,
      verbal_approver: mode === 'VERBAL' ? (verbalApprover.trim() || null) : null,
      memo: memo.trim() || null,
    })));
    setBusy(false);
    const ok = results.filter((x) => x.status === 'fulfilled').length;
    const fail = results.length - ok;
    if (ok > 0) {
      toast.success(`${ok}건 ${mode === 'VERBAL' ? '구두승인 기록(확정)' : 'BP 승인 요청'} 완료` + (fail > 0 ? ` · ${fail}건 실패` : ''));
      void load();
    } else {
      toast.error('처리에 실패했습니다');
    }
  };

  return (
    <AppShell breadcrumb={[{ label: '기투입 등록' }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">기투입 등록 (기통과 소급)</h1>
          <p className="mt-1 text-sm text-slate-500">
            이미 현장에 투입 중인 장비·인력을 선택해 소급 처리합니다. BP가 프로그램을 쓰면 <b>BP 승인 요청</b>,
            아니면 <b>구두승인</b>으로 즉시 확정할 수 있어요. 확정되면 반입검사·건강검진·안전교육 통과가 자동 반영됩니다.
          </p>
        </header>

        {/* 자원 선택 */}
        <section>
          <h2 className="font-bold text-slate-900 mb-2">자원 선택 ({selectedList.length}건 선택됨)</h2>
          {loading ? (
            <div className="text-sm text-slate-400">불러오는 중…</div>
          ) : resources.length === 0 ? (
            <div className="card p-8 text-center text-sm text-slate-400">
              등록된 장비·인력이 없습니다. 먼저 자원을 등록하세요.
            </div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {resources.map((r) => {
                const k = key(r);
                const on = selected.has(k);
                return (
                  <button key={k} type="button" onClick={() => toggle(k)}
                          className={`text-left rounded-xl border p-3 transition-colors ${on ? 'border-brand-500 bg-brand-50/50 ring-1 ring-brand-200' : 'border-slate-200 bg-white hover:border-slate-300'}`}>
                    <div className="flex items-center gap-2">
                      <input type="checkbox" checked={on} readOnly className="pointer-events-none" />
                      <span className="text-[10px] text-slate-500">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      <span className="font-semibold text-slate-800 truncate">{r.label}</span>
                    </div>
                    <div className="mt-1.5">
                      {(() => {
                        const dc = deployMap[k];
                        // 현장 선택 시: 그 현장 기준 판정 우선. 미선택 시: generic readiness.
                        if (dc) {
                          return dc.ready
                            ? <span className="text-[11px] text-emerald-600 font-semibold">이 현장 투입 가능</span>
                            : <span className="text-[11px] text-amber-600">{dc.blocks.map((b) => b.label).join(' · ')}</span>;
                        }
                        return r.ready
                          ? <span className="text-[11px] text-emerald-600 font-semibold">투입 준비 완료</span>
                          : <span className="text-[11px] text-amber-600">{r.pending.join(' · ')}</span>;
                      })()}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </section>

        {/* 등록 정보 */}
        <section className="card p-5 space-y-4">
          <h2 className="font-bold text-slate-900">등록 정보</h2>

          <div>
            <span className="text-xs font-semibold text-slate-500">승인 방식 <span className="text-rose-500">*</span></span>
            <div className="mt-1 flex gap-2">
              <ModeBtn active={mode === 'REQUESTED'} onClick={() => setMode('REQUESTED')}
                       title="BP 승인 요청" desc="BP가 프로그램에서 일괄 승인" />
              <ModeBtn active={mode === 'VERBAL'} onClick={() => setMode('VERBAL')}
                       title="구두승인 기록" desc="공급사 단독 · 즉시 확정" />
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">현장</span>
              <select value={siteId} onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : '')} className="input mt-1 w-full">
                <option value="">현장 미지정 (직접 입력)</option>
                {siteOptions.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
              {siteId === '' && (
                <input value={siteName} onChange={(e) => setSiteName(e.target.value)} className="input mt-1.5 w-full" placeholder="현장명 직접 입력 (선택)" />
              )}
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">
                BP사 {mode === 'REQUESTED' && <span className="text-rose-500">*</span>}
                {mode === 'VERBAL' && <span className="text-slate-400"> (선택)</span>}
              </span>
              <select value={bpId} onChange={(e) => setBpId(e.target.value ? Number(e.target.value) : '')} className="input mt-1 w-full">
                <option value="">{mode === 'REQUESTED' ? 'BP사 선택' : 'BP사 선택 안 함'}</option>
                {bpOptions.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </label>
          </div>

          <div>
            <span className="text-xs font-semibold text-slate-500">완료일 (선택 — 적으면 재검·재검진 주기가 그날부터 이어집니다)</span>
            <div className="mt-1 grid grid-cols-1 sm:grid-cols-3 gap-2">
              <DateField label="반입검사일 (장비)" value={inspectionDate} onChange={setInspectionDate} />
              <DateField label="안전교육일 (인원)" value={educationDate} onChange={setEducationDate} />
              <DateField label="건강검진일 (인원)" value={healthDate} onChange={setHealthDate} />
            </div>
          </div>

          {mode === 'VERBAL' && (
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">구두승인자명</span>
              <input value={verbalApprover} onChange={(e) => setVerbalApprover(e.target.value)} className="input mt-1 w-full" placeholder="예: 센코어테크 김소장" />
            </label>
          )}

          <label className="block">
            <span className="text-xs font-semibold text-slate-500">메모</span>
            <input value={memo} onChange={(e) => setMemo(e.target.value)} className="input mt-1 w-full" placeholder="분쟁 대비 기록 (선택)" />
          </label>

          <div className="flex justify-end">
            <button onClick={submit} disabled={busy || selectedList.length === 0} className="btn-primary disabled:opacity-50">
              {busy ? '처리 중…' : mode === 'VERBAL' ? `${selectedList.length}건 구두승인 기록` : `${selectedList.length}건 BP 승인 요청`}
            </button>
          </div>
        </section>

        {/* 신고 이력 */}
        <section>
          <h2 className="font-bold text-slate-900 mb-2">내 신고 이력 ({history.length})</h2>
          {history.length === 0 ? (
            <div className="card p-6 text-center text-xs text-slate-400">아직 등록한 기투입 건이 없습니다.</div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {history.map((h) => (
                <div key={h.id} className="card p-3.5 flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="font-semibold text-slate-800 truncate">
                      <span className="text-[10px] text-slate-500 mr-1">{h.owner_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      {h.owner_label}
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500">
                      {h.site_name ?? '현장 미지정'}{h.bp_company_name ? ` · ${h.bp_company_name}` : ''}
                      {h.verbal_approver ? ` · 승인자 ${h.verbal_approver}` : ''}
                    </div>
                  </div>
                  <span className={`shrink-0 px-2 py-0.5 rounded text-[11px] font-semibold ${MODE_LABEL[h.mode].cls}`}>
                    {MODE_LABEL[h.mode].text}
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

function ModeBtn({ active, onClick, title, desc }: { active: boolean; onClick: () => void; title: string; desc: string }) {
  return (
    <button type="button" onClick={onClick}
            className={`flex-1 text-left rounded-lg border p-2.5 ${active ? 'border-brand-500 bg-brand-50/60 ring-1 ring-brand-200' : 'border-slate-200 bg-white'}`}>
      <div className="text-sm font-semibold text-slate-800">{title}</div>
      <div className="text-[11px] text-slate-500">{desc}</div>
    </button>
  );
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block">
      <span className="text-[11px] text-slate-500">{label}</span>
      <input type="date" value={value} onChange={(e) => onChange(e.target.value)} className="input mt-0.5 w-full text-sm" />
    </label>
  );
}
