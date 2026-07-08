import { useCallback, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { VERIFICATION_STATUS_LABEL, type DocumentTypeResponse } from '../../types/document';
import type { ReviewItemResponse } from '../../types/notification';
import OcrUploadDialog from './OcrUploadDialog';
import { toast } from '../../lib/toast';

const VERIFY_RESULT_LABEL: Record<string, string> = {
  NTS_INVALID: '국세청 검증 실패 — 사업자번호가 휴/폐업 또는 미등록',
  BIZNAME_MISMATCH: '회사명 불일치 — OCR/입력 회사명과 등록된 회사명이 다름',
  REJECTED: '반려됨',
  UNKNOWN: '판정 불가 — OCR 결과 부족',
  TIMEOUT: '검증 API 호출 시간 초과',
  OCR_ERROR: 'OCR 처리 실패',
  UPSTREAM_ERROR: '외부 검증 서버 일시 오류',
  UPSTREAM_DISABLED: '외부 검증 서버 비활성화',
  KOSHA_LOOKUP_FAILED: 'KOSHA 포털 조회 실패',
  SUCCESS: '검증 성공',
};

const OWNER_TYPE_LABEL: Record<string, string> = {
  PERSON: '인원',
  EQUIPMENT: '장비',
  COMPANY: '회사',
};

const OCR_FIELD_LABEL: Record<string, string> = {
  // 공통
  name: '성명',
  birthDate: '생년월일',
  birth_date: '생년월일',
  phoneNumber: '전화번호',
  phone: '전화번호',
  address: '주소',
  // 운전면허
  licenseNumber: '면허번호',
  license_no: '면허번호',
  licenseType: '면허종류',
  license_condition_code: '면허종류',
  licenseTypeCode: '면허종별코드',
  license_type_code: '면허종별코드',
  serialNo: '일련번호',
  serialNumber: '일련번호',
  serial_no: '일련번호',
  expiryDate: '만료일',
  expiry_date: '만료일',
  issueDate: '발급일',
  // 사업자등록증
  businessNumber: '사업자번호',
  biz_no: '사업자번호',
  businessName: '상호',
  business_name: '상호',
  representativeName: '대표자',
  owner_name: '대표자',
  startDate: '개업일',
  start_date: '개업일',
  businessType: '업태/종목',
  business_type: '업태/종목',
  // KOSHA 안전교육 이수증
  registrationNumber: '등록번호',
  registration_no: '등록번호',
  registrationNo: '등록번호',
  bizNo: '사업자번호',
  ownerName: '대표자',
  photoFileNo: '사진 파일번호',
  fileSeq: '파일 순번',
  eduName: '교육명',
  courseName: '교육명',
  trneName: '교육명',
  eduDate: '이수일',
  completionDate: '이수일',
  trneDt: '이수일',
  certNo: '수료번호',
  // 자동차등록증
  vehicleNo: '차량번호',
  vehicle_no: '차량번호',
  vehicleNumber: '차량번호',
  registrationDate: '최초등록일',
  registration_date: '최초등록일',
  manufacturer: '제조사',
  model: '모델',
  year: '연식',
  chassisNumber: '차대번호',
  chassis_number: '차대번호',
  ownerBusinessNo: '차주 사업자번호',
  owner_business_no: '차주 사업자번호',
  usage: '용도',
  fuelType: '연료',
  fuel_type: '연료',
  displacement: '배기량',
  // 화물운송자격증
  cargoLicenseNo: '자격증 번호',
  cargo_license_no: '자격증 번호',
  // 기타
  fullText: '전체 텍스트',
};

/** 영어 fallback 키를 사람이 읽기 좋게 — camelCase → "Camel Case" */
function humanizeKey(k: string): string {
  return k
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/^./, (c) => c.toUpperCase());
}

/** 같은 항목을 가리키는 다양한 키들을 표준 키로 통일. registrationNo / registration_no → registrationNumber */
const KEY_ALIAS: Record<string, string> = {
  registrationNo: 'registrationNumber',
  registration_no: 'registrationNumber',
  birth_date: 'birthDate',
  license_no: 'licenseNumber',
  license_condition_code: 'licenseType',
  licenseTypeCode: 'licenseType',
  license_type_code: 'licenseType',
  serialNo: 'serialNumber',
  serial_no: 'serialNumber',
  biz_no: 'businessNumber',
  business_name: 'businessName',
  owner_name: 'representativeName',
  ownerName: 'representativeName',
  start_date: 'startDate',
  business_type: 'businessType',
  vehicle_no: 'vehicleNo',
  vehicleNumber: 'vehicleNo',
  registration_date: 'registrationDate',
};

/** manual 접두어 제거 + alias 정규화. manualBirthDate → birthDate, registrationNo → registrationNumber */
function normalizeOcrKey(k: string): string {
  let key = k;
  if (key.startsWith('manual') && key.length > 6) {
    const rest = key.slice(6);
    key = rest.charAt(0).toLowerCase() + rest.slice(1);
  }
  return KEY_ALIAS[key] ?? key;
}

/** OCR 자동 추출 + 사용자 수동 입력 키 합치기. 같은 항목이면 빈 값 아닌 쪽 우선. */
function mergeOcrData(raw: Record<string, any>): Record<string, any> {
  const out: Record<string, any> = {};
  for (const [k, v] of Object.entries(raw)) {
    const norm = normalizeOcrKey(k);
    const existing = out[norm];
    if (existing === undefined || existing == null || existing === '') {
      out[norm] = v;
    }
  }
  return out;
}

function humanizeVerifyResult(raw: string | null | undefined): string | null {
  if (!raw) return null;
  try {
    const obj = JSON.parse(raw);
    const reason = typeof obj.reason === 'string' ? obj.reason : null;
    const result = typeof obj.result === 'string' ? obj.result : null;
    const reasonLabel = reason && VERIFY_RESULT_LABEL[reason];
    if (reasonLabel) return reasonLabel;
    if (reason) return reason;
    const resultLabel = result && VERIFY_RESULT_LABEL[result];
    if (resultLabel) return resultLabel;
    return result;
  } catch {
    return raw;
  }
}

function parseJsonSafe(s: string | null | undefined): Record<string, any> | null {
  if (!s) return null;
  try { return JSON.parse(s); } catch { return null; }
}

/**
 * ADMIN 검토 큐. OCR_REVIEW_REQUIRED + REJECTED chain head.
 * 좌측 표 + 우측 디테일 패널 (이미지/OCR/검증결과/액션).
 */
type Tab = 'pending' | 'processed';

export default function ReviewQueuePage() {
  const [items, setItems] = useState<ReviewItemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [tab, setTab] = useState<Tab>('pending');
  const [pendingCount, setPendingCount] = useState<number | null>(null);
  const [processedCount, setProcessedCount] = useState<number | null>(null);
  const [docTypes, setDocTypes] = useState<Map<number, DocumentTypeResponse>>(new Map());
  const [reverify, setReverify] = useState<ReviewItemResponse | null>(null);

  useEffect(() => {
    // 모든 document_type 한 번에 fetch — applies_to=PERSON|EQUIPMENT|COMPANY 다 합쳐서
    Promise.all([
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'PERSON' } }),
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } }),
      api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'COMPANY' } }),
    ]).then(([p, e, c]) => {
      const map = new Map<number, DocumentTypeResponse>();
      [...p.data, ...e.data, ...c.data].forEach((t) => map.set(t.id, t));
      setDocTypes(map);
    }).catch(() => {});
  }, []);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    const url = tab === 'pending' ? '/api/documents/review-queue' : '/api/documents/processed-queue';
    api.get<ReviewItemResponse[]>(url)
      .then((res) => {
        setItems(res.data);
        if (tab === 'pending') setPendingCount(res.data.length);
        else setProcessedCount(res.data.length);
      })
      .catch((err) => {
        if (err instanceof AxiosError) setError(err.response?.data?.message ?? '불러오기 실패');
      })
      .finally(() => setLoading(false));
  }, [tab]);

  useEffect(() => { setSelectedId(null); load(); }, [load]);

  // 다른 탭의 카운트도 한 번 미리 fetch (탭 라벨에 숫자 표시용).
  useEffect(() => {
    const other = tab === 'pending' ? '/api/documents/processed-queue' : '/api/documents/review-queue';
    api.get<ReviewItemResponse[]>(other).then((res) => {
      if (tab === 'pending') setProcessedCount(res.data.length);
      else setPendingCount(res.data.length);
    }).catch(() => {});
  }, [tab]);

  const selected = useMemo(() => items.find((i) => i.id === selectedId) ?? null, [items, selectedId]);

  function openVerifyDialog(item: ReviewItemResponse) {
    const type = docTypes.get(item.document_type_id);
    if (!type) { alert('서류 타입 정보를 불러오지 못했습니다'); return; }
    if (!type.verify_endpoint) {
      alert(`${type.name} 는 자동 검증 대상이 아닙니다.\n수동으로 검증 완료/반려 처리하세요.`);
      return;
    }
    setReverify(item);
  }

  async function approve(id: number) {
    if (!confirm('수동으로 검증 완료 처리할까요?')) return;
    try {
      await api.patch(`/api/documents/${id}/verified?verified=true`);
      setSelectedId(null);
      load();
      toast.success('검증 완료 처리되었습니다');
    } catch (err) {
      if (err instanceof AxiosError) toast.error(err.response?.data?.message ?? '검증 처리 실패');
    }
  }

  async function reject(id: number) {
    const reason = prompt('반려 사유를 입력하세요:');
    if (!reason || !reason.trim()) return;
    try {
      await api.post(`/api/documents/${id}/reject`, { reason: reason.trim() });
      setSelectedId(null);
      load();
      toast.success('반려 처리되었습니다');
    } catch (err) {
      if (err instanceof AxiosError) toast.error(err.response?.data?.message ?? '반려 실패');
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '서류 검토' }]}>
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-bold">서류 검토 큐</h1>
          <p className="text-sm text-slate-500 mt-1">
            {tab === 'pending'
              ? 'OCR 검토 필요 / 반려 상태인 서류. 사진과 OCR 결과를 확인하고 검증/반려를 결정합니다.'
              : '이미 처리 완료된 서류 — 검증 완료 또는 반려 처리한 내역.'}
          </p>
        </div>

        <div className="flex gap-2">
          <button type="button" onClick={() => setTab('pending')}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
              tab === 'pending'
                ? 'bg-amber-500 text-white border-amber-500'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}>
            처리 대기 {pendingCount != null && `(${pendingCount})`}
          </button>
          <button type="button" onClick={() => setTab('processed')}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
              tab === 'processed'
                ? 'bg-emerald-600 text-white border-emerald-600'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}>
            처리 완료 {processedCount != null && `(${processedCount})`}
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
          {/* 좌측 목록 */}
          <div className="lg:col-span-5 rounded-xl border border-slate-200 bg-white overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr className="text-left text-slate-500 text-xs">
                  <th className="px-3 py-2 font-medium">서류</th>
                  <th className="px-3 py-2 font-medium">자원</th>
                  <th className="px-3 py-2 font-medium">상태</th>
                  <th className="px-3 py-2 font-medium">업로드</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr><td colSpan={4} className="px-4 py-12 text-center text-slate-400">불러오는 중...</td></tr>
                ) : error ? (
                  <tr><td colSpan={4} className="px-4 py-12 text-center text-rose-600">{error}</td></tr>
                ) : items.length === 0 ? (
                  <tr><td colSpan={4} className="px-4 py-12 text-center text-slate-400">
                    {tab === 'pending' ? '검토할 서류가 없습니다.' : '아직 처리 완료된 서류가 없습니다.'}
                  </td></tr>
                ) : items.map((it) => {
                  const cls = it.verification_status === 'REJECTED' ? 'bg-rose-100 text-rose-700'
                    : it.verification_status === 'VERIFIED' ? 'bg-emerald-100 text-emerald-700'
                    : 'bg-amber-100 text-amber-700';
                  const active = it.id === selectedId;
                  return (
                    <tr key={it.id} onClick={() => setSelectedId(it.id)}
                        className={`cursor-pointer ${active ? 'bg-brand-50' : 'hover:bg-slate-50'}`}>
                      <td className="px-3 py-3">
                        <div className="font-semibold text-slate-900 truncate max-w-[160px]">{it.document_type_name}</div>
                        <div className="text-xs text-slate-500 truncate max-w-[160px]">{it.owner_supplier_name ?? '-'}</div>
                      </td>
                      <td className="px-3 py-3">
                        <div className="text-slate-900 truncate max-w-[120px]">{it.owner_name}</div>
                        <div className="text-xs text-slate-500">{OWNER_TYPE_LABEL[it.owner_type] ?? it.owner_type}</div>
                      </td>
                      <td className="px-3 py-3">
                        <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls}`}>
                          {VERIFICATION_STATUS_LABEL[it.verification_status] ?? it.verification_status}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-xs text-slate-700 whitespace-nowrap">
                        {new Date(it.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(5, 16)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* 우측 디테일 패널 */}
          <div className="lg:col-span-7">
            {selected ? (
              <DetailPanel item={selected}
                readOnly={tab === 'processed'}
                onAutoReverify={() => openVerifyDialog(selected)}
                onApprove={() => approve(selected.id)}
                onReject={() => reject(selected.id)} />
            ) : (
              <div className="rounded-xl border border-dashed border-slate-300 bg-white p-12 text-center text-slate-400">
                좌측에서 서류를 선택하면 사진과 OCR 추출 데이터를 볼 수 있습니다.
              </div>
            )}
          </div>
        </div>
      </div>

      {reverify && (
        <OcrUploadDialog
          open
          ownerType={reverify.owner_type}
          ownerId={reverify.owner_id}
          types={Array.from(docTypes.values())}
          presetTypeId={reverify.document_type_id}
          title={`${reverify.document_type_name} 재검증`}
          reverifyDocId={reverify.id}
          reverifyExtractedData={reverify.extracted_data}
          onClose={() => setReverify(null)}
          onUploaded={(updated) => {
            setReverify(null);
            const status = updated.verification_status;
            if (status === 'VERIFIED') toast.success('검증 완료');
            else if (status === 'REJECTED') toast.error('반려 처리됨');
            else if (status === 'OCR_REVIEW_REQUIRED') toast.info('OCR 검토 필요 — 보충 입력이 필요합니다');
            else toast.info(`결과: ${status}`);
            load();
          }}
        />
      )}
    </AppShell>
  );
}

function DetailPanel({ item, readOnly, onAutoReverify, onApprove, onReject }: {
  item: ReviewItemResponse;
  readOnly?: boolean;
  onAutoReverify: () => void;
  onApprove: () => void;
  onReject: () => void;
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [isPdf, setIsPdf] = useState(false);

  useEffect(() => {
    let revoked = false;
    let createdUrl: string | null = null;
    setBlobUrl(null);
    setPreviewError(null);
    api.get(`/api/documents/${item.id}/file`, { responseType: 'blob' })
      .then((res) => {
        const blob = res.data as Blob;
        setIsPdf(blob.type === 'application/pdf' || item.file_name.toLowerCase().endsWith('.pdf'));
        createdUrl = URL.createObjectURL(blob);
        if (!revoked) setBlobUrl(createdUrl);
      })
      .catch((e) => { setPreviewError(e?.response?.data?.message ?? '미리보기 로드 실패'); });
    return () => { revoked = true; if (createdUrl) URL.revokeObjectURL(createdUrl); };
  }, [item.id, item.file_name]);

  const extractedRaw = parseJsonSafe(item.extracted_data);
  const verificationRaw = parseJsonSafe(item.verification_result);
  // ocr 자동 결과 + 사용자 수동 입력(manual* 접두어) 합치기. 같은 항목 중복 제거.
  const ocrData: Record<string, any> = mergeOcrData((extractedRaw?.ocrData ?? extractedRaw) ?? {});
  const ownerLink = item.owner_type === 'EQUIPMENT' ? `/equipment/${item.owner_id}`
    : item.owner_type === 'PERSON' ? `/persons/${item.owner_id}`
    : `/admin/companies/${item.owner_id}`;

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
        <div className="min-w-0">
          <div className="font-bold text-slate-900 truncate">{item.document_type_name}</div>
          <div className="text-xs text-slate-500 truncate">
            <Link to={ownerLink} className="text-brand-700 hover:text-brand-800">{item.owner_name}</Link>
            {' · '}
            {item.owner_supplier_name ?? '-'}
            {' · '}
            {item.file_name}
          </div>
        </div>
        <div className="flex flex-wrap gap-1 shrink-0 items-center">
          {readOnly ? (
            <span className={`text-xs px-2 py-1 rounded-full font-semibold ${
              item.verification_status === 'VERIFIED' ? 'bg-emerald-100 text-emerald-700'
                : 'bg-rose-100 text-rose-700'
            }`}>
              {item.verification_status === 'VERIFIED' ? '✓ 검증 완료' : '✕ 반려'}
            </span>
          ) : (
            <>
              <button type="button" onClick={onAutoReverify}
                      className="text-xs px-2 py-1 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50">
                재검증
              </button>
              <button type="button" onClick={onApprove}
                      className="text-xs px-2 py-1 rounded-lg bg-emerald-600 text-white hover:bg-emerald-700">
                검증 완료
              </button>
              <button type="button" onClick={onReject}
                      className="text-xs px-2 py-1 rounded-lg bg-rose-600 text-white hover:bg-rose-700">
                반려
              </button>
            </>
          )}
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 divide-x divide-slate-100">
        {/* 이미지 미리보기 */}
        <div className="bg-slate-100 p-3 flex items-center justify-center min-h-[400px] max-h-[600px] overflow-auto">
          {previewError ? (
            <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded px-3 py-2">{previewError}</p>
          ) : !blobUrl ? (
            <p className="text-sm text-slate-400">로딩 중…</p>
          ) : isPdf ? (
            <iframe src={blobUrl} sandbox="" title={item.file_name} className="w-full h-[560px] border-0 bg-white rounded" />
          ) : (
            <img src={blobUrl} alt={item.file_name} className="max-w-full max-h-[560px] object-contain rounded shadow" />
          )}
        </div>

        {/* OCR / 검증 결과 */}
        <div className="p-4 space-y-4 overflow-auto max-h-[600px]">
          {item.expiry_date && (
            <div>
              <div className="text-xs font-semibold text-slate-500 mb-1">만료일</div>
              <div className="text-sm text-slate-900">{item.expiry_date}</div>
            </div>
          )}

          {item.rejected_reason && item.verification_status !== 'VERIFIED' && (
            <div>
              <div className="text-xs font-semibold text-slate-500 mb-1">검증 사유</div>
              <div className="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded px-3 py-2">
                {VERIFY_RESULT_LABEL[item.rejected_reason] ?? item.rejected_reason}
              </div>
            </div>
          )}

          {Object.keys(ocrData).length > 0 && (
            <div>
              <div className="text-xs font-semibold text-slate-500 mb-1">OCR 추출 데이터</div>
              <div className="rounded-lg border border-slate-200 divide-y divide-slate-100 text-sm">
                {Object.entries(ocrData)
                  .filter(([k]) => k !== 'fullText')
                  .map(([k, v]) => (
                    <div key={k} className="flex px-3 py-1.5">
                      <div className="w-32 shrink-0 text-slate-500 break-keep">{OCR_FIELD_LABEL[k] ?? humanizeKey(k)}</div>
                      <div className="flex-1 text-slate-900 break-all min-w-0">{v == null || v === '' ? '-' : String(v)}</div>
                    </div>
                  ))}
              </div>
              {typeof ocrData.fullText === 'string' && (
                <details className="mt-2">
                  <summary className="text-xs text-slate-500 cursor-pointer hover:text-slate-700">전체 OCR 텍스트 보기</summary>
                  <pre className="text-[11px] text-slate-600 bg-slate-50 rounded p-2 mt-1 whitespace-pre-wrap max-h-[200px] overflow-auto">{ocrData.fullText}</pre>
                </details>
              )}
            </div>
          )}

          {verificationRaw && (
            <div>
              <div className="text-xs font-semibold text-slate-500 mb-1">외부 API 검증 결과</div>
              {verificationRaw.message && (
                <div className="text-sm mb-1">{humanizeVerifyResult(item.verification_result) ?? verificationRaw.message}</div>
              )}
              <details>
                <summary className="text-xs text-slate-500 cursor-pointer hover:text-slate-700">원본 응답</summary>
                <pre className="text-[11px] text-slate-600 bg-slate-50 rounded p-2 mt-1 whitespace-pre-wrap max-h-[200px] overflow-auto">{JSON.stringify(verificationRaw, null, 2)}</pre>
              </details>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
