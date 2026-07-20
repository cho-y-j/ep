import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';
import type { SiteResponse } from '../../types/site';
import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import type { PersonResponse } from '../../types/person';
import {
  CHANGE_KIND_LABEL,
  type CreateResourceChangePayload,
  type ResourceChangeKind,
  type ResourceChangeRequestResponse,
} from '../../types/resourceChange';

/**
 * 업체변경 신청서 v0 작성 (L2a). 공급사·BP. 저장 시 서버가 라벨/차량번호/연락처 스냅샷 +
 * 신규자원 deploy-check(L3) 스냅샷을 자동 생성 → 인쇄용 신청서 뷰로 이동.
 */
export default function ResourceChangeCreatePage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { user } = useAuth();
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const supplierScopeId = isSupplier ? user?.company_id ?? undefined : undefined;

  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [operators, setOperators] = useState<PersonResponse[]>([]);

  const [changeKind, setChangeKind] = useState<ResourceChangeKind>('EQUIPMENT');
  const [siteId, setSiteId] = useState<number | ''>(params.get('siteId') ? Number(params.get('siteId')) : '');
  const [oldEquipmentId, setOldEquipmentId] = useState<number | ''>('');
  const [newEquipmentId, setNewEquipmentId] = useState<number | ''>('');
  const [oldPersonId, setOldPersonId] = useState<number | ''>('');
  const [newPersonId, setNewPersonId] = useState<number | ''>('');
  const [applyDate, setApplyDate] = useState<string>(new Date().toISOString().slice(0, 10));
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const workPlanId = params.get('workPlanId') ? Number(params.get('workPlanId')) : undefined;

  useEffect(() => {
    api.get<SiteResponse[]>('/api/sites').then((r) => setSites(r.data)).catch(() => setSites([]));
    const eqParams = supplierScopeId ? { supplierId: supplierScopeId } : {};
    api.get<EquipmentResponse[]>('/api/equipment', { params: eqParams })
      .then((r) => setEquipment(r.data)).catch(() => setEquipment([]));
    const pParams = { role: 'OPERATOR', size: 200, ...(supplierScopeId ? { supplierId: supplierScopeId } : {}) };
    api.get<{ content: PersonResponse[] }>('/api/persons', { params: pParams })
      .then((r) => setOperators(r.data.content ?? [])).catch(() => setOperators([]));
  }, [supplierScopeId]);

  const selectedSite = useMemo(() => sites.find((s) => s.id === siteId) ?? null, [sites, siteId]);
  const isOperator = changeKind === 'OPERATOR';

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      // BP/ADMIN 은 신규 자원 공급사에서 대상 공급사 도출. 공급사는 서버가 본인 회사 사용.
      let supplierCompanyId: number | undefined;
      if (!isSupplier) {
        const newEq = equipment.find((e) => e.id === newEquipmentId);
        const newP = operators.find((p) => p.id === newPersonId);
        supplierCompanyId = isOperator ? newP?.supplier_id : newEq?.supplier_id;
      }
      const payload: CreateResourceChangePayload = {
        change_kind: changeKind,
        site_id: siteId ? Number(siteId) : undefined,
        bp_company_id: selectedSite?.bp_company_id ?? undefined,
        supplier_company_id: supplierCompanyId,
        old_equipment_id: !isOperator && oldEquipmentId ? Number(oldEquipmentId) : undefined,
        new_equipment_id: !isOperator && newEquipmentId ? Number(newEquipmentId) : undefined,
        old_person_id: isOperator && oldPersonId ? Number(oldPersonId) : undefined,
        new_person_id: isOperator && newPersonId ? Number(newPersonId) : undefined,
        apply_date: applyDate || undefined,
        reason: reason.trim() || undefined,
        work_plan_id: workPlanId,
      };
      const res = await api.post<ResourceChangeRequestResponse>('/api/resource-change-requests', payload);
      navigate(`/resource-change-requests/${res.data.id}`);
    } catch (e) {
      setError(e instanceof AxiosError ? (e.response?.data?.message ?? '작성 실패') : '작성 실패');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AppShell breadcrumb={[{ label: '업체변경 신청서', to: '/resource-change-requests' }, { label: '신규 작성' }]}>
      <div className="mx-auto max-w-2xl space-y-4">
        <div className="card space-y-4">
          <h1 className="text-lg font-bold text-slate-950">업체변경 신청서 작성</h1>
          <p className="text-xs text-slate-500">
            같은 현장의 기검증 자원으로 교체할 때 사용합니다. 저장하면 신규 자원의 투입 사전판정(L3)이 자동으로 함께 기록됩니다.
          </p>

          {/* 변경구분 */}
          <div>
            <div className="mb-1.5 text-xs font-bold text-slate-700">변경구분</div>
            <div className="flex gap-2">
              {(['EQUIPMENT', 'OPERATOR', 'COMPANY'] as ResourceChangeKind[]).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => setChangeKind(k)}
                  className={`rounded-lg border px-3 py-1.5 text-sm font-semibold ${
                    changeKind === k ? 'border-blue-500 bg-blue-50 text-blue-700' : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                  }`}
                >{CHANGE_KIND_LABEL[k]}</button>
              ))}
            </div>
          </div>

          {/* 현장 + 적용일 */}
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="mb-1 block text-xs font-bold text-slate-700">현장</span>
              <select value={siteId} onChange={(e) => setSiteId(e.target.value ? Number(e.target.value) : '')} className="input w-full">
                <option value="">현장 선택</option>
                {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
              {selectedSite?.bp_company_name && (
                <span className="mt-1 block text-[11px] text-slate-400">발주사: {selectedSite.bp_company_name}</span>
              )}
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-bold text-slate-700">적용일</span>
              <input type="date" value={applyDate} onChange={(e) => setApplyDate(e.target.value)} className="input w-full" />
            </label>
          </div>

          {/* 변경 전/후 자원 */}
          {isOperator ? (
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1 block text-xs font-bold text-slate-700">변경 전 조종원</span>
                <select value={oldPersonId} onChange={(e) => setOldPersonId(e.target.value ? Number(e.target.value) : '')} className="input w-full">
                  <option value="">선택 (선택사항)</option>
                  {operators.map((p) => <option key={p.id} value={p.id}>{p.name}{p.phone ? ` · ${p.phone}` : ''}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="mb-1 block text-xs font-bold text-slate-700">변경 후 조종원</span>
                <select value={newPersonId} onChange={(e) => setNewPersonId(e.target.value ? Number(e.target.value) : '')} className="input w-full">
                  <option value="">선택</option>
                  {operators.map((p) => <option key={p.id} value={p.id}>{p.name}{p.phone ? ` · ${p.phone}` : ''}</option>)}
                </select>
              </label>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="mb-1 block text-xs font-bold text-slate-700">변경 전 장비{changeKind === 'COMPANY' ? ' (업체)' : ''}</span>
                <select value={oldEquipmentId} onChange={(e) => setOldEquipmentId(e.target.value ? Number(e.target.value) : '')} className="input w-full">
                  <option value="">선택 (선택사항)</option>
                  {equipment.map((eq) => <option key={eq.id} value={eq.id}>{eq.vehicle_no ?? eq.model ?? `장비#${eq.id}`} · {equipmentCategoryLabel(eq.category)}{eq.supplier_name ? ` · ${eq.supplier_name}` : ''}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="mb-1 block text-xs font-bold text-slate-700">변경 후 장비{changeKind === 'COMPANY' ? ' (업체)' : ''}</span>
                <select value={newEquipmentId} onChange={(e) => setNewEquipmentId(e.target.value ? Number(e.target.value) : '')} className="input w-full">
                  <option value="">선택</option>
                  {equipment.map((eq) => <option key={eq.id} value={eq.id}>{eq.vehicle_no ?? eq.model ?? `장비#${eq.id}`} · {equipmentCategoryLabel(eq.category)}{eq.supplier_name ? ` · ${eq.supplier_name}` : ''}</option>)}
                </select>
              </label>
            </div>
          )}

          {/* 사유 */}
          <label className="block">
            <span className="mb-1 block text-xs font-bold text-slate-700">변경 사유</span>
            <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} placeholder="예: 기존 장비 정기점검 입고로 대체 투입" className="input w-full" />
          </label>

          {error && <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</div>}

          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => navigate('/resource-change-requests')} className="btn-secondary">취소</button>
            <button type="button" onClick={() => void submit()} disabled={busy} className="btn-primary disabled:opacity-50">
              {busy ? '작성 중…' : '신청서 작성 + L3 판정'}
            </button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
