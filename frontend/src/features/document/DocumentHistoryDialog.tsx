import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { VERIFICATION_STATUS_LABEL, type DocumentResponse } from '../../types/document';

type Props = {
  open: boolean;
  documentId: number;
  documentTypeName: string;
  onClose: () => void;
};

/**
 * S-4 단계 4: 갱신 이력 보기. 같은 (owner_type, owner_id, document_type_id) 의 모든 버전.
 * 가장 최신부터 옛 버전 순. previous_document_id 체인 시각화.
 */
export default function DocumentHistoryDialog({ open, documentId, documentTypeName, onClose }: Props) {
  const [history, setHistory] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    api.get<DocumentResponse[]>(`/api/documents/${documentId}/history`)
      .then((res) => setHistory(res.data))
      .catch(() => setHistory([]))
      .finally(() => setLoading(false));
  }, [open, documentId]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-xl max-w-2xl w-full p-6 space-y-4 max-h-[80vh] overflow-y-auto">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-lg font-bold">갱신 이력</h3>
            <p className="text-sm text-slate-500 mt-1">{documentTypeName}</p>
          </div>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-700">✕</button>
        </div>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : history.length === 0 ? (
          <p className="text-sm text-slate-400">이력이 없습니다.</p>
        ) : (
          <ul className="space-y-3">
            {history.map((d, idx) => {
              const isHead = idx === 0;
              return (
                <li key={d.id} className="rounded-lg border border-slate-200 bg-white p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-900 flex items-center gap-2 flex-wrap">
                        <span>doc #{d.id}</span>
                        {isHead && (
                          <span className="text-xs px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 font-semibold">현재</span>
                        )}
                        <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 font-semibold">
                          {VERIFICATION_STATUS_LABEL[d.verification_status] ?? d.verification_status}
                        </span>
                      </div>
                      <div className="text-xs text-slate-500 mt-1 truncate">{d.file_name}</div>
                      {d.expiry_date && (
                        <div className="text-xs text-slate-500 mt-0.5">만료일: {d.expiry_date}</div>
                      )}
                      {d.rejected_reason && (
                        <div className="text-xs text-rose-600 mt-1">반려 사유: {d.rejected_reason}</div>
                      )}
                    </div>
                    <div className="text-xs text-slate-400 shrink-0">
                      {new Date(d.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)}
                    </div>
                  </div>
                  {d.previous_document_id != null && (
                    <div className="text-xs text-slate-400 mt-2">↳ 이전 버전: doc #{d.previous_document_id}</div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
