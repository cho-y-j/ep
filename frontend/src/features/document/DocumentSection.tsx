import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { type DocumentResponse, type OwnerType } from '../../types/document';
import DocumentUploadForm from './DocumentUploadForm';
import DocumentCard from './DocumentCard';
import DocumentRenewDialog from './DocumentRenewDialog';
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
  const [renewing, setRenewing] = useState<DocumentResponse | null>(null);
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
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
          {docs.map((d) => (
            <DocumentCard
              key={d.id}
              doc={d}
              canEdit={canEdit}
              isAdmin={isAdmin}
              onOpen={() => openFile(d)}
              onDelete={() => setPendingDelete(d)}
              onToggleVerify={() => setVerified(d, !d.verified)}
              onRenew={() => setRenewing(d)}
            />
          ))}
        </div>
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

      {renewing && (
        <DocumentRenewDialog
          open
          ownerType={renewing.owner_type}
          ownerId={renewing.owner_id}
          documentTypeId={renewing.document_type_id}
          documentTypeName={renewing.document_type_name}
          oldDocumentId={renewing.id}
          hasExpiry={renewing.document_type_has_expiry}
          onClose={() => setRenewing(null)}
          onDone={() => { setRenewing(null); void load(); }}
        />
      )}
    </div>
  );
}

