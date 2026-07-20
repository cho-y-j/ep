import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { DocumentResponse } from '../../types/document';
import { IconFile, IconAlertTriangle } from '../../components/icons';

interface BusinessRegUploadDialogProps {
  ownerCompanyId: number;
  /** 회사 가입 시 입력한 회사명 — OCR 추출 상호와 비교해 불일치 시 사유 입력 강제. */
  companyName?: string | null;
  /** 회사 가입 시 입력한 사업자번호 — 표시용. */
  companyBusinessNumber?: string | null;
  documentTypeId: number;
  open: boolean;
  onClose: () => void;
  onUploaded: (doc: DocumentResponse) => void;
}

interface ExtractedFields {
  businessNumber?: string;
  representativeName?: string;
  businessName?: string;
  startDate?: string;
  address?: string;
  businessType?: string;
}

type Step = 'pick' | 'ocr' | 'review' | 'mismatch';

/**
 * S-9-G.2: 사업자등록증 업로드 전용 다이얼로그.
 *
 * 단계:
 *   1) pick     — 파일 선택 (안내 화면)
 *   2) ocr      — 큰 스피너 + "OCR 분석 중..." 전체 다이얼로그 점유
 *   3) review   — 좌 미리보기 + 우 자동채움 폼. 사용자 [업로드] 클릭:
 *                   - 회사명 일치 → 즉시 submit + 다이얼로그 닫기 (자동 처리)
 *                   - 회사명 불일치 → step=mismatch 로 넘김
 *   4) mismatch — "회사 등록명과 상호가 달라요" 안내 + 사유 입력 → submit
 */
export default function BusinessRegUploadDialog({
  ownerCompanyId,
  companyName,
  companyBusinessNumber: _companyBusinessNumber,
  documentTypeId,
  open,
  onClose,
  onUploaded,
}: BusinessRegUploadDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [fields, setFields] = useState<ExtractedFields>({});
  const [step, setStep] = useState<Step>('pick');
  const [ocrError, setOcrError] = useState('');
  const [mismatchReason, setMismatchReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 회사명 normalize — "(주)다인", "㈜다인", "주식회사 다인" 모두 "다인" 으로.
  const normalizeName = (s?: string | null) =>
    (s ?? '')
      .trim()
      .replace(/\([^)]*\)/g, '')
      .replace(/㈜|\(주\)|주식회사|유한회사|\(유\)|합자회사|합명회사/g, '')
      .replace(/[\s·.,/_\-]+/g, '')
      .toLowerCase();

  const dbName = normalizeName(companyName);
  const certName = normalizeName(fields.businessName);
  const mismatch = !!certName && !!dbName && certName !== dbName;

  // 다이얼로그 닫힐 때 상태 초기화
  useEffect(() => {
    if (!open) {
      setFile(null);
      setPreviewUrl(null);
      setFields({});
      setStep('pick');
      setOcrError('');
      setMismatchReason('');
      setBusy(false);
      setError(null);
    }
  }, [open]);

  // 파일 변경 시 미리보기 URL 생성/해제
  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // 파일 선택 → step=ocr 로 즉시 전환 후 OCR preview 호출
  const onFilePicked = (f: File) => {
    setFile(f);
    setStep('ocr');
    setOcrError('');
    setFields({});
    runOcr(f);
  };

  const runOcr = async (f: File) => {
    try {
      const fd = new FormData();
      fd.append('file', f);
      fd.append('ocrType', 'BUSINESS_REGISTRATION');
      const res = await api.post<{
        ok: boolean;
        fields?: ExtractedFields;
        reasonCode?: string;
        message?: string;
      }>('/api/documents/ocr-preview', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (res.data.ok && res.data.fields) {
        setFields(res.data.fields);
      } else {
        setOcrError(`OCR 자동 추출 실패 (${res.data.reasonCode ?? 'UNKNOWN'}). 직접 입력해주세요.`);
      }
    } catch (err: any) {
      setOcrError('OCR 호출 실패. 직접 입력해주세요.');
    } finally {
      setStep('review');
    }
  };

  if (!open) return null;

  const isImage = file && file.type.startsWith('image/');
  const isPdf = file && file.type === 'application/pdf';

  const updateField = (key: keyof ExtractedFields, value: string) => {
    setFields((f) => ({ ...f, [key]: value }));
  };

  /** 실제 업로드 수행. mismatch 인 경우 사유 함께 전송. */
  const doUpload = async (extraReason?: string) => {
    if (!file) return;
    setError(null);
    setBusy(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      if (fields.businessNumber) fd.append('manualBusinessNumber', fields.businessNumber);
      if (fields.representativeName) fd.append('manualRepresentativeName', fields.representativeName);
      if (fields.businessName) fd.append('manualBusinessName', fields.businessName);
      if (fields.startDate) fd.append('manualStartDate', fields.startDate);
      if (fields.address) fd.append('manualAddress', fields.address);
      if (fields.businessType) fd.append('manualBusinessType', fields.businessType);
      if (extraReason && extraReason.trim()) fd.append('manualMismatchReason', extraReason.trim());
      const params: Record<string, string> = {
        ownerType: 'COMPANY',
        ownerId: String(ownerCompanyId),
        documentTypeId: String(documentTypeId),
      };
      const res = await api.post<DocumentResponse>('/api/documents', fd, { params });
      onUploaded(res.data);
      try {
        await api.post(`/api/documents/${res.data.id}/verify`, {});
      } catch {
        /* 자동 검증 실패는 무시 — review queue 로 fallback */
      }
      onClose();
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '업로드 실패');
      } else {
        setError('업로드 실패');
      }
    } finally {
      setBusy(false);
    }
  };

  /** review 단계의 [업로드 + 검증] 버튼. 회사명 일치 → 즉시 업로드. 불일치 → step=mismatch 로. */
  const onReviewSubmit = () => {
    if (!file) {
      setError('파일을 선택하세요');
      return;
    }
    if (mismatch) {
      setStep('mismatch');
      return;
    }
    doUpload();
  };

  /** mismatch 단계의 [관리자에게 검토 요청] 버튼. */
  const onMismatchSubmit = () => {
    if (!mismatchReason.trim()) {
      setError('사유를 입력해주세요.');
      return;
    }
    doUpload(mismatchReason);
  };

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={() => !busy && step !== 'ocr' && onClose()}
    >
      <div
        className="bg-white rounded-xl max-w-5xl w-full max-h-[92vh] overflow-hidden shadow-2xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* ───────── 헤더 ───────── */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200">
          <div>
            <h3 className="text-lg font-bold text-slate-900">사업자 등록증 업로드</h3>
            <div className="text-xs text-slate-500 mt-0.5">
              {step === 'pick' && '파일을 선택하면 OCR 자동 추출 + 국세청 자동 검증이 시작됩니다.'}
              {step === 'ocr' && '문서를 분석 중입니다...'}
              {step === 'review' && 'OCR 결과를 검토하고 [업로드] 를 누르세요.'}
              {step === 'mismatch' && '회사 등록명과 상호가 다릅니다. 사유 입력 후 관리자 검토 요청.'}
            </div>
          </div>
          <button
            type="button"
            onClick={() => !busy && step !== 'ocr' && onClose()}
            disabled={busy || step === 'ocr'}
            className="text-slate-400 hover:text-slate-700 text-xl leading-none px-2 disabled:opacity-30"
          >
            ✕
          </button>
        </div>

        {/* ───────── 본문 ───────── */}
        {step === 'pick' && (
          <div className="flex-1 overflow-auto p-10 flex flex-col items-center justify-center gap-4 min-h-[400px]">
            <IconFile size={80} className="text-slate-300" />
            <div className="text-base font-semibold text-slate-900">사업자 등록증 이미지 또는 PDF</div>
            <div className="text-xs text-slate-500 max-w-md text-center leading-relaxed">
              파일을 선택하면 Google Vision OCR 로 사업자번호/상호/대표자/주소 등을 자동 추출하고,
              국세청 사업자등록상태조회로 검증합니다.
            </div>
            <label className="mt-4 px-6 py-3 rounded-md bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 cursor-pointer shadow-sm">
              파일 선택
              <input
                type="file"
                accept="image/*,application/pdf"
                capture="environment"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) onFilePicked(f);
                }}
              />
            </label>
          </div>
        )}

        {step === 'ocr' && (
          <div className="flex-1 overflow-auto p-10 flex flex-col items-center justify-center gap-5 min-h-[400px]">
            <div className="relative w-20 h-20">
              <div className="absolute inset-0 rounded-full border-4 border-slate-200" />
              <div className="absolute inset-0 rounded-full border-4 border-brand-600 border-t-transparent animate-spin" />
            </div>
            <div className="text-lg font-semibold text-slate-900">OCR 분석 중...</div>
            <div className="text-xs text-slate-500 max-w-md text-center leading-relaxed">
              Google Vision OCR 로 사업자번호 / 상호 / 대표자 / 개업연월일 / 주소 / 업태·종목을 추출합니다.
              <br />
              파일 크기에 따라 5~20초 소요됩니다.
            </div>
          </div>
        )}

        {step === 'review' && (
          <div className="flex flex-1 min-h-0">
            <div className="w-1/2 border-r border-slate-200 bg-slate-100 flex flex-col">
              <div className="px-4 py-2 border-b border-slate-200 bg-white flex items-center justify-between">
                <span className="text-xs font-semibold text-slate-700">미리보기</span>
                <label className="text-xs px-2 py-1 rounded-md bg-brand-600 text-white hover:bg-brand-700 cursor-pointer">
                  파일 변경
                  <input
                    type="file"
                    accept="image/*,application/pdf"
                capture="environment"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) onFilePicked(f);
                    }}
                  />
                </label>
              </div>
              <div className="flex-1 overflow-auto flex items-center justify-center p-3">
                {isImage ? (
                  <img
                    src={previewUrl ?? undefined}
                    alt="preview"
                    className="max-w-full max-h-full object-contain rounded shadow"
                  />
                ) : isPdf ? (
                  <iframe src={previewUrl ?? undefined} sandbox="" className="w-full h-full border-0" title="pdf" />
                ) : (
                  <div className="text-sm text-slate-500">미리보기 미지원 형식</div>
                )}
              </div>
            </div>

            <div className="w-1/2 flex flex-col">
              <div className="px-4 py-2 border-b border-slate-200 bg-white">
                <span className="text-xs font-semibold text-slate-700">사업자 등록증 정보 (검토/수정 가능)</span>
              </div>
              <div className="flex-1 overflow-auto p-4 space-y-3">
                {ocrError && (
                  <div className="text-xs px-2 py-1 rounded bg-amber-50 text-amber-700 border border-amber-200">
                    ! {ocrError}
                  </div>
                )}
                <Field
                  label="사업자번호"
                  value={fields.businessNumber ?? ''}
                  placeholder="1234567890 (10자리)"
                  onChange={(v) => updateField('businessNumber', v)}
                />
                <Field
                  label="상호 (법인명)"
                  value={fields.businessName ?? ''}
                  placeholder="예: ㈜원온중기"
                  onChange={(v) => updateField('businessName', v)}
                />
                <Field
                  label="대표자 성명"
                  value={fields.representativeName ?? ''}
                  placeholder="예: 홍길동"
                  onChange={(v) => updateField('representativeName', v)}
                />
                <Field
                  label="개업연월일"
                  value={fields.startDate ?? ''}
                  placeholder="YYYYMMDD"
                  onChange={(v) => updateField('startDate', v)}
                />
                <Field
                  label="사업장 소재지"
                  value={fields.address ?? ''}
                  placeholder="예: 서울특별시 ..."
                  onChange={(v) => updateField('address', v)}
                  multiline
                />
                <Field
                  label="업태 / 종목"
                  value={fields.businessType ?? ''}
                  placeholder="예: 건설업 / 토목공사"
                  onChange={(v) => updateField('businessType', v)}
                />
                {error && (
                  <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1">{error}</p>
                )}
              </div>
              <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={onClose}
                  disabled={busy}
                  className="text-sm px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100"
                >
                  취소
                </button>
                <button
                  type="button"
                  onClick={onReviewSubmit}
                  disabled={busy || !file}
                  className="text-sm px-4 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50"
                >
                  {busy ? '업로드 중...' : '업로드 + 검증'}
                </button>
              </div>
            </div>
          </div>
        )}

        {step === 'mismatch' && (
          <div className="flex-1 overflow-auto p-10 flex flex-col items-center gap-5 min-h-[400px]">
            <IconAlertTriangle size={64} className="text-amber-500" />
            <div className="text-xl font-bold text-slate-900">회사 등록명과 상호가 달라요!</div>
            <div className="w-full max-w-xl bg-amber-50 border-2 border-amber-300 rounded-lg p-4 space-y-2">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <div className="text-xs font-semibold text-amber-800">회사 등록명</div>
                  <div className="font-semibold text-slate-900 mt-0.5">{companyName}</div>
                </div>
                <div>
                  <div className="text-xs font-semibold text-amber-800">cert 상호</div>
                  <div className="font-semibold text-slate-900 mt-0.5">{fields.businessName}</div>
                </div>
              </div>
              <div className="text-xs text-amber-700 leading-relaxed pt-2 border-t border-amber-200">
                국세청 검증은 통과했지만 등록 정보의 회사명과 cert 의 상호가 다릅니다.
                관리자가 사유를 보고 회사 정보 갱신 또는 반려를 결정합니다.
              </div>
            </div>
            <div className="w-full max-w-xl space-y-2">
              <label className="text-sm font-semibold text-slate-800">불일치 사유 *</label>
              <textarea
                value={mismatchReason}
                onChange={(e) => setMismatchReason(e.target.value)}
                placeholder="예: 가입 시 잘못 입력 / 회사명 변경 / 본사-지사 통합 / 법인 전환 등"
                rows={4}
                className="input w-full text-sm bg-white"
                autoFocus
              />
              {error && (
                <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1">{error}</p>
              )}
            </div>
            <div className="w-full max-w-xl flex justify-between gap-2">
              <button
                type="button"
                onClick={() => setStep('review')}
                disabled={busy}
                className="text-sm px-4 py-2 rounded border border-slate-300 text-slate-700 hover:bg-slate-100"
              >
                ← 뒤로 (다시 수정)
              </button>
              <button
                type="button"
                onClick={onMismatchSubmit}
                disabled={busy || !mismatchReason.trim()}
                className="text-sm px-5 py-2 rounded bg-amber-600 text-white font-semibold hover:bg-amber-700 disabled:opacity-50"
              >
                {busy ? '업로드 중...' : '관리자에게 검토 요청'}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  placeholder,
  onChange,
  multiline,
}: {
  label: string;
  value: string;
  placeholder?: string;
  onChange: (v: string) => void;
  multiline?: boolean;
}) {
  return (
    <div>
      <label className="text-xs font-medium text-slate-600 block mb-0.5">{label}</label>
      {multiline ? (
        <textarea
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          rows={2}
          className="input w-full text-sm"
        />
      ) : (
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          className="input w-full text-sm"
        />
      )}
    </div>
  );
}
