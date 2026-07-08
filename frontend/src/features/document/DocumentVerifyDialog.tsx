import { useEffect, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { parseRequiredFields, type DocumentResponse, type DocumentTypeResponse } from '../../types/document';

type Props = {
  open: boolean;
  doc: DocumentResponse;
  type: DocumentTypeResponse;
  onClose: () => void;
  onDone: (updated: DocumentResponse) => void;
};

const FIELD_LABEL: Record<string, string> = {
  license_no: '면허/자격번호',
  name: '이름',
  birth_date: '생년월일 (YYYYMMDD)',
  license_condition_code: '면허 종별 코드 (예: 01)',
  biz_no: '사업자번호',
  start_date: '개업일 (YYYYMMDD)',
  owner_name: '대표자명',
  vehicle_no: '차량번호',
};

/**
 * S-4 단계 3: verify-api 호출 시 type.required_fields 기반으로 사용자 보충 입력을 받는 모달.
 *
 * - extracted_data 가 이미 채워져 있으면 그 값을 초기 채움 (OCR 결과).
 * - user_inputs 는 extracted_data 보다 우선 (덮어씀).
 * - 빈 값 그대로 호출도 허용 — 결과적으로 OCR_REVIEW_REQUIRED 가 될 가능성 있음.
 */
export default function DocumentVerifyDialog({ open, doc, type, onClose, onDone }: Props) {
  const requiredFields = parseRequiredFields(type.required_fields);
  const [values, setValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    // OCR 결과 prefill
    let prefill: Record<string, string> = {};
    if (doc.extracted_data) {
      try {
        const parsed = JSON.parse(doc.extracted_data);
        if (parsed && typeof parsed === 'object') {
          for (const k of requiredFields) {
            const v = parsed[k];
            if (typeof v === 'string') prefill[k] = v;
          }
        }
      } catch { /* ignore */ }
    }
    setValues(prefill);
    setError(null);
  }, [open, doc.id]);

  if (!open) return null;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const filtered: Record<string, string> = {};
      for (const k of requiredFields) {
        const v = (values[k] ?? '').trim();
        if (v) filtered[k] = v;
      }
      const res = await api.post<DocumentResponse>(`/api/documents/${doc.id}/verify`,
              { user_inputs: filtered });
      onDone(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '검증 실패');
      } else {
        setError('검증 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-slate-900/40" onClick={busy ? undefined : onClose} />
      <form onSubmit={submit} className="relative bg-white rounded-xl shadow-xl max-w-md w-full p-6 space-y-4">
        <div>
          <h3 className="text-lg font-bold">자동 검증</h3>
          <p className="text-sm text-slate-500 mt-1">{type.name}</p>
          {type.verify_endpoint && (
            <p className="text-xs text-slate-400 mt-1">검증 경로: {type.verify_endpoint}</p>
          )}
        </div>

        {requiredFields.length === 0 ? (
          <p className="text-sm text-slate-600">
            추가 입력 없이 이미지/파일로 검증을 시도합니다 (KOSHA 등). 진행하시겠습니까?
          </p>
        ) : (
          <div className="space-y-3">
            <p className="text-xs text-slate-500">
              OCR 결과로 자동 채워진 값은 수정할 수 있습니다. 비어 있어도 진행 가능하며 미보충 시 OCR 검토 필요로 떨어질 수 있습니다.
            </p>
            {requiredFields.map((key) => (
              <label key={key} className="block">
                <span className="text-xs font-medium text-slate-600">
                  {FIELD_LABEL[key] ?? key}
                </span>
                <input
                  type="text"
                  value={values[key] ?? ''}
                  onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
                  className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm"
                  autoComplete="off"
                />
              </label>
            ))}
          </div>
        )}

        {error && (
          <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded-lg px-3 py-2">{error}</p>
        )}

        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} disabled={busy}
                  className="px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100 disabled:opacity-50">
            취소
          </button>
          <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '검증 중...' : '검증 실행'}
          </button>
        </div>
      </form>
    </div>
  );
}
