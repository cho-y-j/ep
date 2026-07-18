import { Fragment, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { EmptyState } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import PipelineDetail from './PipelineDetail';
import {
  type PipelineItem,
  type Stage,
  type StageFilter,
  STAGE_ORDER,
  STAGE_FILTER_LABEL,
  currentIndex,
  currentStage,
  itemKey,
} from './pipeline';

const STAGE_FILTERS: StageFilter[] = ['docs', 'inspection', 'readiness', 'deployed', 'work', 'settlement', 'done'];

/**
 * 자원 파이프라인 — 목록(필터·단계 드릴다운) + 개별 자원 상세(1대 뷰) 전환.
 * 필터/선택 상태는 URL 쿼리에 동기화되어 공유 가능. GET /api/resources/pipeline 집계 재사용.
 */
export default function ResourcePipelinePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [items, setItems] = useState<PipelineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [filtersOpen, setFiltersOpen] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api.get<PipelineItem[]>('/api/resources/pipeline')
      .then((r) => { if (!cancelled) setItems(r.data ?? []); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  // URL 쿼리 = 필터/선택 상태(공유 가능한 링크).
  const site = params.get('site') ?? '';        // '' | '<id>' | 'none'
  const company = params.get('company') ?? '';  // '' | '<id>'
  const type = params.get('type') ?? '';        // '' | 'EQUIPMENT' | 'PERSON'
  const stage = (params.get('stage') ?? '') as '' | StageFilter;
  const resourceKey = params.get('resource') ?? '';

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

  // site/company/type 로 좁힌 기본 집합 — 단계 칩 집계의 분모(단계 필터엔 영향 안 받게).
  const baseItems = useMemo(() => items.filter((it) => {
    if (type && it.resource_type !== type) return false;
    if (company && it.supplier_company_id !== Number(company)) return false;
    if (site === 'none') { if (it.site_id != null) return false; }
    else if (site && it.site_id !== Number(site)) return false;
    return true;
  }), [items, type, company, site]);

  const stageCounts = useMemo(() => {
    const c: Record<StageFilter, number> = { docs: 0, inspection: 0, readiness: 0, deployed: 0, work: 0, settlement: 0, done: 0 };
    baseItems.forEach((it) => { c[currentStage(it.stages)]++; });
    return c;
  }, [baseItems]);

  const listItems = stage ? baseItems.filter((it) => currentStage(it.stages) === stage) : baseItems;

  const selected = resourceKey ? items.find((it) => itemKey(it) === resourceKey) ?? null : null;
  const activeFilterCount = [site, company, type].filter(Boolean).length + (stage ? 1 : 0);

  // 개별 자원 선택 시 = 1대 뷰(상세 패널).
  if (selected) {
    return (
      <AppShell breadcrumb={[{ label: '자원 파이프라인', to: '/resource-pipeline' }, { label: selected.label }]}>
        <PipelineDetail item={selected} onBack={() => setParam('resource', '')} />
      </AppShell>
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '자원 파이프라인' }]}>
      {/* 상단 고정 필터바 */}
      <div className="sticky top-[68px] z-20 bg-slate-50 pb-3 pt-1">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0">
            <h1 className="text-xl font-bold text-slate-900">
              자원 파이프라인
              <span className="ml-2 rounded-full bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-600 align-middle">
                {listItems.length}
                {listItems.length !== items.length && <span className="text-slate-400"> / {items.length}</span>}
              </span>
            </h1>
            <p className="mt-0.5 hidden text-sm text-slate-500 sm:block">
              장비·인력의 서류 → 검사 → 투입대기 → 투입 → 작업 → 정산 진행 상태. 단계를 눌러 그 단계만, 자원을 눌러 상세를 봅니다.
            </p>
          </div>
          <div className="flex items-center gap-2">
            {activeFilterCount > 0 && (
              <button onClick={() => { setParams(resourceKey ? new URLSearchParams({ resource: resourceKey }) : new URLSearchParams(), { replace: true }); }}
                      className="text-xs font-medium text-slate-500 hover:text-slate-800">필터 초기화</button>
            )}
            <button onClick={() => setFiltersOpen((o) => !o)}
                    className="btn-ghost md:hidden">
              필터{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
            </button>
          </div>
        </div>

        {/* 필터 컨트롤 — 모바일 접힘 */}
        <div className={`${filtersOpen ? 'flex' : 'hidden'} mt-2 flex-wrap items-center gap-2 md:flex`}>
          <ResourcePicker items={items} onPick={(it) => setParam('resource', itemKey(it))} />
          <select value={type} onChange={(e) => setParam('type', e.target.value)} className="input w-auto">
            <option value="">유형 전체</option>
            <option value="EQUIPMENT">장비</option>
            <option value="PERSON">인원</option>
          </select>
          {showCompanyFilter && (
            <select value={company} onChange={(e) => setParam('company', e.target.value)} className="input w-auto">
              <option value="">업체 전체</option>
              {companyOptions.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.id === user?.company_id ? `${c.name} (우리 회사)` : c.name}
                </option>
              ))}
            </select>
          )}
          {(siteOptions.length > 0 || hasUnassigned) && (
            <select value={site} onChange={(e) => setParam('site', e.target.value)} className="input w-auto">
              <option value="">현장 전체</option>
              {siteOptions.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              {hasUnassigned && <option value="none">현장 미배정</option>}
            </select>
          )}
        </div>

        {/* 단계 집계 칩 = 드릴다운(클릭 시 그 단계만, 다시 클릭 해제) */}
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
      </div>

      <Legend />

      <div className="mt-3">
        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : items.length === 0 ? (
          <EmptyState title="표시할 자원이 없습니다" text="장비·인력을 등록하면 파이프라인에 나타납니다." />
        ) : listItems.length === 0 ? (
          <EmptyState
            title="조건에 맞는 자원이 없습니다"
            text="필터를 바꾸거나 초기화하세요."
            action={<button onClick={() => setParams(new URLSearchParams(), { replace: true })} className="btn-ghost">필터 초기화</button>}
          />
        ) : (
          <div className="space-y-3">
            {listItems.map((it) => (
              <PipelineRow
                key={itemKey(it)}
                item={it}
                showSupplier={showCompanyFilter}
                onSelect={() => setParam('resource', itemKey(it))}
              />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}

/** 개별 자원 검색 드롭다운 — 입력으로 좁혀 선택하면 그 자원의 1대 뷰로. */
function ResourcePicker({ items, onPick }: { items: PipelineItem[]; onPick: (it: PipelineItem) => void }) {
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const needle = q.trim().toLowerCase();
  const matches = (needle ? items.filter((it) => it.label.toLowerCase().includes(needle)) : items).slice(0, 10);

  return (
    <div className="relative">
      <input
        value={q}
        onChange={(e) => { setQ(e.target.value); setOpen(true); }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder="개별 자원 검색·선택…"
        className="input w-full sm:w-56"
      />
      {open && matches.length > 0 && (
        <ul className="absolute z-30 mt-1 max-h-64 w-full min-w-[220px] overflow-auto rounded-md border border-slate-200 bg-white shadow-lg">
          {matches.map((it) => (
            <li key={itemKey(it)}>
              <button
                type="button"
                onMouseDown={(e) => { e.preventDefault(); onPick(it); setQ(''); setOpen(false); }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-slate-50"
              >
                <span className={`rounded px-1 py-0.5 text-[9px] font-semibold ${
                  it.resource_type === 'EQUIPMENT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
                  {it.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
                </span>
                <span className="truncate">{it.label}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
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

function PipelineRow({ item, showSupplier, onSelect }: { item: PipelineItem; showSupplier: boolean; onSelect: () => void }) {
  const cur = currentIndex(item.stages);
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
