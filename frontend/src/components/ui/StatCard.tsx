import type { ReactNode } from 'react';

type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger';

type Props = {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  tone?: Tone;
  icon?: ReactNode;
  onClick?: () => void;
  selected?: boolean;
};

const TONE: Record<Tone, { label: string; value: string; bg: string; border: string }> = {
  neutral: { label: 'text-slate-500', value: 'text-slate-900', bg: 'bg-white',     border: 'border-slate-200' },
  brand:   { label: 'text-brand-700',  value: 'text-slate-900', bg: 'bg-white',     border: 'border-slate-200' },
  success: { label: 'text-emerald-700',value: 'text-emerald-900', bg: 'bg-emerald-50', border: 'border-emerald-200' },
  warning: { label: 'text-amber-700',  value: 'text-amber-900', bg: 'bg-amber-50',  border: 'border-amber-200' },
  danger:  { label: 'text-rose-700',   value: 'text-rose-900',  bg: 'bg-rose-50',   border: 'border-rose-200' },
};

/** 대시보드 + 목록 상단의 stat 카드. mock 톤 — 컴팩트, 색상 의미 통일. */
export default function StatCard({ label, value, hint, tone = 'neutral', icon, onClick, selected }: Props) {
  const t = TONE[tone];
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag
      onClick={onClick}
      className={`text-left ${t.bg} border ${t.border} rounded-lg p-3 transition-colors ${
        onClick ? 'hover:bg-slate-50 cursor-pointer' : ''
      } ${selected ? 'ring-2 ring-brand-500' : ''}`}
    >
      <div className="flex items-center justify-between gap-2 mb-1">
        <span className={`text-[11px] font-medium ${t.label}`}>{label}</span>
        {icon && <span className="shrink-0 text-slate-400">{icon}</span>}
      </div>
      <div className={`text-xl font-bold ${t.value}`}>{value}</div>
      {hint && <div className="text-[11px] text-slate-500 mt-0.5">{hint}</div>}
    </Tag>
  );
}
