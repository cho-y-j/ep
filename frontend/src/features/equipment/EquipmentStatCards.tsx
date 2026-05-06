import type { EquipmentResponse } from '../../types/equipment';

type Props = {
  equipment: EquipmentResponse[];
};

export default function EquipmentStatCards({ equipment }: Props) {
  const total = equipment.length;
  const running = equipment.filter((e) => (e.utilization_pct ?? 0) >= 50 && e.expiring_count === 0).length;
  const inspect = equipment.filter((e) => e.expiring_count > 0).length;
  const broken = equipment.filter((e) => (e.utilization_pct ?? 0) === 0 && e.expiring_count === 0).length;
  const idle = Math.max(0, total - running - inspect - broken);

  const pct = (n: number) => total === 0 ? 0 : Math.round((n / total) * 1000) / 10;

  const cards = [
    { label: '전체 장비', value: total, sub: '등록된 전체 장비', tone: 'slate', icon: <IconExc /> },
    { label: '가동 중', value: running, sub: `${pct(running)}%`, tone: 'emerald', icon: <IconCheck /> },
    { label: '점검 필요', value: inspect, sub: `${pct(inspect)}%`, tone: 'amber', icon: <IconWarn /> },
    { label: '고장', value: broken, sub: `${pct(broken)}%`, tone: 'rose', icon: <IconWrench /> },
    { label: '미사용', value: idle, sub: `${pct(idle)}%`, tone: 'slate', icon: <IconPause /> },
  ] as const;

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
      {cards.map((c) => (
        <div key={c.label} className="rounded-xl border border-slate-200 bg-white p-4 flex items-center gap-4">
          <div className={`shrink-0 inline-flex h-12 w-12 items-center justify-center rounded-xl ${TONE_BG[c.tone]}`}>
            <span className={TONE_TEXT[c.tone]}>{c.icon}</span>
          </div>
          <div className="min-w-0">
            <div className="text-xs font-medium text-slate-500">{c.label}</div>
            <div className="mt-0.5 text-2xl font-bold text-slate-900">{c.value}<span className="text-sm font-medium text-slate-500 ml-0.5">대</span></div>
            <div className="text-xs text-slate-400 mt-0.5">{c.sub}</div>
          </div>
        </div>
      ))}
    </div>
  );
}

const TONE_BG: Record<string, string> = {
  slate: 'bg-slate-100',
  emerald: 'bg-emerald-100',
  amber: 'bg-amber-100',
  rose: 'bg-rose-100',
};
const TONE_TEXT: Record<string, string> = {
  slate: 'text-slate-500',
  emerald: 'text-emerald-600',
  amber: 'text-amber-600',
  rose: 'text-rose-600',
};

function IconExc() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="10" width="20" height="8" rx="1.5" />
      <circle cx="7" cy="19" r="2" />
      <circle cx="17" cy="19" r="2" />
      <path d="M5 10V6h6l3 4" />
    </svg>
  );
}
function IconCheck() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" /><polyline points="22 4 12 14.01 9 11.01" />
    </svg>
  );
}
function IconWarn() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}
function IconWrench() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
    </svg>
  );
}
function IconPause() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
      <rect x="6" y="4" width="4" height="16" rx="0.5" /><rect x="14" y="4" width="4" height="16" rx="0.5" />
    </svg>
  );
}
