import Avatar from '../../../../components/Avatar';
import type { PersonResponse } from '../../../../types/person';
import { PERSON_STATUS_LABEL, EMPLOYMENT_TYPE_LABEL } from '../../../../types/person';
import type { RequiredRoleDef } from '../types';

interface PersonRoleCardGroupProps {
  role: RequiredRoleDef;
  candidates: PersonResponse[];
  assignedIds: number[];
  onToggle: (personId: number) => void;
  /** 후보가 비었을 때 표시할 안내 메시지. */
  emptyMsg: string;
}

/** 역할 1개 — 후보 인원을 사진 카드 그리드로 표시. 클릭 시 토글. 배정된 카드는 emerald 보더/체크. */
export function PersonRoleCardGroup({
  role,
  candidates,
  assignedIds,
  onToggle,
  emptyMsg,
}: PersonRoleCardGroupProps) {
  const assignedSet = new Set(assignedIds);
  const assignedCount = candidates.filter((p) => assignedSet.has(p.id)).length;

  return (
    <div className="border border-slate-200 rounded-xl bg-white">
      <div className="flex items-center justify-between px-3 py-2 border-b border-slate-100">
        <div className="text-sm font-semibold text-slate-800 flex items-center gap-1.5">
          <span>{role.label}</span>
          {role.required && <span className="text-rose-500">*</span>}
          {assignedCount > 0 ? (
            <span className="ml-1 text-[11px] bg-emerald-100 text-emerald-800 px-1.5 py-0.5 rounded-full">
              {assignedCount}명 선택됨
            </span>
          ) : (
            <span className="ml-1 text-[11px] text-slate-400">후보 {candidates.length}명</span>
          )}
        </div>
      </div>
      <div className="p-2">
        {candidates.length === 0 ? (
          <div className="text-xs text-slate-400 italic py-4 text-center">{emptyMsg}</div>
        ) : (
          <div className="divide-y divide-slate-100 overflow-hidden rounded-lg border border-slate-100 bg-white">
            {candidates.map((p) => {
              const selected = assignedSet.has(p.id);
              const statusCls = p.status === 'WORKING'
                ? 'bg-emerald-100 text-emerald-800'
                : p.status === 'VACATION'
                ? 'bg-amber-100 text-amber-800'
                : 'bg-rose-100 text-rose-700';
              return (
                <button
                  key={p.id}
                  type="button"
                  onClick={() => onToggle(p.id)}
                  className={`grid w-full grid-cols-[28px_1fr_auto] items-center gap-2 px-2.5 py-2 text-left transition ${
                    selected
                      ? 'bg-blue-50/80'
                      : 'bg-white hover:bg-slate-50'
                  }`}
                  title={selected ? '클릭하면 배정 해제' : '클릭하면 배정'}
                >
                  <Avatar
                    fetchUrl={p.has_photo ? `/api/persons/${p.id}/photo` : undefined}
                    alt={p.name}
                    fallbackText={p.name}
                    size={28}
                    rounded="full"
                  />
                  <div className="min-w-0">
                    <div className="flex min-w-0 items-center gap-1.5">
                      <span className="truncate text-xs font-semibold text-slate-900">{p.name}</span>
                      <span className="shrink-0 text-[10px] text-slate-400">{EMPLOYMENT_TYPE_LABEL[p.employment_type]}</span>
                    </div>
                    <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-slate-500">
                      <span className="truncate">{p.job_title ?? '-'}</span>
                      <span className="text-slate-300">|</span>
                      <span className="truncate">{p.phone ?? p.employee_no ?? '-'}</span>
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className={`hidden rounded-full px-1.5 py-0.5 text-[9px] font-medium sm:inline-block ${statusCls}`}>
                      {PERSON_STATUS_LABEL[p.status]}
                    </span>
                    <span className={`inline-flex h-4 w-4 items-center justify-center rounded border text-[10px] font-bold ${
                      selected ? 'border-blue-500 bg-blue-600 text-white' : 'border-slate-300 bg-white text-transparent'
                    }`}>
                      ✓
                    </span>
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
