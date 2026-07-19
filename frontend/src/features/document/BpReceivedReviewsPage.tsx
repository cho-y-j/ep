import { useCallback, useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import { VERIFICATION_STATUS_LABEL, type VerificationStatus } from '../../types/document';
import IssueResourceCheckDialog from '../resourceCheck/IssueResourceCheckDialog';
import type { ResourceOwnerType } from '../../types/resourceCheck';

type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

type ReviewItem = {
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  label: string;
  doc_count: number;
};
type Review = {
  id: number;
  supplier_company_id: number;
  supplier_company_name: string | null;
  supplier_company_type: 'BP' | 'EQUIPMENT' | 'MANPOWER' | null;
  message: string | null;
  sent_at: string;
  read_at: string | null;
  status: ReviewStatus;
  rejected_reason: string | null;
  acted_at: string | null;
  total_docs: number;
  items: ReviewItem[];
};
type ReviewDoc = {
  id: number;
  document_type_name: string;
  file_name: string;
  expiry_date: string | null;
  has_expiry: boolean;
  verification_status: VerificationStatus;
  verified: boolean;
  rejected_reason: string | null;
};
type DocGroup = {
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  label: string;
  documents: ReviewDoc[];
};

const STATUS_BADGE: Record<ReviewStatus, { label: string; cls: string }> = {
  PENDING: { label: '심사중', cls: 'bg-amber-100 text-amber-700' },
  APPROVED: { label: '승인', cls: 'bg-emerald-100 text-emerald-700' },
  REJECTED: { label: '반려', cls: 'bg-rose-100 text-rose-700' },
};

// 정부 API 검증 사유 코드 → 한글 (BP 뷰어 국소 표기용).
const VERIFY_REASON_LABEL: Record<string, string> = {
  NTS_INVALID: '국세청 검증 실패 — 사업자번호가 휴/폐업 또는 미등록',
  BIZNAME_MISMATCH: '회사명 불일치',
  UNKNOWN: '판정 불가 — OCR 결과 부족',
  TIMEOUT: '검증 API 시간 초과',
  OCR_ERROR: 'OCR 처리 실패',
};

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** BP사 계정 "받은 서류 심사" — 봉투 목록 + 봉투 상세(인라인 뷰어·승인/반려·후속 액션). */
export default function BpReceivedReviewsPage() {
  const [rows, setRows] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  // 클라이언트 필터/정렬 — 로드된 봉투 목록을 좁힘(백엔드 무접촉).
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [sort, setSort] = useState<'newest' | 'oldest'>('newest');

  const refresh = useCallback(() => {
    setLoading(true);
    api.get<Review[]>('/api/document-reviews/received')
      .then((r) => setRows(r.data))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  function toggleAll() {
    setSelected((prev) => {
      const next = new Set(prev);
      if (filtered.length > 0 && filtered.every((r) => prev.has(r.id))) filtered.forEach((r) => next.delete(r.id));
      else filtered.forEach((r) => next.add(r.id));
      return next;
    });
  }
  function markReadLocal(ids: number[]) {
    const now = new Date().toISOString();
    setRows((prev) => prev.map((x) => (ids.includes(x.id) && !x.read_at ? { ...x, read_at: now } : x)));
  }
  function applyUpdated(updated: Review) {
    setRows((prev) => prev.map((x) => (x.id === updated.id ? { ...x, ...updated } : x)));
  }

  async function download(rev: Review) {
    setBusy(rev.id);
    try {
      const res = await api.get(`/api/document-reviews/${rev.id}/download`, { responseType: 'blob', timeout: 120_000 });
      saveBlob(res.data as Blob, `서류심사-${rev.supplier_company_name ?? rev.supplier_company_id}-${rev.id}.zip`);
      if (!rev.read_at) {
        await api.post(`/api/document-reviews/${rev.id}/read`).catch(() => {});
        markReadLocal([rev.id]);
      }
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '다운로드 실패');
    } finally {
      setBusy(null);
    }
  }

  async function downloadSelected() {
    const ids = Array.from(selected);
    if (ids.length === 0) return;
    setBulkBusy(true);
    try {
      const res = await api.post('/api/document-reviews/download', { ids }, { responseType: 'blob', timeout: 300_000 });
      saveBlob(res.data as Blob, `서류심사-모음-${ids.length}건.zip`);
      markReadLocal(ids);
      setSelected(new Set());
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '다운로드 실패');
    } finally {
      setBulkBusy(false);
    }
  }

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => {
    const out = rows.filter((r) => {
      if (statusFilter && r.status !== statusFilter) return false;
      if (typeFilter && !r.items.some((it) => it.owner_type === typeFilter)) return false;
      if (qLower) {
        const hay = `${r.supplier_company_name ?? ''} ${r.items.map((it) => it.label).join(' ')}`.toLowerCase();
        if (!hay.includes(qLower)) return false;
      }
      return true;
    });
    out.sort((a, b) => {
      const ta = new Date(a.sent_at).getTime();
      const tb = new Date(b.sent_at).getTime();
      return sort === 'oldest' ? ta - tb : tb - ta;
    });
    return out;
  }, [rows, statusFilter, typeFilter, qLower, sort]);
  const activeFilterCount = [q, statusFilter, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); setTypeFilter(''); };

  const openReview = openId != null ? rows.find((r) => r.id === openId) ?? null : null;
  const allChecked = filtered.length > 0 && filtered.every((r) => selected.has(r.id));

  if (openReview) {
    return (
      <ReviewDetail
        review={openReview}
        onBack={() => setOpenId(null)}
        onUpdated={applyUpdated}
        onDownload={() => download(openReview)}
        downloading={busy === openReview.id}
      />
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '받은 서류 심사' }]}>
      <div className="max-w-4xl mx-auto px-6 py-8 space-y-4">
        <PageHeader
          title="받은 서류 심사"
          subtitle="공급사가 보낸 자원별 서류 묶음입니다. 봉투를 열어 서류를 확인하고 승인/반려하거나, 압축파일(zip)로 내려받을 수 있습니다."
        />

        {loading ? (
          <div className="card p-8 text-center text-sm text-slate-400">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">받은 서류 심사가 없습니다.</div>
        ) : (
          <>
            <FilterBar
              search={{ value: q, onChange: setQ, placeholder: '업체·자원 검색' }}
              activeFilterCount={activeFilterCount}
              onReset={resetFilters}
              sort={
                <select value={sort} onChange={(e) => setSort(e.target.value as 'newest' | 'oldest')}
                        className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
                  <option value="newest">최신순</option>
                  <option value="oldest">오래된순</option>
                </select>
              }
            >
              <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체"
                options={[
                  { value: 'PENDING', label: '심사중' },
                  { value: 'APPROVED', label: '승인' },
                  { value: 'REJECTED', label: '반려' },
                ]} />
              <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="자원종류 전체"
                options={[
                  { value: 'EQUIPMENT', label: '장비' },
                  { value: 'PERSON', label: '인원' },
                ]} />
            </FilterBar>

            {/* 선택 도구 막대 */}
            <div className="flex items-center justify-between px-1">
              <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 cursor-pointer select-none">
                <input type="checkbox" checked={allChecked} onChange={toggleAll}
                       className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                전체 선택 {selected.size > 0 && <span className="text-slate-400">({selected.size}건)</span>}
              </label>
              <button
                type="button"
                onClick={downloadSelected}
                disabled={selected.size === 0 || bulkBusy}
                className="px-3 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-40"
              >
                {bulkBusy ? '받는 중…' : `선택 다운로드${selected.size > 0 ? ` (${selected.size}건)` : ''}`}
              </button>
            </div>

            {filtered.length === 0 ? (
              <div className="card p-8 text-center text-sm text-slate-400">조건에 맞는 서류 심사가 없습니다.</div>
            ) : (
            <div className="space-y-3">
              {filtered.map((rev) => {
                const badge = STATUS_BADGE[rev.status];
                return (
                  <div key={rev.id} className={`card p-4 ${selected.has(rev.id) ? 'ring-2 ring-brand-200 border-brand-300' : ''}`}>
                    <div className="flex items-start gap-3">
                      <input
                        type="checkbox"
                        checked={selected.has(rev.id)}
                        onChange={() => toggle(rev.id)}
                        onClick={(e) => e.stopPropagation()}
                        className="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                      />
                      <button type="button" onClick={() => setOpenId(rev.id)} className="flex-1 min-w-0 text-left">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="font-bold text-slate-900 truncate">
                                {rev.supplier_company_name ?? `공급사 #${rev.supplier_company_id}`}
                              </span>
                              <span className={`inline-flex items-center px-1.5 h-5 rounded-full text-[11px] font-semibold ${badge.cls}`}>
                                {badge.label}
                              </span>
                              {!rev.read_at && (
                                <span className="inline-flex items-center px-1.5 h-5 rounded-full bg-brand-600 text-white text-[11px] font-semibold">
                                  NEW
                                </span>
                              )}
                            </div>
                            <div className="text-xs text-slate-500 mt-0.5">
                              {new Date(rev.sent_at).toLocaleString('ko-KR')} · 자원 {rev.items.length}건 · 서류 {rev.total_docs}건
                            </div>
                          </div>
                          <span className="shrink-0 text-xs font-semibold text-brand-700">열기 ›</span>
                        </div>

                        {rev.message && (
                          <div className="mt-2 text-sm text-slate-700 bg-slate-50 rounded-lg px-3 py-2 whitespace-pre-wrap">
                            {rev.message}
                          </div>
                        )}

                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {rev.items.map((it) => (
                            <span
                              key={`${it.owner_type}:${it.owner_id}`}
                              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium ${
                                it.owner_type === 'EQUIPMENT' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                              }`}
                            >
                              {it.label} <span className="opacity-70">· {it.doc_count}건</span>
                            </span>
                          ))}
                        </div>
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
            )}
          </>
        )}
      </div>
    </AppShell>
  );
}

/** 봉투 상세 — 자원별 문서 리스트 + 인라인 뷰어 + 승인/반려 + 승인 후속 액션. */
function ReviewDetail({ review, onBack, onUpdated, onDownload, downloading }: {
  review: Review;
  onBack: () => void;
  onUpdated: (r: Review) => void;
  onDownload: () => void;
  downloading: boolean;
}) {
  const [groups, setGroups] = useState<DocGroup[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [selectedDoc, setSelectedDoc] = useState<ReviewDoc | null>(null);
  const [activeOwner, setActiveOwner] = useState<DocGroup | null>(null);
  const [acting, setActing] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [checkOpen, setCheckOpen] = useState(false);

  useEffect(() => {
    setLoadingDocs(true);
    api.get<DocGroup[]>(`/api/document-reviews/${review.id}/documents`)
      .then((r) => {
        setGroups(r.data);
        setActiveOwner(r.data[0] ?? null);
        const firstDoc = r.data.flatMap((g) => g.documents)[0] ?? null;
        setSelectedDoc(firstDoc);
      })
      .catch(() => setGroups([]))
      .finally(() => setLoadingDocs(false));
  }, [review.id]);

  async function approve() {
    if (acting) return;
    setActing(true);
    try {
      const res = await api.post<Review>(`/api/document-reviews/${review.id}/approve`);
      onUpdated(res.data);
      toast.success('승인되었습니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '승인 실패');
    } finally {
      setActing(false);
    }
  }

  async function submitReject() {
    if (!reason.trim()) { toast.error('반려 사유를 입력하세요'); return; }
    setActing(true);
    try {
      const res = await api.post<Review>(`/api/document-reviews/${review.id}/reject`, { reason: reason.trim() });
      onUpdated(res.data);
      setRejectOpen(false);
      setReason('');
      toast.success('반려 처리되었습니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '반려 실패');
    } finally {
      setActing(false);
    }
  }

  function openWorkPlan() {
    const params = new URLSearchParams();
    const supplierName = review.supplier_company_name ?? `공급사 #${review.supplier_company_id}`;
    params.set('title', `${supplierName} 작업계획`);
    // 발신 공급사의 회사 타입 기준으로만 프리필 — 봉투에 담긴 자원(owner_type)이 아니라 발신사 타입.
    // (장비공급사가 PERSON 자원을 보내도 manpowerSupplierId 로 잘못 넣지 않도록.)
    if (review.supplier_company_type === 'EQUIPMENT') {
      params.set('equipmentSupplierId', String(review.supplier_company_id));
    } else if (review.supplier_company_type === 'MANPOWER') {
      params.set('manpowerSupplierId', String(review.supplier_company_id));
    }
    window.open(`/work-plans/new?${params.toString()}`, '_blank', 'noopener');
  }

  const badge = STATUS_BADGE[review.status];

  return (
    <AppShell breadcrumb={[{ label: '받은 서류 심사', to: '/document-reviews/received' }, { label: '봉투 상세' }]}>
      <div className="max-w-6xl mx-auto px-6 py-6 space-y-4">
        <button type="button" onClick={onBack} className="text-sm text-slate-600 hover:text-slate-900">← 목록으로</button>

        {/* 헤더 + 액션 */}
        <div className="card p-4 space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold truncate">
                  {review.supplier_company_name ?? `공급사 #${review.supplier_company_id}`}
                </h1>
                <span className={`inline-flex items-center px-2 h-6 rounded-full text-xs font-semibold ${badge.cls}`}>
                  {badge.label}
                </span>
              </div>
              <div className="text-xs text-slate-500 mt-1">
                {new Date(review.sent_at).toLocaleString('ko-KR')} · 자원 {review.items.length}건 · 서류 {review.total_docs}건
              </div>
            </div>
            <button
              type="button"
              onClick={onDownload}
              disabled={downloading}
              className="shrink-0 px-3 py-2 rounded-lg bg-slate-100 text-slate-700 text-sm font-semibold hover:bg-slate-200 disabled:opacity-50"
            >
              {downloading ? '받는 중…' : 'zip 다운로드'}
            </button>
          </div>

          {review.message && (
            <div className="text-sm text-slate-700 bg-slate-50 rounded-lg px-3 py-2 whitespace-pre-wrap">{review.message}</div>
          )}

          {review.status === 'REJECTED' && review.rejected_reason && (
            <div className="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2">
              <span className="font-semibold">반려 사유:</span> {review.rejected_reason}
            </div>
          )}

          {/* 액션 바 */}
          <div className="flex flex-wrap items-center gap-2 pt-1">
            {review.status === 'PENDING' && (
              <>
                <button type="button" onClick={approve} disabled={acting}
                        className="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm font-semibold hover:bg-emerald-700 disabled:opacity-50">
                  승인
                </button>
                <button type="button" onClick={() => setRejectOpen(true)} disabled={acting}
                        className="px-4 py-2 rounded-lg bg-rose-600 text-white text-sm font-semibold hover:bg-rose-700 disabled:opacity-50">
                  반려
                </button>
              </>
            )}
            {review.status === 'APPROVED' && (
              <>
                <span className="text-xs text-slate-500 mr-1">승인 완료 — 후속 작업:</span>
                <button type="button" onClick={openWorkPlan}
                        className="px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700">
                  작업계획서 만들기
                </button>
                <button type="button" onClick={() => setCheckOpen(true)} disabled={!activeOwner}
                        className="px-4 py-2 rounded-lg border border-brand-300 text-brand-700 text-sm font-semibold hover:bg-brand-50 disabled:opacity-50">
                  검사·교육·검진 날짜 통보
                  {activeOwner && <span className="ml-1 text-xs font-normal text-slate-500">(대상: {activeOwner.label})</span>}
                </button>
              </>
            )}
          </div>
        </div>

        {/* 자원별 문서 리스트 + 뷰어 */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
          <div className="lg:col-span-5 space-y-3">
            {loadingDocs ? (
              <div className="card p-8 text-center text-sm text-slate-400">서류 불러오는 중…</div>
            ) : groups.length === 0 ? (
              <div className="card p-8 text-center text-sm text-slate-400">서류가 없습니다.</div>
            ) : groups.map((g) => {
              const isActive = activeOwner?.owner_type === g.owner_type && activeOwner?.owner_id === g.owner_id;
              return (
                <div key={`${g.owner_type}:${g.owner_id}`}
                     className={`rounded-xl border bg-white overflow-hidden ${isActive ? 'border-brand-300 ring-1 ring-brand-200' : 'border-slate-200'}`}>
                  <button type="button" onClick={() => setActiveOwner(g)}
                          className="w-full flex items-center justify-between gap-2 px-3 py-2 border-b border-slate-100 text-left">
                    <span className="flex items-center gap-2 min-w-0">
                      <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold ${
                        g.owner_type === 'EQUIPMENT' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                      }`}>
                        {g.owner_type === 'EQUIPMENT' ? '장비' : '인원'}
                      </span>
                      <span className="font-semibold text-slate-900 truncate">{g.label}</span>
                    </span>
                    <span className="text-[11px] text-slate-400 shrink-0">{g.documents.length}건</span>
                  </button>
                  {g.documents.length === 0 ? (
                    <div className="px-3 py-4 text-xs text-slate-400 text-center">등록된 서류가 없습니다.</div>
                  ) : (
                    <div className="divide-y divide-slate-100">
                      {g.documents.map((d) => {
                        const sel = selectedDoc?.id === d.id;
                        const cls = d.verification_status === 'REJECTED' ? 'bg-rose-100 text-rose-700'
                          : d.verification_status === 'VERIFIED' ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-amber-100 text-amber-700';
                        return (
                          <button key={d.id} type="button"
                                  onClick={() => { setSelectedDoc(d); setActiveOwner(g); }}
                                  className={`w-full flex items-center justify-between gap-2 px-3 py-2 text-left ${sel ? 'bg-brand-50' : 'hover:bg-slate-50'}`}>
                            <span className="min-w-0">
                              <span className="block text-sm font-medium text-slate-900 truncate">{d.document_type_name}</span>
                              <span className="block text-[11px] text-slate-500 truncate">
                                {d.file_name}{d.has_expiry && d.expiry_date ? ` · 만료 ${d.expiry_date}` : ''}
                              </span>
                            </span>
                            <span className={`shrink-0 inline-flex px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${cls}`}>
                              {VERIFICATION_STATUS_LABEL[d.verification_status] ?? d.verification_status}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          <div className="lg:col-span-7">
            {selectedDoc ? <DocViewer doc={selectedDoc} /> : (
              <div className="rounded-xl border border-dashed border-slate-300 bg-white p-12 text-center text-slate-400">
                왼쪽에서 서류를 선택하면 여기에서 원본을 볼 수 있습니다.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 반려 사유 다이얼로그 */}
      {rejectOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
            <div className="px-5 py-3 border-b">
              <h3 className="font-bold text-slate-900">서류 심사 반려</h3>
            </div>
            <div className="px-5 py-4">
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">반려 사유</span>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={4} autoFocus
                          placeholder="반려 사유를 입력하세요"
                          className="mt-1 w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded" />
              </label>
            </div>
            <div className="px-5 py-3 border-t flex justify-end gap-2">
              <button type="button" onClick={() => { setRejectOpen(false); setReason(''); }}
                      className="px-3 py-1.5 rounded text-sm text-slate-700 hover:bg-slate-100">취소</button>
              <button type="button" onClick={submitReject} disabled={acting}
                      className="px-4 py-1.5 rounded bg-rose-600 text-white text-sm font-semibold hover:bg-rose-700 disabled:opacity-50">
                {acting ? '처리 중…' : '반려'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 검사·교육·검진 날짜 통보 다이얼로그 (계획서 없이 발행) */}
      {checkOpen && activeOwner && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckOpen(false)}
          onIssued={() => {}}
          workPlanId={null}
          ownerType={activeOwner.owner_type as ResourceOwnerType}
          ownerId={activeOwner.owner_id}
          ownerLabel={activeOwner.label}
          supplierCompanyId={review.supplier_company_id}
          supplierCompanyName={review.supplier_company_name}
        />
      )}
    </AppShell>
  );
}

/** 문서 인라인 뷰어 — /api/documents/{id}/file blob (이미지/PDF) + 만료일 + 검증상태/사유. */
function DocViewer({ doc }: { doc: ReviewDoc }) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [isPdf, setIsPdf] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    let revoked = false;
    let created: string | null = null;
    setBlobUrl(null);
    setErr(null);
    api.get(`/api/documents/${doc.id}/file`, { responseType: 'blob' })
      .then((res) => {
        const blob = res.data as Blob;
        setIsPdf(blob.type === 'application/pdf' || doc.file_name.toLowerCase().endsWith('.pdf'));
        created = URL.createObjectURL(blob);
        if (!revoked) setBlobUrl(created);
      })
      .catch((e) => setErr(e?.response?.data?.message ?? '미리보기 로드 실패'));
    return () => { revoked = true; if (created) URL.revokeObjectURL(created); };
  }, [doc.id, doc.file_name]);

  const statusCls = doc.verification_status === 'REJECTED' ? 'bg-rose-100 text-rose-700'
    : doc.verification_status === 'VERIFIED' ? 'bg-emerald-100 text-emerald-700'
    : 'bg-amber-100 text-amber-700';
  const reasonLabel = doc.rejected_reason
    ? (VERIFY_REASON_LABEL[doc.rejected_reason] ?? doc.rejected_reason)
    : null;

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="font-bold text-slate-900 truncate">{doc.document_type_name}</div>
          <div className="text-xs text-slate-500 truncate">{doc.file_name}</div>
        </div>
        <span className={`shrink-0 text-xs px-2 py-1 rounded-full font-semibold ${statusCls}`}>
          {VERIFICATION_STATUS_LABEL[doc.verification_status] ?? doc.verification_status}
        </span>
      </div>

      <div className="bg-slate-100 p-3 flex items-center justify-center min-h-[400px] max-h-[600px] overflow-auto">
        {err ? (
          <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded px-3 py-2">{err}</p>
        ) : !blobUrl ? (
          <p className="text-sm text-slate-400">로딩 중…</p>
        ) : isPdf ? (
          <iframe src={blobUrl} sandbox="" title={doc.file_name} className="w-full h-[560px] border-0 bg-white rounded" />
        ) : (
          <img src={blobUrl} alt={doc.file_name} className="max-w-full max-h-[560px] object-contain rounded shadow" />
        )}
      </div>

      <div className="px-4 py-3 space-y-2 text-sm">
        {doc.has_expiry && (
          <div className="flex gap-2">
            <span className="w-20 shrink-0 text-slate-500">만료일</span>
            <span className="text-slate-900">{doc.expiry_date ?? '없음'}</span>
          </div>
        )}
        {reasonLabel && (
          <div className="flex gap-2">
            <span className="w-20 shrink-0 text-slate-500">검증 사유</span>
            <span className="text-rose-700">{reasonLabel}</span>
          </div>
        )}
      </div>
    </div>
  );
}
