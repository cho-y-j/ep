import { REQUIRED_ROLES, type DocPreviewTarget, type RoleKey } from '../types';
import { PersonRoleCardGroup } from '../components/PersonRoleCardGroup';
import { RoleDocsGroup } from '../components/DocPanel';
import type { WorkPlanCreateState } from '../hooks/useWorkPlanCreate';

interface Step3Props {
  state: WorkPlanCreateState;
  onPreview: (t: DocPreviewTarget) => void;
  showDocuments?: boolean;
}

/** Step 3: 인력공급사 + 작업지휘자/유도원/화기감시자/신호수 배정 + 첨부 서류. */
export function Step3Manpower({ state, onPreview, showDocuments = true }: Step3Props) {
  // BP 가 견적/사인으로 연동된 인력공급사만 노출. bypassConnection ON 시 전체.
  const manpowerSuppliers = state.bypassConnection ? state.allManpowerSuppliers : state.connectedManpowerSuppliers;

  const nonOperatorRoles = REQUIRED_ROLES.filter((r) => r.key !== 'operator');

  return (
    <div id="step-manpower" className="card p-4 bg-white border-slate-200 space-y-3">
      <div className="text-sm font-bold text-slate-900">
        인원 선택
        {state.loadingManpowerSupplier && <span className="ml-2 text-xs text-slate-500">(로드 중...)</span>}
      </div>

      <div>
        <label className="text-[10px] font-medium text-slate-600 mb-0.5 block">
          인력공급사 {state.bypassConnection ? '(전체)' : '(견적으로 연동된 공급사)'}
        </label>
        <select
          value={state.manpowerSupplierId ?? ''}
          onChange={(e) => state.setManpowerSupplierId(e.target.value ? Number(e.target.value) : null)}
          className="w-full border border-slate-300 rounded-lg px-2.5 py-1.5 text-sm bg-white"
        >
          <option value="">-- 인력공급사 선택 --</option>
          {manpowerSuppliers.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
        {manpowerSuppliers.length === 0 && (
          <div className="text-[10px] text-slate-400 mt-0.5">
            아직 견적으로 연동된 인력공급사가 없습니다.
          </div>
        )}
      </div>

      {state.manpowerSupplierId && (
        <div className="space-y-3">
          {nonOperatorRoles.map((role) => {
            const candidates = state.manpowerPersons.filter((p) => p.roles.includes(role.personRole));
            return (
              <PersonRoleCardGroup
                key={role.key}
                role={role}
                candidates={candidates}
                assignedIds={state.roleAssign[role.key as RoleKey]}
                onToggle={(pid) => state.toggleAssign(role.key, pid)}
                emptyMsg={`이 인력공급사에 ${role.label} 등록 인력 없음`}
              />
            );
          })}
        </div>
      )}

      {showDocuments && (() => {
        const peopleByRole = nonOperatorRoles.map((role) => ({
          role,
          people: state.manpowerPersons.filter((p) => state.roleAssign[role.key].includes(p.id)),
        }));
        const anyAssigned = peopleByRole.some((g) => g.people.length > 0);
        if (!anyAssigned) return null;
        return (
          <div className="space-y-3 pt-1">
            {peopleByRole
              .filter((g) => g.people.length > 0)
              .map((g) => (
                <RoleDocsGroup
                  key={g.role.key}
                  role={g.role}
                  people={g.people}
                  personDocs={state.personDocs}
                  selectedIds={state.selectedPersonDocIds}
                  onSelect={state.setSelectedPersonDocIds}
                  onPreview={onPreview}
                  onSwap={state.swapAttachment}
                  order={state.attachmentOrder}
                />
              ))}
          </div>
        );
      })()}
    </div>
  );
}
