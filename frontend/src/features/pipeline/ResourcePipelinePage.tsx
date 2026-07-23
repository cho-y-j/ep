import { Fragment, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { EmptyState, PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import PipelineDetail from './PipelineDetail';
import SetBoard from './SetBoard';
import {
  type PipelineItem,
  type PipelineOperator,
  type SetStageFilter,
  type Stage,
  type StageFilter,
  STAGE_ORDER,
  STAGE_FILTER_LABEL,
  comboReadyOf,
  currentIndex,
  currentStage,
  itemKey,
} from './pipeline';

const STAGE_FILTERS: StageFilter[] = ['docs', 'inspection', 'readiness', 'deployed', 'work', 'settlement', 'done'];

/**
 * 자원 현황(파이프라인) — 세트(조합) 흐름 보드(기본) + 자원별 목록 토글 + 개별 자원 상세(1대 뷰) 전환.
 * 필터/선택 상태는 URL 쿼리에 동기화되어 공유 가능. GET /api/resources/pipeline 집계 재사용.
 */
export default function ResourcePipelinePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [items, setItems] = useState<PipelineItem[]>([]);
  const [operatorsByEquip, setOperatorsByEquip] = useState<Record<number, PipelineOperator[]>>({});
  const [loading, setLoading] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);
  // 세트 보드에서 검사 통보 발송 후 재로딩 트리거.
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api.get<PipelineItem[]>('/api/resources/pipeline')
      .then(async (r) => {
        if (cancelled) return;
        const list = r.data ?? [];
        setItems(list);
        // R1 조합 표시 — 장비별 조합(교대조) 조종원을 배치 1회로 로드(TargetPicker 와 동일 endpoint).
        const equipmentIds = list.filter((it) => it.resource_type === 'EQUIPMENT').map((it) => it.resource_id);
        if (equipmentIds.length === 0) { setOperatorsByEquip({}); return; }
        try {
          const b = await api.post<{ results: Array<{ equipment_id: number; operators: PipelineOperator[] }> }>(
            '/api/equipment/default-operators', { equipment_ids: equipmentIds });
          if (cancelled) return;
          const map: Record<number, PipelineOperator[]> = {};
          b.data.results.forEach((res) => { map[res.equipment_id] = res.operators; });
          setOperatorsByEquip(map);
        } catch { if (!cancelled) setOperatorsByEquip({}); }
      })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [reloadKey]);

  // 조합 준비 파생용 — PERSON 행 lookup (comboReadyOf 가 조종원 readiness 를 이 맵에서 찾는다).
  const itemsByKey = useMemo(() => new Map(items.map((it) => [itemKey(it), it])), [items]);

  // URL 쿼리 = 필터/선택 상태(공유 가능한 링크).
  const site = params.get('site') ?? '';        // '' | '<id>' | 'none'
  const company = params.get('company') ?? '';  // '' | '<id>'
  const type = params.get('type') ?? '';        // '' | 'EQUIPMENT' | 'PERSON'
  const stage = (params.get('stage') ?? '') as '' | StageFilter;
  const resourceKey = params.get('resource') ?? '';
  const q = params.get('q') ?? '';              // 자원명 검색
  // 보기 전환 — 기본은 세트(조합) 흐름 보드, 'resources' 면 기존 자원 단위 목록(무손실 유지).
  const view = params.get('view') === 'resources' ? 'resources' : 'sets';
  const sstage = (params.get('sstage') ?? '') as '' | SetStageFilter; // 세트 보드 단계 필터

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  // 필터 옵션 — 응답 데이터에서 파생(공급사는 /api/sites 미노출이라 현장도 자원에서 도출).
  const siteOptions = useMemo(() => {
    const m = new Map<number, string>();
    items.forEach((it) => { if (it.site_id != null) m.set(it.site_id, it.site_name ?? `현장 #${it.site_id}`); });
    return [...m.entries()].map(([id, name]) => ({ id, name }));
  }, [items]);
  const hasUnassigned = useMemo(() => items.some((it) => it.site_id == null), [items]);

  const companyOptions = useMemo(() => {
    const m = new Map<number, string>();
    items.forEach((it) => { if (it.supplier_company_id != null) m.set(it.supplier_company_id, it.supplier_name ?? `업체 #${it.supplier_company_id}`); });
    return [...m.entries()].map(([id, name]) => ({ id, name }));
  }, [items]);
  const showCompanyFilter = companyOptions.length > 1; // 하위공급사(협력사) 있을 때만

  // site/company/type/검색 으로 좁힌 기본 집합 — 단계 칩 집계의 분모(단계 필터엔 영향 안 받게).
  const qLower = q.trim().toLowerCase();
  const baseItems = useMemo(() => items.filter((it) => {
    if (type && it.resource_type !== type) return false;
    if (company && it.supplier_company_id !== Number(company)) return false;
    if (site === 'none') { if (it.site_id != null) return false; }
    else if (site && it.site_id !== Number(site)) return false;
    if (qLower && !it.label.toLowerCase().includes(qLower)) return false;
    return true;
  }), [items, type, company, site, qLower]);

  const stageCounts = useMemo(() => {
    const c: Record<StageFilter, number> = { docs: 0, inspection: 0, readiness: 0, deployed: 0, work: 0, settlement: 0, done: 0 };
    baseItems.forEach((it) => { c[currentStage(it.stages)]++; });
    return c;
  }, [baseItems]);

  const listItems = stage ? baseItems.filter((it) => currentStage(it.stages) === stage) : baseItems;

  const selected = resourceKey ? items.find((it) => itemKey(it) === resourceKey) ?? null : null;
  const activeFilterCount = [site, company, type, q].filter(Boolean).length
    + ((view === 'resources' ? stage : sstage) ? 1 : 0);

  // 개별 자원 선택 시 = 1대 뷰(상세 패널).
  if (selected) {
    return (
      <AppShell breadcrumb={[{ label: '자원 현황', to: '/resource-pipeline' }, { label: selected.label }]}>
        <PipelineDetail item={selected} onBack={() => setParam('resource', '')} />
      </AppShell>
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '자원 현황' }]}>
      {/* 상단 고정 필터바 */}
      <div className="sticky top-[68px] z-20 bg-slate-50 pb-3 pt-1">
        <PageHeader
          title="자원 현황"
          subtitle={view === 'sets'
            ? '장비+조종원 세트별 서류 → 심사 → 검사 → 투입 대기 → 투입 중 → 정산 흐름. 카드의 버튼으로 다음 할 일로 이동합니다.'
            : '장비·인력의 서류 → 검사 → 투입대기 → 투입 → 작업 → 정산 진행 상태. 단계를 눌러 그 단계만, 자원을 눌러 상세를 봅니다.'}
          actions={
            <>
              {view === 'resources' && (
                <span className="rounded-full bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-600">
                  {listItems.length}
                  {listItems.length !== items.length && <span className="text-slate-400"> / {items.length}</span>}
                </span>
              )}
              <button onClick={() => setFiltersOpen((o) => !o)} className="btn-ghost md:hidden">
                필터{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </button>
            </>
          }
        />

        {/* 보기 전환 — 세트(조합) 보드(기본) ↔ 자원별 목록 */}
        <div className="mt-1 inline-flex overflow-hidden rounded-lg border border-slate-300 bg-white">
          {([['sets', '세트 보드'], ['resources', '자원별 보기']] as const).map(([v, label]) => (
            <button key={v} type="button" onClick={() => setParam('view', v === 'sets' ? '' : v)}
              className={`px-3 py-1.5 text-xs font-semibold ${
                view === v ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-50'}`}>
              {label}
            </button>
          ))}
        </div>

        {/* 필터 컨트롤 — 모바일 접힘 */}
        <div className={`${filtersOpen ? 'block' : 'hidden'} md:block`}>
          <FilterBar
            search={{ value: q, onChange: (v) => setParam('q', v), placeholder: '자원명 검색' }}
            activeFilterCount={activeFilterCount}
            onReset={() => {
              const next = new URLSearchParams();
              if (resourceKey) next.set('resource', resourceKey);
              if (view === 'resources') next.set('view', 'resources');
              setParams(next, { replace: true });
            }}
          >
            <FilterSelect value={type} onChange={(v) => setParam('type', v)} placeholder="유형 전체"
              options={[{ value: 'EQUIPMENT', label: '장비' }, { value: 'PERSON', label: '인원' }]} />
            {showCompanyFilter && (
              <FilterSelect value={company} onChange={(v) => setParam('company', v)} placeholder="업체 전체"
                options={companyOptions.map((c) => ({ value: String(c.id), label: c.id === user?.company_id ? `${c.name} (우리 회사)` : c.name }))} />
            )}
            {(siteOptions.length > 0 || hasUnassigned) && (
              <FilterSelect value={site} onChange={(v) => setParam('site', v)} placeholder="현장 전체"
                options={[
                  ...siteOptions.map((s) => ({ value: String(s.id), label: s.name })),
                  ...(hasUnassigned ? [{ value: 'none', label: '현장 미배정' }] : []),
                ]} />
            )}
          </FilterBar>
        </div>

        {/* 단계 집계 칩 = 드릴다운(자원별 보기 전용 — 세트 보드는 자체 칩 사용) */}
        {view === 'resources' && (
          <div className="mt-2 flex gap-1.5 overflow-x-auto pb-1">
            {STAGE_FILTERS.map((k) => {
              const active = stage === k;
              const count = stageCounts[k];
              return (
                <button
                  key={k}
                  onClick={() => setParam('stage', active ? '' : k)}
                  className={`flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                    active
                      ? 'border-brand-500 bg-brand-600 text-white'
                      : count === 0
                        ? 'border-slate-200 bg-white text-slate-300'
                        : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300 hover:text-brand-700'}`}
                >
                  {STAGE_FILTER_LABEL[k]}
                  <span className={`rounded-full px-1.5 text-[10px] font-bold ${
                    active ? 'bg-white/20 text-white' : count === 0 ? 'bg-slate-100 text-slate-300' : 'bg-slate-100 text-slate-600'}`}>
                    {count}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <Legend />

      {loading ? (
        <p className="mt-3 text-sm text-slate-400">불러오는 중…</p>
      ) : items.length === 0 ? (
        <div className="mt-3">
          <EmptyState title="표시할 자원이 없습니다" text="장비·인력을 등록하면 파이프라인에 나타납니다." />
        </div>
      ) : view === 'sets' ? (
        <SetBoard
          items={items}
          operatorsByEquip={operatorsByEquip}
          itemsByKey={itemsByKey}
          q={q}
          type={type}
          site={site}
          company={company}
          showSupplier={showCompanyFilter}
          stage={sstage}
          onStageChange={(v) => setParam('sstage', v)}
          onSelectResource={(k) => setParam('resource', k)}
          onIssued={() => setReloadKey((k) => k + 1)}
        />
      ) : (
        <div className="mt-3">
          {listItems.length === 0 ? (
            <EmptyState
              title="조건에 맞는 자원이 없습니다"
              text="필터를 바꾸거나 초기화하세요."
              action={<button onClick={() => setParams(new URLSearchParams({ view: 'resources' }), { replace: true })} className="btn-ghost">필터 초기화</button>}
            />
          ) : (
            <div className="space-y-3">
              {listItems.map((it) => (
                <PipelineRow
                  key={itemKey(it)}
                  item={it}
                  operators={it.resource_type === 'EQUIPMENT' ? operatorsByEquip[it.resource_id] ?? [] : []}
                  itemsByKey={itemsByKey}
                  showSupplier={showCompanyFilter}
                  onSelect={() => setParam('resource', itemKey(it))}
                />
              ))}
            </div>
          )}
        </div>
      )}
    </AppShell>
  );
}

function Legend() {
  return (
    <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-flex h-3.5 w-3.5 items-center justify-center rounded-full bg-emerald-500" /> 완료
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-flex h-3.5 w-3.5 rounded-full border-2 border-brand-500 bg-white" /> 현재 단계
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-flex h-3.5 w-3.5 rounded-full border border-slate-300 bg-slate-100" /> 미달(사유)
      </span>
      <span className="inline-flex items-center gap-1.5">
        <span className="inline-flex h-3.5 w-3.5 rounded-full border border-dashed border-slate-300 bg-slate-50" /> 해당없음
      </span>
    </div>
  );
}

function PipelineRow({ item, operators, itemsByKey, showSupplier, onSelect }: {
  item: PipelineItem;
  operators: PipelineOperator[];
  itemsByKey: Map<string, PipelineItem>;
  showSupplier: boolean;
  onSelect: () => void;
}) {
  const cur = currentIndex(item.stages);
  const comboReady = item.resource_type === 'EQUIPMENT' ? comboReadyOf(item, operators, itemsByKey) : null;
  return (
    <button
      onClick={onSelect}
      className="card w-full p-4 text-left transition-colors hover:border-brand-300 hover:bg-brand-50/30"
    >
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-5">
        <div className="flex w-full items-center gap-2 lg:w-48 lg:shrink-0">
          <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
            item.resource_type === 'EQUIPMENT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
            {item.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
          </span>
          <div className="min-w-0">
            <div className="truncate font-medium text-slate-900">{item.label}</div>
            {(showSupplier || item.site_name) && (
              <div className="truncate text-[11px] text-slate-400">
                {showSupplier && item.supplier_name}
                {showSupplier && item.site_name && ' · '}
                {item.site_name && `현장 ${item.site_name}`}
              </div>
            )}
            {/* R1 조합(교대조) 조종원 칩 + 조합 준비 뱃지 — 장비 행에만. */}
            {operators.length > 0 && (
              <div className="mt-1 flex flex-wrap items-center gap-1">
                {comboReady != null && (
                  <span className={`rounded-full px-1.5 py-0.5 text-[10px] font-bold ${
                    comboReady ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                    {comboReady ? '조합 준비' : '조합 미준비'}
                  </span>
                )}
                {operators.map((op) => {
                  const row = itemsByKey.get(`PERSON:${op.person_id}`);
                  const state = row?.stages.readiness.state;
                  return (
                    <span key={op.person_id}
                      className={`rounded-full px-1.5 py-0.5 text-[10px] font-medium ${
                        state === 'DONE'
                          ? 'bg-emerald-50 text-emerald-700'
                          : state
                            ? 'bg-amber-50 text-amber-700'
                            : 'bg-slate-100 text-slate-500'}`}>
                      {op.person_name ?? `인원 #${op.person_id}`}
                    </span>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div className="flex flex-1 items-start">
          {STAGE_ORDER.map((s, i) => (
            <Fragment key={s.key}>
              {i > 0 && (
                <div className={`mt-3 h-0.5 flex-1 ${
                  item.stages[STAGE_ORDER[i - 1].key].state === 'DONE' ? 'bg-emerald-400' : 'bg-slate-200'}`} />
              )}
              <StageCell index={i + 1} label={s.label} stage={item.stages[s.key]} current={i === cur} />
            </Fragment>
          ))}
        </div>
      </div>
    </button>
  );
}

function StageCell({ index, label, stage, current }: { index: number; label: string; stage: Stage; current: boolean }) {
  const done = stage.state === 'DONE';
  const na = stage.state === 'NA';

  const circle = done
    ? 'bg-emerald-500 text-white border-emerald-500'
    : current
      ? 'bg-white text-brand-700 border-2 border-brand-500'
      : na
        ? 'bg-slate-50 text-slate-300 border border-dashed border-slate-300'
        : 'bg-slate-100 text-slate-400 border border-slate-200';

  const summaryColor = done
    ? 'text-emerald-600'
    : current
      ? 'text-brand-600 font-medium'
      : na
        ? 'text-slate-300'
        : 'text-slate-400';

  return (
    <div className="flex w-12 shrink-0 flex-col items-center text-center sm:w-16 lg:w-20">
      <div className={`inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold ${circle}`}>
        {done ? <IconCheck /> : na ? '–' : index}
      </div>
      <div className={`mt-1 text-[11px] font-medium ${current ? 'text-brand-700' : 'text-slate-600'}`}>{label}</div>
      <div className={`mt-0.5 text-[10px] leading-tight ${summaryColor}`} title={stage.summary}>{stage.summary}</div>
    </div>
  );
}

function IconCheck() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}
