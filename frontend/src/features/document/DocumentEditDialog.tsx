import { useEffect, useRef, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import DocumentCornerAligner from './DocumentCornerAligner';
import DocumentMaskEditor from './DocumentMaskEditor';
import { warpImageByCorners } from './warpImage';
import type { OwnerType } from '../../types/document';

type DocRef = {
  id: number;
  owner_type: OwnerType;
  owner_id: number;
  document_type_id: number;
  document_type_name?: string | null;
  file_name?: string | null;
  expiry_date?: string | null;
};

type Props = {
  doc: DocRef;
  onClose: () => void;
  onDone: () => void;
};

type Stage =
  | { kind: 'load' }
  | { kind: 'align'; file: File; url: string }
  | { kind: 'mask'; file: File; url: string }
  | { kind: 'save' };

/**
 * 업로드된 이미지 서류 재편집 — 저장본을 불러와 4모서리 재크롭(DocumentCornerAligner) →
 * 가리기(DocumentMaskEditor) 후, 기존 재업로드 교체 체인(POST /api/documents, 백엔드가
 * previous_document_id 자동 연결)으로 저장한다. 원본은 이미 크롭된 저장본이므로 여백 복구는 불가.
 * (정렬→가리기 흐름·오버레이 레이아웃은 CollectPublicPage 관례 재사용.)
 */
export default function DocumentEditDialog({ doc, onClose, onDone }: Props) {
  const [stage, setStage] = useState<Stage>({ kind: 'load' });
  const [error, setError] = useState<string | null>(null);
  // 만든 objectURL 은 모아뒀다 언마운트 시 일괄 회수 — 저장 실패 후 이전 단계 복귀에도 안전.
  const urlsRef = useRef<string[]>([]);
  function track(url: string): string {
    urlsRef.current.push(url);
    return url;
  }
  useEffect(() => () => { urlsRef.current.forEach((u) => URL.revokeObjectURL(u)); }, []);

  // 저장본 파일 로드 → 정렬 단계. 이미지가 아니면 편집 불가 안내.
  useEffect(() => {
    let cancelled = false;
    api.get(`/api/documents/${doc.id}/file`, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        const blob = r.data as Blob;
        if (!blob.type.startsWith('image/')) {
          setError('이미지 서류만 편집할 수 있습니다. PDF는 재업로드를 이용하세요.');
          return;
        }
        const file = new File([blob], doc.file_name ?? 'document.jpg', { type: blob.type });
        setStage({ kind: 'align', file, url: track(URL.createObjectURL(file)) });
      })
      .catch(() => { if (!cancelled) setError('파일을 불러오지 못했습니다'); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc.id]);

  /** 4모서리 확정 → 브라우저 원근보정(warp) 후 가리기 단계로. */
  async function onAligned(corners: [number, number][]) {
    if (stage.kind !== 'align') return;
    const warped = await warpImageByCorners(stage.file, corners).catch(() => stage.file);
    setStage({ kind: 'mask', file: warped, url: track(URL.createObjectURL(warped)) });
  }

  /** 편집 결과 저장 — 기존 만료일 유지, 옛 파일은 갱신 이력으로 보존(chain). */
  async function save(file: File) {
    const prev = stage;
    setStage({ kind: 'save' });
    setError(null);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const params: Record<string, string> = {
        ownerType: doc.owner_type,
        ownerId: String(doc.owner_id),
        documentTypeId: String(doc.document_type_id),
      };
      if (doc.expiry_date) params.expiryDate = doc.expiry_date;
      await api.post('/api/documents', fd, { params });
      onDone();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '저장 실패') : '저장 실패');
      setStage(prev); // 가리기 화면 유지 — 재시도/닫기 선택
    }
  }

  const busy = stage.kind === 'save';

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-black/70">
      <div className="flex items-center justify-between gap-3 bg-white px-4 py-3">
        <div className="min-w-0">
          <span className="text-sm font-bold text-slate-800">
            서류 편집{doc.document_type_name ? ` — ${doc.document_type_name}` : ''}
            {stage.kind === 'mask' && ' · 가릴 곳 덮기 (선택)'}
          </span>
          <p className="text-xs text-slate-500">
            저장된 이미지 기준 재편집입니다 — 원본 촬영본의 잘린 여백은 복구되지 않습니다. 기존 파일은 갱신 이력으로 보존됩니다.
          </p>
        </div>
        <button type="button" onClick={onClose} disabled={busy}
          className="shrink-0 rounded p-1 text-2xl leading-none text-slate-400 hover:bg-slate-100 disabled:opacity-50" aria-label="닫기">
          ×
        </button>
      </div>
      {error && (
        <p className="bg-rose-50 px-4 py-2 text-sm text-rose-700">{error}</p>
      )}
      {stage.kind === 'load' && !error && (
        <div className="flex flex-1 items-center justify-center text-sm text-slate-300">불러오는 중…</div>
      )}
      {stage.kind === 'save' && (
        <div className="flex flex-1 items-center justify-center text-sm text-slate-300">저장 중…</div>
      )}
      {stage.kind === 'align' && (
        <DocumentCornerAligner
          imageUrl={stage.url}
          onConfirm={(c) => void onAligned(c as [number, number][])}
          onCancel={onClose}
        />
      )}
      {stage.kind === 'mask' && (
        <DocumentMaskEditor
          imageUrl={stage.url}
          onConfirm={(f) => void save(f)}
          onCancel={() => void save(stage.file)}
        />
      )}
    </div>
  );
}
