import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api';
import { daysUntilExpiry, type DocumentResponse, type VerificationStatus } from '../../types/document';

type Props = {
  doc: DocumentResponse;
  canEdit: boolean;
  isAdmin: boolean;
  /** type.verify_endpoint 있을 때만 true. 자동 검증 빠른 버튼 노출 여부. */
  canAutoVerify?: boolean;
  onOpen: () => void;
  onDelete: () => void;
  onToggleVerify: () => void;
  onAutoVerify?: () => void;       // V14: verify-api 자동 검증 트리거
  onReject?: () => void;            // V14: ADMIN 수동 반려
  onHistory?: () => void;           // S-4 단계 4: 갱신 이력 보기
  onRenew: () => void;
};

const VERIFICATION_BADGE: Record<VerificationStatus, { label: string; cls: string }> = {
  PENDING: { label: '확인 대기', cls: 'bg-slate-100 text-slate-600' },
  VERIFIED: { label: '검증 완료', cls: 'bg-emerald-100 text-emerald-700' },
  REJECTED: { label: '반려', cls: 'bg-rose-100 text-rose-700' },
  OCR_REVIEW_REQUIRED: { label: 'OCR 검토 필요', cls: 'bg-amber-100 text-amber-700' },
};

export default function DocumentCard({ doc, canEdit, isAdmin, canAutoVerify, onOpen, onDelete, onToggleVerify, onAutoVerify, onReject, onHistory, onRenew }: Props) {
  const [thumbUrl, setThumbUrl] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const isImage = doc.content_type.startsWith('image/');
  const isPdf = doc.content_type === 'application/pdf';

  useEffect(() => {
    if (!isImage) return;
    let revoked: string | null = null;
    api.get(`/api/documents/${doc.id}/file`, { responseType: 'blob' })
      .then((res) => {
        const url = URL.createObjectURL(res.data);
        revoked = url;
        setThumbUrl(url);
      })
      .catch(() => { /* ignore */ });
    return () => { if (revoked) URL.revokeObjectURL(revoked); };
  }, [doc.id, isImage]);

  useEffect(() => {
    if (!menuOpen) return;
    function onClick(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, [menuOpen]);

  const days = daysUntilExpiry(doc.expiry_date);
  const expired = days != null && days < 0;
  const soon = days != null && days >= 0 && days <= 30;

  // 만료 상태가 우선 (서류 자체는 VERIFIED 일 수 있어도 만료되면 표시는 만료).
  // V14: 만료가 아니면 verification_status 기반 라벨/색.
  const verificationBadge = VERIFICATION_BADGE[doc.verification_status] ?? VERIFICATION_BADGE.PENDING;
  const statusLabel = expired ? '만료됨' : soon ? '만료임박' : verificationBadge.label;
  const statusClass = expired
    ? 'bg-rose-100 text-rose-700'
    : soon
      ? 'bg-amber-100 text-amber-700'
      : verificationBadge.cls;

  return (
    // overflow-hidden → overflow-visible: kebab dropdown 이 카드 경계에서 잘리지 않도록.
    // 썸네일 자체는 별도 button 의 overflow-hidden 으로 코너 처리.
    <div className="rounded-xl border border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md flex flex-col">
      {/* 썸네일 (코너 클립용 별도 wrapper) */}
      <button
        type="button"
        onClick={onOpen}
        className="relative block aspect-[4/3] w-full overflow-hidden rounded-t-xl bg-slate-100 hover:bg-slate-200 transition-colors"
        title="클릭하여 열기"
      >
        {isImage && thumbUrl ? (
          <img src={thumbUrl} alt={doc.file_name} className="w-full h-full object-contain" />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-slate-400 gap-2">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
            <span className="text-xs">{isPdf ? 'PDF' : '파일'}</span>
          </div>
        )}
        {/* 검증 corner badge — 만료/상태와 별개로 검증 여부만 표시 */}
        {canAutoVerify && (
          doc.verification_status === 'VERIFIED' ? (
            <span className="absolute top-2 left-2 inline-flex items-center gap-1 rounded-full bg-emerald-600 text-white shadow px-2 py-0.5 text-[11px] font-bold ring-2 ring-white">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
              검증
            </span>
          ) : doc.verification_status === 'REJECTED' ? (
            <span className="absolute top-2 left-2 inline-flex items-center gap-1 rounded-full bg-rose-600 text-white shadow px-2 py-0.5 text-[11px] font-bold ring-2 ring-white">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
              반려
            </span>
          ) : (
            <span className="absolute top-2 left-2 inline-flex items-center gap-1 rounded-full bg-slate-500 text-white shadow px-2 py-0.5 text-[11px] font-bold ring-2 ring-white">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
              미검증
            </span>
          )
        )}
      </button>

      {/* 정보 */}
      <div className="flex flex-col p-3 gap-2 flex-1">
        <div className="flex items-start justify-between gap-2 min-w-0">
          <button
            type="button"
            onClick={onOpen}
            className="block min-w-0 flex-1 truncate text-left text-sm font-bold text-slate-900 hover:text-brand-700"
            title={`${doc.document_type_name} — ${doc.file_name}`}
          >
            {doc.document_type_name}
          </button>
          {(canEdit || isAdmin) && (
            <div className="relative shrink-0" ref={menuRef}>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); setMenuOpen((v) => !v); }}
                className="text-slate-400 hover:text-slate-700 p-1"
                aria-label="더보기"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="1.6" /><circle cx="12" cy="12" r="1.6" /><circle cx="12" cy="19" r="1.6" /></svg>
              </button>
              {menuOpen && (
                <div className="absolute right-0 top-full mt-1 z-50 min-w-[140px] bg-white rounded-lg shadow-lg border border-slate-200 py-1">
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onRenew(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      재업로드
                    </button>
                  )}
                  {onHistory && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onHistory(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      이력 보기
                    </button>
                  )}
                  {/* V14 자동 검증 (verify-api). canEdit (자기 회사 공급사) + ADMIN 모두 가능 */}
                  {(canEdit || isAdmin) && onAutoVerify && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onAutoVerify(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      자동 검증
                    </button>
                  )}
                  {/* 진위 확정(수동 검증) — ADMIN 전체 + 자기/자식 소유 자원 공급사(canEdit). 백엔드 ensureCanModify 로 최종 스코프 강제. */}
                  {(canEdit || isAdmin) && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onToggleVerify(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      {doc.verified ? '검증 취소' : '검증 표시'}
                    </button>
                  )}
                  {/* V14 반려 (ADMIN 만, 사유 필수) */}
                  {isAdmin && onReject && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onReject(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm text-rose-600 hover:bg-rose-50"
                    >
                      반려
                    </button>
                  )}
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onDelete(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm text-rose-600 hover:bg-rose-50"
                    >
                      삭제
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="flex items-end justify-between gap-2">
          <div className="min-w-0">
            <div className="text-xs text-slate-500">만료일</div>
            <div className="text-sm font-semibold text-slate-900">{doc.expiry_date ?? '-'}</div>
          </div>
          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${statusClass}`}>
            {statusLabel}
          </span>
        </div>

        {canEdit && (expired || soon || doc.verification_status === 'REJECTED' || doc.verification_status === 'OCR_REVIEW_REQUIRED') && (
          <div className="mt-1 pt-2 border-t border-slate-100 flex flex-wrap gap-1.5">
            {canAutoVerify && onAutoVerify && (doc.verification_status === 'REJECTED' || doc.verification_status === 'OCR_REVIEW_REQUIRED') && (
              <button type="button" onClick={onAutoVerify}
                className="inline-flex items-center gap-1 rounded-md bg-brand-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-brand-700">
                재검증
              </button>
            )}
            <button type="button" onClick={onRenew}
              className={`inline-flex items-center gap-1 rounded-md px-2.5 py-1 text-xs font-semibold ${
                expired ? 'bg-rose-600 text-white hover:bg-rose-700'
                  : soon ? 'bg-amber-500 text-white hover:bg-amber-600'
                  : 'border border-slate-300 text-slate-700 hover:bg-slate-50'
              }`}>
              재업로드
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
