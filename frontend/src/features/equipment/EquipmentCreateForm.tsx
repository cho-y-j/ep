import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { useSubSuppliers } from '../company/useSubSuppliers';
import DocumentSection from '../document/DocumentSection';
import DocumentCornerAligner from '../document/DocumentCornerAligner';
import { detectDocumentCorners } from '../document/detectCorners';
import { rotateImage90 } from '../document/imageRotate';
import EquipmentFields, { EMPTY_EQUIPMENT_FIELDS, type EquipmentFieldValues } from './EquipmentFields';
import type { CompanyResponse } from '../../types/auth';
import type { EquipmentResponse } from '../../types/equipment';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL } from '../../types/equipment';
import { useEquipmentTypes } from './useEquipmentTypes';
import { useHandledEquipmentTypes, handledCodeFilter } from './useHandledEquipmentTypes';
import type { DocumentTypeResponse } from '../../types/document';
import { ownerMatches } from '../document/docTypeGrouping';

type Props = {
  /** ADMIN 등 supplier_id 직접 선택 가능한 컨텍스트일 때 회사 목록 전달 */
  equipmentSuppliers?: CompanyResponse[];
  /** ADMIN 컨텍스트일 때 supplier_id 미지정이면 클라이언트에서 막음 */
  requireSupplierId?: boolean;
  /** true 면 등록 성공 후 같은 화면에서 서류 업로드 단계를 노출한 뒤 onCreated 를 호출. */
  showDocumentStep?: boolean;
  onCreated: (e: EquipmentResponse) => void;
  onCancel: () => void;
};

/** 이 장비종류에 필요한 '등록증'(자동차/건설기계 등록증) 서류를 설정에서 고른다.
 *  영역템플릿 보유(=자동채움 가능) + 카테고리 적용 + 필수 우선. 없으면 null. */
function pickRegistrationType(types: DocumentTypeResponse[], category: string): DocumentTypeResponse | null {
  const cands = types.filter(
    (t) => t.active && !!t.ocr_region_template && ownerMatches(t, 'EQUIPMENT', undefined, category),
  );
  return cands.find((t) => t.required) ?? cands[0] ?? null;
}

/** data URL(base64) → Blob. warped 미리보기는 data URL 이라 그대로 새 탭에 열면 크기제한이 걸려서,
 *  Blob + createObjectURL 로 열어야 브라우저 네이티브 확대/맞춤이 정상 동작한다. (동기 변환 → 팝업차단 회피) */
function dataUrlToBlob(dataUrl: string): Blob {
  const [head, b64] = dataUrl.split(',');
  const mime = head.match(/:(.*?);/)?.[1] ?? 'image/png';
  const bin = atob(b64);
  const arr = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  return new Blob([arr], { type: mime });
}

/** OCR 만료일 문자열(2026-04-19 / 2026.4.19 등)을 <input type=date> 용 YYYY-MM-DD 로. 매칭 실패면 ''. */
function toIsoDate(s?: string): string {
  if (!s) return '';
  const m = s.match(/(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})/);
  return m ? `${m[1]}-${m[2].padStart(2, '0')}-${m[3].padStart(2, '0')}` : '';
}

export default function EquipmentCreateForm({ equipmentSuppliers, requireSupplierId, showDocumentStep, onCreated, onCancel }: Props) {
  const { company } = useAuth();
  // V77 대행 등록: 회사 관리자면 직속 자식(EQUIPMENT 협력사) 소유로도 등록 가능. 없으면 기존과 동일.
  const subSuppliers = useSubSuppliers().filter((c) => c.type === 'EQUIPMENT');
  const { options: typeOptions, labelOf: categoryLabelOf } = useEquipmentTypes();
  const categoryOptions = typeOptions.length
    ? typeOptions
    : EQUIPMENT_CATEGORIES.map((c) => ({ code: c, name: EQUIPMENT_CATEGORY_LABEL[c], grp: '' }));
  // 취급 장비종류 설정이 있으면 그 종류만 기본 표시 + '전체 보기' 토글. 없으면 전체(기존 동작).
  const handledTypes = useHandledEquipmentTypes();
  const [showAllTypes, setShowAllTypes] = useState(false);
  const typePass = handledCodeFilter(handledTypes, showAllTypes);
  const shownCategoryOptions = typePass ? categoryOptions.filter((c) => typePass(c.code)) : categoryOptions;
  const [values, setValues] = useState<EquipmentFieldValues>(EMPTY_EQUIPMENT_FIELDS);
  const [inspectionExpiry, setInspectionExpiry] = useState(''); // 검사만료일(정기검사 유효기간) — 폼 입력/OCR 자동채움
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 기사(조종원) — 등록된 인력(Person) 중 선택. 외부(조달) 장비일 때 노출.
  const [operatorPersonId, setOperatorPersonId] = useState<number | ''>('');
  const [operatorCandidates, setOperatorCandidates] = useState<Array<{ id: number; name: string }>>([]);
  // 등록 성공 후 서류 업로드 단계용 (showDocumentStep 일 때).
  const [created, setCreated] = useState<EquipmentResponse | null>(null);
  // 문서-우선: 자동차등록증 파일을 먼저 올려 OCR 프리필 → 등록 후 그대로 서류 첨부. (선택 경로)
  const [regFile, setRegFile] = useState<File | null>(null);
  const [regPreviewUrl, setRegPreviewUrl] = useState<string | null>(null);
  // 정렬 완료 후 warp(원근보정+크롭)된 결과 이미지 — 있으면 원본 대신 "맞춘 이미지"로 미리보기.
  const [warpedPreviewUrl, setWarpedPreviewUrl] = useState<string | null>(null);
  const [ocrBusy, setOcrBusy] = useState(false);
  const [ocrNote, setOcrNote] = useState('');
  // Phase 2 정렬(4모서리 맞추기) — 이미지 등록증일 때만. alignTypeId = 영역맵 보유 자동차등록증 doc-type id.
  const [aligning, setAligning] = useState(false);
  const [alignTypeId, setAlignTypeId] = useState<number | null>(null);
  const [alignInitialCorners, setAlignInitialCorners] = useState<[number, number][] | undefined>(undefined);
  const [rotating, setRotating] = useState(false); // 정렬 단계 90° 회전 진행중
  const [detecting, setDetecting] = useState(false); // 백그라운드 자동 영역감지 진행중
  const [alignKey, setAlignKey] = useState(0);       // 정렬기 강제 리마운트(감지/회전 시 시작박스 반영)
  // 장비종류별 필요 등록증(설정 기반) — 자동차/건설기계 등록증 하드코딩 대신.
  const [equipTypes, setEquipTypes] = useState<DocumentTypeResponse[]>([]);
  const regDocType = useMemo(() => pickRegistrationType(equipTypes, values.category), [equipTypes, values.category]);

  // 취급 종류로 좁혀졌는데 현재 선택 종류가 목록 밖이면 첫 표시 종류로 이동(빈 select 방지).
  useEffect(() => {
    if (!typePass) return;
    setValues((prev) => (typePass(prev.category) || shownCategoryOptions.length === 0)
      ? prev
      : { ...prev, category: shownCategoryOptions[0].code as EquipmentFieldValues['category'] });
  }, [handledTypes, showAllTypes]); // eslint-disable-line react-hooks/exhaustive-deps

  /** 미리보기 클릭 → 새 창(축소/확대·%·화면맞춤·닫기 컨트롤 포함)으로 열기. 폼을 덮지 않고 나란히 보며 입력. */
  function openPreviewInNewTab() {
    let src: string | null = null;
    if (warpedPreviewUrl) {
      src = URL.createObjectURL(dataUrlToBlob(warpedPreviewUrl));
    } else if (regFile && regFile.type.startsWith('image/')) {
      src = URL.createObjectURL(regFile);
    }
    if (!src) return;
    const w = window.open('', '_blank', 'width=1040,height=880,scrollbars=yes,resizable=yes');
    if (!w) { window.open(src, '_blank'); return; } // 팝업 차단 시 기본 뷰 폴백
    w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>맞춘 이미지</title>
<style>html,body{margin:0;height:100%;background:#222;font-family:system-ui,sans-serif}
.bar{position:sticky;top:0;display:flex;gap:8px;align-items:center;padding:8px 10px;background:#111;color:#fff;z-index:1}
.bar button{background:#333;color:#fff;border:0;padding:6px 12px;border-radius:6px;cursor:pointer;font-size:15px}
.bar button:hover{background:#484848}.pct{min-width:58px;text-align:center;font-size:13px}
.wrap{height:calc(100vh - 46px);overflow:auto;text-align:center;padding:10px;box-sizing:border-box}
img{display:inline-block}</style></head><body>
<div class="bar"><button onclick="z(-0.25)">− 축소</button><span class="pct" id="p">맞춤</span>
<button onclick="z(0.25)">＋ 확대</button><button onclick="fit()">화면맞춤</button>
<button style="margin-left:auto;background:#a33" onclick="window.close()">닫기 ✕</button></div>
<div class="wrap"><img id="im" src="${src}"></div>
<script>var im=document.getElementById('im'),p=document.getElementById('p'),s=0;
function fit(){s=0;im.style.width='';im.style.maxWidth='100%';im.style.maxHeight='calc(100vh - 66px)';p.textContent='맞춤';}
function z(d){s=Math.min(5,Math.max(0.4,(s||1)+d));im.style.maxWidth='none';im.style.maxHeight='none';im.style.width=(s*100)+'%';p.textContent=Math.round(s*100)+'%';}
fit();</script></body></html>`);
    w.document.close();
    setTimeout(() => URL.revokeObjectURL(src!), 120000);
  }

  // 자동차등록증 픽 → 차량번호·차명·연식 프리필. 템플릿 보유 시 로컬 영역-크롭 OCR(ocr-region-preview),
  // 아니면 기존 Vision(ocr-preview). 실패/미설정이면 빈 값 유지(수기 입력).
  async function onRegFilePicked(f: File) {
    setRegFile(f);
    setWarpedPreviewUrl(null); // 새 파일 → 이전 정렬 결과 초기화
    setOcrNote('');
    // 이 장비종류에 걸린 등록증(설정 기반). 템플릿 보유 → 로컬 영역 OCR (자동차/건설기계 공통 vehicle_no·model·year).
    const regionTypeId: number | null = regDocType ? regDocType.id : null;

    // 이미지 등록증 + 영역맵 보유 → 4모서리 정렬 모달을 '즉시' 연다. 자동 코너검출(detect-corners)은
    // 회전된 큰 사진에서 십수 초 걸릴 수 있어 기다리면 모달이 안 떠 회전 버튼조차 못 본다 → 백그라운드로.
    if (regionTypeId != null && f.type.startsWith('image/')) {
      setAlignTypeId(regionTypeId);
      setAlignInitialCorners(undefined); // 우선 이미지 꼭짓점 박스 → 사용자가 회전/드래그로 보정
      setDetecting(true);
      setAligning(true);
      setAlignKey((k) => k + 1);
      detectDocumentCorners(f)
        .then((c) => { if (c && c.length === 4) { setAlignInitialCorners(c); setAlignKey((k) => k + 1); } })
        .finally(() => setDetecting(false));
      return;
    }

    setOcrBusy(true);
    try {
      const fd = new FormData();
      fd.append('file', f);
      let ok = false;
      if (regionTypeId != null) {
        // 로컬 영역-크롭 OCR (warp 없이 평면 가정). 응답 fields 는 snake_case.
        fd.append('documentTypeId', String(regionTypeId));
        const res = await api.post<{ ok: boolean; fields?: Record<string, string> }>(
          '/api/documents/ocr-region-preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
        const ocr = res.data.fields ?? {};
        ok = res.data.ok && !!(ocr.vehicle_no || ocr.model || ocr.year);
        if (ok) {
          setValues((prev) => ({
            ...prev,
            vehicleNo: ocr.vehicle_no || prev.vehicleNo,
            model: ocr.model || prev.model,
            year: ocr.year || prev.year,
          }));
        }
      } else {
        // 템플릿 없음 → 기존 Vision 경로.
        fd.append('ocrType', 'EQUIPMENT_REGISTRATION');
        const res = await api.post<{ ok: boolean; fields?: Record<string, string> }>(
          '/api/documents/ocr-preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
        const ocr = res.data.fields ?? {};
        ok = res.data.ok && !!(ocr.vehicleNumber || ocr.modelName || ocr.productionYear);
        if (ok) {
          setValues((prev) => ({
            ...prev,
            vehicleNo: ocr.vehicleNumber || prev.vehicleNo,
            model: ocr.modelName || prev.model,
            year: ocr.productionYear || prev.year,
          }));
        }
      }
      setOcrNote(ok
        ? '자동 추출 완료 — 아래 값을 확인/수정 후 등록하세요.'
        : '자동 추출을 못 했습니다. 아래 항목을 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
    } catch {
      setOcrNote('OCR 호출 실패 — 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
    } finally {
      setOcrBusy(false);
    }
  }

  // 정렬 완료 → 맞춘 4모서리(원본 px)로 warp+영역-크롭 OCR → 차량번호·차명·연식 프리필.
  async function onAlignConfirm(corners: [number, number][]) {
    setAligning(false);
    if (!regFile || alignTypeId == null) return;
    setOcrBusy(true);
    setOcrNote('');
    try {
      const fd = new FormData();
      fd.append('file', regFile);
      fd.append('documentTypeId', String(alignTypeId));
      fd.append('corners', JSON.stringify(corners));
      const res = await api.post<{ ok: boolean; fields?: Record<string, string>; warped_image_base64?: string }>(
        '/api/documents/ocr-region-preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      // 보정된 맞춘 이미지 미리보기 + 밴드+패턴 추출(코너 편차에 강건). 차량번호·차명·연식 3개만 자동채움.
      if (res.data.warped_image_base64) {
        setWarpedPreviewUrl(`data:image/png;base64,${res.data.warped_image_base64}`);
      }
      const ocr = res.data.fields ?? {};
      const iso = toIsoDate(ocr.expiry_date); // 검사유효기간 → 검사만료일 자동채움
      if (iso) setInspectionExpiry(iso);
      const got = !!(ocr.vehicle_no || ocr.model || ocr.year || iso);
      if (ocr.vehicle_no || ocr.model || ocr.year) {
        setValues((prev) => ({
          ...prev,
          vehicleNo: ocr.vehicle_no || prev.vehicleNo,
          model: ocr.model || prev.model,
          year: ocr.year || prev.year,
        }));
      }
      setOcrNote(got
        ? `자동 추출됨${iso ? ` (검사만료일 ${iso} 포함)` : ''} — 맞춘 이미지(클릭하면 확대)를 보고 값을 확인·수정하세요. OCR 특성상 오탈자가 있을 수 있습니다.`
        : '자동 추출을 못 했습니다. 맞춘 이미지를 보고 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
    } catch {
      setOcrNote('OCR 호출 실패 — 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
    } finally {
      setOcrBusy(false);
    }
  }

  function onAlignCancel() {
    setAligning(false);
    setOcrNote('정렬을 취소했습니다. 아래 항목을 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
  }

  /** 정렬 단계에서 이미지 90° 회전 → 파일 교체(똑바로 세워 저장) → 코너 재검출(백그라운드). 세로로 찍힌 사진 대응. */
  async function rotateAlignImage() {
    if (!regFile || rotating) return;
    setRotating(true);
    try {
      const rotated = await rotateImage90(regFile); // 캔버스 회전(빠름)만 await
      setAlignInitialCorners(undefined); // 회전 → 시작 박스 초기화(이미지 꼭짓점)
      setRegFile(rotated);               // regPreviewUrl 갱신 → 정렬 이미지도 회전본(똑바로) + 저장도 이 파일
      setWarpedPreviewUrl(null);
      setAlignKey((k) => k + 1);
      setDetecting(true);
      detectDocumentCorners(rotated)     // 자동감지는 백그라운드(느려도 회전은 즉시 반영)
        .then((c) => { if (c && c.length === 4) { setAlignInitialCorners(c); setAlignKey((k) => k + 1); } })
        .finally(() => setDetecting(false));
    } catch {
      /* 회전 실패 무시 — 사용자가 다시 시도 */
    } finally {
      setRotating(false);
    }
  }

  // 장비 서류종류 로드 — 종류별 필요 등록증(자동차/건설기계) 자동선택용.
  useEffect(() => {
    api.get<DocumentTypeResponse[]>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } })
      .then((r) => setEquipTypes(r.data))
      .catch(() => setEquipTypes([]));
  }, []);

  // 조종원 후보 — 등록된 OPERATOR 인력 (백엔드가 actor 권한별로 스코프). EquipmentDefaultOperators 패턴 재사용.
  useEffect(() => {
    api.get('/api/persons?role=OPERATOR&size=200')
      .then((r) => {
        const data = r.data as { content?: Array<{ id: number; name: string }> } | Array<{ id: number; name: string }>;
        const list = Array.isArray(data) ? data : (data.content ?? []);
        setOperatorCandidates(list.map((p) => ({ id: p.id, name: p.name })));
      })
      .catch(() => setOperatorCandidates([]));
  }, []);

  // 자동차등록증 미리보기 — 이미지/PDF blob URL 생성, 파일 교체/언마운트 시 revoke.
  useEffect(() => {
    if (!regFile) { setRegPreviewUrl(null); return; }
    const url = URL.createObjectURL(regFile);
    setRegPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [regFile]);

  // 소유자 파생: 협력사(subSupplier)를 소유자로 고르면 조달(is_external)로 파생. 우리 회사면 supplierId='' → 본인 소유.
  const ownerCompany = values.supplierId !== ''
    ? subSuppliers.find((c) => c.id === values.supplierId)
    : undefined;
  const isExternal = !!ownerCompany;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (requireSupplierId && !values.supplierId) {
      setError('장비공급사를 선택하세요');
      return;
    }

    setBusy(true);
    try {
      // 조달 시: 선택한 협력사(subSupplier) 의 사업자 정보를 차주(vehicle_owner_*)로 파생 전송 (컬럼/표시/서류심사 그대로).
      const body: Record<string, unknown> = {
        category: values.category,
        vehicle_no: values.vehicleNo || null,
        model: values.model || null,
        manufacturer: values.manufacturer || null,
        year: values.year ? Number(values.year) : null,
        is_external: isExternal,
        vehicle_owner_name: isExternal ? (ownerCompany?.name ?? null) : null,
        vehicle_owner_business_no: isExternal ? (ownerCompany?.business_number ?? null) : null,
        operator_person_id: isExternal && operatorPersonId !== '' ? operatorPersonId : null,
        inspection_due_date: inspectionExpiry || null,
      };
      if (values.supplierId) {
        body.supplier_id = values.supplierId;
      }
      const res = await api.post<EquipmentResponse>('/api/equipment', body);
      // 문서-우선: 보관한 자동차등록증을 새 장비에 그대로 첨부 (best-effort — 실패해도 등록은 완료).
      if (regFile && regDocType) {
        try {
          const fd = new FormData();
          fd.append('file', regFile);
          const docRes = await api.post<{ id: number }>('/api/documents', fd, {
            params: { ownerType: 'EQUIPMENT', ownerId: String(res.data.id), documentTypeId: String(regDocType.id) },
          });
          try { await api.post(`/api/documents/${docRes.data.id}/verify`, {}); } catch { /* ignore */ }
        } catch { /* 첨부 실패해도 장비 등록은 유지 */ }
      }
      setValues(EMPTY_EQUIPMENT_FIELDS);
      setOperatorPersonId('');
      setInspectionExpiry('');
      setRegFile(null);
      setOcrNote('');
      if (showDocumentStep) {
        setCreated(res.data);
      } else {
        onCreated(res.data);
      }
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '등록 실패');
      } else {
        setError('등록 실패');
      }
    } finally {
      setBusy(false);
    }
  }

  if (created && showDocumentStep) {
    return (
      <div className="card mb-6 space-y-4">
        <div>
          <h2 className="text-base font-bold">장비 등록 완료 — 서류 업로드</h2>
          <p className="mt-1 text-sm text-slate-500">
            등록은 완료되었습니다. 아래에서 필수 서류를 지금 업로드하거나, 나중에 상세 화면에서 추가할 수 있습니다.
          </p>
        </div>
        <DocumentSection
          ownerType="EQUIPMENT"
          ownerId={created.id}
          canEdit
          ownerCategory={created.category}
          title="필수 서류"
          excludeTypeName={created.is_external ? undefined : '사업자등록증(외부장비)'}
        />
        <div className="flex justify-end">
          <button type="button" onClick={() => onCreated(created)} className="btn-primary">
            완료
          </button>
        </div>
      </div>
    );
  }

  return (
    <>
    <form onSubmit={onSubmit} className="card mb-6 space-y-4">
      <h2 className="text-base font-bold">새 장비 등록</h2>
      {/* 장비 종류 먼저 — 종류에 따라 필요한 등록증(자동차/건설기계)이 달라진다. */}
      <label className="block">
        <span className="flex items-center gap-2 text-sm font-medium text-slate-700">
          장비 종류 <span className="text-xs text-rose-600 font-semibold">(필수)</span>
          {handledTypes && handledTypes.length > 0 && (
            <button type="button" onClick={() => setShowAllTypes((v) => !v)}
              className="ml-auto text-xs font-medium text-brand-600 hover:underline">
              {showAllTypes ? '취급 종류만 보기' : '전체 보기'}
            </button>
          )}
        </span>
        <select
          value={values.category}
          onChange={(e) => setValues({ ...values, category: e.target.value as EquipmentFieldValues['category'] })}
          className="input mt-1 bg-white"
        >
          {shownCategoryOptions.map((c) => (
            <option key={c.code} value={c.code}>{c.name}</option>
          ))}
        </select>
      </label>
      {regDocType ? (
      <div className="rounded-lg border border-slate-200 bg-brand-50 p-3">
        <p className="text-sm font-semibold text-brand-700">{regDocType.name}으로 시작 <span className="font-normal text-slate-400">(선택)</span></p>
        <p className="mt-0.5 text-xs text-slate-500">{categoryLabelOf(values.category)} — {regDocType.name} 이미지를 올리면 차량번호·차명·연식이 자동으로 채워집니다. 등록 완료 시 서류로도 함께 첨부됩니다.</p>
        <label className="mt-2 flex items-center justify-center gap-2 rounded-lg border-2 border-dashed border-brand-500 px-3 py-2 text-sm font-medium text-brand-700 cursor-pointer hover:bg-white">
          <input type="file" accept="image/*,application/pdf" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void onRegFilePicked(f); }} />
          {regFile ? `선택됨: ${regFile.name}` : `${regDocType.name} 파일 선택`}
        </label>
        {regFile && (warpedPreviewUrl || regPreviewUrl) && (
          <div className="mt-2 rounded-lg border border-slate-200 bg-white p-2">
            {warpedPreviewUrl ? (
              <>
                <p className="mb-1 text-center text-xs font-medium text-brand-700">맞춘 이미지 (자동 크롭·원근보정)</p>
                <button type="button" onClick={openPreviewInNewTab} title="클릭하면 새 창에서 크게 보기"
                  className="flex w-full cursor-zoom-in items-center justify-center">
                  <img src={warpedPreviewUrl} alt="맞춘 이미지 미리보기" className="max-h-56 max-w-full rounded object-contain" />
                </button>
                <p className="mt-1 text-center text-[11px] text-slate-400">🔍 클릭하면 새 창에서 크게 — 폼을 보며 값 확인·입력</p>
              </>
            ) : (
              <div className="flex items-center justify-center">
                {regFile.type.startsWith('image/') ? (
                  <button type="button" onClick={openPreviewInNewTab} title="클릭하면 새 창에서 크게 보기"
                    className="cursor-zoom-in">
                    <img src={regPreviewUrl ?? ''} alt="자동차등록증 미리보기" className="max-h-56 max-w-full rounded object-contain" />
                  </button>
                ) : regFile.type === 'application/pdf' ? (
                  <iframe src={regPreviewUrl ?? ''} sandbox="" className="h-56 w-full border-0" title="자동차등록증 미리보기" />
                ) : (
                  <div className="text-sm text-slate-500">미리보기 미지원</div>
                )}
              </div>
            )}
          </div>
        )}
        {ocrBusy && <p className="mt-1 text-xs text-slate-500">OCR 분석 중...</p>}
        {ocrNote && !ocrBusy && <p className="mt-1 text-xs text-slate-600">{ocrNote}</p>}
      </div>
      ) : (
        <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-500">
          이 장비 종류는 자동추출 가능한 등록증 템플릿이 없습니다. 아래 항목을 입력해 등록하세요. (등록증은 등록 후 서류에서 첨부)
        </p>
      )}
      {!requireSupplierId && (
        <label className="block">
          <span className="text-sm font-medium text-slate-700">소유자</span>
          <select
            value={values.supplierId}
            onChange={(e) => setValues({ ...values, supplierId: e.target.value === '' ? '' : Number(e.target.value) })}
            className="input mt-1 bg-white"
          >
            <option value="">우리 회사{company ? ` — ${company.name}` : ''}</option>
            {subSuppliers.map((c) => (
              <option key={c.id} value={c.id}>{c.name} (협력사)</option>
            ))}
          </select>
          <span className="mt-1 block text-xs text-slate-400">
            {subSuppliers.length > 0
              ? '협력사(하위공급사)를 선택하면 그 회사 소유(조달)로 등록되고 사업자 정보가 차주로 기록됩니다.'
              : '등록된 협력사(하위공급사)가 없어 우리 회사 장비로만 등록됩니다.'}
          </span>
        </label>
      )}
      <EquipmentFields
        values={values}
        onChange={setValues}
        equipmentSuppliers={equipmentSuppliers}
        required
        hideCategory
      />
      <label className="block">
        <span className="text-sm font-medium text-slate-700">검사만료일 <span className="text-xs font-normal text-slate-400">(정기검사 유효기간 — 만료관리)</span></span>
        <input
          type="date"
          value={inspectionExpiry}
          onChange={(e) => setInspectionExpiry(e.target.value)}
          className="input mt-1 bg-white"
        />
        <span className="mt-1 block text-xs text-slate-400">
          자동차등록증을 올리면 "검사유효기간"에서 자동으로 채워집니다. 값을 확인·수정하세요. 만료가 임박하면 알림 대상이 됩니다.
        </span>
      </label>
      {isExternal && (
        <div className="space-y-2 rounded-lg border border-blue-200 bg-blue-50/40 p-3">
          <p className="text-xs text-blue-700">외부 장비 <strong>기사(조종원)</strong> — 등록된 인력 중에서 선택하세요. (선택)</p>
          <label className="block">
            <span className="text-sm font-medium text-slate-700">기사(조종원)</span>
            <select
              value={operatorPersonId}
              onChange={(e) => setOperatorPersonId(e.target.value === '' ? '' : Number(e.target.value))}
              className="input mt-1 bg-white"
            >
              <option value="">— 선택 안 함 —</option>
              {operatorCandidates.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            {operatorCandidates.length === 0 && (
              <span className="mt-1 block text-xs text-slate-400">선택 가능한 조종원(인력)이 없습니다. 인력 관리에서 먼저 등록하세요.</span>
            )}
          </label>
        </div>
      )}
      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
      )}
      <div className="flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">
          취소
        </button>
        <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
          {busy ? '등록 중...' : '등록'}
        </button>
      </div>
    </form>
    {aligning && regPreviewUrl && (
      <div className="fixed inset-0 z-50 bg-black/60 flex items-center justify-center p-4">
        <div className="bg-white rounded-xl max-w-4xl w-full max-h-[92vh] overflow-hidden shadow-2xl flex flex-col">
          <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-5 py-3">
            <span className="text-sm font-bold text-slate-900">{regDocType?.name ?? '등록증'} 영역 맞추기</span>
            <div className="flex items-center gap-2">
              {detecting
                ? <span className="hidden text-xs text-amber-600 sm:inline">자동 영역 감지 중… 잠시 후 시작점이 맞춰집니다</span>
                : <span className="hidden text-xs text-slate-400 sm:inline">사진이 누워있으면 회전으로 똑바로 세우고 4모서리를 맞추세요</span>}
              <button type="button" onClick={() => void rotateAlignImage()} disabled={rotating}
                className="flex items-center gap-1 rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                {rotating ? '회전 중…' : '↻ 90° 회전'}
              </button>
            </div>
          </div>
          <DocumentCornerAligner
            key={alignKey}
            imageUrl={regPreviewUrl}
            initialCorners={alignInitialCorners}
            onConfirm={(c) => void onAlignConfirm(c)}
            onCancel={onAlignCancel}
          />
        </div>
      </div>
    )}
    </>
  );
}
