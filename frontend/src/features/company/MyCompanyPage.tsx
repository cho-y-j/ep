import { useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';
import DocumentSection from '../document/DocumentSection';
import { api } from '../../lib/api';
import { IconAlertTriangle, IconXCircle, IconClock, IconCheck } from '../../components/icons';
import type { DocumentResponse, DocumentTypeResponse } from '../../types/document';
import BusinessRegUploadDialog from './BusinessRegUploadDialog';

/**
 * S-9-G: 회사 사용자 (BP / EQUIPMENT_SUPPLIER / MANPOWER_SUPPLIER) 가
 * 자기 회사 정보 + 회사 단위 서류 (사업자 등록증, 통장 사본, 건설업 등록증, 4대보험 가입증명원) 를
 * 등록/조회하는 페이지.
 *
 * - 사업자 등록증 업로드 시 OCR (BUSINESS extract) → 국세청 사업자등록상태 조회 (NTS_BIZ) 자동 라우팅.
 * - 결과: 계속사업자 → VERIFIED, 휴업/폐업 → REJECTED, 외부 API 실패 → OCR_REVIEW_REQUIRED (검토 큐).
 */
export default function MyCompanyPage() {
  const { user, company } = useAuth();
  const [docs, setDocs] = useState<DocumentResponse[]>([]);
  const [bizCertTypeId, setBizCertTypeId] = useState<number | null>(null);
  const [bizDialogOpen, setBizDialogOpen] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (!company) return;
    api.get<DocumentResponse[]>('/api/documents', {
      params: { ownerType: 'COMPANY', ownerId: company.id },
    })
      .then((res) => setDocs(res.data))
      .catch(() => {});
    api.get<DocumentTypeResponse[]>('/api/document-types', {
      params: { appliesTo: 'COMPANY' },
    })
      .then((res) => {
        const biz = res.data.find((t) => t.name === '사업자 등록증');
        if (biz) setBizCertTypeId(biz.id);
      })
      .catch(() => {});
  }, [company, reloadKey]);

  // 사업자 등록증 (document_type id=14) 검증 상태 — chain head 만 검사 (previous_document_id 없는 최신).
  const bizCertChain = docs.filter((d) => d.document_type_name === '사업자 등록증');
  const bizCertHead = bizCertChain.find((d) => !bizCertChain.some((other) => other.previous_document_id === d.id));
  const bizCertStatus = bizCertHead?.verification_status;
  const bizCertMissing = !bizCertHead;
  const bizCertRejected = bizCertStatus === 'REJECTED';
  const bizCertPending = bizCertStatus === 'PENDING' || bizCertStatus === 'OCR_REVIEW_REQUIRED';
  const bizCertVerified = bizCertStatus === 'VERIFIED';

  if (!company) {
    return (
      <AppShell breadcrumb={[{ label: '내 회사' }]}>
        <div className="card p-8 text-center">
          <h1 className="text-xl font-bold text-slate-900 mb-2">소속 회사 없음</h1>
          <p className="text-sm text-slate-500">
            현재 계정에 소속 회사가 지정돼있지 않습니다. 관리자에게 문의해 회사를 연결해주세요.
          </p>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '내 회사' }, { label: company.name }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{company.name}</h1>
          <p className="text-sm text-slate-500 mt-1">
            {user?.name}님의 소속 회사 정보와 회사 단위 서류 (사업자 등록증, 통장 사본 등) 를 관리합니다.
          </p>
        </div>

        {/* 사업자 등록증 상태 배너 — 미등록/반려/대기/검증 4단계. */}
        {bizCertMissing && (
          <div className="rounded-lg border-2 border-rose-300 bg-rose-50 px-4 py-3">
            <div className="flex items-start gap-3">
              <IconAlertTriangle className="text-rose-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="text-sm font-bold text-rose-900">사업자 등록증 미등록</div>
                <div className="text-xs text-rose-700 mt-0.5">
                  사업자 등록증을 업로드하지 않으면 자원 (장비/인원) 등록 + 작업계획서 자원 추가가 차단됩니다.
                  업로드 시 OCR + 국세청 사업자등록상태조회로 자동 검증됩니다.
                </div>
              </div>
              {bizCertTypeId && (
                <button
                  type="button"
                  onClick={() => setBizDialogOpen(true)}
                  className="px-3 py-1.5 rounded-md bg-rose-600 text-white text-sm font-medium hover:bg-rose-700 shrink-0"
                >
                  사업자 등록증 업로드
                </button>
              )}
            </div>
          </div>
        )}
        {bizCertRejected && (
          <div className="rounded-lg border-2 border-rose-300 bg-rose-50 px-4 py-3">
            <div className="flex items-start gap-3">
              <IconXCircle className="text-rose-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="text-sm font-bold text-rose-900">사업자 등록증 검증 반려됨</div>
                <div className="text-xs text-rose-700 mt-0.5">
                  사유: {bizCertHead?.rejected_reason ?? '확인 필요'}.
                  사업자번호/대표자명이 회사 정보와 다르거나 휴업/폐업 상태일 수 있습니다.
                  다시 업로드하거나 회사 정보를 갱신해 주세요.
                </div>
              </div>
            </div>
          </div>
        )}
        {bizCertPending && (
          <div className="rounded-lg border-2 border-amber-300 bg-amber-50 px-4 py-3">
            <div className="flex items-start gap-3">
              <IconClock className="text-amber-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="text-sm font-bold text-amber-900">사업자 등록증 검토 대기 중</div>
                <div className="text-xs text-amber-700 mt-0.5">
                  자동 OCR/NTS 검증이 부분적으로 실패해 관리자 검토가 필요합니다. 처리 후 알림을 받습니다.
                </div>
              </div>
            </div>
          </div>
        )}
        {bizCertVerified && (
          <div className="rounded-lg border-2 border-emerald-300 bg-emerald-50 px-4 py-3">
            <div className="flex items-start gap-3">
              <IconCheck className="text-emerald-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="text-sm font-bold text-emerald-900">사업자 등록증 검증 완료</div>
                <div className="text-xs text-emerald-700 mt-0.5">국세청 계속사업자 상태로 확인되었습니다.</div>
              </div>
            </div>
          </div>
        )}

        <section className="card space-y-3">
          <h2 className="text-lg font-bold text-slate-900">기본 정보</h2>
          <dl className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <dt className="text-xs font-semibold text-slate-500">회사명</dt>
              <dd className="text-sm text-slate-900">{company.name}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold text-slate-500">유형</dt>
              <dd className="text-sm text-slate-900">{company.type}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold text-slate-500">사업자번호</dt>
              <dd className="text-sm text-slate-900">{company.business_number ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-xs font-semibold text-slate-500">회사 ID</dt>
              <dd className="text-sm text-slate-500">#{company.id}</dd>
            </div>
          </dl>
          <p className="text-xs text-slate-500">
            기본 정보 수정은 관리자에게 요청하세요. 회사명/사업자번호 변경은 사업자 등록증 재인증이 필요합니다.
          </p>
        </section>

        <section className="card space-y-3">
          <div className="flex items-baseline justify-between">
            <h2 className="text-lg font-bold text-slate-900">회사 서류</h2>
            <span className="text-xs text-slate-500">
              사업자 등록증 업로드 시 국세청 사업자등록상태 자동 조회
            </span>
          </div>
          <DocumentSection ownerType="COMPANY" ownerId={company.id} canEdit={true} title="회사 서류" />
        </section>

        {bizCertTypeId && (
          <BusinessRegUploadDialog
            ownerCompanyId={company.id}
            companyName={company.name}
            companyBusinessNumber={company.business_number}
            documentTypeId={bizCertTypeId}
            open={bizDialogOpen}
            onClose={() => setBizDialogOpen(false)}
            onUploaded={() => setReloadKey((k) => k + 1)}
          />
        )}
      </div>
    </AppShell>
  );
}
