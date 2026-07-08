import { useEffect, useState } from 'react';
import { api } from '../../../../lib/api';
import type { DocumentResponse } from '../../../../types/document';

interface DocCardProps {
  doc: DocumentResponse;
  ownerHint?: string;
  checked: boolean;
  onToggle: () => void;
  onPreview: () => void;
  /** 이미지 첨부 위/아래 이동 — 콜백 없으면 버튼 숨김. */
  onMove?: (dir: -1 | 1) => void;
}

/** 첨부 서류 카드 — 썸네일(이미지) / 파일 아이콘(비이미지) + 서류명 + 만료일 + 상태 chip. */
export function DocCard({ doc, ownerHint, checked, onToggle, onPreview, onMove }: DocCardProps) {
  const isImage = doc.content_type.startsWith('image/');
  const isPdf = doc.content_type === 'application/pdf';
  const [thumbUrl, setThumbUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!isImage) return;
    let revoked: string | null = null;
    api
      .get(`/api/documents/${doc.id}/file`, { responseType: 'blob' })
      .then((res) => {
        const url = URL.createObjectURL(res.data);
        revoked = url;
        setThumbUrl(url);
      })
      .catch(() => {});
    return () => {
      if (revoked) URL.revokeObjectURL(revoked);
    };
  }, [doc.id, isImage]);

  const expDate = doc.expiry_date ? new Date(doc.expiry_date) : null;
  const now = Date.now();
  const expired = expDate ? expDate.getTime() < now : false;
  const expSoon = expDate ? !expired && expDate.getTime() - now < 30 * 24 * 60 * 60 * 1000 : false;
  const expStr = expDate ? expDate.toISOString().slice(0, 10) : null;

  const chip = !doc.verified
    ? { label: '미검증', cls: 'bg-amber-100 text-amber-800' }
    : expired
    ? { label: '만료', cls: 'bg-rose-100 text-rose-800' }
    : expSoon
    ? { label: '만료임박', cls: 'bg-amber-100 text-amber-800' }
    : { label: '검증완료', cls: 'bg-emerald-100 text-emerald-800' };

  return (
    <div
      className={`relative rounded-xl border bg-white overflow-hidden transition shadow-sm ${
        checked ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200 opacity-70 hover:opacity-100'
      }`}
    >
      <label className="absolute top-2 left-2 z-10 bg-white/95 rounded p-0.5 shadow-sm cursor-pointer">
        <input
          type="checkbox"
          checked={checked}
          onChange={onToggle}
          className="w-4 h-4 cursor-pointer block"
          title={checked ? '첨부 포함됨' : '첨부 제외'}
        />
      </label>

      {isImage && onMove && checked && (
        <div className="absolute top-2 right-2 z-10 flex flex-col gap-1">
          <button
            type="button"
            onClick={() => onMove(-1)}
            className="bg-white/95 border border-slate-200 rounded w-6 h-6 text-xs leading-none hover:bg-slate-50 shadow-sm"
            title="위로"
          >
            ↑
          </button>
          <button
            type="button"
            onClick={() => onMove(1)}
            className="bg-white/95 border border-slate-200 rounded w-6 h-6 text-xs leading-none hover:bg-slate-50 shadow-sm"
            title="아래로"
          >
            ↓
          </button>
        </div>
      )}

      <button
        type="button"
        onClick={onPreview}
        className="block w-full aspect-[4/3] bg-slate-50 overflow-hidden hover:bg-slate-100 transition"
        title="클릭하면 큰 화면으로 미리보기"
      >
        {thumbUrl ? (
          <img src={thumbUrl} alt="" className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center text-slate-400 text-xs gap-1">
            <span className="text-2xl font-bold text-slate-300">{isPdf ? 'PDF' : 'FILE'}</span>
            <span className="text-[10px] text-slate-400 truncate max-w-[80%]">{doc.file_name}</span>
          </div>
        )}
      </button>

      <div className="px-3 py-2.5 space-y-1">
        <div className="text-sm font-semibold text-slate-900 truncate" title={doc.document_type_name}>
          {doc.document_type_name}
        </div>
        {ownerHint && (
          <div className="text-[11px] text-slate-500 truncate">{ownerHint}</div>
        )}
        <div className="flex items-end justify-between gap-2 pt-0.5">
          <div className="text-[11px] text-slate-500">
            {expStr ? (
              <>
                만료일
                <div className="text-slate-800 font-medium tabular-nums">{expStr}</div>
              </>
            ) : (
              <span className="text-slate-400">만료일 없음</span>
            )}
          </div>
          <span className={`inline-block text-[10px] px-2 py-0.5 rounded-full font-medium ${chip.cls}`}>
            {chip.label}
          </span>
        </div>
      </div>
    </div>
  );
}
