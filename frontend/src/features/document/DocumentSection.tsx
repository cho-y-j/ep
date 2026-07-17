import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { daysUntilExpiry, type DocumentResponse, type DocumentTypeResponse, type OwnerType } from '../../types/document';
import { groupDocTypes } from './docTypeGrouping';
import OcrUploadDialog from './OcrUploadDialog';
import DocumentCard from './DocumentCard';
import DocumentVerifyDialog from './DocumentVerifyDialog';
import DocumentHistoryDialog from './DocumentHistoryDialog';
import DocFilePreviewDialog from '../compliance/DocFilePreviewDialog';
import ConfirmDialog from '../../components/ConfirmDialog';
import { tokenStorage } from '../../lib/tokenStorage';

type Props = {
  ownerType: OwnerType;
  ownerId: number;
  canEdit: boolean;
  title?: string;
  /** 이 이름의 서류타입을 목록에서 제외 (예: 내부 장비일 때 외부장비 사업자등록증 숨김) */
  excludeTypeName?: string;
  /** EQUIPMENT 자원의 카테고리 — 업로드 다이얼로그에서 필수/선택 서류 그룹핑에 사용. */
  ownerCategory?: string;
  /** PERSON 자원의 역할 목록 — 업로드 다이얼로그에서 필수/선택 서류 그룹핑에 사용. */
  ownerRoles?: string[];
};

export default function DocumentSection({ ownerType, ownerId, canEdit, title = '서류', excludeTypeName, ownerCategory, ownerRoles }: Props) {
  const [docs, setDocs] = useState<DocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<DocumentResponse | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [renewing, setRenewing] = useState<DocumentResponse | null>(null);
  const [verifying, setVerifying] = useState<{ doc: DocumentResponse; type: DocumentTypeResponse } | null>(null);
  const [historyOf, setHistoryOf] = useState<DocumentResponse | null>(null);
  // 파일 클릭 시 인앱 blob 모달 (window.open 팝업차단 회피).
  const [previewDoc, setPreviewDoc] = useState<DocumentResponse | null>(null);
  // 체크리스트에서 미등록 항목 업로드 — 종류 고정(presetTypeId) 다이얼로그.
  const [checklistTypeId, setChecklistTypeId] = useState<number | null>(null);
  const [docTypes, setDocTypes] = useState<Map<number, DocumentTypeResponse>>(new Map());
  // 보완요청 deep-link — ?supplementType={typeId} 로 진입하면 해당 서류 업로드 다이얼로그 자동 오픈
  const [supplementTypeId, setSupplementTypeId] = useState<number | null>(null);
  // EQUIPMENT: 종류×서류 junction requirement (doc_type_id → required). 없으면 기존 CSV/글로벌 required.
  const [reqByTypeId, setReqByTypeId] = useState<Map<number, boolean> | undefined>(undefined);
  const [searchParams, setSearchParams] = useSearchParams();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const [res, typesRes] = await Promise.all([
        api.get<DocumentResponse[]>('/api/documents', { params: { ownerType, ownerId } }),
        api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: ownerType } }),
      ]);
      setDocs(res.data);
      // type id → DocumentTypeResponse 매핑 (verify 모달이 사용)
      const map = new Map<number, DocumentTypeResponse>();
      typesRes.data.forEach((t) => map.set(t.id, t));
      if (excludeTypeName) {
        for (const [id, t] of map) if (t.name === excludeTypeName) map.delete(id);
      }
      setDocTypes(map);
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
  }, [ownerType, ownerId, excludeTypeName]);

  // 자원별 junction 기반 필수/선택/해당없음 조회 (어드민 체크리스트 반영).
  // EQUIPMENT=종류×서류, PERSON=역할×서류(여러 역할이면 합집합, required 는 OR). 없으면 기존 CSV/글로벌.
  const rolesKey = ownerRoles?.join(',') ?? '';
  useEffect(() => {
    if (ownerType === 'EQUIPMENT' && ownerCategory) {
      api.get<Array<{ document_type_id: number; required: boolean }>>(`/api/equipment-types/${ownerCategory}/documents`)
        .then((r) => setReqByTypeId(new Map(r.data.map((x) => [x.document_type_id, x.required]))))
        .catch(() => setReqByTypeId(undefined));
      return;
    }
    if (ownerType === 'PERSON' && rolesKey) {
      Promise.all(rolesKey.split(',').map((role) =>
        api.get<Array<{ document_type_id: number; required: boolean }>>(`/api/person-roles/${role}/documents`)
          .then((r) => r.data).catch(() => [] as Array<{ document_type_id: number; required: boolean }>)))
        .then((lists) => {
          const m = new Map<number, boolean>();
          for (const list of lists) for (const x of list) m.set(x.document_type_id, (m.get(x.document_type_id) ?? false) || x.required);
          setReqByTypeId(m);
        });
      return;
    }
    setReqByTypeId(undefined);
  }, [ownerType, ownerCategory, rolesKey]);

  // 보완요청에서 넘어온 경우: 해당 서류 타입 업로드 다이얼로그 자동 오픈 (1회) + 쿼리 소비
  useEffect(() => {
    const sp = searchParams.get('supplementType');
    if (!sp || !canEdit || docTypes.size === 0) return;
    const tid = Number(sp);
    if (Number.isNaN(tid) || !docTypes.has(tid)) return;
    setSupplementTypeId(tid);
    const next = new URLSearchParams(searchParams);
    next.delete('supplementType');
    setSearchParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, canEdit, docTypes]);

  // 파일 열기 — 인앱 blob 모달(DocFilePreviewDialog)로 표시. 모달이 blob fetch/revoke 를 담당.
  function openFile(doc: DocumentResponse) {
    setPreviewDoc(doc);
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

  /**
   * S-4 단계 3: 자동 검증 모달 띄우기. type.required_fields 기반으로 사용자 보충 입력 받고 POST /verify.
   */
  function openVerifyDialog(doc: DocumentResponse) {
    const type = docTypes.get(doc.document_type_id);
    if (!type) {
      alert('서류 타입 정보를 불러오지 못했습니다');
      return;
    }
    if (!type.verify_endpoint) {
      alert(`${type.name} 는 자동 검증 대상이 아닙니다`);
      return;
    }
    setVerifying({ doc, type });
  }

  async function reject(doc: DocumentResponse) {
    const reason = prompt(`반려 사유를 입력하세요\n(${doc.document_type_name})`);
    if (!reason || !reason.trim()) return;
    try {
      await api.post(`/api/documents/${doc.id}/reject`, { reason: reason.trim() });
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '반려 실패');
      }
    }
  }

  // 체크리스트 — 이 자원에 필요한 서류 종류를 필수/선택으로 그룹핑 (docTypes 는 이미 appliesTo/제외 필터 적용됨).
  const checklist = useMemo(
    () => groupDocTypes(Array.from(docTypes.values()), ownerType, ownerRoles, ownerCategory, reqByTypeId),
    [docTypes, ownerType, ownerRoles, ownerCategory, reqByTypeId],
  );
  // 종류별 대표 문서(있으면) — 상태 뱃지 판정용.
  const docByType = useMemo(() => {
    const m = new Map<number, DocumentResponse>();
    for (const d of docs) if (!m.has(d.document_type_id)) m.set(d.document_type_id, d);
    return m;
  }, [docs]);

  // 토큰 없으면 노출 안 함
  if (!tokenStorage.access) return null;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h3 className="flex items-center gap-2 text-lg font-bold text-slate-900">
          {title}
          {!loading && (
            <span className="rounded-full bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
              {docs.length}
            </span>
          )}
        </h3>
        {canEdit && !uploading && (
          <button
            type="button"
            onClick={() => setUploading(true)}
            className="rounded-lg border border-brand-100 bg-white px-3 py-2 text-sm font-semibold text-brand-700 shadow-sm hover:bg-brand-50"
          >
            + 서류 추가
          </button>
        )}
      </div>

      {canEdit && (
        <OcrUploadDialog
          open={uploading}
          ownerType={ownerType}
          ownerId={ownerId}
          types={Array.from(docTypes.values())}
          ownerCategory={ownerCategory}
          ownerRoles={ownerRoles}
          reqByTypeId={reqByTypeId}
          onClose={() => setUploading(false)}
          onUploaded={() => { setUploading(false); void load(); }}
        />
      )}

      {canEdit && (checklist.required.length > 0 || checklist.optional.length > 0) && (
        <div className="mb-4 rounded-xl border border-slate-200 bg-slate-50/60 p-4">
          {checklist.required.length > 0 && (
            <>
              <div className="mb-2 text-sm font-bold text-slate-900">필수 서류</div>
              <div className="space-y-1.5">
                {checklist.required.map((t) => (
                  <ChecklistRow key={t.id} type={t} required doc={docByType.get(t.id)} onUpload={() => setChecklistTypeId(t.id)} />
                ))}
              </div>
            </>
          )}
          {checklist.optional.length > 0 && (
            <details className="mt-3" open={checklist.required.length === 0}>
              <summary className="cursor-pointer text-xs font-semibold text-slate-500 hover:text-slate-700">
                선택 서류 ({checklist.optional.length}건) ▾
              </summary>
              <div className="mt-2 space-y-1.5">
                {checklist.optional.map((t) => (
                  <ChecklistRow key={t.id} type={t} required={false} doc={docByType.get(t.id)} onUpload={() => setChecklistTypeId(t.id)} />
                ))}
              </div>
            </details>
          )}
        </div>
      )}

      {loading ? (
        <p className="text-xs text-slate-400">불러오는 중...</p>
      ) : error ? (
        <p className="text-xs text-red-600">{error}</p>
      ) : docs.length === 0 ? (
        <p className="text-xs text-slate-400">등록된 서류가 없습니다</p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {docs.map((d) => (
            <DocumentCard
              key={d.id}
              doc={d}
              canEdit={canEdit}
              isAdmin={isAdmin}
              canAutoVerify={!!docTypes.get(d.document_type_id)?.verify_endpoint}
              onOpen={() => openFile(d)}
              onDelete={() => setPendingDelete(d)}
              onToggleVerify={() => setVerified(d, !d.verified)}
              onAutoVerify={() => openVerifyDialog(d)}
              onHistory={() => setHistoryOf(d)}
              onReject={() => reject(d)}
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
        <OcrUploadDialog
          open
          ownerType={renewing.owner_type}
          ownerId={renewing.owner_id}
          types={Array.from(docTypes.values())}
          presetTypeId={renewing.document_type_id}
          title={`${renewing.document_type_name} 재업로드`}
          onClose={() => setRenewing(null)}
          onUploaded={() => { setRenewing(null); void load(); }}
        />
      )}

      {supplementTypeId !== null && (
        <OcrUploadDialog
          open
          ownerType={ownerType}
          ownerId={ownerId}
          types={Array.from(docTypes.values())}
          presetTypeId={supplementTypeId}
          title={`${docTypes.get(supplementTypeId)?.name ?? '서류'} 보완 업로드`}
          onClose={() => setSupplementTypeId(null)}
          onUploaded={() => { setSupplementTypeId(null); void load(); }}
        />
      )}

      {checklistTypeId !== null && (
        <OcrUploadDialog
          open
          ownerType={ownerType}
          ownerId={ownerId}
          types={Array.from(docTypes.values())}
          presetTypeId={checklistTypeId}
          title={`${docTypes.get(checklistTypeId)?.name ?? '서류'} 업로드`}
          ownerCategory={ownerCategory}
          ownerRoles={ownerRoles}
          onClose={() => setChecklistTypeId(null)}
          onUploaded={() => { setChecklistTypeId(null); void load(); }}
        />
      )}

      {verifying && (
        <DocumentVerifyDialog
          open
          doc={verifying.doc}
          type={verifying.type}
          onClose={() => setVerifying(null)}
          onDone={(updated) => {
            const label = updated.verification_status === 'VERIFIED' ? '검증 완료'
              : updated.verification_status === 'REJECTED' ? '반려'
              : updated.verification_status === 'OCR_REVIEW_REQUIRED' ? 'OCR 검토 필요'
              : updated.verification_status;
            alert(`결과: ${label}`);
            setVerifying(null);
            void load();
          }}
        />
      )}

      {historyOf && (
        <DocumentHistoryDialog
          open
          documentId={historyOf.id}
          documentTypeName={historyOf.document_type_name}
          onClose={() => setHistoryOf(null)}
        />
      )}

      {previewDoc && (
        <DocFilePreviewDialog
          doc={previewDoc}
          docType={docTypes.get(previewDoc.document_type_id)}
          canReverify={canEdit && !!docTypes.get(previewDoc.document_type_id)?.verify_endpoint}
          onClose={() => setPreviewDoc(null)}
          onReverified={() => void load()}
          onReupload={() => { const d = previewDoc; setPreviewDoc(null); setRenewing(d); }}
        />
      )}
    </div>
  );
}

/** 체크리스트 한 줄 — 종류명 + 등록 상태 뱃지 + (미등록이면) 업로드 버튼.
 *  required 는 이 자원 기준 필수 여부(EQUIPMENT junction / 그 외 글로벌) — 소속 섹션이 전달. */
function ChecklistRow({ type, doc, onUpload, required }: {
  type: DocumentTypeResponse;
  doc?: DocumentResponse;
  onUpload: () => void;
  required: boolean;
}) {
  const days = daysUntilExpiry(doc?.expiry_date);
  const expired = doc != null && days != null && days < 0;
  const status = !doc
    ? { label: '미등록', cls: 'bg-slate-200 text-slate-600' }
    : expired
      ? { label: '만료', cls: 'bg-rose-100 text-rose-700' }
      : doc.verification_status === 'VERIFIED'
        ? { label: '검증완료', cls: 'bg-emerald-100 text-emerald-700' }
        : { label: '등록됨', cls: 'bg-blue-100 text-blue-700' };
  return (
    <div className="flex items-center justify-between gap-2 rounded-lg bg-white px-3 py-2 ring-1 ring-slate-200">
      <div className="min-w-0 flex-1 truncate text-sm text-slate-800">
        {required && <span className="mr-1 text-rose-500">*</span>}
        {type.name}
        {type.has_expiry && <span className="ml-2 text-[10px] text-slate-400">만료관리</span>}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${status.cls}`}>{status.label}</span>
        {!doc && (
          <button type="button" onClick={onUpload}
            className="rounded-md bg-brand-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-brand-700">
            업로드
          </button>
        )}
      </div>
    </div>
  );
}
