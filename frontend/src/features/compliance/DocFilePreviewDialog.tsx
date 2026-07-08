import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { parseRequiredFields, type DocumentTypeResponse } from '../../types/document';

type DocBrief = {
  id: number;
  document_type_id: number;
  document_type_name?: string | null;
  owner_type: 'EQUIPMENT' | 'PERSON' | 'COMPANY';
  owner_id: number;
  owner_name?: string | null;
  file_name?: string | null;
  expiry_date?: string | null;
  verification_status?: string | null;
  extracted_data?: string | null;
  rejected_reason?: string | null;
};

type Props = {
  doc: DocBrief;
  docType?: DocumentTypeResponse;
  canReverify: boolean;
  onClose: () => void;
  onReverified: () => void;
  onReupload: () => void;
};

const REASON_LABEL: Record<string, string> = {
  // NTS 사업자등록증
  NTS_ACTIVE: '정상 사업자',
  NTS_SUSPENDED: '휴업 사업자',
  NTS_CLOSED: '폐업 사업자',
  NTS_INVALID: '국세청에 등록되지 않은 사업자번호입니다. 번호를 확인해 주세요.',
  NTS_NO_DATA: '국세청 조회 결과 일치하는 사업자가 없습니다.',
  BIZNAME_MISMATCH: '회사 등록 정보와 사업자등록증 상호/대표자가 다릅니다.',
  // 외부 API 실패
  UPSTREAM_ERROR: '검증 서버에 일시적 장애가 있습니다. 잠시 후 다시 시도하거나 ADMIN 수동 확인을 기다려 주세요.',
  UPSTREAM_DISABLED: '검증 서버가 비활성화되어 있어 ADMIN 수동 확인이 필요합니다.',
  // OCR
  OCR_FAILED: 'OCR이 사진에서 필요한 정보를 추출하지 못했습니다. 빛 반사·각도를 조정해 재촬영하거나 수동 입력해 주세요.',
  // 일반
  VERIFICATION_FAILED: '검증에 실패했습니다.',
};

function describeReason(code?: string | null): string {
  if (!code) return '';
  return REASON_LABEL[code] ?? code;
}

const FIELD_LABEL: Record<string, string> = {
  biz_no: '사업자번호',
  start_date: '개업연월일',
  owner_name: '대표자 성명',
  business_name: '상호',
  address: '주소',
  business_type: '업태/종목',
  license_no: '면허번호',
  license_condition_code: '면허종류',
  birth_date: '생년월일 (YYYYMMDD)',
  name: '성명',
  vehicle_no: '차량번호',
  expiry_date: '만료일',
  registration_no: '교육 등록번호',
};

export default function DocFilePreviewDialog({ doc, docType, canReverify, onClose, onReverified, onReupload }: Props) {
  const [src, setSrc] = useState<string | null>(null);
  const [contentType, setContentType] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const requiredFields = parseRequiredFields(docType?.required_fields);

  // extracted_data (OCR 추출 + 이전 manual 입력) 으로 inputs 초기값 prefill.
  // 키 매핑: OCR 응답 camelCase + manual_* 둘 다 시도.
  const initialInputs: Record<string, string> = (() => {
    const out: Record<string, string> = {};
    if (!doc.extracted_data) return out;
    try {
      const parsed = JSON.parse(doc.extracted_data) as Record<string, unknown>;
      for (const k of requiredFields) {
        // 1) snake_case 직접 매치
        const v1 = parsed[k];
        if (typeof v1 === 'string' && v1) { out[k] = v1; continue; }
        // 2) manual_<key> (사용자가 OCR preview 단계에서 입력한 값)
        const v2 = parsed[`manual_${k}`];
        if (typeof v2 === 'string' && v2) { out[k] = v2; continue; }
      }
    } catch { /* ignore */ }
    return out;
  })();

  const [inputs, setInputs] = useState<Record<string, string>>(initialInputs);
  const [result, setResult] = useState<{
    status?: string;
    rejectedReason?: string | null;
    verificationResult?: string | null;
  } | null>(null);

  useEffect(() => {
    let url: string | null = null;
    let cancelled = false;
    setLoading(true);
    api.get(`/api/documents/${doc.id}/file`, { responseType: 'blob' })
      .then((r) => {
        if (cancelled) return;
        const blob = r.data as Blob;
        url = URL.createObjectURL(blob);
        setSrc(url);
        setContentType(blob.type || null);
      })
      .catch(() => { if (!cancelled) setSrc(null); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; if (url) URL.revokeObjectURL(url); };
  }, [doc.id]);

  async function reverify() {
    setBusy(true);
    setResult(null);
    try {
      const filtered: Record<string, string> = {};
      for (const k of requiredFields) {
        const v = (inputs[k] ?? '').trim();
        if (v) filtered[k] = v;
      }
      const res = await api.post(`/api/documents/${doc.id}/verify`, { user_inputs: filtered });
      const d = res.data as { verification_status?: string; rejected_reason?: string | null; verification_result?: string | null };
      setResult({
        status: d.verification_status,
        rejectedReason: d.rejected_reason,
        verificationResult: d.verification_result,
      });
      onReverified();
    } catch (err) {
      const msg = err instanceof AxiosError ? (err.response?.data?.message ?? err.message) : '재검증 실패';
      setResult({ status: 'ERROR', rejectedReason: msg });
    } finally {
      setBusy(false);
    }
  }

  const isImage = contentType?.startsWith('image/');
  const isPdf = contentType === 'application/pdf';
  const currentStatus = result?.status ?? doc.verification_status;
  const verifyChip = currentStatus === 'VERIFIED'
    ? <span className="px-2 py-0.5 rounded text-xs font-bold bg-emerald-600 text-white">✓ 검증 완료</span>
    : currentStatus === 'REJECTED'
      ? <span className="px-2 py-0.5 rounded text-xs font-bold bg-rose-600 text-white">✗ 반려</span>
      : currentStatus === 'OCR_REVIEW_REQUIRED'
        ? <span className="px-2 py-0.5 rounded text-xs font-bold bg-amber-500 text-white">OCR 검토 필요</span>
        : currentStatus === 'ERROR'
          ? <span className="px-2 py-0.5 rounded text-xs font-bold bg-rose-600 text-white">오류</span>
          : <span className="px-2 py-0.5 rounded text-xs font-semibold bg-slate-200 text-slate-700">대기</span>;

  // verification_result는 JSON 문자열일 가능성 — 사람이 읽을 수 있게 펼침
  const parsedResult: Record<string, unknown> | null = (() => {
    if (!result?.verificationResult) return null;
    try { return JSON.parse(result.verificationResult); } catch { return null; }
  })();

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[92vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[11px] text-slate-500">{doc.owner_type === 'EQUIPMENT' ? '장비' : doc.owner_type === 'PERSON' ? '인원' : '회사'}</span>
              <span className="text-sm font-semibold text-slate-900 truncate">{doc.owner_name ?? '#' + doc.owner_id}</span>
              <span className="text-slate-300">·</span>
              <span className="text-sm font-bold text-slate-900 truncate">{doc.document_type_name ?? '#' + doc.document_type_id}</span>
              {verifyChip}
            </div>
            <div className="text-xs text-slate-500 mt-0.5">
              {doc.file_name ?? '-'}{doc.expiry_date ? ` · 만료 ${doc.expiry_date}` : ''}
            </div>
          </div>
          <button onClick={onClose} className="text-slate-500 hover:text-slate-900 text-2xl leading-none shrink-0">×</button>
        </div>

        <div className="flex-1 overflow-auto bg-slate-50 grid grid-cols-1 md:grid-cols-2 gap-0 min-h-0">
          {/* 좌측: 미리보기 */}
          <div className="flex items-center justify-center p-3 bg-slate-100 min-h-[400px] md:min-h-0">
            {loading ? (
              <span className="text-sm text-slate-400">불러오는 중…</span>
            ) : !src ? (
              <span className="text-sm text-slate-400">파일을 불러올 수 없습니다</span>
            ) : isImage ? (
              <img src={src} alt={doc.file_name ?? ''} className="max-h-[70vh] object-contain" />
            ) : isPdf ? (
              <iframe src={src} title={doc.file_name ?? 'document'} className="w-full h-[70vh]" />
            ) : (
              <a href={src} download={doc.file_name ?? 'document'} className="btn-primary">다운로드</a>
            )}
          </div>

          {/* 우측: 입력 + 결과 */}
          <div className="p-4 bg-white overflow-y-auto space-y-3">
            {canReverify && requiredFields.length > 0 && (
              <>
                <div className="text-sm font-bold text-slate-900">수동 입력 (선택)</div>
                <p className="text-xs text-slate-500">
                  OCR로 안 잡히거나 잘못된 값을 직접 채우면 검증이 더 잘 됩니다. 비워두고 진행도 가능.
                </p>
                {requiredFields.map((key) => (
                  <label key={key} className="block">
                    <span className="text-xs font-medium text-slate-600">{FIELD_LABEL[key] ?? key}</span>
                    <input
                      type="text"
                      value={inputs[key] ?? ''}
                      onChange={(e) => setInputs((v) => ({ ...v, [key]: e.target.value }))}
                      className="mt-1 w-full px-3 py-1.5 rounded-lg border border-slate-200 text-sm"
                      autoComplete="off"
                    />
                  </label>
                ))}
              </>
            )}

            {canReverify && requiredFields.length === 0 && (
              <p className="text-xs text-slate-500">
                추가 입력 없이 이미지/파일 기반으로 검증을 시도합니다.
              </p>
            )}

            {!canReverify && (
              <p className="text-xs text-slate-500">
                이 서류 종류는 자동 검증이 지원되지 않습니다. ADMIN이 수동으로 확인합니다.
              </p>
            )}

            {!result && doc.rejected_reason && (
              <div className="rounded-lg p-3 text-sm space-y-1 bg-rose-50 border border-rose-200 text-rose-800">
                <div className="font-bold">이전 반려 사유</div>
                <div className="text-xs">{describeReason(doc.rejected_reason)}</div>
              </div>
            )}

            {result && (
              <div className={`rounded-lg p-3 text-sm space-y-1 ${
                result.status === 'VERIFIED' ? 'bg-emerald-50 border border-emerald-200 text-emerald-800'
                : result.status === 'REJECTED' ? 'bg-rose-50 border border-rose-200 text-rose-800'
                : result.status === 'OCR_REVIEW_REQUIRED' ? 'bg-amber-50 border border-amber-200 text-amber-800'
                : 'bg-rose-50 border border-rose-200 text-rose-800'
              }`}>
                <div className="font-bold">
                  {result.status === 'VERIFIED' ? '검증 완료'
                    : result.status === 'REJECTED' ? '반려'
                    : result.status === 'OCR_REVIEW_REQUIRED' ? 'OCR 검토 필요 — 자동 인식 실패'
                    : '오류'}
                </div>
                {result.rejectedReason && <div className="text-xs">사유: {describeReason(result.rejectedReason)}</div>}
                {parsedResult && (
                  <details className="text-xs">
                    <summary className="cursor-pointer text-slate-600">상세 결과</summary>
                    <pre className="mt-1 whitespace-pre-wrap text-[10px]">{JSON.stringify(parsedResult, null, 2)}</pre>
                  </details>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="px-5 py-3 border-t border-slate-200 flex items-center justify-end gap-2">
          <button onClick={onClose} className="btn-ghost text-sm" disabled={busy}>닫기</button>
          <button onClick={onReupload} disabled={busy}
                  className="px-4 py-2 text-sm font-semibold rounded-lg bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
            + 재업로드
          </button>
          {canReverify && (
            <button onClick={reverify} disabled={busy}
                    className="px-4 py-2 text-sm font-semibold rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50">
              {busy ? '검증 중…' : '재검증'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
