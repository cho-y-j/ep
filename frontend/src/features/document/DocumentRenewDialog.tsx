import { useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { OwnerType } from '../../types/document';

type Props = {
  open: boolean;
  ownerType: OwnerType;
  ownerId: number;
  documentTypeId: number;
  documentTypeName: string;
  oldDocumentId?: number;
  hasExpiry?: boolean;
  onClose: () => void;
  onDone: () => void;
};

/**
 * 만료 임박 서류 재업로드 (renewal).
 *
 * V14 부터: 백엔드가 같은 (owner_type, owner_id, document_type_id) 의 가장 최신 문서를
 * 자동으로 `previous_document_id` 로 묶는다. 옛 문서는 보존되고 list 에서는 chain head 만 노출.
 * → 프론트는 옛 doc 삭제를 호출하지 않는다 (이력 추적성 유지).
 */
export default function DocumentRenewDialog({
  open, ownerType, ownerId, documentTypeId, documentTypeName,
  hasExpiry = true, onClose, onDone,
}: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [expiry, setExpiry] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) return null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!file) {
      setError('파일을 선택하세요');
      return;
    }
    if (hasExpiry && !expiry) {
      setError('만료일을 선택하세요');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const params: Record<string, string> = {
        ownerType,
        ownerId: String(ownerId),
        documentTypeId: String(documentTypeId),
      };
      if (expiry) params.expiryDate = expiry;
      await api.post('/api/documents', formData, { params });
      // V14: 백엔드가 자동으로 previous_document_id 묶음. 옛 doc 삭제 불필요.
      onDone();
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '업로드 실패');
      } else {
        setError('업로드 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-slate-900/40" onClick={busy ? undefined : onClose} />
      <form onSubmit={onSubmit} className="relative bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
        <div>
          <h3 className="text-lg font-bold">서류 재업로드</h3>
          <p className="text-sm text-slate-500 mt-1">{documentTypeName}</p>
        </div>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">새 파일</span>
          <input
            type="file"
            accept="image/*,application/pdf"
            capture="environment"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            required
            className="block w-full mt-1 text-sm text-slate-700 file:mr-4 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:bg-brand-600 file:text-white file:cursor-pointer hover:file:bg-brand-700"
          />
        </label>

        {hasExpiry && (
          <label className="block">
            <span className="text-sm font-medium text-slate-700">새 만료일</span>
            <input
              type="date"
              value={expiry}
              onChange={(e) => setExpiry(e.target.value)}
              required
              className="input mt-1"
            />
          </label>
        )}

        <p className="text-xs text-slate-500">
          기존 서류는 보존되며 갱신 이력으로 연결됩니다 (previous_document_id).
        </p>

        {error && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} disabled={busy} className="px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100 disabled:opacity-50">
            취소
          </button>
          <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '업로드 중...' : '재업로드'}
          </button>
        </div>
      </form>
    </div>
  );
}
