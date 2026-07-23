import { Fragment, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { EmptyState, PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import IssueResourceCheckDialog from '../resourceCheck/IssueResourceCheckDialog';
import type { SiteResponse } from '../../types/site';
import type { ResourceCheckResponse } from '../../types/resourceCheck';
import type { FieldDeploymentResponse } from '../../types/fieldDeployment';
import type { DeployCheckResult } from '../readiness/DeployCheckCard';
import {
  type BoardPlan,
  type BpIndexes,
  type BpMember,
  type BpSet,
  type BpStageFilter,
  type ReceivedReview,
  type Stage,
  BP_STAGE_ORDER,
  BP_STAGE_FILTER_LABEL,
  buildIndexes,
  buildPlanSets,
  buildReviewSets,
  bpSetActionOf,
  currentBpIndex,
  currentBpStage,
  ownerKey,
  roleLabel,
} from './siteFlow';

const BP_STAGE_FILTERS: BpStageFilter[] = ['review', 'plan', 'inspection', 'ready', 'deployed', 'done'];

/** 검사 통보 다이얼로그 대상 — IssueResourceCheckDialog(기존) 프롭 그대로. */
type CheckTarget = {
  ownerType: 'EQUIPMENT' | 'PERSON';
  ownerId: number;
  ownerLabel: string;
  supplierCompanyId: number;
  supplierCompanyName?: string | null;
  initialTypes?: ResourceCheckResponse['check_type'][];
};

/**
 * BP 현장 보드 — 현장 카드 안에 세트(장비+조종원+유도원 등, 작업계획서 배치 기준)별
 * 심사 → 계획서 → 검사 → 투입 대기 → 투입 중 흐름과 다음 액션 1개.
 * 판정은 기존 BP 수신/발행 API 4종 합성(siteFlow.ts) — 신규 게이트·저장 없음.
 */
export default function SiteFlowPage() {
  const [params, setParams] = useSearchParams();
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [plans, setPlans] = useState<BoardPlan[]>([]);
  const [reviews, setReviews] = useState<ReceivedReview[]>([]);
  const [checks, setChecks] = useState<ResourceCheckResponse[]>([]);
  const [deployments, setDeployments] = useState<FieldDeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [checkTarget, setCheckTarget] = useState<CheckTarget | null>(null);
  // 투입 준비 뱃지 — 요청 대기 세트 head 의 deploy-check(기존 판정 그대로). key = set.key.
  const [deployCheckBySet, setDeployCheckBySet] = useState<Map<string, DeployCheckResult>>(new Map());
  // 검사 게이트 보강 — '검사 미통보' 세트 구성원의 deploy-check CHECK 게이트(공급사 발행·승인분 포함). key = ownerKey.
  const [checkGates, setCheckGates] = useState<Map<string, boolean>>(new Map());

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      api.get<SiteResponse[]>('/api/sites').catch(() => ({ data: [] as SiteResponse[] })),
      api.get<BoardPlan[]>('/api/work-plans/board').catch(() => ({ data: [] as BoardPlan[] })),
      api.get<ReceivedReview[]>('/api/document-reviews/received').catch(() => ({ data: [] as ReceivedReview[] })),
      api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list').catch(() => ({ data: [] as ResourceCheckResponse[] })),
      api.get<FieldDeploymentResponse[]>('/api/field-deployments/bp').catch(() => ({ data: [] as FieldDeploymentResponse[] })),
    ]).then(([s, p, r, c, d]) => {
      if (cancelled) return;
      setSites(s.data ?? []);
      setPlans(p.data ?? []);
      setReviews(r.data ?? []);
      setChecks(c.data ?? []);
      setDeployments(d.data ?? []);
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [reloadKey]);

  const baseIdx: BpIndexes = useMemo(
    () => buildIndexes(reviews, checks, deployments),
    [reviews, checks, deployments],
  );
  // 검사 게이트(deploy-check) 보강본 — 게이트 로드 전엔 baseIdx 그대로(순수 BP 발행분 판정).
  const idx: BpIndexes = useMemo(
    () => ({ ...baseIdx, checkGateByOwner: checkGates }),
    [baseIdx, checkGates],
  );

  const plannedOwnerKeys = useMemo(() => {
    const planned = new Set<string>();
    for (const p of plans) {
      p.equipment.forEach((e) => planned.add(ownerKey('EQUIPMENT', e.equipment_id)));
      p.persons.forEach((x) => planned.add(ownerKey('PERSON', x.person_id)));
    }
    return planned;
  }, [plans]);

  // 계획서 세트 + 심사만 수신(계획서 없음) 세트 합성 — 전부 클라이언트 조합.
  const planSets = useMemo(() => plans.flatMap((p) => buildPlanSets(p, idx)), [plans, idx]);
  const reviewSets = useMemo(
    () => buildReviewSets(reviews, plannedOwnerKeys, idx),
    [reviews, plannedOwnerKeys, idx],
  );

  // '검사 미통보' 세트 구성원만 deploy-check 조회 — CHECK 게이트(반입검사·검진·교육 승인) 통과 여부.
  // V122: 공급사가 발행·승인한 검사는 bp-list 에 없어 미통보로 오표시 — 자원 스코프 403/404 는 생략(보강 안 함).
  useEffect(() => {
    const baseSets = [
      ...plans.flatMap((p) => buildPlanSets(p, baseIdx)),
      ...buildReviewSets(reviews, plannedOwnerKeys, baseIdx),
    ];
    const targets = new Map<string, { path: 'equipment' | 'person'; id: number }>();
    for (const s of baseSets) {
      if (s.stages.inspection.summary !== '검사 미통보') continue;
      for (const m of s.members) {
        targets.set(ownerKey(m.owner_type, m.owner_id), {
          path: m.owner_type === 'EQUIPMENT' ? 'equipment' : 'person',
          id: m.owner_id,
        });
      }
    }
    if (targets.size === 0) { setCheckGates(new Map()); return; }
    let cancelled = false;
    void Promise.all(Array.from(targets.entries()).map(([key, t]) =>
      api.get<DeployCheckResult>(`/api/resources/${t.path}/${t.id}/deploy-check`)
        .then((res) => [key, !res.data.blocks.some((b) => b.kind === 'CHECK')] as const)
        .catch(() => null),
    )).then((results) => {
      if (cancelled) return;
      const map = new Map<string, boolean>();
      for (const row of results) if (row) map.set(row[0], row[1]);
      setCheckGates(map);
    });
    return () => { cancelled = true; };
  }, [plans, reviews, plannedOwnerKeys, baseIdx]);

  // URL 쿼리 = 필터 상태(공유 가능한 링크) — 자원 현황(공급사) 보드와 동일 패턴.
  const q = params.get('q') ?? '';
  const site = params.get('site') ?? '';   // '' | '<id>' | 'none'
  const stage = (params.get('stage') ?? '') as '' | BpStageFilter;
  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
  };

  // 검색·현장 필터(단계 칩 분모) — 검색은 현장명 + 세트 구성원 이름까지.
  const qLower = q.trim().toLowerCase();
  const matches = (set: BpSet) => {
    if (site === 'none') { if (set.siteId != null) return false; }
    else if (site && set.siteId !== Number(site)) return false;
    if (qLower) {
      const hay = [set.siteName ?? '', set.planTitle ?? '', ...set.members.map((m) => m.label)]
        .join(' ').toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  };
  const basePlanSets = planSets.filter(matches);
  const baseReviewSets = site ? [] : reviewSets.filter(matches); // 심사 세트는 현장 미정 — 현장 필터 시 제외

  const stageCounts = useMemo(() => {
    const c: Record<BpStageFilter, number> = { review: 0, plan: 0, inspection: 0, ready: 0, deployed: 0, done: 0 };
    [...basePlanSets, ...baseReviewSets].forEach((s) => { c[currentBpStage(s.stages)]++; });
    return c;
  }, [basePlanSets, baseReviewSets]);

  const listPlanSets = stage ? basePlanSets.filter((s) => currentBpStage(s.stages) === stage) : basePlanSets;
  const listReviewSets = stage ? baseReviewSets.filter((s) => currentBpStage(s.stages) === stage) : baseReviewSets;

  // 현장 카드 구성 — BP 자기 현장 전부(세트 0건 현장도 흐름 시작점으로 노출). 현장 미지정 계획서는 별도 카드.
  const siteCards = useMemo(() => {
    const bySite = new Map<number | null, BpSet[]>();
    for (const s of listPlanSets) {
      const key = s.siteId ?? null;
      const list = bySite.get(key);
      if (list) list.push(s); else bySite.set(key, [s]);
    }
    const cards: Array<{ siteId: number | null; siteName: string; sets: BpSet[] }> = [];
    for (const s of sites) {
      if (site === 'none') continue;
      if (site && String(s.id) !== site) continue;
      if (qLower || stage) {
        // 검색/단계 필터 중엔 매칭 세트 있는 현장만.
        const sets = bySite.get(s.id) ?? [];
        if (sets.length === 0) continue;
        cards.push({ siteId: s.id, siteName: s.name, sets });
      } else {
        cards.push({ siteId: s.id, siteName: s.name, sets: bySite.get(s.id) ?? [] });
      }
      bySite.delete(s.id);
    }
    // /api/sites 에 없는 현장(스코프 밖)·현장 미지정 계획서 잔여분.
    for (const [siteId, sets] of bySite) {
      cards.push({ siteId, siteName: sets[0].siteName ?? '현장 미지정', sets });
    }
    return cards;
  }, [sites, listPlanSets, site, qLower, stage]);

  // 투입 준비 뱃지 — '공급사 요청 대기' 세트 head 만 deploy-check(N 소수). 404/403 은 생략.
  useEffect(() => {
    const targets = [...planSets, ...reviewSets].filter((s) =>
      currentBpStage(s.stages) === 'ready' && !s.stages.ready.summary.includes('수락 필요'));
    if (targets.length === 0) { setDeployCheckBySet(new Map()); return; }
    let cancelled = false;
    void Promise.all(targets.map((s) => {
      const path = s.head.owner_type === 'EQUIPMENT' ? 'equipment' : 'person';
      return api.get<DeployCheckResult>(`/api/resources/${path}/${s.head.owner_id}/deploy-check`,
        { params: s.siteId != null ? { siteId: s.siteId } : {} })
        .then((res) => [s.key, res.data] as const)
        .catch(() => null);
    })).then((results) => {
      if (cancelled) return;
      const map = new Map<string, DeployCheckResult>();
      for (const row of results) if (row) map.set(row[0], row[1]);
      setDeployCheckBySet(map);
    });
    return () => { cancelled = true; };
  }, [planSets, reviewSets]);

  const siteOptions = useMemo(
    () => sites.map((s) => ({ value: String(s.id), label: s.name })),
    [sites],
  );
  const hasUnassigned = useMemo(
    () => planSets.some((s) => s.siteId == null),
    [planSets],
  );
  const activeFilterCount = [q, site, stage].filter(Boolean).length;

  const openCheck = (target: BpMember, initialTypes?: CheckTarget['initialTypes']) => {
    if (target.supplier_company_id == null) return;
    setCheckTarget({
      ownerType: target.owner_type,
      ownerId: target.owner_id,
      ownerLabel: target.label,
      supplierCompanyId: target.supplier_company_id,
      supplierCompanyName: target.supplier_company_name,
      initialTypes,
    });
  };

  return (
    <AppShell breadcrumb={[{ label: '현장 보드' }]}>
      {/* 상단 고정 필터바 — 자원 현황 보드와 동일 패턴 */}
      <div className="sticky top-[68px] z-20 bg-slate-50 pb-3 pt-1">
        <PageHeader
          title="현장 보드"
          subtitle="현장별 세트(장비+조종원·유도원)의 심사 → 계획서 → 검사 → 투입 대기 → 투입 중 흐름. 카드의 버튼으로 다음 할 일로 이동합니다."
        />

        <FilterBar
          search={{ value: q, onChange: (v) => setParam('q', v), placeholder: '현장·장비·인원 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={() => setParams(new URLSearchParams(), { replace: true })}
        >
          {(siteOptions.length > 0 || hasUnassigned) && (
            <FilterSelect value={site} onChange={(v) => setParam('site', v)} placeholder="현장 전체"
              options={[
                ...siteOptions,
                ...(hasUnassigned ? [{ value: 'none', label: '현장 미지정' }] : []),
              ]} />
          )}
        </FilterBar>

        {/* 단계 집계 칩 = 드릴다운(클릭 시 그 단계만, 다시 클릭 해제) */}
        <div className="mt-2 flex gap-1.5 overflow-x-auto pb-1">
          {BP_STAGE_FILTERS.map((k) => {
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
                {BP_STAGE_FILTER_LABEL[k]}
                <span className={`rounded-full px-1.5 text-[10px] font-bold ${
                  active ? 'bg-white/20 text-white' : count === 0 ? 'bg-slate-100 text-slate-300' : 'bg-slate-100 text-slate-600'}`}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {loading ? (
        <p className="mt-3 text-sm text-slate-400">불러오는 중…</p>
      ) : siteCards.length === 0 && listReviewSets.length === 0 ? (
        <div className="mt-3">
          <EmptyState
            title={sites.length === 0 ? '등록된 현장이 없습니다' : '조건에 맞는 세트가 없습니다'}
            text={sites.length === 0 ? '현장 관리에서 현장을 생성하면 여기에서 흐름을 관리할 수 있습니다.' : '필터를 바꾸거나 초기화하세요.'}
            action={sites.length === 0
              ? <Link to="/sites" className="btn-primary text-sm">현장 관리로</Link>
              : <button onClick={() => setParams(new URLSearchParams(), { replace: true })} className="btn-ghost">필터 초기화</button>}
          />
        </div>
      ) : (
        <div className="mt-3 space-y-4">
          {/* 현장 카드 — BP 자기 현장 + 그 안의 세트들 */}
          {siteCards.map((card) => (
            <section key={card.siteId ?? 'none'} className="card p-4">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 pb-2.5">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="rounded bg-slate-900 px-1.5 py-0.5 text-[10px] font-bold text-white">현장</span>
                  <h2 className="truncate text-[15px] font-bold text-slate-900">{card.siteName}</h2>
                  <span className="text-xs text-slate-400">세트 {card.sets.length}</span>
                </div>
                {card.siteId != null && (
                  <Link to={`/sites/${card.siteId}`} className="text-xs font-semibold text-brand-700 hover:underline">
                    현장 상세 →
                  </Link>
                )}
              </div>
              {card.sets.length === 0 ? (
                <p className="pt-3 text-sm text-slate-400">
                  배치된 세트가 없습니다 — 심사 수신 후 작업계획서를 작성하면 여기에 나타납니다.
                </p>
              ) : (
                <div className="divide-y divide-slate-100">
                  {card.sets.map((set) => (
                    <SetRow key={set.key} set={set} idx={idx}
                      deployCheck={deployCheckBySet.get(set.key)} onCheck={openCheck} />
                  ))}
                </div>
              )}
            </section>
          ))}

          {/* 심사만 수신(계획서 없음) — 현장 미정 세트 */}
          {listReviewSets.length > 0 && (
            <section className="card border-amber-200 bg-amber-50/30 p-4">
              <div className="flex items-center gap-2 border-b border-amber-100 pb-2.5">
                <span className="rounded bg-amber-600 px-1.5 py-0.5 text-[10px] font-bold text-white">심사 수신</span>
                <h2 className="text-[15px] font-bold text-slate-900">계획서 작성 대기</h2>
                <span className="text-xs text-slate-500">— 심사로 받은 자원 중 아직 계획서에 배치 안 됨</span>
              </div>
              <div className="divide-y divide-amber-100">
                {listReviewSets.map((set) => (
                  <SetRow key={set.key} set={set} idx={idx}
                    deployCheck={deployCheckBySet.get(set.key)} onCheck={openCheck} />
                ))}
              </div>
            </section>
          )}
        </div>
      )}

      {/* 검사 통보 — 기존 다이얼로그 재사용(받은 심사·보낸 점검 요청 화면과 동일 경로) */}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={() => setReloadKey((k) => k + 1)}
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

/** 세트 1행 — 구성(장비·역할별 인원) + 5단계 트랙 + 현재 단계 요약 + 다음 액션 1개. */
function SetRow({ set, idx, deployCheck, onCheck }: {
  set: BpSet;
  idx: BpIndexes;
  deployCheck?: DeployCheckResult;
  onCheck: (target: BpMember, initialTypes?: ResourceCheckResponse['check_type'][]) => void;
}) {
  const { head, members, stages } = set;
  const cur = currentBpIndex(stages);
  const crew = members.filter((m) => m !== head);
  const isEquipSet = head.owner_type === 'EQUIPMENT';
  const action = bpSetActionOf(set, idx);

  const curLabel = cur >= 0 ? BP_STAGE_ORDER[cur].label : null;
  const curSummary = cur >= 0 ? stages[BP_STAGE_ORDER[cur].key].summary : null;

  // 투입 준비 뱃지 — deploy-check(기존 4게이트 판정 그대로) 결과 요약.
  const readiness = deployCheck == null ? null : deployCheck.ready
    ? { label: '투입 준비됨', cls: 'bg-emerald-100 text-emerald-800', title: undefined as string | undefined }
    : {
        label: `투입 준비까지 ${deployCheck.blocks.length}건`,
        cls: 'bg-slate-100 text-slate-600',
        title: deployCheck.blocks.map((b) => b.label).join('\n'),
      };

  return (
    <div className="py-3 first:pt-3">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-5">
        {/* 세트 구성 — 장비 + 역할별 인원(조종원·유도원 등) */}
        <div className="w-full lg:w-60 lg:shrink-0">
          <div className="flex items-center gap-2">
            <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
              isEquipSet
                ? crew.length > 0 ? 'bg-indigo-100 text-indigo-700' : 'bg-blue-100 text-blue-700'
                : 'bg-emerald-100 text-emerald-700'}`}>
              {isEquipSet ? (crew.length > 0 ? '세트' : '장비') : '인원'}
            </span>
            <span className="truncate font-medium text-slate-900">
              {isEquipSet ? head.label : set.kind === 'plan' ? '현장 인원' : head.label}
            </span>
          </div>
          {(isEquipSet ? crew : members).length > 0 && (
            <div className="mt-0.5 flex flex-wrap gap-x-2 gap-y-0.5 text-xs text-slate-500">
              {(isEquipSet ? crew : members).map((m) => (
                <span key={ownerKey(m.owner_type, m.owner_id)} className="whitespace-nowrap">
                  {m.owner_type === 'PERSON' && roleLabel(m.role) ? `${roleLabel(m.role)} ` : ''}{m.label}
                </span>
              ))}
            </div>
          )}
          <div className="mt-0.5 truncate text-[11px] text-slate-400">
            {set.planTitle ? `계획서 ${set.planTitle}` : set.review ? `심사 · ${set.review.supplier_company_name ?? `공급사 #${set.review.supplier_company_id}`}` : null}
          </div>
        </div>

        {/* 단계 트랙 — 심사 → 계획서 → 검사 → 투입 대기 → 투입 중 */}
        <div className="flex flex-1 items-start">
          {BP_STAGE_ORDER.map((s, i) => (
            <Fragment key={s.key}>
              {i > 0 && (
                <div className={`mt-3 h-0.5 flex-1 ${
                  stages[BP_STAGE_ORDER[i - 1].key].state === 'DONE' ? 'bg-emerald-400' : 'bg-slate-200'}`} />
              )}
              <BpStageCell index={i + 1} label={s.label} stage={stages[s.key]} current={i === cur} />
            </Fragment>
          ))}
        </div>
      </div>

      {/* 상태 요약 + 다음 액션 */}
      <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
        <div className="flex min-w-0 flex-wrap items-center gap-2 text-xs">
          {curLabel ? (
            <span className="text-slate-600"><b className="text-brand-700">{curLabel}</b> — {curSummary}</span>
          ) : (
            <span className="font-medium text-emerald-600">전 단계 완료</span>
          )}
          {readiness && (
            <span title={readiness.title} className={`rounded-full px-1.5 py-0.5 text-[10px] font-semibold ${readiness.cls}`}>
              {readiness.label}
            </span>
          )}
        </div>
        {action && (
          action.kind === 'link' ? (
            <Link to={action.to} className="btn-primary px-3 py-1.5 text-xs">{action.label}</Link>
          ) : action.kind === 'newtab' ? (
            <a href={action.to} target="_blank" rel="noopener" className="btn-primary px-3 py-1.5 text-xs">
              {action.label}
            </a>
          ) : (
            <button onClick={() => onCheck(action.target, action.initialTypes)} className="btn-primary px-3 py-1.5 text-xs">
              {action.label}
            </button>
          )
        )}
      </div>
    </div>
  );
}

function BpStageCell({ index, label, stage, current }: { index: number; label: string; stage: Stage; current: boolean }) {
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
