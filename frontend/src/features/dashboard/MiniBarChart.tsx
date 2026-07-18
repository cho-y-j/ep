export type BarDatum = { label: string; value: number; tone?: 'brand' | 'emerald' | 'amber' | 'slate' | 'rose' };

const TONE: Record<string, string> = {
  brand: 'bg-brand-500',
  emerald: 'bg-emerald-500',
  amber: 'bg-amber-500',
  slate: 'bg-slate-400',
  rose: 'bg-rose-500',
};

/** 의존성 없는 CSS 가로 막대 차트 — 대시보드 분포 시각화용(값 라벨 포함). */
export default function MiniBarChart({ title, data, emptyText }: { title: string; data: BarDatum[]; emptyText?: string }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const hasData = data.some((d) => d.value > 0);

  return (
    <section className="card">
      <h2 className="mb-3 text-sm font-bold text-slate-900">{title}</h2>
      {!hasData ? (
        <div className="rounded border border-dashed border-slate-200 py-6 text-center text-xs text-slate-400">
          {emptyText ?? '표시할 데이터가 없습니다'}
        </div>
      ) : (
        <ul className="space-y-2">
          {data.map((d) => (
            <li key={d.label} className="flex items-center gap-3">
              <span className="w-24 shrink-0 truncate text-xs text-slate-600" title={d.label}>{d.label}</span>
              <div className="h-5 flex-1 overflow-hidden rounded bg-slate-100">
                <div className={`h-full rounded ${TONE[d.tone ?? 'brand']}`} style={{ width: `${Math.round((d.value / max) * 100)}%` }} />
              </div>
              <span className="w-9 shrink-0 text-right text-xs font-semibold tabular-nums text-slate-700">{d.value}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
