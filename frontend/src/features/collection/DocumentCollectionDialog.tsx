import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { DocumentTypeResponse, OwnerType } from '../../types/document';

/** 필수 기본 선택 서류 이름 (비파괴/안전점검/갑부/차량서류). 나머지는 선택. */
const REQUIRED_DEFAULTS = ['비파괴검사 보고서(MT/UT)', '안전점검표', '갑부', '차량서류'];

type Item = {
  document_type_id: number;
  document_type_name: string;
  required: boolean;
  uploaded: boolean;
  file_name?: string | null;
};
type CollectionResponse = {
  id: number;
  token: string;
  status: string;
  title?: string | null;
  recipient_name?: string | null;
  recipient_email?: string | null;
  recipient_phone?: string | null;
  public_url: string;
  items: Item[];
};

type Props = {
  ownerType: OwnerType;
  ownerId: number;
  ownerLabel: string;
  onClose: () => void;
};

export default function DocumentCollectionDialog({ ownerType, ownerId, ownerLabel, onClose }: Props) {
  const [types, setTypes] = useState<DocumentTypeResponse[]>([]);
  const [requests, setRequests] = useState<CollectionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // 선택 상태: typeId → 'none' | 'required' | 'optional'
  const [sel, setSel] = useState<Record<number, 'none' | 'required' | 'optional'>>({});
  const [recipientName, setRecipientName] = useState('');
  const [recipientEmail, setRecipientEmail] = useState('');
  const [recipientPhone, setRecipientPhone] = useState('');
  const [title, setTitle] = useState('');

  async function reload() {
    setLoading(true);
    try {
      const [t, r] = await Promise.all([
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: ownerType } }),
        api.get<CollectionResponse[]>('/api/document-collections', { params: { ownerType, ownerId } }),
      ]);
      setTypes(t.data);
      setRequests(r.data);
      // 첫 로드 시 필수 기본 선택
      setSel((prev) => {
        if (Object.keys(prev).length > 0) return prev;
        const next: Record<number, 'none' | 'required' | 'optional'> = {};
        t.data.forEach((ty) => { next[ty.id] = REQUIRED_DEFAULTS.includes(ty.name) ? 'required' : 'none'; });
        return next;
      });
      setError(null);
    } catch {
      setError('불러오기 실패');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [ownerType, ownerId]);

  const selectedCount = useMemo(() => Object.values(sel).filter((v) => v !== 'none').length, [sel]);

  async function create() {
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
        recipient_email: recipientEmail.trim() || null,
        recipient_phone: recipientPhone.trim() || null,
      });
      setTitle(''); setRecipientName(''); setRecipientEmail(''); setRecipientPhone('');
      await reload();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '생성 실패') : '생성 실패');
    } finally { setBusy(false); }
  }

  async function copyLink(url: string) {
    try { await navigator.clipboard.writeText(url); alert('링크를 복사했습니다.'); }
    catch { window.prompt('아래 링크를 복사하세요', url); }
  }

  async function sendPdf(req: CollectionResponse) {
    const to = window.prompt('PDF를 보낼 이메일', req.recipient_email ?? '');
    if (!to || !to.trim()) return;
    setBusy(true); setError(null);
    try {
      await api.post(`/api/document-collections/${req.id}/send`, { email: to.trim() });
      alert('PDF를 합쳐 이메일로 발송했습니다.');
      await reload();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '발송 실패') : '발송 실패');
    } finally { setBusy(false); }
  }

  async function sendLinkSms(req: CollectionResponse) {
    setBusy(true); setError(null);
    try {
      const r = await api.post<{ status?: string }>(`/api/document-collections/${req.id}/send-link`);
      alert(r.data?.status === 'SENT' ? '링크를 문자로 발송했습니다.' : `문자 발송 결과: ${r.data?.status ?? '실패'}`);
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '문자 발송 실패') : '문자 발송 실패');
    } finally { setBusy(false); }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/40 p-4" onClick={onClose}>
      <div className="mt-8 w-full max-w-2xl rounded-xl bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
          <h2 className="text-base font-bold text-slate-900">서류 수집 요청 — {ownerLabel}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">✕</button>
        </div>

        <div className="max-h-[75vh] space-y-5 overflow-y-auto p-5">
          {error && <p className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

          {/* 새 수집 요청 */}
          <section className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">새 요청 — 수집할 서류 선택</h3>
            <div className="rounded-lg border border-slate-200 divide-y divide-slate-100">
              {loading ? <p className="p-3 text-xs text-slate-400">불러오는 중…</p> :
                types.map((ty) => (
                  <div key={ty.id} className="flex items-center justify-between px-3 py-2">
                    <span className="text-sm text-slate-700">{ty.name}</span>
                    <div className="inline-flex overflow-hidden rounded-md border border-slate-300 text-xs">
                      {(['none', 'required', 'optional'] as const).map((v) => (
                        <button key={v} type="button"
                          onClick={() => setSel((s) => ({ ...s, [ty.id]: v }))}
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
            <div className="space-y-2">
              <div className="text-sm font-medium text-slate-700">받는사람 <span className="text-xs font-normal text-slate-400">— 전부 선택 (비워도 됩니다)</span></div>
              <div className="grid grid-cols-2 gap-2">
                <input value={recipientName} onChange={(e) => setRecipientName(e.target.value)} placeholder="이름 (선택)" className="input" />
                <input value={recipientPhone} onChange={(e) => setRecipientPhone(e.target.value)} placeholder="010-1234-5678 (하이픈 무관, 선택)" className="input" />
                <input value={recipientEmail} onChange={(e) => setRecipientEmail(e.target.value)} placeholder="이메일 — PDF 발송용 (선택)" className="input" />
                <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="제목 (선택)" className="input" />
              </div>
              <p className="text-xs text-slate-400">연락처·이메일을 비우면 <strong>링크를 복사해 직접</strong> 보내면 됩니다. 연락처 → <strong>문자</strong>, 이메일 → <strong>PDF 메일</strong>.</p>
            </div>
            <button onClick={create} disabled={busy || selectedCount === 0} className="btn-primary w-full disabled:opacity-50">
              {busy ? '처리 중…' : `링크 생성 (${selectedCount}종 선택) — 받는사람 안 넣어도 됨`}
            </button>
          </section>

          {/* 기존 요청 */}
          <section className="space-y-2">
            <h3 className="text-sm font-bold text-slate-800">생성된 링크</h3>
            {requests.length === 0 ? <p className="text-xs text-slate-400">아직 없습니다.</p> :
              requests.map((req) => {
                const up = req.items.filter((i) => i.uploaded).length;
                return (
                  <div key={req.id} className="rounded-lg border border-slate-200 p-3 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-semibold text-slate-800">{req.title || '서류 수집'}
                        <span className={`ml-2 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                          req.status === 'SENT' ? 'bg-emerald-100 text-emerald-700' : req.status === 'SUBMITTED' ? 'bg-blue-100 text-blue-700' : req.status === 'CANCELLED' ? 'bg-slate-100 text-slate-400' : 'bg-amber-100 text-amber-700'}`}>
                          {req.status}</span></span>
                      <span className="text-xs text-slate-500">{up}/{req.items.length} 업로드</span>
                    </div>
                    <div className="flex items-center gap-2">
                      <input readOnly value={req.public_url} className="input flex-1 text-xs" />
                      <button onClick={() => copyLink(req.public_url)} className="shrink-0 rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold hover:bg-slate-50">복사</button>
                      <button onClick={() => sendLinkSms(req)} disabled={busy || !req.recipient_phone}
                        className="shrink-0 rounded-md border border-brand-300 bg-brand-50 px-2.5 py-1.5 text-xs font-semibold text-brand-700 hover:bg-brand-100 disabled:opacity-40"
                        title={req.recipient_phone ? '' : '받는사람 연락처가 없습니다'}>문자 발송</button>
                    </div>
                    <button onClick={() => sendPdf(req)} disabled={busy || up === 0}
                      className="w-full rounded-md bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-40">
                      받은 서류 PDF로 합쳐 이메일 발송
                    </button>
                  </div>
                );
              })}
          </section>
        </div>
      </div>
    </div>
  );
}
