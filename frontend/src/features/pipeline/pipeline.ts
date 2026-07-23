// 자원 파이프라인 공용 타입·헬퍼 (JSX 없음). 화면 컴포넌트가 공유한다.

export type StageState = 'DONE' | 'PENDING' | 'NA';
export type Stage = { state: StageState; summary: string };
export type StageKey = 'docs' | 'inspection' | 'readiness' | 'deployed' | 'work' | 'settlement';
export type Stages = Record<StageKey, Stage>;

/** 이 자원이 담긴 최신 심사 봉투(DocumentReview) 상태 — 미발송이면 필드 자체가 없음. */
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type PipelineItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  label: string;
  // 백엔드 JSON 은 non_null 직렬화 — null 필드(현장 미배정 등)는 응답에서 생략되므로 optional.
  supplier_company_id?: number | null;
  supplier_name?: string | null;
  site_id?: number | null;
  site_name?: string | null;
  review_status?: ReviewStatus | null;
  review_rejected_reason?: string | null;
  stages: Stages;
};

export const STAGE_ORDER: { key: StageKey; label: string }[] = [
  { key: 'docs', label: '서류' },
  { key: 'inspection', label: '검사' },
  { key: 'readiness', label: '투입대기' },
  { key: 'deployed', label: '투입' },
  { key: 'work', label: '작업' },
  { key: 'settlement', label: '정산' },
];

/** 단계 필터 값 = 현재 단계 key, 또는 전부 완료면 'done'. */
export type StageFilter = StageKey | 'done';

export const STAGE_FILTER_LABEL: Record<StageFilter, string> = {
  docs: '서류',
  inspection: '검사',
  readiness: '투입대기',
  deployed: '투입',
  work: '작업',
  settlement: '정산',
  done: '완료',
};

/** 첫 미완료(완료/해당없음 아닌) 단계 index = 현재 단계. 없으면 -1(전부 완료). */
export function currentIndex(stages: Stages): number {
  return STAGE_ORDER.findIndex((s) => {
    const st = stages[s.key].state;
    return st !== 'DONE' && st !== 'NA';
  });
}

/** 이 자원의 현재 단계(필터 버킷). 전부 완료면 'done'. */
export function currentStage(stages: Stages): StageFilter {
  const i = currentIndex(stages);
  return i < 0 ? 'done' : STAGE_ORDER[i].key;
}

/** 개별 자원 식별 키 — URL 쿼리(resource=EQUIPMENT:29)·React key 공용. */
export function itemKey(it: Pick<PipelineItem, 'resource_type' | 'resource_id'>): string {
  return `${it.resource_type}:${it.resource_id}`;
}

/** R1 조합(교대조) 조종원 칩 — POST /api/equipment/default-operators 배치 응답 항목. */
export type PipelineOperator = { person_id: number; person_name?: string | null };

/**
 * 조합 준비 파생(이 화면 데이터 합성, 별도 API 없음) —
 * 장비 readiness DONE AND 조종원 전원(같은 파이프라인 응답의 PERSON 행) readiness DONE.
 * 조종원 0명(장비 단독)이거나, 스코프 밖 조종원(행 없음)이 있어 확정 못 하면 null(뱃지 숨김).
 * 확정 판정은 자원 상세의 deploy-check-combo(4게이트) 카드가 담당.
 */
export function comboReadyOf(
  equip: PipelineItem,
  operators: PipelineOperator[],
  byKey: Map<string, PipelineItem>,
): boolean | null {
  if (operators.length === 0) return null;
  if (equip.stages.readiness.state !== 'DONE') return false;
  let unknown = false;
  for (const op of operators) {
    const row = byKey.get(`PERSON:${op.person_id}`);
    if (!row) { unknown = true; continue; }
    if (row.stages.readiness.state !== 'DONE') return false;
  }
  return unknown ? null : true;
}

/** deploy-check 카드·자원 상세 라우트용 ownerType(소문자). */
export function ownerType(it: PipelineItem): 'equipment' | 'person' {
  return it.resource_type === 'EQUIPMENT' ? 'equipment' : 'person';
}

/** 자원 상세 라우트(서류·투입대기·부족항목을 실제로 해결하는 화면). 장비 라우트는 단수형 /equipment/:id. */
export function resourcePath(it: PipelineItem): string {
  return it.resource_type === 'EQUIPMENT' ? `/equipment/${it.resource_id}` : `/persons/${it.resource_id}`;
}

/** 단계별 '다음 행동' 바로가기 — 현재 단계에서 갈 곳. */
export const STAGE_ACTION: Record<StageFilter, { label: string; to: (it: PipelineItem) => string }> = {
  docs: { label: '서류 관리로', to: resourcePath },
  inspection: { label: '검사·온보딩으로', to: () => '/resource-onboardings' },
  readiness: { label: '투입가능 판정 보기', to: resourcePath },
  deployed: { label: '투입 관리로', to: () => '/field-deployments/supplier' },
  work: { label: '작업확인서로', to: () => '/daily-work-logs' },
  settlement: { label: '정산으로', to: () => '/settlements' },
  done: { label: '자원 상세 보기', to: resourcePath },
};

/** 각 단계 카드에서 그 단계 화면으로 가는 바로가기(타임라인 재사용). */
export const STAGE_LINK: Record<StageKey, (it: PipelineItem) => string> = {
  docs: resourcePath,
  inspection: () => '/resource-onboardings',
  readiness: resourcePath,
  deployed: () => '/field-deployments/supplier',
  work: () => '/daily-work-logs',
  settlement: () => '/settlements',
};

// ── 세트(조합) 흐름 보드 — 장비+교대조 조종원을 한 카드로 합성(기존 파이프라인 응답 재조합, 신규 판정 없음) ──

export type SetStageKey = 'docs' | 'review' | 'inspection' | 'readiness' | 'deployed' | 'settlement';

export const SET_STAGE_ORDER: { key: SetStageKey; label: string }[] = [
  { key: 'docs', label: '서류' },
  { key: 'review', label: '심사' },
  { key: 'inspection', label: '검사' },
  { key: 'readiness', label: '투입 대기' },
  { key: 'deployed', label: '투입 중' },
  { key: 'settlement', label: '정산' },
];

export type SetStageFilter = SetStageKey | 'done';

export const SET_STAGE_FILTER_LABEL: Record<SetStageFilter, string> = {
  docs: '서류',
  review: '심사',
  inspection: '검사',
  readiness: '투입 대기',
  deployed: '투입 중',
  settlement: '정산',
  done: '완료',
};

/** 세트 1장 = 장비 + 스코프 안에서 찾은 조종원 행(없으면 장비 단독). 인력 전용 카드는 head=PERSON, operators=[]. */
export type ResourceSet = {
  key: string;                      // itemKey(head) — React key·상세 열기 공용
  head: PipelineItem;
  operators: PipelineItem[];        // 파이프라인 응답에서 찾은 조종원 행
  unknownOperators: PipelineOperator[]; // 행 없는 조종원(스코프 밖) — 이름 표시만
  stages: Record<SetStageKey, Stage>;
  inspectionRejected: boolean;      // 검사 단계에 반려 건 존재(재검사 통보 라벨용)
  reviewNotSent: boolean;           // 심사 봉투 미발송(심사 보내기 액션용)
};

/** 미비 멤버 요약 — 멤버 2명 이상이면 이름을 붙이고, 여러 명이면 "외 N". */
function memberSummary(pending: PipelineItem[], totalMembers: number, k: StageKey): string {
  const first = pending[0];
  const base = totalMembers > 1 ? `${first.label} — ${first.stages[k].summary}` : first.stages[k].summary;
  return pending.length > 1 ? `${base} 외 ${pending.length - 1}` : base;
}

/**
 * 세트 단계 합성 — 각 멤버의 기존 6단계 + 심사 상태(review_status)를 세트 트랙
 * 서류→심사→검사→투입 대기→투입 중→정산 으로 재배열한다. 도메인 게이트 재구현 없음:
 * 서류/검사/투입대기 = 멤버 전원 완료 여부, 투입/정산 = head(장비) 단계 그대로,
 * 심사 = 최신 심사 봉투 상태(미발송인데 이미 투입까지 간 세트는 흐름을 막지 않게 해당없음 표기).
 */
export function buildSet(
  head: PipelineItem,
  operators: PipelineOperator[],
  byKey: Map<string, PipelineItem>,
): ResourceSet {
  const opRows: PipelineItem[] = [];
  const unknown: PipelineOperator[] = [];
  for (const op of operators) {
    const row = byKey.get(`PERSON:${op.person_id}`);
    if (row) opRows.push(row); else unknown.push(op);
  }
  const members = [head, ...opRows];

  const docsPending = members.filter((m) => m.stages.docs.state === 'PENDING');
  const docs: Stage = docsPending.length === 0
    ? { state: 'DONE', summary: '서류 완비' }
    : { state: 'PENDING', summary: memberSummary(docsPending, members.length, 'docs') };

  const rejectedMember = members.find((m) => m.review_status === 'REJECTED');
  const anyReviewPending = members.some((m) => m.review_status === 'PENDING');
  let review: Stage;
  let reviewNotSent = false;
  if (rejectedMember) {
    review = { state: 'PENDING', summary: `반려 — ${rejectedMember.review_rejected_reason ?? '재발송 필요'}` };
  } else if (anyReviewPending) {
    review = { state: 'PENDING', summary: '심사중 — BP 확인 대기' };
  } else if (head.review_status === 'APPROVED') {
    review = { state: 'DONE', summary: '심사 승인' };
  } else {
    reviewNotSent = true;
    review = head.stages.deployed.state === 'DONE'
      ? { state: 'NA', summary: '미발송' }
      : { state: 'PENDING', summary: '심사 미발송' };
  }

  const inspPending = members.filter((m) => m.stages.inspection.state === 'PENDING');
  const inspection: Stage = inspPending.length === 0
    ? { state: 'DONE', summary: '검사 완료' }
    : { state: 'PENDING', summary: memberSummary(inspPending, members.length, 'inspection') };

  const notReady = members.filter((m) => m.stages.readiness.state !== 'DONE');
  const readiness: Stage = notReady.length === 0
    ? { state: 'DONE', summary: '투입 준비됨' }
    : { state: 'PENDING', summary: memberSummary(notReady, members.length, 'readiness') };

  // 세트 트랙의 '투입 중' 단계 라벨과 백엔드 요약 '미투입'이 붙으면 "투입 중 — 미투입"으로 상충 —
  // 미투입(PENDING) 요약만 다음 행동이 읽히게 정돈(그 외 요약·판정은 백엔드 그대로).
  const deployed: Stage = head.stages.deployed.state === 'PENDING' && head.stages.deployed.summary === '미투입'
    ? { state: 'PENDING', summary: '투입 요청 필요' }
    : head.stages.deployed;

  return {
    key: itemKey(head),
    head,
    operators: opRows,
    unknownOperators: unknown,
    stages: { docs, review, inspection, readiness, deployed, settlement: head.stages.settlement },
    inspectionRejected: inspPending.some((m) => m.stages.inspection.summary.includes('반려')),
    reviewNotSent,
  };
}

/** 첫 미완료(PENDING) 세트 단계 index. 없으면 -1(전부 완료/해당없음). */
export function currentSetIndex(stages: Record<SetStageKey, Stage>): number {
  return SET_STAGE_ORDER.findIndex((s) => stages[s.key].state === 'PENDING');
}

/** 이 세트의 현재 단계(필터 버킷). 전부 완료면 'done'. */
export function currentSetStage(stages: Record<SetStageKey, Stage>): SetStageFilter {
  const i = currentSetIndex(stages);
  return i < 0 ? 'done' : SET_STAGE_ORDER[i].key;
}

/** 세트 카드의 다음 액션 — link(라우트 이동) | check(검사 통보 다이얼로그) | detail(1대 뷰 열기). */
export type SetAction =
  | { kind: 'link'; label: string; to: string }
  | { kind: 'check'; label: string; target: PipelineItem }
  | { kind: 'detail'; label: string; resourceKey: string };

/**
 * 현재 세트 단계에 맞는 다음 액션 1개 — 전부 기존 화면·다이얼로그로 연결(신규 플로우 없음).
 * 심사중(BP 확인 대기)·전부 완료는 공급사가 할 일이 없어 null.
 */
export function setActionOf(set: ResourceSet): SetAction | null {
  const { head, operators, stages } = set;
  const members = [head, ...operators];
  const cur = currentSetStage(stages);
  switch (cur) {
    case 'docs': {
      const target = members.find((m) => m.stages.docs.state === 'PENDING') ?? head;
      return { kind: 'link', label: '서류 관리로', to: resourcePath(target) };
    }
    case 'review': {
      if (!set.reviewNotSent && !stages.review.summary.startsWith('반려')) return null; // 심사중
      const param = head.resource_type === 'EQUIPMENT' ? `equipment=${head.resource_id}` : `person=${head.resource_id}`;
      return { kind: 'link', label: set.reviewNotSent ? '심사 보내기' : '심사 다시 보내기', to: `/document-review-send?${param}` };
    }
    case 'inspection': {
      const target = members.find((m) => m.stages.inspection.state === 'PENDING') ?? head;
      if (target.supplier_company_id == null) return null;
      return { kind: 'check', label: set.inspectionRejected ? '재검사 통보' : '검사 통보', target };
    }
    case 'readiness': {
      const target = members.find((m) => m.stages.readiness.state !== 'DONE') ?? head;
      return { kind: 'detail', label: '투입가능 판정 보기', resourceKey: itemKey(target) };
    }
    case 'deployed':
      // 장비 세트는 ?equipment= 프리필 — 투입 요청 화면이 그 장비 선택 + 조합 다이얼로그를 바로 연다.
      return {
        kind: 'link',
        label: '투입 요청',
        to: head.resource_type === 'EQUIPMENT'
          ? `/field-deployments/supplier?equipment=${head.resource_id}`
          : '/field-deployments/supplier',
      };
    case 'settlement':
      return { kind: 'link', label: '정산으로', to: '/settlements' };
    default:
      return null; // done
  }
}
