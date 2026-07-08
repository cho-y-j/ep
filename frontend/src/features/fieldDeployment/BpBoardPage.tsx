import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';

type BoardItem = {
  deployment_id: number;
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  resource_label: string;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  target_site_id?: number | null;
  target_site_name?: string | null;
  site_latitude?: number | null;
  site_longitude?: number | null;
  start_date?: string | null;
  activated_at?: string | null;
  total_days?: number | null;
  total_hours?: number | null;
  last_work_date?: string | null;
  today_attended?: boolean | null;
  recent_confirmations?: Array<{
    id: number;
    work_date: string;
    total_hours?: number | null;
    morning_time?: string | null;
    afternoon_time?: string | null;
    signed_by_supplier?: boolean;
    signed_by_bp?: boolean;
  }>;
};

export default function BpDeploymentBoardPage() {
  const [items, setItems] = useState<BoardItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'waiting' | 'live'>('live');

  useEffect(() => {
    let cancelled = false;
    api.get<BoardItem[]>('/api/field-deployments/bp/board')
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const { waiting, live } = useMemo(() => {
    const waiting = items.filter((i) => (i.total_days ?? 0) === 0);
    const live = items.filter((i) => (i.total_days ?? 0) > 0);
    return { waiting, live };
  }, [items]);

  const current = tab === 'waiting' ? waiting : live;

  // 현장별 그룹화
  const groups = current.reduce<Record<string, BoardItem[]>>((acc, it) => {
    const key = it.target_site_name ?? '현장 미지정';
    (acc[key] = acc[key] ?? []).push(it);
    return acc;
  }, {});

  return (
    <AppShell breadcrumb={[{ label: '투입 현황' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">투입 현황</h1>
          <p className="mt-1 text-sm text-slate-500">
            현장에 배치된 자원과 출퇴근·가동 상태판. 일일 작업확인서로 계산됩니다.
          </p>
        </header>

        <div className="border-b border-slate-200 flex gap-1">
          <button onClick={() => setTab('waiting')}
                  className={`px-4 py-2 text-sm font-semibold border-b-2 ${tab === 'waiting' ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
            대기 ({waiting.length})
          </button>
          <button onClick={() => setTab('live')}
                  className={`px-4 py-2 text-sm font-semibold border-b-2 ${tab === 'live' ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-800'}`}>
            운영중+종료 ({live.length})
          </button>
        </div>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : current.length === 0 ? (
          <div className="card p-10 text-center text-sm text-slate-400">
            {tab === 'waiting' ? '대기 중인 자원이 없습니다 (모두 운영 중이거나 종료).' : '운영 중인 자원이 없습니다.'}
          </div>
        ) : (
          <div className="space-y-4">
            {Object.entries(groups).map(([siteName, list]) => {
              const first = list[0];
              return (
                <section key={siteName} className="card p-0 overflow-hidden">
                  <div className="px-4 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between">
                    <div>
                      <h2 className="font-bold text-slate-900">{siteName}</h2>
                      {first.site_latitude != null && first.site_longitude != null && (
                        <a href={`https://map.kakao.com/link/map/${encodeURIComponent(siteName)},${first.site_latitude},${first.site_longitude}`}
                           target="_blank" rel="noopener"
                           className="text-[10px] text-slate-500 hover:text-blue-600">
                          📍 {first.site_latitude.toFixed(5)}, {first.site_longitude.toFixed(5)} — 카카오맵
                        </a>
                      )}
                    </div>
                    <span className="text-xs text-slate-500">{list.length}건</span>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3 p-3">
                    {list.map((it) => <ResourceCard key={it.deployment_id} item={it} />)}
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </div>
    </AppShell>
  );
}

function ResourceCard({ item }: { item: BoardItem }) {
  const isPerson = item.resource_type === 'PERSON';
  const href = isPerson ? `/persons/${item.resource_id}` : `/equipment/${item.resource_id}`;
  const totalHours = item.total_hours != null ? Number(item.total_hours).toFixed(1) : '-';

  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 hover:shadow transition">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="min-w-0">
          <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-700 mr-1">
            {isPerson ? '인원' : '장비'}
          </span>
          <Link to={href} className="font-bold text-slate-900 hover:text-brand-700">
            {item.resource_label}
          </Link>
          <div className="text-[11px] text-slate-500 mt-0.5 truncate">
            공급사: {item.supplier_company_name ?? '#' + item.supplier_company_id}
          </div>
        </div>
        {item.today_attended === true && (
          <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-100 text-emerald-700 shrink-0">
            오늘 출근
          </span>
        )}
        {item.today_attended === false && isPerson && (
          <span className="px-1.5 py-0.5 rounded-full text-[10px] font-semibold bg-slate-200 text-slate-600 shrink-0">
            오늘 미출근
          </span>
        )}
      </div>
      <div className="grid grid-cols-3 gap-2 text-xs mb-2">
        <div>
          <div className="text-slate-400">시작</div>
          <div className="font-semibold tabular-nums">{item.start_date ?? '-'}</div>
        </div>
        <div>
          <div className="text-slate-400">누적</div>
          <div className="font-semibold tabular-nums">
            {isPerson ? `${item.total_days ?? 0}일 / ${totalHours}h` : '—'}
          </div>
        </div>
        <div>
          <div className="text-slate-400">최근</div>
          <div className="font-semibold tabular-nums">{item.last_work_date ?? '-'}</div>
        </div>
      </div>
      {isPerson && (item.recent_confirmations ?? []).length > 0 && (
        <details className="text-xs">
          <summary className="cursor-pointer text-slate-500 hover:text-slate-800">최근 작업확인서 펼치기</summary>
          <ul className="mt-1 space-y-0.5 max-h-[180px] overflow-y-auto">
            {(item.recent_confirmations ?? []).map((c) => (
              <li key={c.id} className="flex items-center gap-2 px-1.5 py-1 rounded hover:bg-slate-50">
                <span className="tabular-nums w-[80px]">{c.work_date}</span>
                {c.morning_time && <span className="text-slate-500">오{c.morning_time}</span>}
                {c.afternoon_time && <span className="text-slate-500">오{c.afternoon_time}</span>}
                <span className="ml-auto tabular-nums font-semibold">{c.total_hours ?? 0}h</span>
                {c.signed_by_supplier && c.signed_by_bp && (
                  <span className="px-1 py-0.5 rounded text-[9px] bg-emerald-100 text-emerald-700">✓</span>
                )}
              </li>
            ))}
          </ul>
        </details>
      )}
      {!isPerson && (
        <p className="text-[10px] text-slate-400 italic">
          장비 가동 통계는 별도 가동일지 또는 운전자 작업확인서 매핑이 필요합니다.
        </p>
      )}
    </div>
  );
}
