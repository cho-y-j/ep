import { useEffect, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { useSubSuppliers } from '../company/useSubSuppliers';
import DocumentSection from '../document/DocumentSection';
import EquipmentFields, { EMPTY_EQUIPMENT_FIELDS, type EquipmentFieldValues } from './EquipmentFields';
import type { CompanyResponse } from '../../types/auth';
import type { EquipmentResponse } from '../../types/equipment';

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

export default function EquipmentCreateForm({ equipmentSuppliers, requireSupplierId, showDocumentStep, onCreated, onCancel }: Props) {
  const { company } = useAuth();
  // V77 대행 등록: 회사 관리자면 직속 자식(EQUIPMENT 협력사) 소유로도 등록 가능. 없으면 기존과 동일.
  const subSuppliers = useSubSuppliers().filter((c) => c.type === 'EQUIPMENT');
  const [values, setValues] = useState<EquipmentFieldValues>(EMPTY_EQUIPMENT_FIELDS);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 기사(조종원) — 등록된 인력(Person) 중 선택. 외부(조달) 장비일 때 노출.
  const [operatorPersonId, setOperatorPersonId] = useState<number | ''>('');
  const [operatorCandidates, setOperatorCandidates] = useState<Array<{ id: number; name: string }>>([]);
  // 등록 성공 후 서류 업로드 단계용 (showDocumentStep 일 때).
  const [created, setCreated] = useState<EquipmentResponse | null>(null);
  // 문서-우선: 자동차등록증 파일을 먼저 올려 OCR 프리필 → 등록 후 그대로 서류 첨부. (선택 경로)
  const [regFile, setRegFile] = useState<File | null>(null);
  const [ocrBusy, setOcrBusy] = useState(false);
  const [ocrNote, setOcrNote] = useState('');

  // 자동차등록증 픽 → ocr-preview 로 차량번호·차명·연식 프리필. 실패/미설정이면 빈 값 유지(수기 입력).
  async function onRegFilePicked(f: File) {
    setRegFile(f);
    setOcrBusy(true);
    setOcrNote('');
    try {
      const fd = new FormData();
      fd.append('file', f);
      fd.append('ocrType', 'EQUIPMENT_REGISTRATION');
      const res = await api.post<{ ok: boolean; fields?: Record<string, string> }>(
        '/api/documents/ocr-preview', fd,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      );
      const ocr = res.data.fields ?? {};
      if (res.data.ok && (ocr.vehicleNumber || ocr.modelName || ocr.productionYear)) {
        setValues((prev) => ({
          ...prev,
          vehicleNo: ocr.vehicleNumber || prev.vehicleNo,
          model: ocr.modelName || prev.model,
          year: ocr.productionYear || prev.year,
        }));
        setOcrNote('자동 추출 완료 — 아래 값을 확인/수정 후 등록하세요.');
      } else {
        setOcrNote('자동 추출을 못 했습니다. 아래 항목을 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
      }
    } catch {
      setOcrNote('OCR 호출 실패 — 직접 입력하세요. (등록증은 등록 후 그대로 첨부됩니다)');
    } finally {
      setOcrBusy(false);
    }
  }

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
      };
      if (values.supplierId) {
        body.supplier_id = values.supplierId;
      }
      const res = await api.post<EquipmentResponse>('/api/equipment', body);
      // 문서-우선: 보관한 자동차등록증을 새 장비에 그대로 첨부 (best-effort — 실패해도 등록은 완료).
      if (regFile) {
        try {
          const typesRes = await api.get<Array<{ id: number; name: string }>>('/api/document-types', { params: { appliesTo: 'EQUIPMENT' } });
          const vType = typesRes.data.find((t) => t.name === '자동차등록증');
          if (vType) {
            const fd = new FormData();
            fd.append('file', regFile);
            const docRes = await api.post<{ id: number }>('/api/documents', fd, {
              params: { ownerType: 'EQUIPMENT', ownerId: String(res.data.id), documentTypeId: String(vType.id) },
            });
            try { await api.post(`/api/documents/${docRes.data.id}/verify`, {}); } catch { /* ignore */ }
          }
        } catch { /* 첨부 실패해도 장비 등록은 유지 */ }
      }
      setValues(EMPTY_EQUIPMENT_FIELDS);
      setOperatorPersonId('');
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
    <form onSubmit={onSubmit} className="card mb-6 space-y-4">
      <h2 className="text-base font-bold">새 장비 등록</h2>
      <div className="rounded-lg border border-slate-200 bg-brand-50 p-3">
        <p className="text-sm font-semibold text-brand-700">자동차등록증으로 시작 <span className="font-normal text-slate-400">(선택)</span></p>
        <p className="mt-0.5 text-xs text-slate-500">등록증 이미지를 올리면 차량번호·차명·연식이 자동으로 채워집니다. 등록 완료 시 서류로도 함께 첨부됩니다.</p>
        <label className="mt-2 flex items-center justify-center gap-2 rounded-lg border-2 border-dashed border-brand-500 px-3 py-2 text-sm font-medium text-brand-700 cursor-pointer hover:bg-white">
          <input type="file" accept="image/*,application/pdf" className="hidden"
            onChange={(e) => { const f = e.target.files?.[0]; if (f) void onRegFilePicked(f); }} />
          {regFile ? `선택됨: ${regFile.name}` : '자동차등록증 파일 선택'}
        </label>
        {ocrBusy && <p className="mt-1 text-xs text-slate-500">OCR 분석 중...</p>}
        {ocrNote && !ocrBusy && <p className="mt-1 text-xs text-slate-600">{ocrNote}</p>}
      </div>
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
      />
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
  );
}
