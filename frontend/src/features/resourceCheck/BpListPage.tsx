import { useEffect, useState, type ReactElement } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect, compareValues } from '../../components/ui';
import { useAuth } from '../auth/AuthContext';
import IssueResourceCheckDialog from './IssueResourceCheckDialog';
import SupplierResourcePickerDialog, { type PickedResource } from './SupplierResourcePickerDialog';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS,
  type ResourceCheckResponse, type ResourceCheckType,
} from '../../types/resourceCheck';
import type { InspectionResponse } from '../../types/safety';
import type { DeployCheckResult } from '../readiness/DeployCheckCard';

const TYPE_OPTIONS = Object.entries(CHECK_TYPE_LABEL).map(([value, label]) => ({ value, label }));
const STATUS_OPTIONS = Object.entries(CHECK_STATUS_LABEL).map(([value, label]) => ({ value, label }));

export default function ResourceCheckBpList() {
  const { user } = useAuth();
  // 공급사도 자기+자식 자원에 직접 발행·승인(BP 미사용 현실 대응) — 발행 진입점은 공급사에게만 노출.
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const [items, setItems] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  // 클라이언트 필터 — 로드된 요청을 좁힘.
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  // 카드 목록이라 헤더 정렬 대신 정렬 셀렉트(기본 = 서버 순서).
  const [sortSel, setSortSel] = useState<'' | 'due' | 'type' | 'status' | 'owner'>('');
  // 공급사 발행: 자원 선택 → IssueResourceCheckDialog (계획서 없이 발행 — BpReceivedReviewsPage 경로 재사용).
  // 재검사 통보는 initialTypes 로 반려 건의 종류만 프리필해 같은 다이얼로그를 연다.
  const [pickerOpen, setPickerOpen] = useState(false);
  const [checkTarget, setCheckTarget] =
    useState<(PickedResource & { initialTypes?: ResourceCheckType[] }) | null>(null);
  // A2 연결뷰: target(owner) 별 SafetyInspection 상태 병기. key = `${owner_type}:${owner_id}`.
  const [inspByTarget, setInspByTarget] = useState<Map<string, InspectionResponse[]>>(new Map());
  // 투입 준비 뱃지: target 별 deploy-check(기존 4게이트 판정 그대로) 결과. key 동일.
  const [deployByTarget, setDeployByTarget] = useState<Map<string, DeployCheckResult>>(new Map());

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list');
      setItems(res.data);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  // distinct target 만 by-target 조회. 신규 엔드포인트 미배포(404)면 해당 항목은 map 에 안 담겨 병기 생략.
  // by-target 은 BP 본인 현장 스코프 전용(B5) — 공급사 발행 뷰에선 호출 자체를 생략(403 소음 방지).
  useEffect(() => {
    if (items.length === 0 || isSupplier) { setInspByTarget(new Map()); return; }
    let cancelled = false;
    const distinct = new Map<string, { targetType: 'VEHICLE' | 'PERSON'; targetId: number }>();
    for (const r of items) {
      distinct.set(`${r.owner_type}:${r.owner_id}`, {
        targetType: r.owner_type === 'EQUIPMENT' ? 'VEHICLE' : 'PERSON',
        targetId: r.owner_id,
      });
    }
    void Promise.all(
      Array.from(distinct.entries()).map(([key, t]) =>
        api.get<InspectionResponse[]>('/api/safety-inspections/by-target', {
          params: { targetType: t.targetType, targetId: t.targetId },
        })
          .then((res) => [key, res.data] as const)
          .catch(() => null),
      ),
    ).then((results) => {
      if (cancelled) return;
      const map = new Map<string, InspectionResponse[]>();
      for (const row of results) if (row) map.set(row[0], row[1]);
      setInspByTarget(map);
    });
    return () => { cancelled = true; };
  }, [items, isSupplier]);

  // 투입 준비 뱃지 — distinct target 별 deploy-check(신규 판정 없음, 기존 API 재사용).
  // 접근 불가(BP 스코프 밖 등)·미배포 404 는 catch 로 생략 — 해당 행만 뱃지 미표시.
  useEffect(() => {
    if (items.length === 0) { setDeployByTarget(new Map()); return; }
    let cancelled = false;
    const distinct = new Map<string, { path: 'equipment' | 'person'; id: number }>();
    for (const r of items) {
      distinct.set(`${r.owner_type}:${r.owner_id}`, {
        path: r.owner_type === 'EQUIPMENT' ? 'equipment' : 'person',
        id: r.owner_id,
      });
    }
    void Promise.all(
      Array.from(distinct.entries()).map(([key, t]) =>
        api.get<DeployCheckResult>(`/api/resources/${t.path}/${t.id}/deploy-check`)
          .then((res) => [key, res.data] as const)
          .catch(() => null),
      ),
    ).then((results) => {
      if (cancelled) return;
      const map = new Map<string, DeployCheckResult>();
      for (const row of results) if (row) map.set(row[0], row[1]);
      setDeployByTarget(map);
    });
    return () => { cancelled = true; };
  }, [items]);

  const inspStatusFor = (r: ResourceCheckResponse): { label: string; cls: string } | null => {
    const list = inspByTarget.get(`${r.owner_type}:${r.owner_id}`);
    if (!list) return null;
    if (list.length === 0) return { label: '없음', cls: 'bg-slate-100 text-slate-500' };
    if (list.some((i) => i.status === 'COMPLETED')) return { label: '완료', cls: 'bg-emerald-100 text-emerald-800' };
    return { label: '일정 대기', cls: 'bg-amber-100 text-amber-800' };
  };

  // 투입 준비 뱃지 텍스트 — ready 면 초록 "투입 준비됨", 아니면 회색으로 부족 게이트 요약(2건 초과는 "외 N건").
  const readinessFor = (r: ResourceCheckResponse): { label: string; cls: string; title?: string } | null => {
    const d = deployByTarget.get(`${r.owner_type}:${r.owner_id}`);
    if (!d) return null;
    if (d.ready) return { label: '투입 준비됨', cls: 'bg-emerald-100 text-emerald-800' };
    const labels = d.blocks.map((b) => b.label);
    const rest = labels.length - 2;
    return {
      label: `투입 준비까지 ${labels.length}건 — ${labels.slice(0, 2).join(' · ')}${rest > 0 ? ` 외 ${rest}건` : ''}`,
      cls: 'bg-slate-100 text-slate-600',
      title: labels.join('\n'),
    };
  };

  // 재검사 판정 — 같은 자원·같은 종류의 이전(id 더 작은) 건에 REJECTED 가 있으면 재발행 건. 클라이언트 판정(백엔드 무변경).
  const isRecheck = (r: ResourceCheckResponse): boolean =>
    items.some((o) => o.id < r.id && o.owner_type === r.owner_type && o.owner_id === r.owner_id
      && o.check_type === r.check_type && o.status === 'REJECTED');

  const review = async (id: number, action: 'approve' | 'reject') => {
    let note: string | null = null;
    if (action === 'reject') {
      note = window.prompt('반려 사유 (선택)') ?? '';
    }
    setBusy(id);
    try {
      await api.post(`/api/resource-checks/${id}/${action}`, { note });
      toast.success(action === 'approve' ? '승인됨' : '반려됨');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '실패');
    } finally { setBusy(null); }
  };

  const qLower = q.trim().toLowerCase();
  const filtered = items.filter((r) => {
    if (statusFilter && r.status !== statusFilter) return false;
    if (typeFilter && r.check_type !== typeFilter) return false;
    if (qLower) {
      const hay = `${CHECK_TYPE_LABEL[r.check_type]} ${r.owner_label} ${r.supplier_company_name ?? ''} ${r.notes ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  });
  if (sortSel) {
    filtered.sort((a, b) => compareValues(
      sortSel === 'due' ? a.due_date : sortSel === 'type' ? CHECK_TYPE_LABEL[a.check_type]
        : sortSel === 'status' ? CHECK_STATUS_LABEL[a.status] : a.owner_label,
      sortSel === 'due' ? b.due_date : sortSel === 'type' ? CHECK_TYPE_LABEL[b.check_type]
        : sortSel === 'status' ? CHECK_STATUS_LABEL[b.status] : b.owner_label,
    ));
  }
  const activeFilterCount = [q, statusFilter, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); setTypeFilter(''); };

  const renderRow = (r: ResourceCheckResponse) => {
    const si = inspStatusFor(r);
    const rd = readinessFor(r);
    return (
    <div key={r.id} className="card p-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="font-bold text-slate-900">{CHECK_TYPE_LABEL[r.check_type]}</span>
          <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${CHECK_STATUS_CHIP_CLS[r.status]}`}>
            {CHECK_STATUS_LABEL[r.status]}
          </span>
          {isRecheck(r) && (
            <span className="px-1.5 py-0.5 text-[10px] rounded-full font-semibold bg-violet-100 text-violet-800">
              재검사
            </span>
          )}
          {si && (
            <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${si.cls}`}>
              안전점검 {si.label}
            </span>
          )}
          {rd && (
            <span title={rd.title} className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${rd.cls}`}>
              {rd.label}
            </span>
          )}
        </div>
        <div className="mt-0.5 text-sm text-slate-700 truncate">
          {r.owner_label} → {r.supplier_company_name ?? '공급사'}
          {r.due_date && <span className="ml-2 text-xs text-rose-700">마감 {r.due_date}</span>}
        </div>
        {r.notes && <div className="mt-0.5 text-xs text-slate-500 truncate">{r.notes}</div>}
      </div>
      <div className="shrink-0 flex items-center gap-2">
        {r.document_id && (
          <a href={`/api/documents/${r.document_id}/file`} target="_blank" rel="noopener noreferrer"
             className="px-3 py-1.5 text-xs rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
            제출 서류 보기
          </a>
        )}
        {r.status === 'SUBMITTED' && (
          <>
            <button onClick={() => void review(r.id, 'approve')} disabled={busy === r.id}
                    className="px-3 py-1.5 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
              승인
            </button>
            <button onClick={() => void review(r.id, 'reject')} disabled={busy === r.id}
                    className="px-3 py-1.5 text-xs rounded border border-rose-500 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
              반려
            </button>
          </>
        )}
        {r.status === 'REJECTED' && (
          <button
            onClick={() => setCheckTarget({
              ownerType: r.owner_type,
              ownerId: r.owner_id,
              ownerLabel: r.owner_label,
              supplierCompanyId: r.supplier_company_id,
              supplierCompanyName: r.supplier_company_name,
              initialTypes: [r.check_type],
            })}
            className="px-3 py-1.5 text-xs rounded bg-amber-600 text-white hover:bg-amber-700">
            재검사 통보
          </button>
        )}
      </div>
    </div>
    );
  };

  // R2 조합 묶음 — 같은 combo_equipment_id 행을 장비 라벨 헤더 아래로 묶음(첫 등장 위치에 배치).
  // combo_equipment_id 없는 행은 기존 단독 렌더 그대로(무회귀).
  const blocks: ReactElement[] = [];
  const seenCombo = new Set<number>();
  for (const r of filtered) {
    if (r.combo_equipment_id == null) { blocks.push(renderRow(r)); continue; }
    if (seenCombo.has(r.combo_equipment_id)) continue;
    seenCombo.add(r.combo_equipment_id);
    const group = filtered.filter((x) => x.combo_equipment_id === r.combo_equipment_id);
    blocks.push(
      <div key={`combo-${r.combo_equipment_id}`}
           className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-2 space-y-2">
        <div className="flex items-center gap-2 px-1">
          <span className="px-1.5 py-0.5 text-[10px] rounded-full font-semibold bg-indigo-600 text-white">조합</span>
          <span className="text-sm font-bold text-slate-900 truncate">
            {r.combo_equipment_label ?? `장비 #${r.combo_equipment_id}`}
          </span>
          <span className="text-xs text-slate-500">점검 {group.length}건</span>
        </div>
        {group.map((g) => renderRow(g))}
      </div>,
    );
  }

  return (
    <AppShell>
      <PageHeader
        title="보낸 점검 요청"
        subtitle="자원에 발행한 자동차 안전점검·건강검진·안전교육 등 — 회신 검토"
        actions={isSupplier ? (
          <button type="button" onClick={() => setPickerOpen(true)} className="btn-primary text-sm">
            검사 통보
          </button>
        ) : undefined}
      />

      <FilterBar
        search={{ value: q, onChange: setQ, placeholder: '종류·자원·공급사 검색' }}
        activeFilterCount={activeFilterCount}
        onReset={resetFilters}
        sort={
          <select value={sortSel} onChange={(e) => setSortSel(e.target.value as typeof sortSel)}
                  className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50">
            <option value="">기본 순서</option>
            <option value="due">마감일순</option>
            <option value="type">종류순</option>
            <option value="status">상태순</option>
            <option value="owner">자원명순</option>
          </select>
        }
      >
        <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="종류 전체" options={TYPE_OPTIONS} />
        <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체" options={STATUS_OPTIONS} />
      </FilterBar>

      {loading ? (
        <div className="text-sm text-slate-400">로딩…</div>
      ) : items.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">보낸 요청이 없습니다.</div>
      ) : filtered.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">조건에 맞는 요청이 없습니다.</div>
      ) : (
        <div className="space-y-2">
          {blocks}
        </div>
      )}

      {/* 공급사 발행 — 자원 선택 후 검사·교육·검진 날짜 통보 (계획서 없이 발행) */}
      {pickerOpen && (
        <SupplierResourcePickerDialog
          open
          includeEquipment={user?.role === 'EQUIPMENT_SUPPLIER'}
          onClose={() => setPickerOpen(false)}
          onPick={(t) => { setPickerOpen(false); setCheckTarget(t); }}
        />
      )}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={() => { void load(); }}
          workPlanId={null}
          ownerType={checkTarget.ownerType}
          ownerId={checkTarget.ownerId}
          ownerLabel={checkTarget.ownerLabel}
          supplierCompanyId={checkTarget.supplierCompanyId}
          supplierCompanyName={checkTarget.supplierCompanyName}
          initialTypes={checkTarget.initialTypes ?? null}
        />
      )}
    </AppShell>
  );
}
