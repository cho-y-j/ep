// 자원 파이프라인 공용 타입·헬퍼 (JSX 없음). 화면 컴포넌트가 공유한다.

export type StageState = 'DONE' | 'PENDING' | 'NA';
export type Stage = { state: StageState; summary: string };
export type StageKey = 'docs' | 'inspection' | 'readiness' | 'deployed' | 'work' | 'settlement';
export type Stages = Record<StageKey, Stage>;

export type PipelineItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  label: string;
  // 백엔드 JSON 은 non_null 직렬화 — null 필드(현장 미배정 등)는 응답에서 생략되므로 optional.
  supplier_company_id?: number | null;
  supplier_name?: string | null;
  site_id?: number | null;
  site_name?: string | null;
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

/** deploy-check 카드·자원 상세 라우트용 ownerType(소문자). */
export function ownerType(it: PipelineItem): 'equipment' | 'person' {
  return it.resource_type === 'EQUIPMENT' ? 'equipment' : 'person';
}

/** 자원 상세 라우트(서류·투입대기·부족항목을 실제로 해결하는 화면). */
export function resourcePath(it: PipelineItem): string {
  return `/${ownerType(it)}s/${it.resource_id}`;
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
