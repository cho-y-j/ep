import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { SupplementResponse } from '../../types/compliance';
import { CHECK_TYPE_LABEL, type ResourceCheckResponse } from '../../types/resourceCheck';

type OwnerKey = string; // `${type}:${id}`

type SuppGroup = {
  key: OwnerKey;
  ownerType: SupplementResponse['target_owner_type'];
  ownerId: number;
  ownerName: string;
  items: SupplementResponse[];
};

type CheckGroup = {
  key: OwnerKey;
  ownerType: ResourceCheckResponse['owner_type'];
  ownerId: number;
  ownerLabel: string;
  items: ResourceCheckResponse[];
};

/** 공급사 대시보드 상단 — 받은 보완요청(OPEN) + 받은 점검요청(REQUESTED).
 *  같은 owner(차량/인원/회사)에 걸린 여러 요청은 한 카드로 묶어 표시. */
export default function IncomingRequestsWidget() {
  const { user } = useAuth();
  const [supps, setSupps] = useState<SupplementResponse[]>([]);
  const [checks, setChecks] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [openKeys, setOpenKeys] = useState<Set<OwnerKey>>(new Set());

  function toggle(key: OwnerKey) {
    setOpenKeys((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<SupplementResponse[]>('/api/document-supplements').then((r) => r.data).catch(() => []),
      api.get<ResourceCheckResponse[]>('/api/resource-checks/supplier-list').then((r) => r.data).catch(() => []),
    ]).then(([s, c]) => {
      if (cancelled) return;
      setSupps(s.filter((x) => x.target_supplier_company_id === user?.company_id && x.status === 'OPEN'));
      setChecks(c.filter((x) => x.status === 'REQUESTED'));
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [user?.company_id]);

  const suppGroups: SuppGroup[] = useMemo(() => {
    const map = new Map<OwnerKey, SuppGroup>();
    for (const s of supps) {
      const key = `${s.target_owner_type}:${s.target_owner_id}`;
      const g = map.get(key);
      if (g) g.items.push(s);
      else map.set(key, {
        key,
        ownerType: s.target_owner_type,
        ownerId: s.target_owner_id,
        ownerName: s.target_owner_name ?? `#${s.target_owner_id}`,
        items: [s],
      });
    }
    return Array.from(map.values());
  }, [supps]);

  const checkGroups: CheckGroup[] = useMemo(() => {
    const map = new Map<OwnerKey, CheckGroup>();
    for (const c of checks) {
      const key = `${c.owner_type}:${c.owner_id}`;
      const g = map.get(key);
      if (g) g.items.push(c);
      else map.set(key, {
        key,
        ownerType: c.owner_type,
        ownerId: c.owner_id,
        ownerLabel: c.owner_label,
        items: [c],
      });
    }
    return Array.from(map.values());
  }, [checks]);

  const total = supps.length + checks.length;

  if (loading) {
    return <div className="card p-4 text-sm text-slate-400">받은 요청 불러오는 중…</div>;
  }
  if (total === 0) {
    return (
      <div className="card p-4 text-sm text-emerald-700 flex items-center gap-2">
        <span>받은 보완요청·점검요청 없음 — 처리 대기 항목이 없습니다.</span>
      </div>
    );
  }

  return (
    <section className="card p-4 border-amber-300 bg-amber-50/30">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-slate-900">
          처리 대기 요청 <span className="ml-1 px-1.5 py-0.5 rounded-full text-[11px] font-semibold bg-amber-200 text-amber-900">{total}</span>
        </h2>
        <div className="flex gap-2 text-[11px] text-slate-500">
          <Link to="/document-management" className="hover:text-slate-900">보완요청 전체 ›</Link>
          <Link to="/resource-checks/supplier" className="hover:text-slate-900">점검요청 전체 ›</Link>
        </div>
      </div>

      {suppGroups.length > 0 && (
        <div className="mb-3">
          <div className="text-xs font-semibold text-slate-500 mb-1">받은 보완요청 ({supps.length})</div>
          <ul className="space-y-2">
            {suppGroups.map((g) => {
              const open = openKeys.has(g.key);
              return (
                <li key={g.key} className="rounded border border-slate-200 bg-white overflow-hidden">
                  <button
                    type="button"
                    onClick={() => toggle(g.key)}
                    className="w-full flex items-center gap-2 px-3 py-2 bg-slate-50 border-b border-slate-200 hover:bg-slate-100 text-left"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={`text-slate-500 transition-transform ${open ? 'rotate-90' : ''}`}>
                      <polyline points="9 18 15 12 9 6" />
                    </svg>
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-200 text-slate-700">
                      {g.ownerType === 'EQUIPMENT' ? '장비' : g.ownerType === 'PERSON' ? '인원' : '회사'}
                    </span>
                    <span className="font-semibold text-slate-900 truncate">{g.ownerName}</span>
                    <span className="ml-auto text-[11px] text-slate-500">{g.items.length}건</span>
                  </button>
                  {open && (
                    <ul className="divide-y divide-slate-100">
                      {g.items.map((s) => (
                        <li key={s.id} className="flex items-center gap-2 px-3 py-1.5 text-sm">
                          <span className="text-slate-700 truncate">{s.document_type_name ?? `#${s.document_type_id}`}</span>
                          {s.reason && (
                            <span className="text-xs text-slate-500 truncate max-w-[300px]" title={s.reason}>— {s.reason}</span>
                          )}
                          <Link
                            to={fixUrl(s)}
                            className="ml-auto shrink-0 px-2 py-1 rounded bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 whitespace-nowrap"
                          >
                            보완하러 가기
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {checkGroups.length > 0 && (
        <div>
          <div className="text-xs font-semibold text-slate-500 mb-1">받은 점검요청 ({checks.length})</div>
          <ul className="space-y-2">
            {checkGroups.map((g) => {
              const open = openKeys.has(g.key);
              return (
                <li key={g.key} className="rounded border border-slate-200 bg-white overflow-hidden">
                  <button
                    type="button"
                    onClick={() => toggle(g.key)}
                    className="w-full flex items-center gap-2 px-3 py-2 bg-slate-50 border-b border-slate-200 hover:bg-slate-100 text-left"
                  >
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className={`text-slate-500 transition-transform ${open ? 'rotate-90' : ''}`}>
                      <polyline points="9 18 15 12 9 6" />
                    </svg>
                    <span className="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-slate-200 text-slate-700">
                      {g.ownerType === 'EQUIPMENT' ? '장비' : '인원'}
                    </span>
                    <span className="font-semibold text-slate-900 truncate">{g.ownerLabel}</span>
                    <span className="ml-auto text-[11px] text-slate-500">{g.items.length}건</span>
                  </button>
                  {open && (
                    <ul className="divide-y divide-slate-100">
                      {g.items.map((c) => (
                        <li key={c.id} className="flex items-center gap-2 px-3 py-1.5 text-sm">
                          <span className="text-slate-700 truncate">{CHECK_TYPE_LABEL[c.check_type]}</span>
                          {c.due_date && <span className="text-xs text-rose-700">마감 {c.due_date}</span>}
                          <Link
                            to="/resource-checks/supplier"
                            className="ml-auto shrink-0 px-2 py-1 rounded border border-blue-500 text-blue-700 text-xs font-semibold hover:bg-blue-50 whitespace-nowrap"
                          >
                            서류 첨부 회신
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </section>
  );
}

function fixUrl(s: SupplementResponse): string {
  const q = `?supplementType=${s.document_type_id}`;
  if (s.target_owner_type === 'EQUIPMENT') return `/equipment/${s.target_owner_id}${q}`;
  if (s.target_owner_type === 'PERSON') return `/persons/${s.target_owner_id}${q}`;
  return `/my-company${q}`;
}
