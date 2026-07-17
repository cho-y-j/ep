import Avatar from '../../../../components/Avatar';
import { equipmentCategoryLabel } from '../../../../types/equipment';
import { REQUIRED_ROLES, type DocPreviewTarget } from '../types';
import { PersonRoleCardGroup } from '../components/PersonRoleCardGroup';
import { EquipDocsGroup, RoleDocsGroup } from '../components/DocPanel';
import type { WorkPlanCreateState } from '../hooks/useWorkPlanCreate';

interface Step2Props {
  state: WorkPlanCreateState;
  onPreview: (t: DocPreviewTarget) => void;
  showDocuments?: boolean;
}

/** Step 2: 장비공급사 + 장비 + 조종원 배정 + 첨부 서류 (장비/조종원). */
export function Step2Equipment({ state, onPreview, showDocuments = true }: Step2Props) {
  // BP 가 견적/사인으로 연동된 장비공급사만 노출. bypassConnection ON 시 전체.
  const equipmentSuppliers = state.bypassConnection ? state.allEquipmentSuppliers : state.connectedEquipmentSuppliers;

  const operatorRole = REQUIRED_ROLES.find((r) => r.key === 'operator')!;
  const assignedOperators = state.equipPersons.filter((p) =>
    state.roleAssign.operator.includes(p.id)
  );

  return (
    <div id="step-equipment" className="card p-4 bg-white border-slate-200 space-y-3">
      <div className="text-sm font-bold text-slate-900">
        장비 및 조종원 선택
        {state.loadingEquipmentSupplier && <span className="ml-2 text-xs text-slate-500">(로드 중...)</span>}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div>
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">
            장비공급사 {state.bypassConnection ? '(전체)' : '(견적으로 연동된 공급사)'}
          </label>
          <select
            value={state.equipmentSupplierId ?? ''}
            onChange={(e) => state.setEquipmentSupplierId(e.target.value ? Number(e.target.value) : null)}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
          >
            <option value="">-- 장비공급사 선택 --</option>
            {equipmentSuppliers.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          {equipmentSuppliers.length === 0 && (
            <div className="text-[10px] text-slate-400 mt-0.5">
              아직 견적으로 연동된 장비공급사가 없습니다. 견적 선정 후 표시됩니다.
            </div>
          )}
        </div>
        <div>
          <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">
            장비 (장비공급사 선택 후 표시)
          </label>
          <select
            value={state.equipmentId ?? ''}
            onChange={(e) => state.setEquipmentId(e.target.value ? Number(e.target.value) : null)}
            disabled={!state.equipmentSupplierId}
            className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white disabled:bg-slate-100"
          >
            <option value="">-- 장비 선택 --</option>
            {state.equipmentList
              .filter((e) => state.bypassConnection
                || state.connectedEquipmentIds.length === 0
                || state.connectedEquipmentIds.includes(e.id))
              .map((e) => (
                <option key={e.id} value={e.id}>
                  {e.vehicle_no || e.model || '#' + e.id} · {equipmentCategoryLabel(e.category)}
                </option>
              ))}
          </select>
          {state.equipmentSupplierId && !state.bypassConnection && state.connectedEquipmentIds.length > 0 && (
            <div className="text-[10px] text-slate-500 mt-0.5">
              견적으로 연동된 장비만 표시 ({state.connectedEquipmentIds.length}대)
            </div>
          )}
        </div>
      </div>

      {state.selectedEquipment && (
        <div className="rounded-lg border border-slate-200 bg-slate-50/70 p-2">
          <div className="flex items-center gap-3">
            <Avatar
              fetchUrl={state.selectedEquipment.has_photo ? `/api/equipment/${state.selectedEquipment.id}/photo` : undefined}
              fallbackText={state.selectedEquipment.vehicle_no || state.selectedEquipment.model || '장비'}
              alt={state.selectedEquipment.vehicle_no ?? ''}
              size={42}
              rounded="lg"
            />
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-semibold text-slate-900">
                {state.selectedEquipment.vehicle_no || state.selectedEquipment.model || `장비 #${state.selectedEquipment.id}`}
              </div>
              <div className="mt-0.5 flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
                <span>{equipmentCategoryLabel(state.selectedEquipment.category)}</span>
                <span>{state.selectedEquipment.code ?? '-'}</span>
                {state.selectedEquipment.current_site_name && <span>@ {state.selectedEquipment.current_site_name}</span>}
              </div>
            </div>
            <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
              선택됨
            </span>
          </div>
        </div>
      )}

      {state.equipmentSupplierId && (
        <PersonRoleCardGroup
          role={operatorRole}
          candidates={state.equipPersons}
          assignedIds={state.roleAssign.operator}
          onToggle={(pid) => state.toggleAssign('operator', pid)}
          emptyMsg="이 장비공급사에 조종원이 등록돼있지 않습니다"
        />
      )}

      {showDocuments && state.selectedEquipment && (
        <EquipDocsGroup
          equipment={state.selectedEquipment}
          docs={state.equipDocs}
          selectedIds={state.selectedEquipDocIds}
          onSelect={state.setSelectedEquipDocIds}
          onPreview={onPreview}
          onSwap={state.swapAttachment}
          order={state.attachmentOrder}
        />
      )}
      {showDocuments && assignedOperators.length > 0 && (
        <RoleDocsGroup
          role={operatorRole}
          people={assignedOperators}
          personDocs={state.personDocs}
          selectedIds={state.selectedPersonDocIds}
          onSelect={state.setSelectedPersonDocIds}
          onPreview={onPreview}
          onSwap={state.swapAttachment}
          order={state.attachmentOrder}
        />
      )}
    </div>
  );
}
