import type { ReactNode } from 'react';

type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'purple';

type Props = {
  tone?: Tone;
  children: ReactNode;
  size?: 'xs' | 'sm';
};

const TONE: Record<Tone, string> = {
  neutral: 'bg-slate-100 text-slate-700',
  brand:   'bg-blue-100 text-blue-800',
  success: 'bg-emerald-100 text-emerald-800',
  warning: 'bg-amber-100 text-amber-800',
  danger:  'bg-rose-100 text-rose-800',
  purple:  'bg-purple-100 text-purple-800',
};

/** 상태 칩. 색상 매핑 중앙화. */
export default function StatusBadge({ tone = 'neutral', children, size = 'xs' }: Props) {
  const sz = size === 'xs' ? 'text-[10px] px-1.5 py-0.5' : 'text-xs px-2 py-0.5';
  return (
    <span className={`inline-flex items-center gap-1 rounded-full font-medium ${TONE[tone]} ${sz}`}>
      {children}
    </span>
  );
}
