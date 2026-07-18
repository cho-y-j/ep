import { Link } from 'react-router-dom';

export type TodayTask = { label: string; count: number; to: string };

/**
 * 역할별 대시보드 상단 "오늘 할 일" 액션 카드 행.
 * count > 0 인 항목만 카드로 노출(클릭 시 해당 화면 이동). 전부 0이면 "모두 처리됨" 빈상태.
 */
export default function TodayTasksRow({ tasks, loading }: { tasks: TodayTask[]; loading?: boolean }) {
  if (loading) {
    return <div className="card p-4 text-sm text-slate-400">오늘 할 일 불러오는 중…</div>;
  }

  const items = tasks.filter((t) => t.count > 0);
  const total = items.reduce((s, t) => s + t.count, 0);

  if (total === 0) {
    return (
      <section className="card flex items-center gap-3 border-emerald-200 bg-emerald-50/40 p-5">
        <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-lg text-emerald-700">✓</span>
        <div>
          <div className="font-bold text-slate-900">오늘 할 일을 모두 처리했습니다</div>
          <div className="text-xs text-slate-500">새 요청·서명·승인 대기가 없습니다.</div>
        </div>
      </section>
    );
  }

  return (
    <section className="card border-amber-300 bg-amber-50/30 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-bold text-slate-900">
          오늘 할 일 <span className="ml-1 rounded-full bg-amber-200 px-1.5 py-0.5 text-[11px] font-semibold text-amber-900">{total}</span>
        </h2>
      </div>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
        {items.map((it) => (
          <Link
            key={it.to}
            to={it.to}
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2.5 transition hover:border-brand-300 hover:bg-brand-50/40"
          >
            <span className="text-sm font-medium text-slate-800">{it.label}</span>
            <span className="inline-flex items-center gap-1.5 text-slate-400">
              <span className="inline-flex h-6 min-w-[24px] items-center justify-center rounded-full bg-brand-600 px-1.5 text-xs font-bold text-white">
                {it.count}
              </span>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
