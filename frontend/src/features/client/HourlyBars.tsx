/** 시간(0~23)별 재실 인원 세로 막대 — 혼잡도 v1(의존성 없는 CSS). 최댓값 시간대는 강조(피크). */
export default function HourlyBars({
  title,
  subtitle,
  data,
  tone = 'brand',
}: {
  title: string;
  subtitle?: string;
  data: number[];
  tone?: 'brand' | 'emerald';
}) {
  const hours = Array.from({ length: 24 }, (_, h) => data[h] ?? 0);
  const max = Math.max(1, ...hours);
  const peak = hours.some((v) => v > 0) ? hours.indexOf(Math.max(...hours)) : -1;
  const hasData = hours.some((v) => v > 0);
  const barColor = tone === 'emerald' ? 'bg-emerald-500' : 'bg-brand-500';

  return (
    <section className="card">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <h2 className="text-sm font-bold text-slate-900">{title}</h2>
        {subtitle && <span className="shrink-0 text-[11px] text-slate-400">{subtitle}</span>}
      </div>
      {!hasData ? (
        <div className="rounded border border-dashed border-slate-200 py-8 text-center text-xs text-slate-400">
          오늘 기록된 출근이 없습니다
        </div>
      ) : (
        <>
          <div className="flex h-28 items-end gap-[2px]">
            {hours.map((v, h) => (
              <div
                key={h}
                className="flex h-full flex-1 flex-col justify-end"
                title={`${h}시 · ${v}명`}
              >
                <div
                  className={`w-full rounded-t transition-all ${h === peak ? 'bg-amber-500' : barColor}`}
                  style={{ height: v > 0 ? `${Math.max(6, Math.round((v / max) * 100))}%` : '0%' }}
                />
              </div>
            ))}
          </div>
          <div className="mt-1 flex justify-between text-[10px] tabular-nums text-slate-400">
            <span>0시</span>
            <span>6시</span>
            <span>12시</span>
            <span>18시</span>
            <span>23시</span>
          </div>
          {peak >= 0 && (
            <p className="mt-2 text-[11px] text-slate-500">
              피크 <span className="font-semibold text-amber-600">{peak}시</span> · 최대 {max}명 재실
            </p>
          )}
        </>
      )}
    </section>
  );
}
