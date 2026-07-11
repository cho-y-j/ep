import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { DocumentTypeResponse } from '../../types/document';

type Props = {
  ownerType: 'EQUIPMENT' | 'PERSON';
  ownerId: number;
  ownerLabel: string;
  onClose: () => void;
  onSubmitted: () => void;
};

/**
 * 부모(공급사)가 자식 자원 서류에 보완 요청(빠꾸)을 생성. 서류 종류 선택 + 사유 → POST /api/document-supplements.
 */
export default function SupplementRequestDialog({ ownerType, ownerId, ownerLabel, onClose, onSubmitted }: Props) {
  const [types, setTypes] = useState<DocumentTypeResponse[]>([]);
  const [documentTypeId, setDocumentTypeId] = useState<number | ''>('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: ownerType } })
      .then((res) => setTypes(res.data))
      .catch(() => setTypes([]));
  }, [ownerType]);

  const submit = async () => {
    if (!documentTypeId) { setError('서류 종류를 선택하세요'); return; }
    setBusy(true);
    setError(null);
    try {
      await api.post('/api/document-supplements', {
        target_owner_type: ownerType,
        target_owner_id: ownerId,
        document_type_id: documentTypeId,
        reason: reason.trim() || undefined,
      });
      onSubmitted();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '발송 실패') : '발송 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={() => !busy && onClose()}>
      <div className="w-full max-w-lg space-y-3 rounded-xl bg-white p-6 shadow-xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="text-lg font-bold">서류 보완 요청 (빠꾸)</h3>
        <div className="space-y-1 rounded bg-slate-50 px-3 py-2 text-sm text-slate-700">
          <div>대상 자원: <b>{ownerLabel}</b> <span className="text-xs text-slate-400">(하위 공급사 자원)</span></div>
        </div>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">서류 종류</span>
          <select value={documentTypeId} onChange={(e) => setDocumentTypeId(e.target.value ? Number(e.target.value) : '')}
                  className="input mt-1 bg-white">
            <option value="">— 선택 —</option>
            {types.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-semibold text-slate-500">사유 (하위 공급사에게 보임)</span>
          <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={4}
                    placeholder="예: 비파괴검사서가 만료됐습니다. 갱신 후 재업로드 부탁드립니다."
                    className="input mt-1 resize-y" />
        </label>
        {error && <p className="rounded border border-rose-200 bg-rose-50 px-2 py-1 text-xs text-rose-700">{error}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={onClose} disabled={busy} className="btn-ghost">취소</button>
          <button type="button" onClick={submit} disabled={busy || !documentTypeId} className="btn-primary disabled:opacity-50">
            {busy ? '발송 중...' : '보완 요청 발송'}
          </button>
        </div>
      </div>
    </div>
  );
}
