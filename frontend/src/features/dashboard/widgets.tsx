import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';

export function StatCard({
  label, value, sub, href, tone = 'slate', emphasize, onClick,
}: {
  label: string;
  value: number | string;
  sub?: string;
  href?: string;
  tone?: 'slate' | 'blue' | 'emerald' | 'amber' | 'rose';
  emphasize?: boolean;
  onClick?: () => void;
}) {
  const tones: Record<string, string> = {
    slate: 'bg-slate-100 text-slate-600',
    blue: 'bg-blue-50 text-blue-700',
    emerald: 'bg-emerald-50 text-emerald-700',
    amber: 'bg-amber-50 text-amber-700',
    rose: 'bg-rose-50 text-rose-700',
  };
  const isClickable = !!(href || onClick);
  const inner = (
    <div className={`rounded-xl border border-slate-200 bg-white p-5 shadow-sm ${isClickable ? 'hover:shadow-md transition-shadow cursor-pointer' : ''} ${emphasize ? 'ring-2 ring-amber-200' : ''}`}>
      <div className={`inline-flex px-2 py-0.5 rounded text-xs font-semibold mb-2 ${tones[tone]}`}>
        {label}
      </div>
      <div className="text-2xl font-bold text-slate-900">{value}</div>
      {sub && <div className="text-xs text-slate-500 mt-1">{sub}</div>}
    </div>
  );
  if (href) return <Link to={href} className="block">{inner}</Link>;
  if (onClick) return <button type="button" onClick={onClick} className="block w-full text-left">{inner}</button>;
  return inner;
}

export function SectionCard({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-bold text-slate-900">{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}

export function EmptyState({ text }: { text: string }) {
  return <p className="text-sm text-slate-400 py-6 text-center">{text}</p>;
}

export function TodoBanner({ text }: { text: string }) {
  return (
    <p className="text-xs text-slate-400 italic">
      <span className="inline-block px-1.5 py-0.5 rounded bg-slate-100 text-slate-500 mr-2">TODO</span>
      {text}
    </p>
  );
}
