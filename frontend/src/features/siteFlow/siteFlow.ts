// BP 현장 보드 공용 타입·판정 헬퍼 (JSX 없음). 화면 컴포넌트가 공유한다.
// 판정은 BP 가 이미 받는/발행한 기존 API 응답 합성만 — 신규 게이트·저장 없음.
// (공급사 세트 보드 pipeline.ts 의 buildSet 패턴을 BP 스코프로 재구성.)

import type { WorkPlanStatus } from '../../types/workPlan';
import type { ResourceCheckResponse, ResourceCheckType } from '../../types/resourceCheck';
import type { FieldDeploymentResponse } from '../../types/fieldDeployment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

export type StageState = 'DONE' | 'PENDING' | 'NA';
export type Stage = { state: StageState; summary: string };

/** BP 하루 흐름 단계 — 심사 → 계획서 → 검사 → 투입 대기 → 투입 중. */
export type BpStageKey = 'review' | 'plan' | 'inspection' | 'ready' | 'deployed';

export const BP_STAGE_ORDER: { key: BpStageKey; label: string }[] = [
  { key: 'review', label: '심사' },
  { key: 'plan', label: '계획서' },
  { key: 'inspection', label: '검사' },
  { key: 'ready', label: '투입 대기' },
  { key: 'deployed', label: '투입 중' },
];

export type BpStageFilter = BpStageKey | 'done';

export const BP_STAGE_FILTER_LABEL: Record<BpStageFilter, string> = {
  review: '심사',
  plan: '계획서',
  inspection: '검사',
  ready: '투입 대기',
  deployed: '투입 중',
  done: '완료',
};

// ── 입력 행 타입 (기존 API 응답 그대로, snake_case) ──

/** GET /api/work-plans/board 응답 1건. */
export type BoardPlan = {
  id: number;
  site_id?: number | null;
  site_name?: string | null;
  status: WorkPlanStatus;
  work_date: string;
  title: string;
  equipment: Array<{
    equipment_id: number;
    label: string;
    supplier_company_id: number;
    supplier_company_name?: string | null;
  }>;
  persons: Array<{
    person_id: number;
    name: string;
    role?: string | null;
    equipment_id?: number | null;
    supplier_company_id: number;
    supplier_company_name?: string | null;
  }>;
};

/** GET /api/document-reviews/received 응답 중 보드가 쓰는 필드만. */
export type ReceivedReview = {
  id: number;
  supplier_company_id: number;
  supplier_company_name: string | null;
  supplier_company_type: 'BP' | 'EQUIPMENT' | 'MANPOWER' | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejected_reason: string | null;
  items: Array<{ owner_type: 'EQUIPMENT' | 'PERSON'; owner_id: number; label: string }>;
};

/** 자원 식별 키 — 심사/검사/투입 인덱스 공용. */
export function ownerKey(type: 'EQUIPMENT' | 'PERSON', id: number): string {
  return `${type}:${id}`;
}

/** 계획서 배치 role(한글 serverRole 또는 enum명 혼재) → 표시 라벨. */
export function roleLabel(role: string | null | undefined): string | null {
  if (!role) return null;
  return PERSON_ROLE_LABEL[role as PersonRole] ?? role;
}

// ── 인덱스 (자원별 최신 상태 lookup) ──

export type BpIndexes = {
  /** ownerKey → 그 자원이 담긴 최신 심사 봉투. */
  reviewByOwner: Map<string, ReceivedReview>;
  /** ownerKey → 종류별 최신 점검 행 목록 (같은 종류 재발행은 최신 건만). */
  checksByOwner: Map<string, ResourceCheckResponse[]>;
  /** ownerKey → 최신 투입 요청 행. */
  deployByOwner: Map<string, FieldDeploymentResponse>;
  /**
   * ownerKey → deploy-check(4게이트)의 CHECK 게이트 통과 여부(true = 반입검사·검진·교육 전부 APPROVED).
   * V122 이후 공급사도 검사를 발행·승인하는데 그 행은 BP 발행 목록(bp-list)에 없어 "미통보"로 오표시 —
   * 자원 스코프를 지키는 deploy-check(BP 는 자기 현장 참여 자원만 200, 그 외 403)로 보강한다. 페이지가 채움.
   */
  checkGateByOwner?: Map<string, boolean>;
};

export function buildIndexes(
  reviews: ReceivedReview[],
  checks: ResourceCheckResponse[],
  deployments: FieldDeploymentResponse[],
): BpIndexes {
  const reviewByOwner = new Map<string, ReceivedReview>();
  for (const r of reviews) {
    for (const it of r.items) {
      const k = ownerKey(it.owner_type, it.owner_id);
      const cur = reviewByOwner.get(k);
      if (!cur || r.id > cur.id) reviewByOwner.set(k, r);
    }
  }
  // (owner, check_type) 별 최신 행만 남긴다 — 재검사 통보로 재발행된 과거 반려 건이 영구 미완처리되지 않게.
  const latestByOwnerType = new Map<string, ResourceCheckResponse>();
  for (const c of checks) {
    const k = `${ownerKey(c.owner_type, c.owner_id)}|${c.check_type}`;
    const cur = latestByOwnerType.get(k);
    if (!cur || c.id > cur.id) latestByOwnerType.set(k, c);
  }
  const checksByOwner = new Map<string, ResourceCheckResponse[]>();
  for (const c of latestByOwnerType.values()) {
    const k = ownerKey(c.owner_type, c.owner_id);
    const list = checksByOwner.get(k);
    if (list) list.push(c); else checksByOwner.set(k, [c]);
  }
  const deployByOwner = new Map<string, FieldDeploymentResponse>();
  for (const d of deployments) {
    const k = ownerKey(d.resource_type, d.resource_id);
    const cur = deployByOwner.get(k);
    if (!cur || d.id > cur.id) deployByOwner.set(k, d);
  }
  return { reviewByOwner, checksByOwner, deployByOwner };
}

// ── 세트(현장 카드 안의 1행) ──

export type BpMember = {
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  label: string;
  role?: string | null; // PERSON 만 — 계획서 배치 역할(조종원/유도원 등)
  supplier_company_id?: number | null;
  supplier_company_name?: string | null;
};

export type BpSet = {
  key: string;                          // React key + URL 안정 키
  kind: 'plan' | 'review';              // plan = 계획서 배치 기준 / review = 심사만 수신(계획서 없음)
  siteId: number | null;
  siteName: string | null;
  planId: number | null;
  planStatus: WorkPlanStatus | null;
  planTitle: string | null;
  review: ReceivedReview | null;        // 이 세트가 참조하는 최신 심사 봉투(액션·프리필용)
  head: BpMember;                       // 장비 우선(없으면 첫 인원)
  members: BpMember[];                  // head 포함 전체 구성원
  stages: Record<BpStageKey, Stage>;
};

/** 첫 미완료(PENDING) 단계 index. 없으면 -1(전부 완료/해당없음). */
export function currentBpIndex(stages: Record<BpStageKey, Stage>): number {
  return BP_STAGE_ORDER.findIndex((s) => stages[s.key].state === 'PENDING');
}

/** 이 세트의 현재 단계(필터 버킷). 전부 완료면 'done'. */
export function currentBpStage(stages: Record<BpStageKey, Stage>): BpStageFilter {
  const i = currentBpIndex(stages);
  return i < 0 ? 'done' : BP_STAGE_ORDER[i].key;
}

// ── 단계 판정 (전부 기존 응답 재조합) ──

/** 심사 — 세트 구성원이 담긴 최신 수신 봉투 상태. 미수신인데 계획서가 이미 있으면 구두 진행으로 보고 해당없음. */
function reviewStage(review: ReceivedReview | null, hasPlan: boolean): Stage {
  if (!review) {
    return hasPlan
      ? { state: 'NA', summary: '심사 없음(구두)' }
      : { state: 'PENDING', summary: '심사 미수신' };
  }
  switch (review.status) {
    case 'APPROVED': return { state: 'DONE', summary: '심사 승인' };
    case 'REJECTED': return { state: 'PENDING', summary: `반려 — ${review.rejected_reason ?? '재발송 대기'}` };
    default: return { state: 'PENDING', summary: '심사 대기 — 확인 필요' };
  }
}

/** 계획서 — WorkPlan 상태 그대로. null = 심사만 있고 계획서 미작성. */
function planStage(status: WorkPlanStatus | null): Stage {
  switch (status) {
    case null: return { state: 'PENDING', summary: '계획서 없음' };
    case 'DRAFT': return { state: 'PENDING', summary: '작성중' };
    case 'SUBMITTED': return { state: 'PENDING', summary: '서명 진행중' };
    case 'APPROVED': return { state: 'DONE', summary: '서명 완료' };
    case 'IN_PROGRESS': return { state: 'DONE', summary: '작업 진행중' };
    case 'DONE': return { state: 'DONE', summary: '작업 완료' };
    default: return { state: 'PENDING', summary: status ?? '-' };
  }
}

/** 검사 — BP 가 발행한 점검(자기 발행분, 종류별 최신 건) 합성. */
function inspectionStage(members: BpMember[], idx: BpIndexes): Stage {
  const rows = members.flatMap((m) => idx.checksByOwner.get(ownerKey(m.owner_type, m.owner_id)) ?? []);
  if (rows.length === 0) {
    // BP 발행분이 없어도 공급사 발행·승인으로 CHECK 게이트가 전 구성원 통과면 완료(발행 주체 무관 실판정).
    const gates = idx.checkGateByOwner;
    if (gates && members.length > 0
        && members.every((m) => gates.get(ownerKey(m.owner_type, m.owner_id)) === true)) {
      return { state: 'DONE', summary: '검사 완료' };
    }
    return { state: 'PENDING', summary: '검사 미통보' };
  }
  if (rows.some((r) => r.status === 'SUBMITTED')) return { state: 'PENDING', summary: '회신 검토 필요' };
  if (rows.some((r) => r.status === 'REQUESTED')) return { state: 'PENDING', summary: '검사 회신 대기' };
  if (rows.some((r) => r.status === 'REJECTED')) return { state: 'PENDING', summary: '점검 반려 — 재통보 필요' };
  return { state: 'DONE', summary: '검사 완료' };
}

/** 투입 대기 — 수신 투입 요청(REQUESTED=수락 필요). 수락하면 즉시 ACTIVE 라 수락 완료=ACTIVE 이후. */
function readyStage(members: BpMember[], idx: BpIndexes): Stage {
  const latest = members
    .map((m) => idx.deployByOwner.get(ownerKey(m.owner_type, m.owner_id)))
    .filter((d): d is FieldDeploymentResponse => !!d);
  const requested = latest.filter((d) => d.status === 'REQUESTED').length;
  if (requested > 0) return { state: 'PENDING', summary: `투입 요청 ${requested}건 — 수락 필요` };
  if (latest.some((d) => d.status === 'ACTIVE' || d.status === 'COMPLETED')) {
    return { state: 'DONE', summary: '수락 완료' };
  }
  return { state: 'PENDING', summary: '공급사 요청 대기' };
}

/** 투입 중 — 최신 투입 요청 ACTIVE 인 구성원 수. */
function deployedStage(members: BpMember[], idx: BpIndexes): Stage {
  const latest = members
    .map((m) => idx.deployByOwner.get(ownerKey(m.owner_type, m.owner_id)))
    .filter((d): d is FieldDeploymentResponse => !!d);
  const active = latest.filter((d) => d.status === 'ACTIVE').length;
  if (active > 0) {
    return {
      state: 'DONE',
      summary: members.length > 1 ? `투입 중 ${active}/${members.length}` : '현장 투입 중',
    };
  }
  if (latest.some((d) => d.status === 'COMPLETED')) return { state: 'DONE', summary: '투입 종료' };
  return { state: 'PENDING', summary: '미투입' };
}

function stagesOf(members: BpMember[], review: ReceivedReview | null,
                  planStatus: WorkPlanStatus | null, idx: BpIndexes): Record<BpStageKey, Stage> {
  return {
    review: reviewStage(review, planStatus != null),
    plan: planStage(planStatus),
    inspection: inspectionStage(members, idx),
    ready: readyStage(members, idx),
    deployed: deployedStage(members, idx),
  };
}

/** 세트 구성원이 담긴 최신 심사 봉투(구성원 간 최대 id). */
function latestReviewOf(members: BpMember[], idx: BpIndexes): ReceivedReview | null {
  let best: ReceivedReview | null = null;
  for (const m of members) {
    const r = idx.reviewByOwner.get(ownerKey(m.owner_type, m.owner_id));
    if (r && (!best || r.id > best.id)) best = r;
  }
  return best;
}

/**
 * 계획서 1건 → 세트들. 장비마다 1세트(장비 + equipment_id 매칭 인원 — 조종원·유도원 등 역할 표시),
 * 장비 미매칭 인원(현장 안전관리자 등)은 "현장 인원" 세트 1장으로 묶는다.
 */
export function buildPlanSets(plan: BoardPlan, idx: BpIndexes): BpSet[] {
  const out: BpSet[] = [];
  const matchedPersonIds = new Set<number>();
  const equipIds = new Set(plan.equipment.map((e) => e.equipment_id));

  for (const eq of plan.equipment) {
    const head: BpMember = {
      owner_type: 'EQUIPMENT', owner_id: eq.equipment_id, label: eq.label,
      supplier_company_id: eq.supplier_company_id, supplier_company_name: eq.supplier_company_name,
    };
    const crew: BpMember[] = plan.persons
      .filter((p) => p.equipment_id === eq.equipment_id)
      .map((p) => {
        matchedPersonIds.add(p.person_id);
        return {
          owner_type: 'PERSON' as const, owner_id: p.person_id, label: p.name, role: p.role,
          supplier_company_id: p.supplier_company_id, supplier_company_name: p.supplier_company_name,
        };
      });
    const members = [head, ...crew];
    const review = latestReviewOf(members, idx);
    out.push({
      key: `plan:${plan.id}:eq:${eq.equipment_id}`,
      kind: 'plan',
      siteId: plan.site_id ?? null,
      siteName: plan.site_name ?? null,
      planId: plan.id,
      planStatus: plan.status,
      planTitle: plan.title,
      review,
      head,
      members,
      stages: stagesOf(members, review, plan.status, idx),
    });
  }

  // 장비 미매칭 인원 — equipment_id 가 없거나 이 계획서 장비가 아닌 경우.
  const rest: BpMember[] = plan.persons
    .filter((p) => !matchedPersonIds.has(p.person_id)
      && (p.equipment_id == null || !equipIds.has(p.equipment_id)))
    .map((p) => ({
      owner_type: 'PERSON' as const, owner_id: p.person_id, label: p.name, role: p.role,
      supplier_company_id: p.supplier_company_id, supplier_company_name: p.supplier_company_name,
    }));
  if (rest.length > 0) {
    const review = latestReviewOf(rest, idx);
    out.push({
      key: `plan:${plan.id}:crew`,
      kind: 'plan',
      siteId: plan.site_id ?? null,
      siteName: plan.site_name ?? null,
      planId: plan.id,
      planStatus: plan.status,
      planTitle: plan.title,
      review,
      head: rest[0],
      members: rest,
      stages: stagesOf(rest, review, plan.status, idx),
    });
  }
  return out;
}

/**
 * 계획서에 아직 안 담긴 수신 심사 봉투 → 심사 단계 세트(계획서 작성 대기).
 * 이미 계획서에 배치된 자원은 계획서 세트가 심사 상태를 보여주므로 제외하고,
 * 같은 자원의 더 새 봉투가 있으면 옛 봉투는 숨긴다(중복 카드 방지).
 */
export function buildReviewSets(
  reviews: ReceivedReview[],
  plannedOwnerKeys: Set<string>,
  idx: BpIndexes,
): BpSet[] {
  const out: BpSet[] = [];
  for (const r of reviews) {
    const unplanned = r.items.filter((it) => !plannedOwnerKeys.has(ownerKey(it.owner_type, it.owner_id)));
    if (unplanned.length === 0) continue;
    // 이 봉투가 담긴 자원들 기준 최신 봉투가 아니면 숨김(재발송 봉투가 대신 뜬다).
    const isLatest = unplanned.some((it) => idx.reviewByOwner.get(ownerKey(it.owner_type, it.owner_id))?.id === r.id);
    if (!isLatest) continue;
    const members: BpMember[] = unplanned.map((it) => ({
      owner_type: it.owner_type, owner_id: it.owner_id, label: it.label,
      supplier_company_id: r.supplier_company_id, supplier_company_name: r.supplier_company_name,
    }));
    const head = members.find((m) => m.owner_type === 'EQUIPMENT') ?? members[0];
    out.push({
      key: `review:${r.id}`,
      kind: 'review',
      siteId: null,
      siteName: null,
      planId: null,
      planStatus: null,
      planTitle: null,
      review: r,
      head,
      members,
      stages: stagesOf(members, r, null, idx),
    });
  }
  return out;
}

// ── 다음 액션 1개 (전부 기존 화면·다이얼로그 재사용, 신규 플로우 없음) ──

export type BpSetAction =
  | { kind: 'link'; label: string; to: string }
  | { kind: 'newtab'; label: string; to: string }                      // 계획서 작성 프리필(기존 새탭 패턴)
  | { kind: 'check'; label: string; target: BpMember; initialTypes?: ResourceCheckType[] };

/** 계획서 작성 프리필 URL — BpReceivedReviewsPage.openWorkPlan 과 동일 파라미터. */
function planPrefillUrl(review: ReceivedReview): string {
  const params = new URLSearchParams();
  const supplierName = review.supplier_company_name ?? `공급사 #${review.supplier_company_id}`;
  params.set('title', `${supplierName} 작업계획`);
  if (review.supplier_company_type === 'EQUIPMENT') {
    params.set('equipmentSupplierId', String(review.supplier_company_id));
  } else if (review.supplier_company_type === 'MANPOWER') {
    params.set('manpowerSupplierId', String(review.supplier_company_id));
  }
  return `/work-plans/new?${params.toString()}`;
}

export function bpSetActionOf(set: BpSet, idx: BpIndexes): BpSetAction | null {
  const cur = currentBpStage(set.stages);
  switch (cur) {
    case 'review':
      return { kind: 'link', label: '받은 심사 확인', to: '/document-reviews/received' };
    case 'plan': {
      if (set.planId == null) {
        if (!set.review) return null;
        return { kind: 'newtab', label: '계획서 작성', to: planPrefillUrl(set.review) };
      }
      return {
        kind: 'link',
        label: set.planStatus === 'DRAFT' ? '계획서 이어쓰기' : '계획서 서명 확인',
        to: `/work-plans/${set.planId}`,
      };
    }
    case 'inspection': {
      const summary = set.stages.inspection.summary;
      if (summary === '회신 검토 필요') {
        return { kind: 'link', label: '검사 회신 검토', to: '/resource-checks/bp' };
      }
      if (summary === '검사 회신 대기') return null; // 공급사 회신 대기 — BP 할 일 없음
      const checksOf = (m: BpMember) => idx.checksByOwner.get(ownerKey(m.owner_type, m.owner_id)) ?? [];
      // 반려 → 반려 구성원 재통보 / 미통보 → 첫 미통보 구성원 통보 (기존 다이얼로그).
      if (summary.startsWith('점검 반려')) {
        for (const m of set.members) {
          const rejected = checksOf(m).filter((r) => r.status === 'REJECTED');
          if (rejected.length > 0) {
            return { kind: 'check', label: '재검사 통보', target: m, initialTypes: rejected.map((r) => r.check_type) };
          }
        }
      }
      const target = set.members.find((m) => checksOf(m).length === 0) ?? set.head;
      if (target.supplier_company_id == null) return null;
      return { kind: 'check', label: '검사 통보', target };
    }
    case 'ready':
      if (set.stages.ready.summary.includes('수락 필요')) {
        return { kind: 'link', label: '투입 요청 수락', to: '/field-deployments/bp' };
      }
      return null; // 공급사 요청 대기
    case 'deployed':
      return null;
    default:
      return { kind: 'link', label: '투입 현황 보기', to: '/work-plans/active' };
  }
}
