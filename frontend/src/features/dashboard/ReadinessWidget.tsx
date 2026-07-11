import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { SectionCard, EmptyState } from './widgets';

type ReadinessItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  label: string;
  ready: boolean;
  pending: string[];
};

/**
 * 투입 준비 현황 — 작업계획서 게이트와 동일 판정을 읽기전용으로 노출(GET /api/resources/readiness).
 * ready 면 초록 "투입 대기", 아니면 회색 "준비중" + 남은 사유(pending).
 */
export default function ReadinessWidget() {
  const [items, setItems] = useState<ReadinessItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<ReadinessItem[]>('/api/resources/readiness')
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const readyCount = items.filter((i) => i.ready).length;

  return (
    <SectionCard
      title="투입 준비 현황"
      action={
        !loading && items.length > 0 ? (
          <span className="text-xs text-slate-500">
            투입 대기 <span className="font-semibold text-emerald-700">{readyCount}</span> / 전체 {items.length}
          </span>
        ) : null
      }
    >
      {loading ? (
        <EmptyState text="불러오는 중…" />
      ) : items.length === 0 ? (
        <EmptyState text="표시할 자원이 없습니다." />
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((i) => (
            <li key={`${i.resource_type}:${i.resource_id}`} className="py-2.5 flex items-center gap-2">
              <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-200 text-slate-700 shrink-0">
                {i.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
              </span>
              <span className="font-medium text-slate-900 truncate">{i.label}</span>
              {i.ready ? (
                <span className="ml-auto shrink-0 inline-flex px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
                  투입 대기
                </span>
              ) : (
                <span className="ml-auto shrink-0 inline-flex items-center gap-1.5" title={i.pending.join(', ')}>
                  <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-500">
                    준비중
                  </span>
                  <span className="text-xs text-slate-500 truncate max-w-[220px]">
                    {i.pending.join(', ')}
                  </span>
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
