import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';

/** BP 대시보드 상단 — 처리 대기 큐. 받은 투입 요청(REQUESTED) + 받은 서류 심사(미읽음).
 *  각 항목 클릭 시 해당 화면으로 이동. 공급사 IncomingRequestsWidget 과 같은 패턴. */
export default function BpPendingQueueWidget() {
  const [deployCount, setDeployCount] = useState<number | null>(null);
  const [reviewCount, setReviewCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<Array<{ status: string }>>('/api/field-deployments/bp').then((r) => r.data).catch(() => []),
      api.get<Array<{ read_at: string | null }>>('/api/document-reviews/received').then((r) => r.data).catch(() => []),
    ]).then(([d, r]) => {
      if (cancelled) return;
      setDeployCount(d.filter((x) => x.status === 'REQUESTED').length);
      setReviewCount(r.filter((x) => !x.read_at).length);
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  if (loading) {
    return <div className="card p-4 text-sm text-slate-400">처리 대기 항목 불러오는 중…</div>;
  }

  const items = [
    { label: '받은 투입 요청', count: deployCount ?? 0, to: '/field-deployments/bp' },
    { label: '받은 서류 심사', count: reviewCount ?? 0, to: '/document-reviews/received' },
  ].filter((it) => it.count > 0);

  const total = items.reduce((s, it) => s + it.count, 0);

  if (total === 0) {
    return (
      <div className="card p-4 text-sm text-emerald-700">
        처리 대기 항목이 없습니다 — 받은 투입 요청·서류 심사가 모두 처리되었습니다.
      </div>
    );
  }

  return (
    <section className="card p-4 border-amber-300 bg-amber-50/30">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-slate-900">
          처리 대기 <span className="ml-1 px-1.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-200 text-amber-900">{total}</span>
        </h2>
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {items.map((it) => (
          <Link
            key={it.to}
            to={it.to}
            className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-3 py-2.5 hover:border-brand-300 hover:bg-brand-50/40 transition"
          >
            <span className="text-sm font-medium text-slate-800">{it.label}</span>
            <span className="inline-flex items-center gap-1.5 text-slate-400">
              <span className="inline-flex items-center justify-center min-w-[24px] h-6 px-1.5 rounded-full bg-brand-600 text-white text-xs font-bold">
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
