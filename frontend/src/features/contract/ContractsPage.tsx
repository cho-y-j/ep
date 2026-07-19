import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import MoneyInput from '../../components/MoneyInput';
import { useAuth } from '../auth/AuthContext';

type RateType = 'DAILY' | 'MONTHLY';

type Contract = {
  id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  bp_company_id?: number | null;
  bp_company_name?: string | null;
  bp_name?: string | null;
  site_id?: number | null;
  site_name?: string | null;
  title?: string | null;
  equipment_desc?: string | null;
  rate_type: RateType;
  base_rate?: number | null;
  rate_early?: number | null;
  rate_lunch?: number | null;
  rate_evening?: number | null;
  rate_night?: number | null;
  rate_overnight?: number | null;
  start_date?: string | null;
  end_date?: string | null;
  has_file: boolean;
  memo?: string | null;
};

type Option = { id: number; name: string };

const OT_FIELDS: Array<{ key: keyof Contract; label: string }> = [
  { key: 'rate_early', label: '조출' },
  { key: 'rate_lunch', label: '점심' },
  { key: 'rate_evening', label: '연장' },
  { key: 'rate_night', label: '야간' },
  { key: 'rate_overnight', label: '철야' },
];

/** 계약 기간(start/end)과 오늘을 비교해 진행 상태 도출 — 서버 상태필드 없음(클라 파생). */
type ContractStatusKey = 'ACTIVE' | 'UPCOMING' | 'ENDED' | 'UNDATED';
function contractStatus(c: Contract): ContractStatusKey {
  if (!c.start_date && !c.end_date) return 'UNDATED';
  const today = new Date().toISOString().slice(0, 10);
  if (c.end_date && c.end_date < today) return 'ENDED';
  if (c.start_date && c.start_date > today) return 'UPCOMING';
  return 'ACTIVE';
}
const CONTRACT_STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '진행중' },
  { value: 'UPCOMING', label: '예정' },
  { value: 'ENDED', label: '종료' },
  { value: 'UNDATED', label: '기간미정' },
];

export default function ContractsPage() {
  const { user } = useAuth();
  const role = user?.role;
  const isSupplier = role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER';

  const [contracts, setContracts] = useState<Contract[]>([]);
  const [loading, setLoading] = useState(true);
  const [bpOptions, setBpOptions] = useState<Option[]>([]);
  const [siteOptions, setSiteOptions] = useState<Option[]>([]);
  const [editing, setEditing] = useState<Contract | 'new' | null>(null);
  // 클라이언트 필터 — 로드된 계약을 좁힘(백엔드 무접촉).
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [siteFilter, setSiteFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await api.get<Contract[]>('/api/contracts');
      setContracts(data ?? []);
    } catch {
      setContracts([]);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => { void load(); }, []);

  useEffect(() => {
    if (!isSupplier) return;
    api.get<Option[]>('/api/companies/bp-list').then((r) => setBpOptions(r.data ?? [])).catch(() => setBpOptions([]));
    api.get<Option[]>('/api/sites').then((r) => setSiteOptions((r.data ?? []).map((s) => ({ id: s.id, name: s.name })))).catch(() => setSiteOptions([]));
  }, [isSupplier]);

  const siteFilterOptions = useMemo(() => {
    const m = new Map<string, string>();
    contracts.forEach((c) => { if (c.site_name) m.set(c.site_name, c.site_name); });
    return [...m.values()].map((v) => ({ value: v, label: v }));
  }, [contracts]);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => contracts.filter((c) => {
    if (statusFilter && contractStatus(c) !== statusFilter) return false;
    if (siteFilter && (c.site_name ?? '') !== siteFilter) return false;
    if (dateFrom && c.end_date && c.end_date < dateFrom) return false;
    if (dateTo && c.start_date && c.start_date > dateTo) return false;
    if (qLower) {
      const hay = `${c.title ?? ''} ${c.equipment_desc ?? ''} ${c.supplier_company_name ?? ''} ${c.bp_company_name ?? ''} ${c.bp_name ?? ''} ${c.site_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [contracts, statusFilter, siteFilter, dateFrom, dateTo, qLower]);

  const activeFilterCount =
    [q, statusFilter, siteFilter].filter(Boolean).length + (dateFrom || dateTo ? 1 : 0);
  const resetFilters = () => {
    setQ(''); setStatusFilter(''); setSiteFilter(''); setDateFrom(''); setDateTo('');
  };

  return (
    <AppShell breadcrumb={[{ label: isSupplier ? '계약 관리' : '계약 조회' }]}>
      <div className="mx-auto max-w-6xl space-y-6">
        <PageHeader
          title={isSupplier ? '계약 관리' : '계약 조회'}
          subtitle={isSupplier
            ? '현장 소장과 맺은 계약을 등록해 두면 정산 단가의 기준이 됩니다. 견적 없이도 계약만 직접 등록할 수 있어요.'
            : '공급사가 우리 회사 앞으로 등록한 계약 단가를 확인합니다.'}
          actions={isSupplier
            ? <button onClick={() => setEditing('new')} className="btn-primary shrink-0">+ 새 계약 등록</button>
            : undefined}
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '업체·현장·제목 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체" options={CONTRACT_STATUS_OPTIONS} />
          <FilterSelect value={siteFilter} onChange={setSiteFilter} placeholder="현장 전체" options={siteFilterOptions} />
          <div className="flex items-center gap-1">
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="input w-auto" />
            <span className="text-xs text-slate-400">~</span>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="input w-auto" />
          </div>
        </FilterBar>

        {loading ? (
          <div className="text-sm text-slate-400">불러오는 중…</div>
        ) : contracts.length === 0 ? (
          <div className="card p-10 text-center">
            <div className="text-4xl mb-2">📄</div>
            <div className="font-semibold text-slate-700">
              {isSupplier ? '아직 등록한 계약이 없습니다' : '조회할 계약이 없습니다'}
            </div>
            <p className="mt-1 text-sm text-slate-400">
              {isSupplier
                ? '우측 상단 "새 계약 등록"으로 첫 계약을 추가하세요. BP사·현장·5분류 단가·계약서 스캔을 담을 수 있습니다.'
                : '공급사가 계약을 등록하면 여기에 표시됩니다.'}
            </p>
          </div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 계약이 없습니다.</div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {filtered.map((c) => (
              <ContractCard key={c.id} c={c} isSupplier={isSupplier} onEdit={() => setEditing(c)} />
            ))}
          </div>
        )}
      </div>

      {editing && (
        <ContractForm
          contract={editing === 'new' ? null : editing}
          bpOptions={bpOptions}
          siteOptions={siteOptions}
          onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); void load(); }}
        />
      )}
    </AppShell>
  );
}

function money(n?: number | null): string {
  return n != null && n > 0 ? n.toLocaleString() + '원' : '-';
}

function ContractCard({ c, isSupplier, onEdit }: { c: Contract; isSupplier: boolean; onEdit: () => void }) {
  const bp = c.bp_company_name ?? c.bp_name ?? '미지정';
  const site = c.site_name ?? '현장 미지정';
  return (
    <div className="card p-5 flex flex-col gap-3">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="font-bold text-slate-900 truncate">{c.title || '(제목 없음)'}</div>
          <div className="mt-0.5 text-xs text-slate-500 truncate">{c.equipment_desc || '장비 설명 없음'}</div>
        </div>
        <span className="shrink-0 rounded-full bg-brand-50 text-brand-700 text-xs font-semibold px-2 py-0.5">
          {c.rate_type === 'DAILY' ? '일대' : '월대'}
        </span>
      </div>

      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
        <span>BP <b className="text-slate-800">{bp}</b></span>
        <span>현장 <b className="text-slate-800">{site}</b></span>
        {c.has_file && (
          <a href={`/api/contracts/${c.id}/file`} target="_blank" rel="noreferrer"
             className="text-brand-600 font-semibold hover:underline">📎 계약서</a>
        )}
      </div>

      <div className="rounded-lg bg-slate-50 px-3 py-2">
        <div className="text-xs text-slate-400">기본단가 ({c.rate_type === 'DAILY' ? '일대' : '월대'})</div>
        <div className="text-lg font-bold text-slate-900 tabular-nums">{money(c.base_rate)}</div>
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {OT_FIELDS.map((f) => {
            const v = c[f.key] as number | null | undefined;
            return (
              <span key={String(f.key)}
                    className={`text-[11px] px-1.5 py-0.5 rounded ${v ? 'bg-white text-slate-700 border border-slate-200' : 'bg-slate-100 text-slate-300'}`}>
                {f.label} {v ? v.toLocaleString() : '-'}
              </span>
            );
          })}
        </div>
      </div>

      <div className="flex items-center justify-between text-xs text-slate-400">
        <span>{c.start_date || '기간 미정'} ~ {c.end_date || ''}</span>
        {isSupplier && (
          <button onClick={onEdit} className="text-brand-600 font-semibold hover:underline">수정</button>
        )}
      </div>
      {c.memo && <div className="text-xs text-slate-500 border-t border-slate-100 pt-2">{c.memo}</div>}
    </div>
  );
}

type FormState = {
  title: string;
  equipment_desc: string;
  rate_type: RateType;
  base_rate: number | '';
  rate_early: number | '';
  rate_lunch: number | '';
  rate_evening: number | '';
  rate_night: number | '';
  rate_overnight: number | '';
  bp_mode: 'company' | 'name';
  bp_company_id: number | '';
  bp_name: string;
  site_id: number | '';
  site_name: string;
  start_date: string;
  end_date: string;
  memo: string;
};

function ContractForm({ contract, bpOptions, siteOptions, onClose, onSaved }: {
  contract: Contract | null;
  bpOptions: Option[];
  siteOptions: Option[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [f, setF] = useState<FormState>(() => ({
    title: contract?.title ?? '',
    equipment_desc: contract?.equipment_desc ?? '',
    rate_type: contract?.rate_type ?? 'DAILY',
    base_rate: contract?.base_rate ?? '',
    rate_early: contract?.rate_early ?? '',
    rate_lunch: contract?.rate_lunch ?? '',
    rate_evening: contract?.rate_evening ?? '',
    rate_night: contract?.rate_night ?? '',
    rate_overnight: contract?.rate_overnight ?? '',
    bp_mode: contract?.bp_name && !contract?.bp_company_id ? 'name' : 'company',
    bp_company_id: contract?.bp_company_id ?? '',
    bp_name: contract?.bp_name ?? '',
    site_id: contract?.site_id ?? '',
    site_name: contract?.site_name ?? '',
    start_date: contract?.start_date ?? '',
    end_date: contract?.end_date ?? '',
    memo: contract?.memo ?? '',
  }));
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((p) => ({ ...p, [k]: v }));
  const numOrNull = (v: number | '') => (v === '' ? null : v);

  const save = async () => {
    if (!f.title.trim()) { toast.error('계약 제목을 입력하세요'); return; }
    setBusy(true);
    try {
      const body = {
        title: f.title.trim(),
        equipment_desc: f.equipment_desc.trim() || null,
        rate_type: f.rate_type,
        base_rate: numOrNull(f.base_rate),
        rate_early: numOrNull(f.rate_early),
        rate_lunch: numOrNull(f.rate_lunch),
        rate_evening: numOrNull(f.rate_evening),
        rate_night: numOrNull(f.rate_night),
        rate_overnight: numOrNull(f.rate_overnight),
        bp_company_id: f.bp_mode === 'company' && f.bp_company_id !== '' ? f.bp_company_id : null,
        bp_name: f.bp_mode === 'name' ? (f.bp_name.trim() || null) : null,
        site_id: f.site_id === '' ? null : f.site_id,
        site_name: f.site_id === '' ? (f.site_name.trim() || null) : null,
        start_date: f.start_date || null,
        end_date: f.end_date || null,
        memo: f.memo.trim() || null,
      };
      const res = contract
        ? await api.put<Contract>(`/api/contracts/${contract.id}`, body)
        : await api.post<Contract>('/api/contracts', body);
      if (file) {
        const fd = new FormData();
        fd.append('file', file);
        await api.post(`/api/contracts/${res.data.id}/file`, fd);
      }
      toast.success(contract ? '계약이 수정되었습니다' : '계약이 등록되었습니다');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장에 실패했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl max-h-[92vh] flex flex-col">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">{contract ? '계약 수정' : '새 계약 등록'}</h3>
        </div>
        <div className="px-5 py-4 space-y-4 text-sm overflow-y-auto">
          <Field label="계약 제목" required>
            <input value={f.title} onChange={(e) => set('title', e.target.value)} className="input w-full"
                   placeholder="예: 센코어테크 용인현장 고소작업차 계약" />
          </Field>
          <Field label="장비 종류·규격">
            <input value={f.equipment_desc} onChange={(e) => set('equipment_desc', e.target.value)} className="input w-full"
                   placeholder="예: 고소작업차 45m" />
          </Field>

          <Field label="단가 방식" required>
            <div className="flex gap-2">
              {(['DAILY', 'MONTHLY'] as RateType[]).map((rt) => (
                <button key={rt} type="button" onClick={() => set('rate_type', rt)}
                        className={`px-4 py-1.5 rounded-lg text-sm font-semibold border ${f.rate_type === rt ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-slate-600 border-slate-200'}`}>
                  {rt === 'DAILY' ? '일대' : '월대'}
                </button>
              ))}
            </div>
          </Field>

          <Field label={`기본단가 (${f.rate_type === 'DAILY' ? '일대' : '월대'}, 원)`}>
            <MoneyInput className="input w-full" value={f.base_rate}
                        onChange={(v) => set('base_rate', v)} />
          </Field>

          <div>
            <div className="text-xs font-semibold text-slate-500 mb-1">OT 5분류 단가 (원)</div>
            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              {OT_FIELDS.map((of) => {
                const k = of.key as keyof FormState;
                return (
                  <label key={String(of.key)} className="block">
                    <span className="text-[11px] text-slate-500">{of.label}</span>
                    <MoneyInput className="input mt-0.5 w-full text-sm" value={f[k] as number | ''} showKorean={false}
                                onChange={(v) => set(k, v as any)} />
                  </label>
                );
              })}
            </div>
          </div>

          <Field label="BP사">
            <div className="flex gap-2 mb-1.5">
              <ToggleBtn active={f.bp_mode === 'company'} onClick={() => set('bp_mode', 'company')}>회사 선택</ToggleBtn>
              <ToggleBtn active={f.bp_mode === 'name'} onClick={() => set('bp_mode', 'name')}>이름 직접입력</ToggleBtn>
            </div>
            {f.bp_mode === 'company' ? (
              <select value={f.bp_company_id} onChange={(e) => set('bp_company_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
                <option value="">BP사 선택 안 함</option>
                {bpOptions.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            ) : (
              <input value={f.bp_name} onChange={(e) => set('bp_name', e.target.value)} className="input w-full"
                     placeholder="미가입 BP 이름 (예: 센코어테크)" />
            )}
          </Field>

          <Field label="현장">
            <select value={f.site_id} onChange={(e) => set('site_id', e.target.value ? Number(e.target.value) : '')} className="input w-full">
              <option value="">현장 미지정 (직접 입력)</option>
              {siteOptions.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            {f.site_id === '' && (
              <input value={f.site_name} onChange={(e) => set('site_name', e.target.value)} className="input mt-1.5 w-full"
                     placeholder="현장명 직접 입력 (선택)" />
            )}
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="계약 시작일">
              <input type="date" value={f.start_date} onChange={(e) => set('start_date', e.target.value)} className="input w-full" />
            </Field>
            <Field label="계약 종료일">
              <input type="date" value={f.end_date} onChange={(e) => set('end_date', e.target.value)} className="input w-full" />
            </Field>
          </div>

          <Field label="계약서 스캔 (선택)">
            <input type="file" onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                   accept=".pdf,image/*,.doc,.docx,.xls,.xlsx" className="text-xs" />
            {contract?.has_file && !file && <div className="mt-1 text-xs text-emerald-600">기존 계약서 첨부됨 (새 파일 선택 시 교체)</div>}
          </Field>

          <Field label="메모">
            <textarea value={f.memo} onChange={(e) => set('memo', e.target.value)} className="input w-full" rows={2} />
          </Field>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '저장 중…' : contract ? '수정 저장' : '계약 등록'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs font-semibold text-slate-500">{label}{required && <span className="text-rose-500"> *</span>}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}

function ToggleBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button type="button" onClick={onClick}
            className={`px-3 py-1 rounded-lg text-xs font-semibold border ${active ? 'bg-brand-600 text-white border-brand-600' : 'bg-white text-slate-600 border-slate-200'}`}>
      {children}
    </button>
  );
}
