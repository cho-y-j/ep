import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../../lib/api';
import { toast } from '../../../lib/toast';
import { useAuth } from '../../auth/AuthContext';
import type { BundleResponse } from '../../../types/bundle';
import type { DispatchedEquipmentResponse, DispatchedPersonResponse } from '../../../types/dispatch';

type Doc = {
  id: number;
  file_name?: string | null;
  content_type?: string | null;
  document_type_id: number;
  document_type_label?: string | null;
  expiry_date?: string | null;
  verification_status?: string | null;
  owner_type: 'EQUIPMENT' | 'PERSON' | 'COMPANY';
  owner_id: number;
};

type Supplement = {
  id: number;
  target_owner_type: 'EQUIPMENT' | 'PERSON' | 'COMPANY';
  target_owner_id: number;
  document_type_id: number;
  status: 'OPEN' | 'RESOLVED' | 'CANCELLED';
};

const suppKey = (t: string, id: number, dt: number) => `${t}:${id}:${dt}`;

type Props = {
  quotationRequestId: number;
};

export default function DocumentBundleSection({ quotationRequestId }: Props) {
  const [dispatched, setDispatched] = useState<DispatchedEquipmentResponse[]>([]);
  const [dispatchedPersons, setDispatchedPersons] = useState<DispatchedPersonResponse[]>([]);
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';

  const [bundles, setBundles] = useState<BundleResponse[]>([]);
  const [docsByEq, setDocsByEq] = useState<Record<number, Doc[]>>({});
  const [docsByPerson, setDocsByPerson] = useState<Record<number, Doc[]>>({});
  const [expandedP, setExpandedP] = useState<Set<number>>(new Set());
  const [includeEmail, setIncludeEmail] = useState(false);
  const [notes, setNotes] = useState('');
  // BP 회사 이메일 prefill (includeEmail 켜질 때 자동 fetch)
  const [bpEmails, setBpEmails] = useState<string[]>([]);
  const [selectedEmails, setSelectedEmails] = useState<Set<string>>(new Set());
  const [addedEmail, setAddedEmail] = useState('');
  const [bpEmailsLoaded, setBpEmailsLoaded] = useState(false);

  // 다중 보완요청 — 선택된 docId set + 사유 다이얼로그
  const [selectedForSupp, setSelectedForSupp] = useState<Set<number>>(new Set());
  const [suppDialogOpen, setSuppDialogOpen] = useState(false);
  const [suppReason, setSuppReason] = useState('');
  const [suppBusy, setSuppBusy] = useState(false);
  const [supplements, setSupplements] = useState<Supplement[]>([]);
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  // 미리보기 모달
  const [previewDoc, setPreviewDoc] = useState<Doc | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  // 행 옆 썸네일 (이미지 한정) — docId → blob URL
  const [thumbUrls, setThumbUrls] = useState<Record<number, string>>({});

  async function loadBundles() {
    setLoading(true);
    try {
      const res = await api.get<BundleResponse[]>(`/api/quotations/${quotationRequestId}/document-bundle`);
      setBundles(res.data);
    } catch {
      setBundles([]);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void loadBundles(); }, [quotationRequestId]);

  useEffect(() => {
    api.get<DispatchedEquipmentResponse[]>(`/api/quotations/${quotationRequestId}/dispatched`)
      .then((r) => {
        setDispatched(r.data);
        // 헤더의 "서류 N건" 표시 정확히 보이도록 docs 미리 fetch
        for (const d of r.data) {
          if (d.equipment_id != null) void loadDocs(d.equipment_id);
        }
      })
      .catch(() => setDispatched([]));
    api.get<DispatchedPersonResponse[]>(`/api/quotations/${quotationRequestId}/dispatched-persons`)
      .then((r) => {
        setDispatchedPersons(r.data);
        for (const d of r.data) {
          if (d.person_id != null) void loadPersonDocs(d.person_id);
        }
      })
      .catch(() => setDispatchedPersons([]));
  }, [quotationRequestId]);

  async function loadSupplements() {
    if (!isBP && !isAdmin) return;
    try {
      const res = await api.get<Supplement[]>('/api/document-supplements');
      setSupplements(res.data);
    } catch {
      setSupplements([]);
    }
  }
  useEffect(() => { void loadSupplements(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [isBP, isAdmin]);

  const openSuppMap = useMemo(() => {
    const m = new Map<string, Supplement>();
    for (const s of supplements) {
      if (s.status === 'OPEN') m.set(suppKey(s.target_owner_type, s.target_owner_id, s.document_type_id), s);
    }
    return m;
  }, [supplements]);

  async function openPreview(doc: Doc) {
    setPreviewDoc(doc);
    setPreviewLoading(true);
    setPreviewUrl(null);
    try {
      const res = await api.get(`/api/documents/${doc.id}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      setPreviewUrl(url);
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '미리보기 실패');
      setPreviewDoc(null);
    } finally {
      setPreviewLoading(false);
    }
  }
  function closePreview() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setPreviewUrl(null);
    setPreviewDoc(null);
  }

  // 이미지 doc 만 썸네일 prefetch (병렬)
  function prefetchThumbs(docList: Doc[]) {
    for (const d of docList) {
      const ct = d.content_type ?? '';
      if (!ct.startsWith('image/')) continue;
      if (thumbUrls[d.id]) continue;
      void api.get(`/api/documents/${d.id}/file`, { responseType: 'blob' })
        .then((r) => {
          const url = URL.createObjectURL(r.data as Blob);
          setThumbUrls((prev) => (prev[d.id] ? prev : { ...prev, [d.id]: url }));
        })
        .catch(() => { /* 썸네일 실패는 조용히 무시 */ });
    }
  }

  async function loadDocs(eqId: number) {
    if (docsByEq[eqId]) return;
    try {
      const res = await api.get<Doc[]>('/api/documents', { params: { ownerType: 'EQUIPMENT', ownerId: eqId } });
      setDocsByEq((prev) => ({ ...prev, [eqId]: res.data }));
      prefetchThumbs(res.data);
    } catch {
      setDocsByEq((prev) => ({ ...prev, [eqId]: [] }));
    }
  }

  async function loadPersonDocs(personId: number) {
    if (docsByPerson[personId]) return;
    try {
      const res = await api.get<Doc[]>('/api/documents', { params: { ownerType: 'PERSON', ownerId: personId } });
      setDocsByPerson((prev) => ({ ...prev, [personId]: res.data }));
      prefetchThumbs(res.data);
    } catch {
      setDocsByPerson((prev) => ({ ...prev, [personId]: [] }));
    }
  }
  // unmount 시 blob URL 정리
  useEffect(() => () => {
    Object.values(thumbUrls).forEach((u) => URL.revokeObjectURL(u));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function quickSupplement(doc: Doc) {
    setSelectedForSupp(new Set([doc.id]));
    setSuppDialogOpen(true);
  }

  /** 한 장비의 선택 가능 doc(=open 보완요청 없음) 들을 전체 선택/해제. */
  function toggleSelectAll(docs: Doc[]) {
    const selectable = docs.filter(
      (d) => !openSuppMap.has(suppKey(d.owner_type, d.owner_id, d.document_type_id)),
    );
    if (selectable.length === 0) return;
    const allOn = selectable.every((d) => selectedForSupp.has(d.id));
    setSelectedForSupp((prev) => {
      const next = new Set(prev);
      if (allOn) selectable.forEach((d) => next.delete(d.id));
      else selectable.forEach((d) => next.add(d.id));
      return next;
    });
  }

  const toggle = (eqId: number) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(eqId)) next.delete(eqId);
      else { next.add(eqId); void loadDocs(eqId); }
      return next;
    });
  };

  const toggleP2 = (personId: number) => {
    setExpandedP((prev) => {
      const next = new Set(prev);
      if (next.has(personId)) next.delete(personId);
      else { next.add(personId); void loadPersonDocs(personId); }
      return next;
    });
  };

  const myBundle = isSupplier && user?.company_id
    ? bundles.find((b) => b.supplier_company_id === user.company_id)
    : null;
  const canSend = isSupplier && dispatched.length > 0 && !myBundle;

  // 공급사: 보내기 전 모든 차량·인원 서류 prefetch → 만료/검증실패 경고용.
  useEffect(() => {
    if (!canSend) return;
    dispatched.forEach((d) => { void loadDocs(d.equipment_id); });
    dispatchedPersons.forEach((d) => { void loadPersonDocs(d.person_id); });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canSend, dispatched, dispatchedPersons]);

  // 만료/임박/검증실패 서류 목록 (공급사 발송 전 경고) — 차량 + 인원
  const docWarnings = useMemo(() => {
    if (!canSend) return [];
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const soon = new Date(today); soon.setDate(soon.getDate() + 30);
    type W = { ownerType: 'EQUIPMENT' | 'PERSON'; ownerId: number; resLabel: string; doc: string; kind: 'expired' | 'expiring' | 'invalid'; date?: string | null };
    const out: W[] = [];
    const scan = (ownerType: 'EQUIPMENT' | 'PERSON', resLabel: string, docs: Doc[]) => {
      for (const doc of docs) {
        const label = doc.document_type_label ?? doc.file_name ?? `문서 #${doc.id}`;
        if (doc.verification_status === 'INVALID') {
          out.push({ ownerType, ownerId: doc.owner_id, resLabel, doc: label, kind: 'invalid', date: doc.expiry_date });
          continue;
        }
        if (doc.expiry_date) {
          const exp = new Date(doc.expiry_date); exp.setHours(0, 0, 0, 0);
          if (exp < today) out.push({ ownerType, ownerId: doc.owner_id, resLabel, doc: label, kind: 'expired', date: doc.expiry_date });
          else if (exp <= soon) out.push({ ownerType, ownerId: doc.owner_id, resLabel, doc: label, kind: 'expiring', date: doc.expiry_date });
        }
      }
    };
    const labelByEq = new Map(dispatched.map((d) => [d.equipment_id, d.equipment_label]));
    for (const [eqId, docs] of Object.entries(docsByEq)) {
      const eid = Number(eqId);
      if (!labelByEq.has(eid)) continue;
      scan('EQUIPMENT', labelByEq.get(eid) ?? `#${eid}`, docs);
    }
    const labelByP = new Map(dispatchedPersons.map((d) => [d.person_id, d.person_label]));
    for (const [pId, docs] of Object.entries(docsByPerson)) {
      const pid = Number(pId);
      if (!labelByP.has(pid)) continue;
      scan('PERSON', labelByP.get(pid) ?? `#${pid}`, docs);
    }
    return out;
  }, [canSend, docsByEq, docsByPerson, dispatched, dispatchedPersons]);

  // includeEmail 토글되면 BP 회사 이메일 한 번만 fetch
  useEffect(() => {
    if (!includeEmail || bpEmailsLoaded) return;
    setBpEmailsLoaded(true);
    api.get<any>(`/api/quotations/${quotationRequestId}`).then((res) => {
      const bpId = res.data?.bp_company_id ?? res.data?.on_behalf_of_bp_company_id;
      if (!bpId) return;
      api.get<any[]>(`/api/companies/${bpId}/bp-users`).then((r) => {
        const emails = (r.data ?? []).map((u: any) => u.email).filter(Boolean);
        const unique = Array.from(new Set<string>(emails));
        setBpEmails(unique);
        setSelectedEmails(new Set(unique));
      }).catch(() => { /* 권한 없음 등 — 비우고 사용자가 직접 추가 */ });
    }).catch(() => {});
  }, [includeEmail, bpEmailsLoaded, quotationRequestId]);

  async function send() {
    setSending(true);
    try {
      const emails = includeEmail ? Array.from(selectedEmails) : undefined;
      // 백엔드 SendBundleRequest record — Jackson SNAKE_CASE 라 include_email 로 전송.
      const payload = {
        include_email: includeEmail,
        notes: notes || null,
        ...(emails && emails.length > 0 ? { emails } : {}),
      };
      await api.post(`/api/quotations/${quotationRequestId}/document-bundle`, payload);
      toast.success(includeEmail
        ? `BP 에 서류 묶음 발송 + 이메일 ${emails?.length ?? 0}명 전송됨`
        : 'BP 에 서류 묶음 발송됨');
      void loadBundles();
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '실패') : '실패');
    } finally {
      setSending(false);
    }
  }

  const toggleSupp = (docId: number) => {
    setSelectedForSupp((prev) => {
      const next = new Set(prev);
      if (next.has(docId)) next.delete(docId);
      else next.add(docId);
      return next;
    });
  };

  /** 선택된 모든 doc 에 대해 한 번에 보완요청 발송 (사유 공통). */
  async function submitSupplementBatch() {
    const allDocs = [...Object.values(docsByEq).flat(), ...Object.values(docsByPerson).flat()];
    const targets = allDocs.filter((d) => selectedForSupp.has(d.id));
    if (targets.length === 0) {
      toast.error('보완요청할 항목을 1개 이상 선택하세요');
      return;
    }
    setSuppBusy(true);
    try {
      // 배치 — 한 번에 보내 공급사 알림 1건으로 집계.
      await api.post('/api/document-supplements/batch', targets.map((d) => ({
        target_owner_type: d.owner_type,
        target_owner_id: d.owner_id,
        document_type_id: d.document_type_id,
        reason: suppReason || null,
      })));
      toast.success(`보완요청 ${targets.length}건 전송됨`);
    } catch (err) {
      toast.error(err instanceof AxiosError ? (err.response?.data?.message ?? '보완요청 전송 실패') : '보완요청 전송 실패');
    } finally {
      setSuppBusy(false);
    }
    setSelectedForSupp(new Set());
    setSuppDialogOpen(false);
    setSuppReason('');
    void loadSupplements();
  }

  function renderDocRow(doc: Doc) {
    const hasOpenSupp = openSuppMap.has(suppKey(doc.owner_type, doc.owner_id, doc.document_type_id));
    const isInvalid = doc.verification_status === 'INVALID';
    const thumb = thumbUrls[doc.id];
    const isImage = (doc.content_type ?? '').startsWith('image/');
    const isPdf = doc.content_type === 'application/pdf';
    return (
      <div key={doc.id} className="flex items-center gap-2 text-sm">
        <button type="button" onClick={() => openPreview(doc)}
                className="w-12 h-12 rounded border border-slate-200 bg-slate-50 overflow-hidden flex items-center justify-center shrink-0 hover:border-brand-500 hover:shadow-sm transition"
                title="클릭하여 크게 보기">
          {isImage && thumb ? (
            <img src={thumb} alt="" className="w-full h-full object-cover" />
          ) : isImage ? (
            <span className="text-[10px] text-slate-400">로딩</span>
          ) : isPdf ? (
            <span className="text-[10px] font-bold text-rose-600">PDF</span>
          ) : (
            <span className="text-[10px] text-slate-400">파일</span>
          )}
        </button>

        <div className="flex-1 min-w-0">
          <button type="button" onClick={() => openPreview(doc)}
                  className="font-medium text-slate-900 hover:underline truncate text-left block w-full">
            {doc.document_type_label ?? doc.file_name ?? `문서 #${doc.id}`}
          </button>
          <div className="text-[11px] text-slate-500 flex gap-1.5 flex-wrap items-center mt-0.5">
            {doc.expiry_date && <span>만료 {doc.expiry_date}</span>}
            {doc.verification_status === 'VALID' && (
              <span className="px-1.5 py-0.5 rounded-full bg-emerald-100 text-emerald-700 text-[10px]">검증완료</span>
            )}
            {isInvalid && (
              <span className="px-1.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-[10px]">검증실패</span>
            )}
            {hasOpenSupp && (
              <span className="px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700 text-[10px]">보완요청중</span>
            )}
          </div>
        </div>

        {(isBP || isAdmin) && !hasOpenSupp && (
          <div className="flex items-center gap-1 shrink-0">
            <label className="flex items-center gap-1 text-[11px] text-slate-600 cursor-pointer px-1.5 py-1 rounded hover:bg-slate-100"
                   title="여러 건 모아서 한 번에 보내기">
              <input type="checkbox"
                     checked={selectedForSupp.has(doc.id)}
                     onChange={() => toggleSupp(doc.id)} />
              <span>선택</span>
            </label>
            <button type="button" onClick={() => quickSupplement(doc)}
                    className="text-[11px] px-2 py-1 rounded bg-amber-600 text-white hover:bg-amber-700"
                    title="이 건만 즉시 보완요청">
              바로 보내기
            </button>
          </div>
        )}
      </div>
    );
  }

  function renderGroup(key: string, label: string, docs: Doc[], isOpen: boolean, onToggle: () => void, selectAllLabel: string) {
    return (
      <div key={key} className="rounded-lg border border-slate-200">
        <button onClick={onToggle} className="w-full flex items-center justify-between px-3 py-2 hover:bg-slate-50">
          <span className="text-sm font-semibold text-slate-900">{label}</span>
          <span className="text-xs text-slate-500">{isOpen ? '▾' : '▸'} 서류 {docs.length}건</span>
        </button>
        {isOpen && (
          <div className="px-3 py-2 border-t border-slate-100 space-y-1.5">
            {(isBP || isAdmin) && docs.length > 0 && (() => {
              const selectable = docs.filter((d) => !openSuppMap.has(suppKey(d.owner_type, d.owner_id, d.document_type_id)));
              if (selectable.length === 0) return null;
              const allOn = selectable.every((d) => selectedForSupp.has(d.id));
              return (
                <label className="flex items-center gap-1.5 text-[11px] text-slate-600 cursor-pointer py-1">
                  <input type="checkbox" checked={allOn} onChange={() => toggleSelectAll(docs)} />
                  <span>{selectAllLabel} ({selectable.length}건)</span>
                </label>
              );
            })()}
            {docs.length === 0 ? (
              <p className="text-xs text-slate-400">서류 없음</p>
            ) : docs.map((doc) => renderDocRow(doc))}
          </div>
        )}
      </div>
    );
  }

  if (dispatched.length === 0 && dispatchedPersons.length === 0) return null; // 차량·인원 send 전엔 표시 안 함

  return (
    <section className="card space-y-3">
      <div>
        <h2 className="text-lg font-bold text-slate-900">서류 묶음</h2>
        <p className="mt-1 text-xs text-slate-500">
          {isSupplier
            ? (myBundle ? '서류 묶음을 BP 에 보냈습니다.' : '차량·인원 서류를 확인하고 BP 에 보내주세요.')
            : '공급사가 보낸 차량·인원 서류. 항목별로 보완요청 가능.'}
        </p>
      </div>

      {/* 차량별 서류 펼침 — 공급사+BP+ADMIN 공통 view */}
      {dispatched.length > 0 && (
        <div className="space-y-2">
          {dispatchedPersons.length > 0 && <div className="text-xs font-semibold text-slate-400">차량</div>}
          {dispatched.map((d) => renderGroup(
            `eq-${d.id}`, d.equipment_label, docsByEq[d.equipment_id] ?? [],
            expanded.has(d.equipment_id), () => toggle(d.equipment_id), '이 차량 전체 선택',
          ))}
        </div>
      )}

      {/* 인원별 서류 펼침 */}
      {dispatchedPersons.length > 0 && (
        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-400">인원</div>
          {dispatchedPersons.map((d) => renderGroup(
            `p-${d.id}`, d.job_title ? `${d.person_label} · ${d.job_title}` : d.person_label,
            docsByPerson[d.person_id] ?? [],
            expandedP.has(d.person_id), () => toggleP2(d.person_id), '이 인원 전체 선택',
          ))}
        </div>
      )}

      {/* BP/ADMIN: 선택 일괄 보완요청 */}
      {(isBP || isAdmin) && selectedForSupp.size > 0 && (
        <div className="sticky bottom-2 z-10 flex items-center justify-between px-3 py-2 rounded-lg border border-amber-300 bg-amber-50 shadow-sm">
          <span className="text-sm text-amber-800">
            선택 <strong>{selectedForSupp.size}</strong>건 보완요청
          </span>
          <div className="flex gap-2">
            <button onClick={() => setSelectedForSupp(new Set())}
                    className="text-xs px-2 py-1 rounded border border-amber-300 text-amber-700 hover:bg-amber-100">
              선택 해제
            </button>
            <button onClick={() => setSuppDialogOpen(true)}
                    className="text-xs px-3 py-1 rounded bg-amber-600 text-white hover:bg-amber-700">
              보완요청 보내기
            </button>
          </div>
        </div>
      )}

      {/* 보완요청 사유 다이얼로그 */}
      {suppDialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
             onClick={() => !suppBusy && setSuppDialogOpen(false)}>
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md"
               onClick={(e) => e.stopPropagation()}>
            <div className="px-4 py-3 border-b">
              <h3 className="font-semibold text-slate-900">보완요청 보내기</h3>
              <p className="text-xs text-slate-500 mt-0.5">선택한 {selectedForSupp.size}건에 같은 사유로 일괄 발송합니다.</p>
            </div>
            <div className="p-4">
              <label className="block">
                <span className="text-xs font-medium text-slate-500">공통 사유 (선택)</span>
                <textarea value={suppReason} onChange={(e) => setSuppReason(e.target.value)}
                          rows={3} maxLength={2000} placeholder="예: 만료일 임박, 사본 상태 불량 등"
                          className="mt-1 w-full px-2 py-1.5 text-sm border border-slate-300 rounded" />
              </label>
            </div>
            <div className="flex justify-end gap-2 px-4 py-3 bg-slate-50 border-t rounded-b-xl">
              <button onClick={() => setSuppDialogOpen(false)} disabled={suppBusy}
                      className="px-3 py-1.5 text-sm rounded text-slate-700 hover:bg-slate-100">
                취소
              </button>
              <button onClick={submitSupplementBatch} disabled={suppBusy}
                      className="px-3 py-1.5 text-sm rounded bg-amber-600 text-white hover:bg-amber-700 disabled:opacity-50">
                {suppBusy ? '발송 중…' : '보내기'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 공급사 측: send 카드 */}
      {canSend && (
        <div className="rounded-lg border border-blue-300 bg-blue-50/30 p-3 space-y-2">
          <div className="text-sm font-semibold text-slate-900">서류 묶음 보내기 — 1회만 가능</div>

          {/* 만료/검증실패 경고 — 갱신 링크 포함 */}
          {docWarnings.length > 0 && (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-2.5 space-y-1.5">
              <div className="text-xs font-semibold text-amber-800">
                ⚠ 보내기 전 확인 — 만료/검증실패 서류 {docWarnings.length}건
              </div>
              <ul className="space-y-1">
                {docWarnings.map((w, i) => (
                  <li key={i} className="flex items-center justify-between gap-2 text-[11px]">
                    <span className="text-slate-700 truncate">
                      <span className="font-medium">{w.resLabel}</span> · {w.doc}
                      <span className={`ml-1.5 px-1 py-0.5 rounded-full text-[10px] ${
                        w.kind === 'expired' ? 'bg-rose-100 text-rose-700'
                        : w.kind === 'invalid' ? 'bg-rose-100 text-rose-700'
                        : 'bg-amber-100 text-amber-700'
                      }`}>
                        {w.kind === 'expired' ? `만료됨 ${w.date ?? ''}` : w.kind === 'invalid' ? '검증실패' : `만료임박 ${w.date ?? ''}`}
                      </span>
                    </span>
                    <a href={`${w.ownerType === 'PERSON' ? '/persons' : '/equipment'}/${w.ownerId}`} target="_blank" rel="noopener"
                       className="shrink-0 px-2 py-0.5 rounded border border-amber-400 text-amber-800 hover:bg-amber-100">
                      갱신하러 가기
                    </a>
                  </li>
                ))}
              </ul>
              <p className="text-[10px] text-amber-700">갱신 후 새로고침하면 경고가 사라집니다. 그대로 보낼 수도 있습니다.</p>
            </div>
          )}

          <div className="rounded-lg border border-slate-200 bg-white p-2.5 space-y-2">
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-800 cursor-pointer">
              <input type="checkbox" checked={includeEmail} onChange={(e) => setIncludeEmail(e.target.checked)} />
              <span>BP 회사 이메일로도 발송 (견적서 PDF + 서류 원본 첨부)</span>
            </label>
            {includeEmail && (
              <>
                {bpEmails.length > 0 ? (
                  <div className="space-y-1">
                    <div className="text-[11px] text-slate-500">BP 회사 사용자 (체크 해제 가능)</div>
                    {bpEmails.map((em) => (
                      <label key={em} className="flex items-center gap-2 text-xs">
                        <input type="checkbox" checked={selectedEmails.has(em)}
                               onChange={() => setSelectedEmails((prev) => {
                                 const next = new Set(prev);
                                 if (next.has(em)) next.delete(em); else next.add(em);
                                 return next;
                               })} />
                        <span className="text-slate-700">{em}</span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <div className="text-[11px] text-amber-600">
                    BP 회사 이메일을 자동으로 가져오지 못했습니다. 직접 추가하세요. (비워두면 서버에서 BP 관리자 이메일로 자동 발송)
                  </div>
                )}
                <div className="flex items-center gap-2">
                  <input type="email" value={addedEmail} onChange={(e) => setAddedEmail(e.target.value)}
                         placeholder="추가 수신자 이메일"
                         className="flex-1 px-2 py-1 text-xs border border-slate-300 rounded" />
                  <button type="button" onClick={() => {
                    const v = addedEmail.trim();
                    if (!v || !v.includes('@')) return;
                    setBpEmails((prev) => prev.includes(v) ? prev : [...prev, v]);
                    setSelectedEmails((prev) => { const n = new Set(prev); n.add(v); return n; });
                    setAddedEmail('');
                  }} className="px-2 py-1 text-xs rounded border border-slate-300 hover:bg-slate-50">+ 추가</button>
                </div>
                <div className="text-[11px] text-slate-500">선택 {selectedEmails.size}명에게 발송됩니다.</div>
              </>
            )}
          </div>
          <label className="block">
            <span className="text-xs font-medium text-slate-500">메모 (선택)</span>
            <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2}
                      className="mt-1 w-full px-2 py-1.5 text-sm border border-slate-300 rounded" />
          </label>
          <div className="flex justify-end">
            <button onClick={send} disabled={sending} className="btn-primary disabled:opacity-50">
              {sending ? '발송 중…' : 'BP 에 서류 묶음 보내기'}
            </button>
          </div>
        </div>
      )}

      {/* 받은 묶음 표시 (공급사 본인 또는 BP/ADMIN) */}
      {!loading && bundles.length > 0 && (
        <div className="text-xs text-slate-600 space-y-1 pt-2 border-t border-slate-100">
          {bundles.map((b) => (
            <div key={b.id}>
              ✓ <strong>{b.supplier_company_name}</strong> — {new Date(b.sent_at).toLocaleString('ko-KR')}
              {b.include_email && b.email_sent_at && <span className="ml-2 text-blue-600">📧 이메일 발송</span>}
            </div>
          ))}
        </div>
      )}

      {/* 문서 미리보기 모달 */}
      {previewDoc && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
             onClick={closePreview}>
          <div className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[92vh] flex flex-col"
               onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <div className="min-w-0">
                <h3 className="font-semibold text-slate-900 truncate">
                  {previewDoc.document_type_label ?? previewDoc.file_name ?? `문서 #${previewDoc.id}`}
                </h3>
                {previewDoc.file_name && previewDoc.document_type_label && (
                  <p className="text-[11px] text-slate-500 truncate">{previewDoc.file_name}</p>
                )}
              </div>
              <div className="flex gap-2 shrink-0">
                {previewUrl && (
                  <a href={previewUrl} download={previewDoc.file_name ?? `document-${previewDoc.id}`}
                     className="text-xs px-3 py-1.5 rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                    다운로드
                  </a>
                )}
                <button onClick={closePreview} className="text-slate-400 hover:text-slate-700 text-xl px-1">×</button>
              </div>
            </div>
            <div className="flex-1 overflow-auto bg-slate-100 flex items-center justify-center">
              {previewLoading && <p className="text-sm text-slate-400">로딩...</p>}
              {!previewLoading && previewUrl && (
                (() => {
                  const ct = previewDoc.content_type ?? '';
                  if (ct.startsWith('image/')) {
                    return <img src={previewUrl} alt="" className="max-w-full max-h-[80vh] object-contain" />;
                  }
                  if (ct === 'application/pdf') {
                    return <iframe src={previewUrl} title="preview" className="w-full h-[80vh]" />;
                  }
                  return (
                    <div className="text-sm text-slate-600 p-6">
                      미리보기를 지원하지 않는 형식입니다.
                      <a href={previewUrl} download={previewDoc.file_name ?? `document-${previewDoc.id}`}
                         className="ml-2 text-brand-600 hover:underline">다운로드</a>
                    </div>
                  );
                })()
              )}
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
