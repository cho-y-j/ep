import { useEffect, useState } from 'react';
import { api } from '../../../../lib/api';
import type { DocPreviewTarget } from '../types';

interface DocLightboxProps {
  target: DocPreviewTarget | null;
  onClose: () => void;
}

/** 첨부 서류 큰 미리보기 라이트박스 — image/pdf 모두 지원.
 *  Authorization 헤더가 필요하므로 blob 으로 fetch 후 ObjectURL 사용. */
export function DocLightbox({ target, onClose }: DocLightboxProps) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const docId = target?.docId;
  const mimeType = target?.mimeType;
  useEffect(() => {
    if (!docId) return;
    let revoked = false;
    let createdUrl: string | null = null;
    setError(null);
    setObjectUrl(null);
    (async () => {
      try {
        const res = await api.get(`/api/documents/${docId}/file`, { responseType: 'blob' });
        const blob = res.data instanceof Blob ? res.data : new Blob([res.data as any], { type: mimeType });
        createdUrl = URL.createObjectURL(blob);
        if (!revoked) setObjectUrl(createdUrl);
        else URL.revokeObjectURL(createdUrl);
      } catch (e: any) {
        setError(e?.response?.data?.message || e?.message || '미리보기 로드 실패');
      }
    })();
    return () => {
      revoked = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [docId, mimeType]);

  if (!target) return null;
  const isImage = target.mimeType.startsWith('image/');
  const isPdf = target.mimeType === 'application/pdf';

  return (
    <div className="fixed inset-0 bg-black/80 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div
        className="bg-white rounded-xl max-w-5xl w-full h-[90vh] overflow-hidden flex flex-col shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 shrink-0">
          <div className="min-w-0">
            <div className="text-sm font-semibold text-slate-900 truncate">{target.category}</div>
            <div className="text-xs text-slate-500 truncate">
              {target.ownerName} · {target.originalName}
            </div>
          </div>
          <div className="flex items-center gap-2">
            {objectUrl && (
              <a
                href={objectUrl}
                download={target.originalName}
                className="text-xs px-2 py-1 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-100"
              >
                다운로드
              </a>
            )}
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-slate-700 text-2xl leading-none px-2"
              title="닫기"
              type="button"
            >
              ✕
            </button>
          </div>
        </div>
        <div className="flex-1 overflow-auto bg-slate-100 flex items-center justify-center p-2 min-h-[400px]">
          {error ? (
            <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded px-3 py-2">{error}</p>
          ) : !objectUrl ? (
            <p className="text-sm text-slate-400">로딩 중…</p>
          ) : isImage ? (
            <img src={objectUrl} alt={target.category} className="max-w-full max-h-full object-contain" />
          ) : isPdf ? (
            <iframe title={target.originalName} src={objectUrl} sandbox="" className="w-full h-full border-0 bg-white" />
          ) : (
            <div className="text-center text-sm text-slate-600 p-8">
              지원하지 않는 파일 형식입니다.
              <br />
              <span className="text-xs text-slate-400">
                {target.mimeType} · {target.originalName}
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
