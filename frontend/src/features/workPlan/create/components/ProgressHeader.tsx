import { ChecklistItem } from './ChecklistItem';
import { REQUIRED_ROLES } from '../types';
import type { WorkPlanCreateState } from '../hooks/useWorkPlanCreate';

interface ProgressHeaderProps {
  state: WorkPlanCreateState;
  overall: number;
  siteOk: boolean;
  equipOk: boolean;
  onJumpSite: () => void;
  onJumpEquipment: () => void;
  onJumpManpower: () => void;
}

/** 최상단 진행률 바 + 필수 항목 체크리스트 (사이트/장비/5역할). */
export function ProgressHeader({
  state,
  overall,
  siteOk,
  equipOk,
  onJumpSite,
  onJumpEquipment,
  onJumpManpower,
}: ProgressHeaderProps) {
  const barColor = overall === 100 ? 'bg-emerald-500' : overall >= 70 ? 'bg-blue-500' : 'bg-yellow-500';
  return (
    <div className="card p-4">
      <div className="flex items-center justify-between mb-3">
        <h1 className="text-xl font-bold">작업계획서 생성</h1>
        <div className="flex items-center gap-3">
          <div className="w-48 h-2 bg-slate-200 rounded-full overflow-hidden">
            <div className={`h-full ${barColor}`} style={{ width: overall + '%' }} />
          </div>
          <span className="text-sm font-medium text-slate-700">{overall}% 준비</span>
        </div>
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 gap-2 text-xs">
        <ChecklistItem
          done={siteOk}
          label={state.selectedSite ? `현장: ${state.selectedSite.name}` : '현장 선택'}
          onClick={onJumpSite}
        />
        <ChecklistItem
          done={equipOk}
          label={state.selectedEquipment ? `장비: ${state.selectedEquipment.vehicle_no || state.selectedEquipment.model || '#' + state.selectedEquipment.id}` : '장비 선택'}
          onClick={onJumpEquipment}
        />
        {REQUIRED_ROLES.map((r) => {
          const count = state.roleAssign[r.key].length;
          return (
            <ChecklistItem
              key={r.key}
              done={count > 0}
              required={r.required}
              label={count > 0 ? `${r.label} ${count}명` : r.label}
              onClick={r.key === 'operator' ? onJumpEquipment : onJumpManpower}
            />
          );
        })}
      </div>
    </div>
  );
}
