import {
  EQUIPMENT_ASSIGNMENT_STATUS_LABEL,
  PERSON_ASSIGNMENT_STATUS_LABEL,
  type EquipmentAssignmentStatus,
  type PersonAssignmentStatus,
} from '../../types/assignment';

type Props = {
  status: EquipmentAssignmentStatus | PersonAssignmentStatus;
};

const CLS: Record<string, string> = {
  AVAILABLE: 'bg-slate-100 text-slate-600',
  ASSIGNED: 'bg-blue-100 text-blue-700',
  ON_DUTY: 'bg-blue-100 text-blue-700',
  OFF_DUTY: 'bg-slate-100 text-slate-600',
  BROKEN: 'bg-rose-100 text-rose-700',
  INACTIVE: 'bg-slate-100 text-slate-500',
};

const DOT: Record<string, string> = {
  AVAILABLE: 'bg-slate-400',
  ASSIGNED: 'bg-blue-500',
  ON_DUTY: 'bg-blue-500',
  OFF_DUTY: 'bg-slate-400',
  BROKEN: 'bg-rose-500',
  INACTIVE: 'bg-slate-300',
};

export default function AssignmentBadge({ status }: Props) {
  const label =
    (EQUIPMENT_ASSIGNMENT_STATUS_LABEL as Record<string, string>)[status] ??
    (PERSON_ASSIGNMENT_STATUS_LABEL as Record<string, string>)[status] ??
    status;
  const cls = CLS[status] ?? 'bg-slate-100 text-slate-600';
  const dotCls = DOT[status] ?? 'bg-slate-400';
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold ${cls}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${dotCls}`} />
      {label}
    </span>
  );
}
