import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import Avatar from '../../components/Avatar';
import AssignmentBadge from './AssignmentBadge';
import type {
  EquipmentCandidateResponse,
  PersonCandidateResponse,
} from '../../types/assignment';
import type { EquipmentResponse } from '../../types/equipment';
import type { PersonResponse } from '../../types/person';
import { EQUIPMENT_CATEGORY_LABEL } from '../../types/equipment';
import { PERSON_ROLE_LABEL } from '../../types/person';

type Kind = 'equipment' | 'person';

type Props = {
  siteId: number;
  /** 이 현장의 BP/ADMIN인 경우만 배치/해제 가능 (서버에서도 강제). */
  canManage: boolean;
};

/**
 * 현장 상세에서 사용. 배치 장비 / 배치 인원 두 섹션을 렌더한다.
 * - 현재 배치된 자원 목록
 * - 배치 추가 모달 (참여 공급사의 자원 후보)
 */
export default function SiteResourcesSection({ siteId, canManage }: Props) {
  return (
    <div className="space-y-4">
      <ResourceList kind="equipment" siteId={siteId} canManage={canManage} />
      <ResourceList kind="person" siteId={siteId} canManage={canManage} />
    </div>
  );
}

function ResourceList({ kind, siteId, canManage }: { kind: Kind; siteId: number; canManage: boolean }) {
  const [items, setItems] = useState<Array<EquipmentResponse | PersonResponse>>([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [filterSupplierId, setFilterSupplierId] = useState<number | 'all'>('all');
  /** 장비별 기본 조종원 캐시. 장비 list 변경 시 fetch. */
  const [defaultOperators, setDefaultOperators] = useState<Map<number, Array<{ id: number; name: string }>>>(new Map());

  const title = kind === 'equipment' ? '배치 장비' : '배치 인원';
  const apiUrl = kind === 'equipment' ? `/api/sites/${siteId}/equipment` : `/api/sites/${siteId}/persons`;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<Array<EquipmentResponse | PersonResponse>>(apiUrl);
      setItems(res.data);
    } finally {
      setLoading(false);
    }
  }, [apiUrl]);

  useEffect(() => { void load(); }, [load]);

  /** 장비 list 가 바뀔 때마다 각 장비의 default-operators 병렬 fetch. */
  useEffect(() => {
    if (kind !== 'equipment' || items.length === 0) return;
    let cancelled = false;
    (async () => {
      const results = await Promise.all(items.map(async (it): Promise<[number, Array<{ id: number; name: string }>]> => {
        try {
          const res = await api.get<Array<{ person_id: number; person_name?: string }>>(`/api/equipment/${it.id}/default-operators`);
          return [it.id, res.data.map((r) => ({ id: r.person_id, name: r.person_name ?? `#${r.person_id}` }))];
        } catch {
          return [it.id, []];
        }
      }));
      if (cancelled) return;
      const map = new Map<number, Array<{ id: number; name: string }>>();
      results.forEach(([id, ops]) => map.set(id, ops));
      setDefaultOperators(map);
    })();
    return () => { cancelled = true; };
  }, [kind, items]);

  /** 공급사별 그룹 — 필터 chip 노출용 */
  const supplierGroups = useMemo(() => {
    const map = new Map<number, { name: string; count: number }>();
    for (const it of items) {
      const sid = it.supplier_id;
      if (sid == null) continue;
      const existing = map.get(sid);
      if (existing) existing.count++;
      else map.set(sid, { name: it.supplier_name ?? `공급사 #${sid}`, count: 1 });
    }
    return Array.from(map.entries()).map(([id, v]) => ({ id, ...v }));
  }, [items]);

  /** 필터 적용된 항목 */
  const visibleItems = useMemo(() => {
    if (filterSupplierId === 'all') return items;
    return items.filter((it) => it.supplier_id === filterSupplierId);
  }, [items, filterSupplierId]);

  async function release(resourceId: number) {
    if (!confirm('이 자원의 현장 배치를 해제하시겠습니까?')) return;
    const url = kind === 'equipment'
      ? `/api/equipment/${resourceId}/assignment`
      : `/api/persons/${resourceId}/assignment`;
    try {
      await api.delete(url, { data: { release_reason: '현장에서 해제' } });
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '해제 실패');
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between mb-4">
        <h3 className="text-lg font-bold text-slate-900 flex items-center gap-2">
          {title}
          <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-700 ring-1 ring-brand-100">
            {items.length}
          </span>
        </h3>
        {canManage && !showAdd && (
          <button
            type="button"
            onClick={() => setShowAdd(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-brand-100 bg-white text-sm font-semibold text-brand-700 hover:bg-brand-50"
          >
            <span>+</span> 배치 추가
          </button>
        )}
      </div>

      {showAdd && canManage && (
        <CandidatePicker
          kind={kind}
          siteId={siteId}
          onDone={() => { setShowAdd(false); void load(); }}
          onCancel={() => setShowAdd(false)}
          assignedIds={new Set(items.map((i) => i.id))}
        />
      )}

      {supplierGroups.length > 1 && (
        <div className="flex flex-wrap gap-1.5 mb-3">
          <button type="button" onClick={() => setFilterSupplierId('all')}
            className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
              filterSupplierId === 'all'
                ? 'bg-brand-600 text-white border-brand-600'
                : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
            }`}>
            전체 ({items.length})
          </button>
          {supplierGroups.map((s) => (
            <button key={s.id} type="button" onClick={() => setFilterSupplierId(s.id)}
              className={`px-2.5 py-0.5 rounded-full text-xs font-semibold border ${
                filterSupplierId === s.id
                  ? 'bg-amber-500 text-white border-amber-500'
                  : 'bg-white text-amber-700 border-amber-200 hover:bg-amber-50'
              }`}>
              {s.name} ({s.count})
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <p className="text-sm text-slate-400">불러오는 중...</p>
      ) : visibleItems.length === 0 ? (
        <p className="text-sm text-slate-400 py-6 text-center">
          {items.length === 0
            ? `현재 배치된 ${kind === 'equipment' ? '장비' : '인원'}이 없습니다.`
            : '해당 공급사에 배치된 자원이 없습니다.'}
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {visibleItems.map((it) => (
            <li key={it.id} className="py-3 flex items-center gap-3">
              <Avatar
                fetchUrl={it.has_photo ? (kind === 'equipment' ? `/api/equipment/${it.id}/photo` : `/api/persons/${it.id}/photo`) : undefined}
                fallbackText={kind === 'equipment'
                  ? (it as EquipmentResponse).vehicle_no ?? (it as EquipmentResponse).model ?? ''
                  : (it as PersonResponse).name}
                size={40}
                rounded="lg"
              />
              <div className="flex-1 min-w-0">
                <Link
                  to={kind === 'equipment' ? `/equipment/${it.id}` : `/persons/${it.id}`}
                  className="text-sm font-semibold text-slate-900 hover:text-brand-700 truncate block"
                >
                  {kind === 'equipment'
                    ? `${EQUIPMENT_CATEGORY_LABEL[(it as EquipmentResponse).category]} ${(it as EquipmentResponse).vehicle_no ?? (it as EquipmentResponse).model ?? ''}`
                    : (it as PersonResponse).name}
                </Link>
                <div className="text-xs text-slate-500 mt-0.5">
                  {kind === 'equipment'
                    ? `${(it as EquipmentResponse).code ?? '-'}${(it as EquipmentResponse).model ? ` · ${(it as EquipmentResponse).model}` : ''}`
                    : `${(it as PersonResponse).employee_no ?? '-'}${(it as PersonResponse).job_title ? ` · ${(it as PersonResponse).job_title}` : ''}`}
                </div>
                {it.supplier_name && (
                  <div className="text-[11px] text-amber-700 mt-0.5">소속: {it.supplier_name}</div>
                )}
                {kind === 'equipment' && defaultOperators.get(it.id) && defaultOperators.get(it.id)!.length > 0 && (
                  <div className="text-[11px] text-slate-600 mt-0.5 flex flex-wrap gap-1 items-center">
                    <span className="text-slate-400">조종원:</span>
                    {defaultOperators.get(it.id)!.map((op) => (
                      <Link key={op.id} to={`/persons/${op.id}`}
                        className="px-1.5 py-0.5 rounded-full bg-slate-100 text-slate-700 hover:bg-brand-50 hover:text-brand-700">
                        {op.name}
                      </Link>
                    ))}
                  </div>
                )}
              </div>
              {it.assignment_status && (
                <AssignmentBadge status={it.assignment_status} />
              )}
              {canManage && (
                <button
                  type="button"
                  onClick={() => void release(it.id)}
                  className="text-xs px-2 py-1 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50"
                >
                  해제
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function CandidatePicker({ kind, siteId, onDone, onCancel, assignedIds }: {
  kind: Kind;
  siteId: number;
  onDone: () => void;
  onCancel: () => void;
  assignedIds: Set<number>;
}) {
  const [candidates, setCandidates] = useState<Array<EquipmentCandidateResponse | PersonCandidateResponse>>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  const url = kind === 'equipment'
    ? `/api/sites/${siteId}/equipment-candidates`
    : `/api/sites/${siteId}/person-candidates`;

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await api.get<Array<EquipmentCandidateResponse | PersonCandidateResponse>>(url);
        if (!cancelled) setCandidates(res.data);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [url]);

  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  async function pick(resourceId: number, blocked: boolean) {
    // 서류 미비 자원은 ADMIN 만 override 로 강제 배치 가능. 사유 prompt.
    let body: Record<string, unknown> = { site_id: siteId };
    if (blocked) {
      if (!isAdmin) {
        alert('서류가 미비한 자원은 배치할 수 없습니다. ADMIN 만 강제 진행 가능합니다.');
        return;
      }
      const reason = prompt('서류 미비 강제 진행 사유를 입력하세요:');
      if (!reason || !reason.trim()) return;
      body = { ...body, override: true, override_reason: reason.trim() };
    }
    setBusyId(resourceId);
    try {
      const assignUrl = kind === 'equipment'
        ? `/api/equipment/${resourceId}/assignment`
        : `/api/persons/${resourceId}/assignment`;
      await api.post(assignUrl, body);
      onDone();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '배치 실패');
    } finally {
      setBusyId(null);
    }
  }

  // 추천 정렬: 이전 투입 > 서류 위험 적음 > 미배치
  const sorted = useMemo(() => {
    return [...candidates].sort((a, b) => {
      // 이미 배치된 (현재 다른 현장에 있는) 자원은 뒤로
      const aAssigned = a.currently_assigned ? 1 : 0;
      const bAssigned = b.currently_assigned ? 1 : 0;
      if (aAssigned !== bAssigned) return aAssigned - bAssigned;
      // 차단(blocked)는 뒤로
      const aBlocked = a.blocked ? 1 : 0;
      const bBlocked = b.blocked ? 1 : 0;
      if (aBlocked !== bBlocked) return aBlocked - bBlocked;
      // 이전 투입은 앞으로
      const aPrev = a.previously_used_on_site ? 0 : 1;
      const bPrev = b.previously_used_on_site ? 0 : 1;
      if (aPrev !== bPrev) return aPrev - bPrev;
      // 만료 임박 적은 순
      return a.expiring_documents - b.expiring_documents;
    });
  }, [candidates]);

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 mb-4">
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-sm font-bold text-slate-900">
          {kind === 'equipment' ? '장비 후보' : '인원 후보'}
          <span className="ml-1 text-xs text-slate-500">(참여 공급사 자원)</span>
        </h4>
        <button
          type="button"
          onClick={onCancel}
          className="text-xs text-slate-500 hover:text-slate-900"
        >
          닫기
        </button>
      </div>
      {loading ? (
        <p className="text-xs text-slate-400">불러오는 중...</p>
      ) : sorted.length === 0 ? (
        <p className="text-xs text-slate-400 py-4 text-center">
          참여 중인 공급사의 사용 가능한 자원이 없습니다.<br />
          참여업체를 먼저 추가하거나 공급사가 자원을 등록해야 합니다.
        </p>
      ) : (
        <ul className="space-y-2 max-h-[420px] overflow-y-auto">
          {sorted.map((c) => {
            const alreadyHere = assignedIds.has(c.id);
            // ADMIN 은 blocked 라도 override 로 진행 가능 → disable 하지 않음. 그 외는 blocked 차단.
            const disabled = alreadyHere || (c.blocked && !isAdmin);
            return (
              <li key={c.id} className="flex items-center gap-3 rounded-lg border border-slate-200 bg-white p-3">
                <Avatar
                  fetchUrl={c.has_photo ? (kind === 'equipment' ? `/api/equipment/${c.id}/photo` : `/api/persons/${c.id}/photo`) : undefined}
                  fallbackText={c.name}
                  size={36}
                  rounded="lg"
                />
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-slate-900 truncate">
                    {c.name}
                    {kind === 'person' && (c as PersonCandidateResponse).roles?.length > 0 && (
                      <span className="ml-2 text-xs text-slate-500">
                        {(c as PersonCandidateResponse).roles.map((r) => PERSON_ROLE_LABEL[r]).join(', ')}
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-500 mt-0.5 truncate">
                    {c.supplier_name ?? `회사 #${c.supplier_id}`}
                    {kind === 'equipment' && (c as EquipmentCandidateResponse).code && ` · ${(c as EquipmentCandidateResponse).code}`}
                    {kind === 'person' && (c as PersonCandidateResponse).employee_no && ` · ${(c as PersonCandidateResponse).employee_no}`}
                  </div>
                  <div className="flex flex-wrap gap-1 mt-1.5">
                    {c.previously_used_on_site && (
                      <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-700">이전 투입</span>
                    )}
                    {c.currently_assigned && c.current_site_name && (
                      <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-700">
                        현재 {c.current_site_name}
                      </span>
                    )}
                    {c.expiring_documents > 0 && (
                      <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-700">
                        만료임박 {c.expiring_documents}
                      </span>
                    )}
                    {c.blocked && (
                      <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-100 text-rose-700">사용 제한</span>
                    )}
                    {alreadyHere && (
                      <span className="inline-flex px-1.5 py-0.5 rounded text-[10px] font-semibold bg-blue-100 text-blue-700">이 현장 배치 중</span>
                    )}
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => void pick(c.id, !!c.blocked)}
                  disabled={disabled || busyId === c.id}
                  className="px-3 py-1.5 rounded-lg bg-brand-600 text-white text-xs font-semibold hover:bg-brand-700 disabled:opacity-30 disabled:hover:bg-brand-600 shrink-0"
                >
                  {busyId === c.id ? '배치 중...' : alreadyHere ? '배치됨' : '배치'}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
