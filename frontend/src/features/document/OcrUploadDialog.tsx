import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { parseRequiredFields, type DocumentResponse, type DocumentTypeResponse, type OwnerType } from '../../types/document';
import { groupDocTypes } from './docTypeGrouping';
import DocumentCornerAligner from './DocumentCornerAligner';
import { detectDocumentCorners } from './detectCorners';

type Props = {
  open: boolean;
  ownerType: OwnerType;
  ownerId: number;
  types: DocumentTypeResponse[];
  /** 재업로드 등 종류가 이미 정해진 경우 — pick step 의 select 가 잠기고 파일 선택부터 시작. */
  presetTypeId?: number;
  /** 모달 헤더 제목. 미지정 시 "서류 추가". */
  title?: string;
  /**
   * 재검증 모드 — 기존 doc 의 이미지/OCR 결과를 그대로 보여주고, 사용자 보완 입력 후 verify 호출.
   * 새 파일 업로드 X, /api/documents/:id/verify 만 호출. presetTypeId 와 함께 사용.
   */
  reverifyDocId?: number;
  /** reverify 모드일 때 prefill 할 OCR/manual 필드. extracted_data JSON 그대로 넘기면 됨. */
  reverifyExtractedData?: string | null;
  /** 이 자원의 PersonRole 목록 — 종류를 필수/선택/기타로 그룹핑하는 데 사용 (PERSON). */
  ownerRoles?: string[];
  /** 이 자원의 EquipmentCategory — 종류 그룹핑용 (EQUIPMENT). */
  ownerCategory?: string;
  onClose: () => void;
  onUploaded: (doc: DocumentResponse) => void;
};

type Step = 'pick' | 'align' | 'ocr' | 'review';

/** 서류 이름 → verify-api ocrType 매핑. */
function ocrTypeFor(typeName: string): string | null {
  const n = typeName ?? '';
  if (n.includes('사업자')) return 'BUSINESS_REGISTRATION';
  if (n.includes('운전면허')) return 'DRIVER_LICENSE';
  if (n.includes('안전교육')) return 'KOSHA';
  if (n.includes('자동차등록')) return 'EQUIPMENT_REGISTRATION';
  return null;
}

/** OCR 응답 키(camelCase) → DocumentType.required_fields 키(snake_case) 매핑.
 *  서로 schema 가 달라 OCR 결과를 required 필드 칸에 자동 채우려면 변환 필요. */
const OCR_KEY_MAP: Record<string, Record<string, string>> = {
  BUSINESS_REGISTRATION: {
    businessNumber: 'biz_no',
    startDate: 'start_date',
    representativeName: 'owner_name',
    businessName: 'business_name',
    address: 'address',
    businessType: 'business_type',
  },
  DRIVER_LICENSE: {
    licenseNumber: 'license_no',
    name: 'name',
    licenseType: 'license_condition_code',
    birth: 'birth_date',
  },
  KOSHA: {
    name: 'name',
    birthDate: 'birth_date',
    registrationNumber: 'registration_no',
  },
  EQUIPMENT_REGISTRATION: {
    vehicleNumber: 'vehicle_no',
    modelName: 'model',
    productionYear: 'year',
  },
};

function remapOcrFields(ocrType: string | null, raw: Record<string, string>): Record<string, string> {
  if (!ocrType || !OCR_KEY_MAP[ocrType]) return raw;
  const map = OCR_KEY_MAP[ocrType];
  const out: Record<string, string> = {};
  Object.entries(raw).forEach(([k, v]) => {
    out[map[k] ?? k] = v;
  });
  return out;
}

/** 필드 키 → 한글 라벨. */
const FIELD_LABEL: Record<string, string> = {
  biz_no: '사업자번호',
  start_date: '개업연월일',
  owner_name: '대표자 성명',
  business_name: '상호',
  address: '주소',
  business_type: '업태/종목',
  license_no: '면허번호',
  license_condition_code: '면허종류',
  birth_date: '생년월일',
  name: '성명',
  vehicle_no: '차량번호',
  model: '차명',
  year: '연식',
  expiry_date: '만료일',
  registration_no: '교육 등록번호',
};

/** 필드 키 → 입력 예시 placeholder. */
const FIELD_PLACEHOLDER: Record<string, string> = {
  biz_no: '예: 1234567890 (10자리)',
  start_date: '예: 20180315',
  owner_name: '예: 홍길동',
  business_name: '예: ㈜다인',
  business_type: '예: 건설업 / 토목공사',
  license_no: '예: 11-12-345678-90',
  birth_date: '예: 19850714',
  name: '예: 홍길동',
  vehicle_no: '예: 12가1234',
};

/** 면허종류 선택지 — 한 면허증에 복수 종류(1종 대형+보통 등) 표기 가능해 다중 선택. */
const LICENSE_TYPE_OPTIONS = [
  '1종 대형', '1종 보통', '1종 소형', '1종 특수',
  '대형견인', '소형견인', '구난차',
  '2종 보통', '2종 소형', '2종 원동기',
];

/** 면허종류 다중 선택 칩. 값은 ", " 로 결합한 문자열 (예: "1종 대형, 1종 보통"). */
function LicenseTypeChips({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const norm = (s: string) => s.replace(/\s+/g, '');
  const tokens = value.split(',').map((s) => s.trim()).filter(Boolean);
  const isOn = (opt: string) => tokens.some((t) => norm(t) === norm(opt));
  function toggle(opt: string) {
    const next = isOn(opt)
      ? tokens.filter((t) => norm(t) !== norm(opt))
      : [...tokens, opt];
    // 알려진 종류는 정해진 순서로 정렬, OCR 이 준 미등록 표기는 뒤에 보존.
    const known = LICENSE_TYPE_OPTIONS.filter((o) => next.some((t) => norm(t) === norm(o)));
    const unknown = next.filter((t) => !LICENSE_TYPE_OPTIONS.some((o) => norm(o) === norm(t)));
    onChange([...known, ...unknown].join(', '));
  }
  return (
    <div className="mt-1">
      <div className="flex flex-wrap gap-1.5">
        {LICENSE_TYPE_OPTIONS.map((opt) => {
          const on = isOn(opt);
          return (
            <button key={opt} type="button" onClick={() => toggle(opt)}
              className={`px-2.5 py-1 rounded-full text-xs font-medium border transition ${
                on ? 'bg-brand-600 text-white border-brand-600'
                   : 'bg-white text-slate-600 border-slate-300 hover:border-slate-400'
              }`}>
              {opt}
            </button>
          );
        })}
      </div>
      <div className="mt-1 text-[11px] text-slate-400">
        보유한 종류를 모두 선택하세요 (복수 선택 가능)
      </div>
    </div>
  );
}

/**
 * 일반화 OCR 업로드 모달.
 * - 서류 종류 select + 파일 선택 → OCR (가능한 종류만) → 결과 검토 → 업로드
 * - OCR 안 되는 종류는 pick 단계에서 바로 업로드.
 */
export default function OcrUploadDialog({
  open, ownerType, ownerId, types, presetTypeId, title,
  reverifyDocId, reverifyExtractedData, ownerRoles, ownerCategory,
  onClose, onUploaded,
}: Props) {
  const isReverify = reverifyDocId != null;
  const [typeId, setTypeId] = useState<number | ''>(presetTypeId ?? '');
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  /** 주민번호 검출 시 서버가 돌려준 마스킹 이미지 (data URL) — 저장도 이 형태로 됨. */
  const [maskedPreview, setMaskedPreview] = useState<string | null>(null);
  const [expiryDate, setExpiryDate] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [step, setStep] = useState<Step>('pick');
  /** 정렬 단계 — 자동검출 코너 프리필(원본 px) + 검출 진행중 여부. */
  const [initialCorners, setInitialCorners] = useState<[number, number][] | undefined>(undefined);
  const [alignBusy, setAlignBusy] = useState(false);
  const [ocrError, setOcrError] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selectedType = useMemo(
    () => types.find((t) => t.id === typeId) ?? null,
    [types, typeId],
  );
  const ocrType = selectedType ? ocrTypeFor(selectedType.name) : null;
  /** 이 doc-type 가 영역-크롭 OCR 템플릿을 가지면(=폰/스캔 정렬 대상) 이미지 파일에 한해 정렬 단계 삽입. */
  const hasRegionTemplate = !!(selectedType?.ocr_region_template && selectedType.ocr_region_template.trim());
  /** 종류별 사전 정의된 필수 필드 (DocumentType.required_fields). OCR 실패 시 직접 입력. */
  const requiredFieldKeys = useMemo(
    () => (selectedType ? parseRequiredFields(selectedType.required_fields) : []),
    [selectedType],
  );
  /** V82: 로컬 OCR 만료일 백필 대상 — verify 미보유 + ocr_enabled + has_expiry (정기검사증).
   *  이 타입만 만료일 입력을 선택으로 완화(업로드 후 OCR 이 자동 백필). BE 게이트와 근사 일치. */
  const isOcrBackfillTarget = !!selectedType
    && !selectedType.verify_endpoint
    && selectedType.ocr_enabled
    && selectedType.has_expiry;

  /** 이 자원에 맞춰 종류를 필수/선택/기타로 그룹핑 (pick 단계 표시용). */
  const typeGroups = useMemo(
    () => groupDocTypes(types, ownerType, ownerRoles, ownerCategory),
    [types, ownerType, ownerRoles, ownerCategory],
  );
  const optLabel = (t: DocumentTypeResponse) =>
    `${t.name}${ocrTypeFor(t.name) ? ' (OCR 자동 추출)' : ''}${t.has_expiry ? ' · 만료일 필수' : ''}`;

  // 종류 변경 시 required_fields 로 fields 초기화 (빈 값) — OCR 결과가 와도 merge.
  useEffect(() => {
    if (requiredFieldKeys.length > 0) {
      setFields((prev) => {
        const next: Record<string, string> = {};
        requiredFieldKeys.forEach((k) => { next[k] = prev[k] ?? ''; });
        return next;
      });
    } else {
      setFields({});
    }
  }, [requiredFieldKeys]);

  useEffect(() => {
    if (!open) {
      setTypeId(presetTypeId ?? '');
      setFile(null);
      setPreviewUrl(null);
      setMaskedPreview(null);
      setExpiryDate('');
      setFields({});
      setStep('pick');
      setInitialCorners(undefined);
      setAlignBusy(false);
      setOcrError('');
      setBusy(false);
      setError(null);
    } else if (presetTypeId !== undefined) {
      setTypeId(presetTypeId);
    }
  }, [open, presetTypeId]);

  /** reverify 모드: 기존 doc 의 파일을 blob 으로 가져와 미리보기 표시 + extracted_data 로 fields prefill */
  useEffect(() => {
    if (!open || !isReverify || reverifyDocId == null) return;
    let revoked: string | null = null;
    setStep('review');
    // 파일 blob fetch
    api.get(`/api/documents/${reverifyDocId}/file`, { responseType: 'blob' })
      .then((res) => {
        const blob = res.data as Blob;
        const url = URL.createObjectURL(blob);
        revoked = url;
        setPreviewUrl(url);
        // file 객체 — Blob 을 File 로 래핑해서 isImage/isPdf 판정에 사용
        setFile(new File([blob], 'reverify-file', { type: blob.type }));
      })
      .catch(() => { /* 미리보기 실패해도 폼 동작은 가능 */ });
    // extracted_data prefill
    if (reverifyExtractedData) {
      try {
        const parsed = JSON.parse(reverifyExtractedData);
        const inner = (parsed?.ocrData ?? parsed) ?? {};
        const out: Record<string, string> = {};
        for (const [k, v] of Object.entries(inner)) {
          if (typeof v !== 'string' && typeof v !== 'number') continue;
          // manual* 접두어 제거
          let key = k;
          if (k.startsWith('manual') && k.length > 6) {
            const rest = k.slice(6);
            key = rest.charAt(0).toLowerCase() + rest.slice(1);
          }
          // camelCase → snake_case (required_fields 와 매칭)
          const snake = key.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
          if (!out[snake]) out[snake] = String(v);
        }
        setFields(out);
      } catch { /* ignore */ }
    }
    return () => { if (revoked) URL.revokeObjectURL(revoked); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isReverify, reverifyDocId]);

  useEffect(() => {
    if (!file) { setPreviewUrl(null); return; }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  if (!open) return null;

  async function runOcr(f: File, ocrT: string) {
    setStep('ocr');
    setOcrError('');
    // 종류별 빈 필드 키 유지하고 OCR 결과로 덮어쓰기 (실패 시 사용자가 직접 입력).
    try {
      const fd = new FormData();
      fd.append('file', f);
      fd.append('ocrType', ocrT);
      const res = await api.post<{
        ok: boolean; fields?: Record<string, string>;
        masked_image_base64?: string; reasonCode?: string; message?: string;
      }>(
        '/api/documents/ocr-preview', fd,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      );
      setMaskedPreview(res.data.masked_image_base64
        ? `data:image/jpeg;base64,${res.data.masked_image_base64}` : null);
      if (res.data.ok && res.data.fields) {
        const mapped = remapOcrFields(ocrT, res.data.fields);
        setFields((prev) => ({ ...prev, ...mapped }));
      } else {
        setOcrError(`OCR 자동 추출 실패 (${res.data.reasonCode ?? 'UNKNOWN'}). 직접 입력해주세요.`);
      }
    } catch {
      setOcrError('OCR 호출 실패. 직접 입력해주세요.');
    } finally {
      setStep('review');
    }
  }

  /** 정렬 단계 진입 — 자동 모서리 검출로 프리필 후 DocumentCornerAligner 표시 (이미지 + 템플릿 보유 시). */
  async function startAlign(f: File) {
    setOcrError('');
    setInitialCorners(undefined);
    setAlignBusy(true);
    setStep('align');
    const corners = await detectDocumentCorners(f);
    setInitialCorners(corners);
    setAlignBusy(false);
  }

  /** 사용자가 맞춘 4모서리(원본 px)로 영역-크롭 OCR → fields 자동채움. */
  async function runRegionOcr(corners: [number, number][]) {
    if (!file || typeId === '') { setStep('review'); return; }
    setStep('ocr');
    setOcrError('');
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('documentTypeId', String(typeId));
      fd.append('corners', JSON.stringify(corners));
      const res = await api.post<{ ok: boolean; fields?: Record<string, string>; reasonCode?: string }>(
        '/api/documents/ocr-region-preview', fd,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      );
      // 영역 OCR 응답 fields 는 required_fields 와 동일 snake_case → 재매핑 없이 merge.
      if (res.data.ok && res.data.fields) {
        setFields((prev) => ({ ...prev, ...res.data.fields }));
      } else {
        setOcrError(`영역 OCR 자동 추출 실패 (${res.data.reasonCode ?? 'UNKNOWN'}). 직접 입력해주세요.`);
      }
    } catch {
      setOcrError('OCR 호출 실패. 직접 입력해주세요.');
    } finally {
      setStep('review');
    }
  }

  function onFilePicked(f: File) {
    setFile(f);
    setMaskedPreview(null);
    // 이미지 + 영역맵 보유 → 4모서리 정렬 단계. PDF·템플릿없음은 기존 흐름(Vision or 수기) 유지.
    if (f.type.startsWith('image/') && hasRegionTemplate) {
      void startAlign(f);
    } else if (ocrType) {
      void runOcr(f, ocrType);
    } else {
      setStep('review');
    }
  }

  async function doUpload() {
    // 재검증 모드: 새 업로드 없이 /api/documents/:id/verify 만 호출
    if (isReverify && reverifyDocId != null) {
      setError(null);
      setBusy(true);
      try {
        const userInputs: Record<string, string> = {};
        Object.entries(fields).forEach(([k, v]) => {
          if (v && String(v).trim()) userInputs[k] = String(v).trim();
        });
        const res = await api.post<DocumentResponse>(`/api/documents/${reverifyDocId}/verify`,
          { user_inputs: userInputs });
        onUploaded(res.data);
      } catch (err) {
        if (err instanceof AxiosError) setError(err.response?.data?.message ?? '재검증 실패');
        else setError('재검증 실패');
      } finally {
        setBusy(false);
      }
      return;
    }

    if (!file || !typeId || !selectedType) { setError('서류 종류와 파일을 선택하세요'); return; }
    // V82: OCR 백필 대상은 만료일 미입력 허용 (업로드 후 자동 백필). 그 외 has_expiry 타입은 필수.
    if (selectedType.has_expiry && !expiryDate && !isOcrBackfillTarget) { setError('만료일을 입력하세요'); return; }
    setError(null);
    setBusy(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      // fields 는 snake_case (required_fields schema). backend manual<CamelCase> 매칭하도록 변환.
      // 예: biz_no → manualBizNo, owner_name → manualOwnerName
      Object.entries(fields).forEach(([k, v]) => {
        if (v && v.trim()) {
          const camel = k.split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join('');
          fd.append('manual' + camel, v.trim());
        }
      });
      const params: Record<string, string> = {
        ownerType,
        ownerId: String(ownerId),
        documentTypeId: String(typeId),
      };
      if (expiryDate) params.expiryDate = expiryDate;
      const res = await api.post<DocumentResponse>('/api/documents', fd, { params });
      // 자동 검증 trigger — verify_endpoint 있는 서류만 의미 있음
      try { await api.post(`/api/documents/${res.data.id}/verify`, {}); } catch { /* ignore */ }
      // 면허증 등 OCR 로 주소가 잡혔고 인원 서류면, 인원 주소(선택)에 반영 — best-effort.
      if (ownerType === 'PERSON' && fields.address && fields.address.trim()) {
        try { await api.patch(`/api/persons/${ownerId}`, { address: fields.address.trim() }); } catch { /* ignore */ }
      }
      onUploaded(res.data);
      onClose();
    } catch (e) {
      if (e instanceof AxiosError) setError(e.response?.data?.message ?? '업로드 실패');
      else setError('업로드 실패');
    } finally {
      setBusy(false);
    }
  }

  const isImage = file && file.type.startsWith('image/');
  const isPdf = file && file.type === 'application/pdf';
  const fieldEntries = Object.entries(fields);

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
         onClick={() => !busy && step !== 'ocr' && step !== 'align' && onClose()}>
      <div className="bg-white rounded-xl max-w-4xl w-full max-h-[92vh] overflow-hidden shadow-2xl flex flex-col"
           onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200">
          <div>
            <h3 className="text-lg font-bold text-slate-900">{title ?? '서류 추가'}</h3>
            <div className="text-xs text-slate-500 mt-0.5">
              {step === 'pick' && '서류 종류 + 파일 선택'}
              {step === 'align' && '문서 영역 맞추기'}
              {step === 'ocr' && 'OCR 분석 중...'}
              {step === 'review' && (ocrType
                ? 'OCR 결과 검토 후 업로드'
                : '필수 정보 입력 후 업로드')}
            </div>
          </div>
          <button type="button" onClick={() => !busy && step !== 'ocr' && onClose()}
                  disabled={busy || step === 'ocr'}
                  className="text-slate-400 hover:text-slate-700 text-xl leading-none px-2 disabled:opacity-30">✕</button>
        </div>

        {step === 'pick' && (
          <div className="flex-1 overflow-auto p-6 space-y-4">
            <label className="block">
              <span className="text-sm font-medium text-slate-700">서류 종류</span>
              <select value={typeId}
                onChange={(e) => setTypeId(e.target.value === '' ? '' : Number(e.target.value))}
                disabled={presetTypeId !== undefined}
                className="input mt-1 disabled:bg-slate-100 disabled:text-slate-700">
                <option value="">— 선택 —</option>
                {typeGroups.required.length > 0 && (
                  <optgroup label="필수 서류">
                    {typeGroups.required.map((t) => <option key={t.id} value={t.id}>{optLabel(t)}</option>)}
                  </optgroup>
                )}
                {typeGroups.optional.length > 0 && (
                  <optgroup label="선택 서류">
                    {typeGroups.optional.map((t) => <option key={t.id} value={t.id}>{optLabel(t)}</option>)}
                  </optgroup>
                )}
                {typeGroups.etc.length > 0 && (
                  <optgroup label="기타">
                    {typeGroups.etc.map((t) => <option key={t.id} value={t.id}>{optLabel(t)}</option>)}
                  </optgroup>
                )}
              </select>
            </label>
            {typeId !== '' && (
              <div>
                <span className="text-sm font-medium text-slate-700">파일</span>
                <label className="mt-1 block px-4 py-6 rounded-lg border-2 border-dashed border-slate-300 text-center cursor-pointer hover:bg-slate-50">
                  <div className="text-sm text-slate-700">클릭하여 파일 선택</div>
                  <div className="text-xs text-slate-400 mt-1">이미지 또는 PDF</div>
                  <input type="file" accept="image/*,application/pdf" capture="environment"
                    className="hidden"
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) onFilePicked(f); }}
                  />
                </label>
                {ocrType && (
                  <p className="text-xs text-brand-600 mt-2">
                    이 서류는 업로드 시 OCR 로 정보가 자동 추출됩니다.
                  </p>
                )}
              </div>
            )}
          </div>
        )}

        {step === 'ocr' && (
          <div className="flex-1 overflow-auto p-10 flex flex-col items-center justify-center gap-5 min-h-[300px]">
            <div className="relative w-16 h-16">
              <div className="absolute inset-0 rounded-full border-4 border-slate-200" />
              <div className="absolute inset-0 rounded-full border-4 border-brand-600 border-t-transparent animate-spin" />
            </div>
            <div className="text-base font-semibold text-slate-900">OCR 분석 중...</div>
            <div className="text-xs text-slate-500">5~20초 소요</div>
          </div>
        )}

        {step === 'align' && (
          <div className="flex flex-1 min-h-0 flex-col">
            {alignBusy || !previewUrl ? (
              <div className="flex-1 flex flex-col items-center justify-center gap-4 p-10 min-h-[300px]">
                <div className="relative w-14 h-14">
                  <div className="absolute inset-0 rounded-full border-4 border-slate-200" />
                  <div className="absolute inset-0 rounded-full border-4 border-brand-600 border-t-transparent animate-spin" />
                </div>
                <div className="text-sm font-semibold text-slate-900">문서 모서리 자동 검출 중...</div>
              </div>
            ) : (
              <DocumentCornerAligner
                imageUrl={previewUrl}
                initialCorners={initialCorners}
                onConfirm={(corners) => void runRegionOcr(corners)}
                onCancel={() => setStep('review')}
              />
            )}
          </div>
        )}

        {step === 'review' && (
          <div className="flex flex-1 min-h-0">
            <div className="w-1/2 border-r border-slate-200 bg-slate-100 flex flex-col">
              <div className="px-4 py-2 border-b border-slate-200 bg-white text-xs font-semibold text-slate-700 flex items-center justify-between">
                <span>미리보기</span>
                {!isReverify && (
                  <label className="text-xs px-2 py-0.5 rounded border border-slate-300 text-slate-600 hover:bg-slate-50 cursor-pointer">
                    다른 파일 선택
                    <input type="file" accept="image/*,application/pdf" className="hidden"
                      onChange={(e) => { const f = e.target.files?.[0]; if (f) onFilePicked(f); }} />
                  </label>
                )}
              </div>
              {maskedPreview && (
                <div className="px-3 py-1.5 text-[11px] font-semibold bg-emerald-50 text-emerald-700 border-b border-emerald-200">
                  주민등록번호 자동 마스킹됨 — 마스킹된 이미지로 저장됩니다
                </div>
              )}
              <div className="flex-1 overflow-auto flex items-center justify-center p-3">
                {isImage ? (
                  <img src={maskedPreview ?? previewUrl ?? undefined} alt="preview" className="max-w-full max-h-full object-contain rounded shadow" />
                ) : isPdf ? (
                  <iframe src={previewUrl ?? undefined} sandbox="" className="w-full h-full border-0" title="pdf" />
                ) : (
                  <div className="text-sm text-slate-500">미리보기 미지원</div>
                )}
              </div>
            </div>

            <div className="w-1/2 flex flex-col">
              <div className="px-4 py-2 border-b border-slate-200 bg-white text-xs font-semibold text-slate-700">
                {ocrType ? '추출 정보 (검토/수정 가능)' : '필수 정보'}
              </div>
              <div className="flex-1 overflow-auto p-4 space-y-3">
                {ocrError && (
                  <div className="text-xs px-2 py-1 rounded bg-amber-50 text-amber-700 border border-amber-200">
                    ! {ocrError}
                  </div>
                )}
                {selectedType?.has_expiry && !isReverify && (
                  <label className="block">
                    <span className="text-xs font-medium text-slate-600">
                      만료일 {isOcrBackfillTarget ? '(선택)' : '*'}
                    </span>
                    <input type="date" value={expiryDate} onChange={(e) => setExpiryDate(e.target.value)}
                      className="input mt-1" />
                    {isOcrBackfillTarget && (
                      <p className="text-[11px] text-brand-600 mt-1">
                        비워두면 업로드 후 OCR 이 약 1~2분 뒤 검사유효기간을 자동 입력합니다.
                      </p>
                    )}
                    <div className="flex gap-1 mt-1.5 flex-wrap">
                      {[
                        { label: '+6개월', m: 6 },
                        { label: '+1년', m: 12 },
                        { label: '+2년', m: 24 },
                        { label: '+3년', m: 36 },
                        { label: '+5년', m: 60 },
                        { label: '+10년', m: 120 },
                      ].map((opt) => (
                        <button key={opt.label} type="button"
                          onClick={() => {
                            const d = new Date();
                            d.setMonth(d.getMonth() + opt.m);
                            setExpiryDate(d.toISOString().slice(0, 10));
                          }}
                          className="text-[11px] px-2 py-0.5 rounded border border-slate-300 text-slate-600 hover:bg-slate-50">
                          {opt.label}
                        </button>
                      ))}
                    </div>
                  </label>
                )}
                {fieldEntries.length > 0 && fieldEntries.map(([k, v]) => (
                  <label key={k} className="block">
                    <span className="text-xs font-medium text-slate-600">{FIELD_LABEL[k] ?? k}</span>
                    {k === 'license_condition_code' ? (
                      <LicenseTypeChips value={v}
                        onChange={(nv) => setFields((prev) => ({ ...prev, [k]: nv }))} />
                    ) : (
                      <input type="text" value={v}
                        placeholder={FIELD_PLACEHOLDER[k] ?? ''}
                        onChange={(e) => setFields((prev) => ({ ...prev, [k]: e.target.value }))}
                        className="input mt-1" />
                    )}
                  </label>
                ))}
                {!ocrType && fieldEntries.length === 0 && (
                  <p className="text-xs text-slate-400">이 서류는 추가 입력 항목이 없습니다.</p>
                )}
                {error && <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1">{error}</p>}
              </div>
              <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex justify-end gap-2">
                <button type="button" onClick={onClose} disabled={busy}
                  className="text-sm px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100">취소</button>
                <button type="button" onClick={doUpload} disabled={busy || (!isReverify && !file)}
                  className="text-sm px-4 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
                  {busy ? (isReverify ? '재검증 중...' : '업로드 중...') : (isReverify ? '재검증 실행' : '업로드')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
