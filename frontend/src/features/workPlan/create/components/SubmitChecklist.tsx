import type { ReactNode } from 'react';

type Item = {
  label: string;
  done: boolean;
  hint?: string;
};

type Props = {
  items: Item[];
  overall: number;
  missing: string[];
  onSubmit: () => void;
  submitDisabled: boolean;
  submitLabel: string;
  submitTitle?: string;
  extra?: ReactNode;
};

/**
 * 우측 사이드바 상단 — 제출 준비 체크리스트.
 * 단순 첨부 패널이 아니라 "제출하려면 뭐가 남았는지" 한눈에.
 */
export default function SubmitChecklist({ items, overall, missing, onSubmit, submitDisabled, submitLabel, submitTitle, extra }: Props) {
  const barColor = overall === 100 ? 'bg-emerald-500' : overall >= 70 ? 'bg-blue-500' : 'bg-amber-500';
  return (
    <section className="card">
      <div className="flex items-center justify-between mb-2">
        <h2 className="text-sm font-bold text-slate-900">제출 준비</h2>
        <span className="text-xs font-bold text-slate-700">{overall}%</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100 mb-3">
        <div className={`h-full ${barColor}`} style={{ width: `${overall}%` }} />
      </div>
      <ul className="space-y-1.5 mb-3">
        {items.map((it, i) => (
          <li key={i} className="flex items-start gap-2 text-xs">
            <span
              className={`shrink-0 inline-flex h-4 w-4 items-center justify-center rounded-full text-[10px] font-bold ${
                it.done ? 'bg-emerald-500 text-white' : 'bg-slate-200 text-slate-500'
              }`}
            >
              {it.done ? '✓' : ''}
            </span>
            <span className={`flex-1 ${it.done ? 'text-slate-500 line-through' : 'text-slate-700 font-medium'}`}>
              {it.label}
              {it.hint && <span className="ml-1 text-slate-400 font-normal">{it.hint}</span>}
            </span>
          </li>
        ))}
      </ul>
      {missing.length > 0 && (
        <div className="rounded-md border border-rose-200 bg-rose-50 px-2 py-1.5 text-[11px] text-rose-700 mb-3">
          필수 누락: {missing.join(', ')}
        </div>
      )}
      <button
        type="button"
        onClick={onSubmit}
        disabled={submitDisabled}
        title={submitTitle}
        className="w-full btn-primary justify-center text-sm py-2 disabled:opacity-50"
      >
        {submitLabel}
      </button>
      {extra && <div className="mt-2">{extra}</div>}
    </section>
  );
}
