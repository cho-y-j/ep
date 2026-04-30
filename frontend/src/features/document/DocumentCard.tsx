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

  return (
    <div className="rounded-lg border border-slate-200 bg-white overflow-hidden flex flex-col">
      <button
        type="button"
        onClick={onOpen}
        className="aspect-[4/3] bg-slate-100 flex items-center justify-center overflow-hidden hover:bg-slate-200 transition-colors"
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

      <div className="p-2 flex-1 flex flex-col">
        <div className="flex items-start justify-between gap-2">
          <button
            type="button"
            onClick={onOpen}
            className="text-xs font-medium text-slate-900 hover:text-brand-700 truncate text-left"
            title={doc.file_name}
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

        {doc.expiry_date && (
          <div className="mt-1 text-xs text-slate-500">만료 {doc.expiry_date}</div>
        )}

        <div className="mt-auto pt-2 flex flex-wrap gap-1">
          {doc.verified && (
            <span className="inline-flex px-1.5 py-0.5 rounded bg-green-100 text-green-700 text-xs">검증완료</span>
          )}
          {expired && (
            <span className="inline-flex px-1.5 py-0.5 rounded bg-red-100 text-red-700 text-xs">만료됨</span>
          )}
          {soon && !expired && (
            <span className="inline-flex px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 text-xs">{days}일 남음</span>
          )}
        </div>
      </div>
    </div>
  );
}
