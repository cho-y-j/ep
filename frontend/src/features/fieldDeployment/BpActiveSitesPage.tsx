import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';

type BoardItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  target_site_id?: number | null;
  target_site_name?: string | null;
  site_latitude?: number | null;
  site_longitude?: number | null;
  today_attended?: boolean | null;
  total_days?: number | null;
  total_hours?: number | null;
};

type SiteSummary = {
  site_id: number | null;
  site_name: string;
  site_latitude: number | null;
  site_longitude: number | null;
  total_resources: number;
  equipment_count: number;
  person_count: number;
  today_attended: number;
  total_hours: number;
};

export default function BpActiveSitesPage() {
  const [items, setItems] = useState<BoardItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<BoardItem[]>('/api/field-deployments/bp/board')
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => { if (!cancelled) setItems([]); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const sites: SiteSummary[] = useMemo(() => {
    const m = new Map<string, SiteSummary>();
    for (const it of items) {
      const key = it.target_site_id != null ? String(it.target_site_id) : 'null';
      const cur = m.get(key) ?? {
        site_id: it.target_site_id ?? null,
        site_name: it.target_site_name ?? '현장 미지정',
        site_latitude: it.site_latitude ?? null,
        site_longitude: it.site_longitude ?? null,
        total_resources: 0,
        equipment_count: 0,
        person_count: 0,
        today_attended: 0,
        total_hours: 0,
      };
      cur.total_resources += 1;
      if (it.resource_type === 'EQUIPMENT') cur.equipment_count += 1;
      else cur.person_count += 1;
      if (it.today_attended) cur.today_attended += 1;
      cur.total_hours += Number(it.total_hours ?? 0);
      m.set(key, cur);
    }
    return Array.from(m.values());
  }, [items]);

  return (
    <AppShell breadcrumb={[{ label: '투입 현황' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">투입 현황</h1>
          <p className="mt-1 text-sm text-slate-500">
            사인 완료된 작업계획서의 현장 목록입니다. 클릭하여 자원 가동·출퇴근 대시보드를 확인하세요.
          </p>
        </header>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중...</p>
        ) : sites.length === 0 ? (
          <div className="card p-10 text-center text-sm text-slate-400">
            현재 사인 완료된 작업계획서가 없습니다.
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
            {sites.map((s) => (
              <Link key={s.site_id ?? 'null'} to={`/work-plans/active/sites/${s.site_id ?? 'none'}`}
                    className="card p-4 hover:shadow transition block">
                <h2 className="font-bold text-slate-900 truncate">{s.site_name}</h2>
                {s.site_latitude != null && s.site_longitude != null && (
                  <div className="text-[11px] text-slate-500 mt-0.5 tabular-nums inline-flex items-center gap-1">
                    <span className="material-symbols-outlined" style={{ fontSize: 12, lineHeight: 1 }}>location_on</span>
                    {s.site_latitude.toFixed(4)}, {s.site_longitude.toFixed(4)}
                  </div>
                )}
                <div className="grid grid-cols-3 gap-2 mt-3 text-xs">
                  <div>
                    <div className="text-slate-400">장비</div>
                    <div className="font-bold tabular-nums">{s.equipment_count}</div>
                  </div>
                  <div>
                    <div className="text-slate-400">인원</div>
                    <div className="font-bold tabular-nums">{s.person_count}</div>
                  </div>
                  <div>
                    <div className="text-slate-400">오늘 출근</div>
                    <div className="font-bold tabular-nums text-emerald-700">{s.today_attended}/{s.person_count}</div>
                  </div>
                </div>
                <div className="mt-2 text-xs text-slate-500">
                  누적: <span className="tabular-nums font-semibold">{s.total_hours.toFixed(1)}h</span>
                </div>
                <div className="mt-2 text-[11px] text-blue-600">상세 보기 →</div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}
