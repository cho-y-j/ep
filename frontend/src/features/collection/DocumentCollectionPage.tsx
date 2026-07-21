import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import type { DocumentTypeResponse, OwnerType } from '../../types/document';
import type { EquipmentResponse } from '../../types/equipment';
import { EQUIPMENT_CATEGORY_LABEL } from '../../types/equipment';
import type { PersonResponse } from '../../types/person';
import { fetchSuggestedSel, type Sel } from './suggest';

type Item = { document_type_id: number; document_type_name: string; required: boolean; uploaded: boolean; };
type CollectionResponse = {
  id: number; token: string; status: string; title?: string | null; owner_type: OwnerType;
  owner_name?: string | null; recipient_name?: string | null;
  recipient_phone?: string | null; public_url: string; items: Item[];
};

const equipLabel = (e: EquipmentResponse) => e.vehicle_no || e.model || EQUIPMENT_CATEGORY_LABEL[e.category] || `장비 #${e.id}`;

export default function DocumentCollectionPage() {
  const [requests, setRequests] = useState<CollectionResponse[]>([]);
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [typesEq, setTypesEq] = useState<DocumentTypeResponse[]>([]);
  const [typesPe, setTypesPe] = useState<DocumentTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 새 요청 폼
  const [showNew, setShowNew] = useState(false);
  const [ownerType, setOwnerType] = useState<OwnerType>('EQUIPMENT');
  const [ownerId, setOwnerId] = useState<number | ''>('');
  const [sel, setSel] = useState<Sel>({});
  const [recipientName, setRecipientName] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [title, setTitle] = useState('');

  // 목록 필터(클라이언트)
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const types = ownerType === 'EQUIPMENT' ? typesEq : typesPe;

  async function reload() {
    setLoading(true);
    try {
      const [r, eq, pe, te, tp] = await Promise.all([
        api.get<CollectionResponse[]>('/api/document-collections'),
        api.get<EquipmentResponse[]>('/api/equipment'),
        api.get<{ content: PersonResponse[] }>('/api/persons', { params: { size: 500 } }),
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } }),
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'PERSON' } }),
      ]);
      setRequests(r.data); setEquipment(eq.data); setPersons(pe.data.content);
      setTypesEq(te.data); setTypesPe(tp.data);
      setError(null);
    } catch {
      setError('불러오기 실패');
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  // 대상을 고르면 그 자원의 유형(장비종류/인력역할)에 설정된 필수·선택 서류를 자동 체크.
  useEffect(() => {
    if (!ownerId) { setSel({}); return; }
    let alive = true;
    void fetchSuggestedSel(ownerType, ownerId).then((s) => { if (alive) setSel(s); });
    return () => { alive = false; };
  }, [ownerType, ownerId]);

  const selectedCount = useMemo(() => Object.values(sel).filter((v) => v !== 'none').length, [sel]);

  const statusOptions = useMemo(
    () => [...new Set(requests.map((r) => r.status))].map((v) => ({ value: v, label: v })),
    [requests],
  );
  const qLower = q.trim().toLowerCase();
  const shownRequests = requests.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (qLower && !`${r.owner_name ?? ''} ${r.title ?? ''} ${r.recipient_name ?? ''}`.toLowerCase().includes(qLower)) return false;
    return true;
  });

  async function create() {
    if (!ownerId) { setError('대상(장비/인원)을 선택하세요'); return; }
    const requiredTypeIds = Object.entries(sel).filter(([, v]) => v === 'required').map(([k]) => Number(k));
    const optionalTypeIds = Object.entries(sel).filter(([, v]) => v === 'optional').map(([k]) => Number(k));
    if (requiredTypeIds.length + optionalTypeIds.length === 0) { setError('서류를 1개 이상 선택하세요'); return; }
    setBusy(true); setError(null);
    try {
      await api.post('/api/document-collections', {
        owner_type: ownerType, owner_id: ownerId,
        required_type_ids: requiredTypeIds, optional_type_ids: optionalTypeIds,
        title: title.trim() || null,
        recipient_name: recipientName.trim() || null,
        recipient_phone: recipientPhone.trim() || null,
      });
      setShowNew(false); setTitle(''); setRecipientName(''); setRecipientPhone('');
      await reload();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '생성 실패') : '생성 실패');
    } finally { setBusy(false); }
  }

  async function copyLink(url: string) {
    try { await navigator.clipboard.writeText(url); alert('링크를 복사했습니다.'); }
    catch { window.prompt('아래 링크를 복사하세요', url); }
  }
  async function sendSms(req: CollectionResponse) {
    setBusy(true); setError(null);
    try {
      const r = await api.post<{ status?: string }>(`/api/document-collections/${req.id}/send-link`);
      alert(r.data?.status === 'SENT' ? '링크를 문자로 발송했습니다.' : `문자 발송 결과: ${r.data?.status ?? '실패'}`);
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '문자 발송 실패') : '문자 발송 실패');
    } finally { setBusy(false); }
  }

  return (
    <AppShell breadcrumb={[{ label: '서류 수집 요청' }]}>
      <div className="space-y-5">
        <PageHeader
          title="서류 수집 요청"
          subtitle="차량주인·인원에게 공개 링크(복사·문자)를 보내 서류를 받습니다. 대상을 고르면 그 종류에 설정된 서류가 자동 선택됩니다."
          actions={
            <button onClick={() => setShowNew((v) => !v)} className="btn-primary">{showNew ? '닫기' : '+ 새 수집 요청'}</button>
          }
        />

        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

        {showNew && (
          <div className="card space-y-4 p-5">
            <h2 className="text-sm font-bold text-slate-800">새 요청</h2>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="block">
                <span className="text-sm font-medium text-slate-700">대상 종류</span>
                <div className="mt-1 inline-flex overflow-hidden rounded-lg border border-slate-300">
                  {(['EQUIPMENT', 'PERSON'] as const).map((t) => (
                    <button key={t} type="button" onClick={() => { setOwnerType(t); setOwnerId(''); }}
                      className={`px-4 py-2 text-sm font-semibold ${ownerType === t ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                      {t === 'EQUIPMENT' ? '장비(차량)' : '인원'}
                    </button>
                  ))}
                </div>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">대상 선택</span>
                <select value={ownerId} onChange={(e) => setOwnerId(e.target.value ? Number(e.target.value) : '')} className="input mt-1 bg-white">
                  <option value="">— 선택 —</option>
                  {ownerType === 'EQUIPMENT'
                    ? equipment.map((e) => <option key={e.id} value={e.id}>{equipLabel(e)}{e.is_external ? ' (외부)' : ''}</option>)
                    : persons.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </label>
            </div>

            <div>
              <span className="text-sm font-medium text-slate-700">수집할 서류</span>
              <div className="mt-1 divide-y divide-slate-100 rounded-lg border border-slate-200">
                {types.length === 0 ? <p className="p-3 text-xs text-slate-400">서류 종류가 없습니다.</p> :
                  types.map((ty) => (
                    <div key={ty.id} className="flex items-center justify-between px-3 py-2">
                      <span className="text-sm text-slate-700">{ty.name}</span>
                      <div className="inline-flex overflow-hidden rounded-md border border-slate-300 text-xs">
                        {(['none', 'required', 'optional'] as const).map((v) => (
                          <button key={v} type="button" onClick={() => setSel((s) => ({ ...s, [ty.id]: v }))}
                            className={`px-2.5 py-1 font-semibold border-r border-slate-200 last:border-r-0 ${
                              (sel[ty.id] ?? 'none') === v
                                ? v === 'required' ? 'bg-rose-600 text-white' : v === 'optional' ? 'bg-amber-500 text-white' : 'bg-slate-500 text-white'
                                : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                            {v === 'none' ? '제외' : v === 'required' ? '필수' : '선택'}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
              </div>
            </div>

            <div className="space-y-2">
              <div className="text-sm font-medium text-slate-700">받는사람 <span className="text-xs font-normal text-slate-400">— 전부 선택 (비워도 됩니다)</span></div>
              <div className="grid grid-cols-2 gap-2">
                <input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} placeholder="이름 (선택)" className="input" />
                <input value={recipientPhone} onChange={(e) => setRecipientPhone(e.target.value)} placeholder="010-1234-5678 (하이픈 무관, 선택)" className="input" />
                <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목 (선택)" className="input" />
              </div>
              <p className="text-xs text-slate-400">연락처를 비우면 만든 <strong>링크를 복사해 직접</strong> 보내면 됩니다. 연락처를 넣으면 <strong>문자</strong>로 보낼 수 있습니다.</p>
            </div>
            <button onClick={create} disabled={busy || !ownerId || selectedCount === 0} className="btn-primary w-full disabled:opacity-50">
              {busy ? '처리 중…' : `링크 생성 (${selectedCount}종 선택) — 받는사람 안 넣어도 됨`}
            </button>
          </div>
        )}

        {/* 생성된 요청 목록 */}
        {loading ? <p className="text-sm text-slate-400">불러오는 중…</p> :
          requests.length === 0 ? <div className="card p-8 text-center text-sm text-slate-400">아직 수집 요청이 없습니다.</div> :
          <>
            <FilterBar
              search={{ value: q, onChange: setQ, placeholder: '대상·제목·받는사람 검색' }}
              activeFilterCount={[q, statusFilter].filter(Boolean).length}
              onReset={() => { setQ(''); setStatusFilter(''); }}
            >
              <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체" options={statusOptions} />
            </FilterBar>
            {shownRequests.length === 0 ? <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 요청이 없습니다.</div> :
            <div className="space-y-2">
              {shownRequests.map((req) => {
                const up = req.items.filter((i) => i.uploaded).length;
                return (
                  <div key={req.id} className="card space-y-2 p-4">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-slate-800">
                        {req.owner_name ?? '대상'}{req.title ? ` · ${req.title}` : ''}
                        <span className={`ml-2 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                          req.status === 'SENT' ? 'bg-emerald-100 text-emerald-700' : req.status === 'SUBMITTED' ? 'bg-blue-100 text-blue-700' : req.status === 'CANCELLED' ? 'bg-slate-100 text-slate-400' : 'bg-amber-100 text-amber-700'}`}>
                          {req.status}</span>
                      </span>
                      <span className="text-xs text-slate-500">{up}/{req.items.length} 업로드</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <input readOnly value={req.public_url} className="input flex-1 text-xs" />
                      <button onClick={() => copyLink(req.public_url)} className="shrink-0 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold hover:bg-slate-50">복사</button>
                      <button onClick={() => sendSms(req)} disabled={busy || !req.recipient_phone}
                        className="shrink-0 rounded-md border border-brand-300 bg-brand-50 px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100 disabled:opacity-40">문자</button>
                    </div>
                  </div>
                );
              })}
            </div>}
          </>}
      </div>
    </AppShell>
  );
}
