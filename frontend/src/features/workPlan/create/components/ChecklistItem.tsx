interface ChecklistItemProps {
  done: boolean;
  required?: boolean;
  label: string;
  onClick?: () => void;
}

/** 상단 진행률 영역에 들어가는 단일 체크 칩. */
export function ChecklistItem({ done, required, label, onClick }: ChecklistItemProps) {
  const color = done
    ? 'border-emerald-300 bg-emerald-50 text-emerald-800'
    : required
      ? 'border-rose-300 bg-rose-50 text-rose-800'
      : 'border-slate-200 bg-slate-50 text-slate-500';
  return (
    <button
      onClick={onClick}
      className={`border rounded-lg px-2 py-1.5 flex items-center gap-1.5 text-left hover:shadow transition ${color}`}
      type="button"
    >
      <span>{done ? '✓' : required ? '!' : '○'}</span>
      <span className="truncate">{label}</span>
    </button>
  );
}
