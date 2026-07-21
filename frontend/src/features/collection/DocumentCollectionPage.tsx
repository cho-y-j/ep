import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import type { CollectionSummary } from '../../types/collection';
import type { DocumentTypeResponse } from '../../types/document';
import TargetDocEditor from './TargetDocEditor';
import TargetPicker from './TargetPicker';
import { fetchSuggestedSelBatch, type Sel } from './suggest';
import { targetKey, type PickedTarget } from './target';

const pickIds = (s: Sel, mode: 'required' | 'optional') =>
  Object.entries(s).filter(([, v]) => v === mode).map(([k]) => Number(k));

export default function DocumentCollectionPage() {
  const [requests, setRequests] = useState<CollectionSummary[]>([]);
  const [typesEq, setTypesEq] = useState<DocumentTypeResponse[]>([]);
  const [typesPe, setTypesPe] = useState<DocumentTypeResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 새 요청 폼 — 대상 고르기 → 대상별 서류 확인 2단계.
  const [showNew, setShowNew] = useState(false);
  const [step, setStep] = useState<'pick' | 'docs'>('pick');
  const [targets, setTargets] = useState<PickedTarget[]>([]);
  const [sel, setSel] = useState<Record<string, Sel>>({});
  const [suggested, setSuggested] = useState<Record<string, Sel>>({});
  const [recipientName, setRecipientName] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [title, setTitle] = useState('');

  // 목록 필터(클라이언트)
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  async function reload() {
    setLoading(true);
    try {
      const [r, te, tp] = await Promise.all([
        api.get<CollectionSummary[]>('/api/document-collections'),
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } }),
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'PERSON' } }),
      ]);
      setRequests(r.data); setTypesEq(te.data); setTypesPe(tp.data);
      setError(null);
    } catch {
      setError('불러오기 실패');
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  /** 선택 확정 — 아직 제안을 못 받은 대상만 모아 suggest-batch 1회. 이미 손댄 대상의 편집은 보존. */
  async function confirmTargets() {
    const missing = targets.filter((t) => suggested[targetKey(t)] === undefined);
    setBusy(true); setError(null);
    try {
      if (missing.length > 0) {
        const got = await fetchSuggestedSelBatch(missing);
        setSuggested((s) => ({ ...s, ...got }));
        setSel((s) => ({ ...got, ...s }));
      }
      setStep('docs');
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '서류 자동 선택 실패') : '서류 자동 선택 실패');
    } finally { setBusy(false); }
  }

  const emptyCount = useMemo(
    () => targets.filter((t) => {
      const s = sel[targetKey(t)] ?? {};
      return pickIds(s, 'required').length + pickIds(s, 'optional').length === 0;
    }).length,
    [targets, sel],
  );
  const itemTotal = useMemo(
    () => targets.reduce((n, t) => {
      const s = sel[targetKey(t)] ?? {};
      return n + pickIds(s, 'required').length + pickIds(s, 'optional').length;
    }, 0),
    [targets, sel],
  );

  const statusOptions = useMemo(
    () => [...new Set(requests.map((r) => r.status))].map((v) => ({ value: v, label: v })),
    [requests],
  );
  const qLower = q.trim().toLowerCase();
  const shownRequests = requests.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (qLower && !`${r.owner_summary ?? ''} ${r.title ?? ''} ${r.recipient_name ?? ''}`.toLowerCase().includes(qLower)) return false;
    return true;
  });

  function resetForm() {
    setShowNew(false); setStep('pick'); setTargets([]); setSel({}); setSuggested({});
    setTitle(''); setRecipientName(''); setRecipientPhone('');
  }

  async function create() {
    if (targets.length === 0) { setError('대상(장비/인원)을 1개 이상 선택하세요'); return; }
    if (emptyCount > 0) { setError('수집할 서류가 0건인 대상이 있습니다'); return; }
    setBusy(true); setError(null);
    try {
      await api.post('/api/document-collections', {
        title: title.trim() || null,
        recipient_name: recipientName.trim() || null,
        recipient_phone: recipientPhone.trim() || null,
        // 배열 순서 = sort_order (공개 화면에 이 순서로 노출된다)
        targets: targets.map((t) => {
          const s = sel[targetKey(t)] ?? {};
          return {
            owner_type: t.owner_type, owner_id: t.owner_id,
            required_type_ids: pickIds(s, 'required'), optional_type_ids: pickIds(s, 'optional'),
          };
        }),
      });
      resetForm();
      await reload();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '생성 실패') : '생성 실패');
    } finally { setBusy(false); }
  }

  async function copyLink(url: string) {
    try { await navigator.clipboard.writeText(url); alert('링크를 복사했습니다.'); }
    catch { window.prompt('아래 링크를 복사하세요', url); }
  }
  async function sendSms(req: CollectionSummary) {
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
          subtitle="장비·인력을 한 번에 골라 링크 1개로 서류를 받습니다. 장비를 고르면 그 장비의 조종원(교대조 전원)도 함께 담기고, 대상마다 종류에 맞는 서류가 자동 선택됩니다."
          actions={
            <button onClick={() => (showNew ? resetForm() : setShowNew(true))} className="btn-primary">
              {showNew ? '닫기' : '+ 새 수집 요청'}
            </button>
          }
        />

        {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

        {showNew && (
          <div className="card space-y-4 p-5">
            {step === 'pick' ? (
              <>
                <h2 className="text-sm font-bold text-slate-800">1단계 — 대상 고르기</h2>
                <TargetPicker value={targets} onChange={setTargets} />
                <button onClick={confirmTargets} disabled={busy || targets.length === 0}
                  className="btn-primary w-full disabled:opacity-50">
                  {busy ? '처리 중…' : `다음: 대상별 서류 확인 (${targets.length}건)`}
                </button>
              </>
            ) : (
              <>
                <div className="flex items-center justify-between">
                  <h2 className="text-sm font-bold text-slate-800">2단계 — 대상별 수집 서류</h2>
                  <button onClick={() => setStep('pick')} className="text-xs font-semibold text-brand-700 hover:underline">
                    ← 대상 다시 고르기
                  </button>
                </div>
                <TargetDocEditor
                  targets={targets} sel={sel} suggested={suggested}
                  onChange={(key, next) => setSel((s) => ({ ...s, [key]: next }))}
                  typesByOwner={{ EQUIPMENT: typesEq, PERSON: typesPe }}
                />

                <div className="space-y-2">
                  <div className="text-sm font-medium text-slate-700">받는사람 <span className="text-xs font-normal text-slate-400">— 전부 선택 (비워도 됩니다)</span></div>
                  <div className="grid grid-cols-2 gap-2">
                    <input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} placeholder="이름 (선택)" className="input" />
                    <input value={recipientPhone} onChange={(e) => setRecipientPhone(e.target.value)} placeholder="010-1234-5678 (하이픈 무관, 선택)" className="input" />
                    <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목 (선택)" className="input" />
                  </div>
                  <p className="text-xs text-slate-400">연락처를 비우면 만든 <strong>링크를 복사해 직접</strong> 보내면 됩니다. 연락처를 넣으면 <strong>문자</strong>로 보낼 수 있습니다.</p>
                </div>
                <button onClick={create} disabled={busy || targets.length === 0 || emptyCount > 0}
                  className="btn-primary w-full disabled:opacity-50">
                  {busy ? '처리 중…' : `링크 생성 — 대상 ${targets.length}건 · 서류 ${itemTotal}건`}
                </button>
              </>
            )}
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
              {shownRequests.map((req) => (
                <div key={req.id} className="card space-y-2 p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-semibold text-slate-800">
                      {req.owner_summary}{req.title ? ` · ${req.title}` : ''}
                      <span className={`ml-2 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                        req.status === 'SENT' ? 'bg-emerald-100 text-emerald-700' : req.status === 'SUBMITTED' ? 'bg-blue-100 text-blue-700' : req.status === 'CANCELLED' ? 'bg-slate-100 text-slate-400' : 'bg-amber-100 text-amber-700'}`}>
                        {req.status}</span>
                    </span>
                    <span className="text-xs text-slate-500">
                      대상 {req.target_count}건 · {req.uploaded_count}/{req.item_count} 업로드
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <input readOnly value={req.public_url} className="input flex-1 text-xs" />
                    <button onClick={() => copyLink(req.public_url)} className="shrink-0 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold hover:bg-slate-50">복사</button>
                    <button onClick={() => sendSms(req)} disabled={busy || !req.recipient_phone}
                      className="shrink-0 rounded-md border border-brand-300 bg-brand-50 px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100 disabled:opacity-40">문자</button>
                  </div>
                </div>
              ))}
            </div>}
          </>}
      </div>
    </AppShell>
  );
}
