import { useEffect, useRef, useState } from 'react';
import { api } from '../../lib/api';
import { daysUntilExpiry, type DocumentResponse } from '../../types/document';

type Props = {
  doc: DocumentResponse;
  canEdit: boolean;
  isAdmin: boolean;
  onOpen: () => void;
  onDelete: () => void;
  onToggleVerify: () => void;
  onRenew: () => void;
};

export default function DocumentCard({ doc, canEdit, isAdmin, onOpen, onDelete, onToggleVerify, onRenew }: Props) {
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

  // outside click 닫기
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

  const statusLabel = expired ? '만료됨' : soon ? '만료임박' : doc.verified ? '검증완료' : '확인대기';
  const statusClass = expired
    ? 'bg-rose-50 text-rose-700 ring-rose-200'
    : soon
      ? 'bg-amber-50 text-amber-700 ring-amber-200'
      : doc.verified
        ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
        : 'bg-slate-100 text-slate-600 ring-slate-200';

  return (
    <div className="flex overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm transition-shadow hover:shadow-md">
      <button
        type="button"
        onClick={onOpen}
        className="m-3 flex h-28 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-slate-100 transition-colors hover:bg-slate-200"
        title="클릭하여 열기"
      >
        {isImage && thumbUrl ? (
          <img src={thumbUrl} alt={doc.file_name} className="w-full h-full object-cover" />
        ) : isPdf ? (
          <div className="text-slate-400 flex flex-col items-center gap-1 text-[10px]">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
            <span>PDF</span>
          </div>
        ) : (
          <div className="text-slate-400 flex flex-col items-center gap-1 text-[10px]">
            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <polyline points="14 2 14 8 20 8" />
            </svg>
            <span>파일</span>
          </div>
        )}
      </button>

      <div className="flex min-w-0 flex-1 flex-col p-4 pl-1">
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
            <div className="relative" ref={menuRef}>
              <button
                type="button"
                onClick={(e) => { e.stopPropagation(); setMenuOpen((v) => !v); }}
                className="text-slate-400 hover:text-slate-700 px-1"
                aria-label="더보기"
              >
                ⋮
              </button>
              {menuOpen && (
                <div className="absolute right-0 top-full mt-1 z-10 min-w-[120px] bg-white rounded-lg shadow-md border border-slate-200 py-1">
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onRenew(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      재업로드
                    </button>
                  )}
                  {isAdmin && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onToggleVerify(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm hover:bg-slate-50"
                    >
                      {doc.verified ? '검증 취소' : '검증 표시'}
                    </button>
                  )}
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => { setMenuOpen(false); onDelete(); }}
                      className="block w-full text-left px-3 py-1.5 text-sm text-red-600 hover:bg-red-50"
                    >
                      삭제
                    </button>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="mt-3 space-y-1 text-xs text-slate-500">
          <p>만료일</p>
          <p className="text-sm font-semibold text-slate-900">{doc.expiry_date ?? '-'}</p>
        </div>

        <div className="mt-auto pt-3">
          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ${statusClass}`}>
            {soon && !expired && days != null ? `${statusLabel} D-${days}` : statusLabel}
          </span>
        </div>
      </div>
    </div>
  );
}
