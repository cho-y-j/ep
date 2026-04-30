import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { daysUntilExpiry, formatFileSize, type DocumentResponse, type OwnerType } from '../../types/document';
import DocumentUploadForm from './DocumentUploadForm';
import ConfirmDialog from '../../components/ConfirmDialog';
import { tokenStorage } from '../../lib/tokenStorage';

type Props = {
  ownerType: OwnerType;
  ownerId: number;
  canEdit: boolean;
};

export default function DocumentSection({ ownerType, ownerId, canEdit }: Props) {
  const [docs, setDocs] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<DocumentResponse | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<DocumentResponse[]>('/api/documents', {
        params: { ownerType, ownerId },
      });
      setDocs(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '서류 목록 불러오기 실패');
      } else {
        setError('서류 목록 불러오기 실패');
      }
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ownerType, ownerId]);

  function downloadUrl(doc: DocumentResponse): string {
    return `/api/documents/${doc.id}/file`;
  }

  async function openFile(doc: DocumentResponse) {
    // 현재 토큰을 헤더에 실어 새 탭으로 보내려면 blob URL 생성
    try {
      const res = await api.get(downloadUrl(doc), { responseType: 'blob' });
      const blob = new Blob([res.data], { type: doc.content_type });
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank');
      // 일정 시간 후 revoke (탭 로드 완료 후)
      setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch {
      // fallback: token URL param (불가) → 그냥 안내
      alert('파일을 열 수 없습니다');
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) return;
    setDeleteBusy(true);
    try {
      await api.delete(`/api/documents/${pendingDelete.id}`);
      setPendingDelete(null);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '삭제 실패');
      }
    } finally {
      setDeleteBusy(false);
    }
  }

  async function setVerified(doc: DocumentResponse, verified: boolean) {
    try {
      await api.patch(`/api/documents/${doc.id}/verified?verified=${verified}`);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '검증 변경 실패');
      }
    }
  }

  // 토큰 없으면 노출 안 함
  if (!tokenStorage.access) return null;

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <h3 className="text-sm font-bold text-slate-700">서류</h3>
        {canEdit && !uploading && (
          <button
            type="button"
            onClick={() => setUploading(true)}
            className="text-xs text-brand-600 hover:text-brand-700 font-medium"
          >
            + 추가
          </button>
        )}
      </div>

      {uploading && canEdit && (
        <div className="mb-3">
          <DocumentUploadForm
            ownerType={ownerType}
            ownerId={ownerId}
            onUploaded={() => { setUploading(false); void load(); }}
            onCancel={() => setUploading(false)}
          />
        </div>
      )}

      {loading ? (
        <p className="text-xs text-slate-400">불러오는 중...</p>
      ) : error ? (
        <p className="text-xs text-red-600">{error}</p>
      ) : docs.length === 0 ? (
        <p className="text-xs text-slate-400">등록된 서류가 없습니다</p>
      ) : (
        <ul className="space-y-1.5">
          {docs.map((d) => (
            <DocumentRow
              key={d.id}
              doc={d}
              canEdit={canEdit}
              isAdmin={isAdmin}
              onOpen={() => openFile(d)}
              onDelete={() => setPendingDelete(d)}
              onToggleVerify={() => setVerified(d, !d.verified)}
            />
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={!!pendingDelete}
        title="서류 삭제"
        message={pendingDelete ? `${pendingDelete.document_type_name} (${pendingDelete.file_name}) 를 삭제합니다.\n파일도 함께 삭제됩니다.` : ''}
        confirmLabel="삭제"
        variant="danger"
        busy={deleteBusy}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

function DocumentRow({ doc, canEdit, isAdmin, onOpen, onDelete, onToggleVerify }: {
  doc: DocumentResponse;
  canEdit: boolean;
  isAdmin: boolean;
  onOpen: () => void;
  onDelete: () => void;
  onToggleVerify: () => void;
}) {
  const days = daysUntilExpiry(doc.expiry_date);
  const expired = days != null && days < 0;
  const soon = days != null && days >= 0 && days <= 30;

  return (
    <li className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg border border-slate-200 hover:bg-slate-50">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={onOpen}
            className="text-sm font-medium text-brand-600 hover:underline truncate"
            title={doc.file_name}
          >
            {doc.document_type_name}
          </button>
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
        <div className="text-xs text-slate-500 truncate">
          {doc.file_name} · {formatFileSize(doc.file_size)}
          {doc.expiry_date && ` · 만료 ${doc.expiry_date}`}
        </div>
      </div>
      <div className="flex items-center gap-1 shrink-0">
        {isAdmin && (
          <button
            type="button"
            onClick={onToggleVerify}
            className="text-xs text-slate-500 hover:text-slate-900 px-2 py-1"
            title={doc.verified ? '검증 취소' : '검증 표시'}
          >
            {doc.verified ? '검증취소' : '검증'}
          </button>
        )}
        {canEdit && (
          <button
            type="button"
            onClick={onDelete}
            className="text-xs text-red-600 hover:text-red-700 px-2 py-1"
          >
            삭제
          </button>
        )}
      </div>
    </li>
  );
}
