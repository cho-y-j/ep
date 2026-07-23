import { Fragment, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { EmptyState } from '../../components/ui';
import IssueResourceCheckDialog from '../resourceCheck/IssueResourceCheckDialog';
import {
  type PipelineItem,
  type PipelineOperator,
  type ResourceSet,
  type SetStageFilter,
  type Stage,
  SET_STAGE_ORDER,
  SET_STAGE_FILTER_LABEL,
  buildSet,
  currentSetIndex,
  currentSetStage,
  setActionOf,
} from './pipeline';

const SET_STAGE_FILTERS: SetStageFilter[] = ['docs', 'review', 'inspection', 'readiness', 'deployed', 'settlement', 'done'];

type Props = {
  items: PipelineItem[];
  operatorsByEquip: Record<number, PipelineOperator[]>;
  itemsByKey: Map<string, PipelineItem>;
  q: string;
  type: string;     // '' | 'EQUIPMENT' | 'PERSON'
  site: string;     // '' | '<id>' | 'none'
  company: string;  // '' | '<id>'
  showSupplier: boolean;
  stage: '' | SetStageFilter;
  onStageChange: (v: string) => void;
  /** 기존 1대 뷰(PipelineDetail) 열기 — resource 쿼리 파라미터. */
  onSelectResource: (key: string) => void;
  /** 검사 통보 발송 후 재로딩. */
  onIssued: () => void;
};

/** 검사 통보 다이얼로그 대상 — IssueResourceCheckDialog(기존) 프롭 그대로. */
type CheckTarget = {
  ownerType: 'EQUIPMENT' | 'PERSON';
  ownerId: number;
  ownerLabel: string;
  supplierCompanyId: number;
  supplierCompanyName?: string | null;
};

/**
 * 세트(조합) 흐름 보드 — 장비+교대조 조종원을 카드 1장으로, 서류→심사→검사→투입 대기→투입 중→정산 트랙과
 * 현재 단계에 맞는 다음 액션 1개를 보여준다. 판정은 buildSet(기존 데이터 합성)만 사용.
 */
export default function SetBoard({
  items, operatorsByEquip, itemsByKey, q, type, site, company, showSupplier,
  stage, onStageChange, onSelectResource, onIssued,
}: Props) {
  const [checkTarget, setCheckTarget] = useState<CheckTarget | null>(null);

  // 조합에 묶인 조종원 id — 미배정 인원 판별용.
  const assignedPersonIds = useMemo(() => {
    const s = new Set<number>();
    Object.values(operatorsByEquip).forEach((ops) => ops.forEach((op) => s.add(op.person_id)));
    return s;
  }, [operatorsByEquip]);

  const equipSets = useMemo(
    () => items.filter((it) => it.resource_type === 'EQUIPMENT')
      .map((it) => buildSet(it, operatorsByEquip[it.resource_id] ?? [], itemsByKey)),
    [items, operatorsByEquip, itemsByKey],
  );
  const personSets = useMemo(
    () => items.filter((it) => it.resource_type === 'PERSON' && !assignedPersonIds.has(it.resource_id))
      .map((it) => buildSet(it, [], itemsByKey)),
    [items, assignedPersonIds, itemsByKey],
  );

  // 세트 단위 필터 — 현장/업체는 head 기준, 검색은 장비 라벨 + 조종원 이름까지 매칭.
  const qLower = q.trim().toLowerCase();
  const matches = (set: ResourceSet) => {
    const h = set.head;
    if (company && h.supplier_company_id !== Number(company)) return false;
    if (site === 'none') { if (h.site_id != null) return false; }
    else if (site && h.site_id !== Number(site)) return false;
    if (qLower) {
      const names = [h.label, ...set.operators.map((o) => o.label),
        ...set.unknownOperators.map((o) => o.person_name ?? '')].join(' ').toLowerCase();
      if (!names.includes(qLower)) return false;
    }
    return true;
  };
  const baseEquipSets = type === 'PERSON' ? [] : equipSets.filter(matches);
  const basePersonSets = type === 'EQUIPMENT' ? [] : personSets.filter(matches);

  const stageCounts = useMemo(() => {
    const c: Record<SetStageFilter, number> = { docs: 0, review: 0, inspection: 0, readiness: 0, deployed: 0, settlement: 0, done: 0 };
    [...baseEquipSets, ...basePersonSets].forEach((s) => { c[currentSetStage(s.stages)]++; });
    return c;
  }, [baseEquipSets, basePersonSets]);

  const listEquipSets = stage ? baseEquipSets.filter((s) => currentSetStage(s.stages) === stage) : baseEquipSets;
  const listPersonSets = stage ? basePersonSets.filter((s) => currentSetStage(s.stages) === stage) : basePersonSets;

  return (
    <>
      {/* 세트 단계 집계 칩 = 드릴다운(클릭 시 그 단계만, 다시 클릭 해제) */}
      <div className="mt-2 flex gap-1.5 overflow-x-auto pb-1">
        {SET_STAGE_FILTERS.map((k) => {
          const active = stage === k;
          const count = stageCounts[k];
          return (
            <button
              key={k}
              onClick={() => onStageChange(active ? '' : k)}
              className={`flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                active
                  ? 'border-brand-500 bg-brand-600 text-white'
                  : count === 0
                    ? 'border-slate-200 bg-white text-slate-300'
                    : 'border-slate-200 bg-white text-slate-600 hover:border-brand-300 hover:text-brand-700'}`}
            >
              {SET_STAGE_FILTER_LABEL[k]}
              <span className={`rounded-full px-1.5 text-[10px] font-bold ${
                active ? 'bg-white/20 text-white' : count === 0 ? 'bg-slate-100 text-slate-300' : 'bg-slate-100 text-slate-600'}`}>
                {count}
              </span>
            </button>
          );
        })}
      </div>

      <div className="mt-3">
        {listEquipSets.length === 0 && listPersonSets.length === 0 ? (
          <EmptyState title="조건에 맞는 세트가 없습니다" text="필터를 바꾸거나 초기화하세요." />
        ) : (
          <div className="space-y-3">
            {listEquipSets.map((set) => (
              <SetCard key={set.key} set={set} showSupplier={showSupplier}
                onSelectResource={onSelectResource} onCheck={setCheckTarget} />
            ))}
            {listPersonSets.length > 0 && (
              <>
                {listEquipSets.length > 0 && (
                  <div className="pt-2 text-[13px] font-bold text-slate-700">
                    미배정 인원 <span className="font-normal text-slate-400">— 장비 조합(교대조)에 속하지 않은 인원</span>
                  </div>
                )}
                {listPersonSets.map((set) => (
                  <SetCard key={set.key} set={set} showSupplier={showSupplier}
                    onSelectResource={onSelectResource} onCheck={setCheckTarget} />
                ))}
              </>
            )}
          </div>
        )}
      </div>

      {/* 검사 통보 — 기존 다이얼로그 재사용(검사 관리 화면과 동일 경로) */}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={onIssued}
          workPlanId={null}
          ownerType={checkTarget.ownerType}
          ownerId={checkTarget.ownerId}
          ownerLabel={checkTarget.ownerLabel}
          supplierCompanyId={checkTarget.supplierCompanyId}
          supplierCompanyName={checkTarget.supplierCompanyName}
        />
      )}
    </>
  );
}

function SetCard({ set, showSupplier, onSelectResource, onCheck }: {
  set: ResourceSet;
  showSupplier: boolean;
  onSelectResource: (key: string) => void;
  onCheck: (t: CheckTarget) => void;
}) {
  const { head, operators, unknownOperators, stages } = set;
  const cur = currentSetIndex(stages);
  const isCombo = head.resource_type === 'EQUIPMENT' && (operators.length > 0 || unknownOperators.length > 0);
  const opNames = [...operators.map((o) => o.label), ...unknownOperators.map((o) => o.person_name ?? `인원 #${o.person_id}`)];

  // 현재 단계에 맞는 다음 액션 1개 — setActionOf(순수 판정) 결과를 렌더만 한다.
  const action = setActionOf(set);

  const curLabel = cur >= 0 ? SET_STAGE_ORDER[cur].label : null;
  const curSummary = cur >= 0 ? stages[SET_STAGE_ORDER[cur].key].summary : null;

  return (
    <div className="card p-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-5">
        {/* 헤더 — 세트 구성(장비 · 조종원) + 메타 */}
        <div className="w-full lg:w-56 lg:shrink-0">
          <div className="flex items-center gap-2">
            <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
              isCombo ? 'bg-indigo-100 text-indigo-700'
                : head.resource_type === 'EQUIPMENT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
              {isCombo ? '세트' : head.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
            </span>
            <button onClick={() => onSelectResource(set.key)}
              className="truncate text-left font-medium text-slate-900 hover:text-brand-700 hover:underline"
              title="자원별 상세 보기">
              {head.label}
            </button>
          </div>
          {opNames.length > 0 && (
            <div className="mt-0.5 truncate text-xs text-slate-500" title={opNames.join(', ')}>
              조종원 {opNames.join(', ')}
            </div>
          )}
          {(showSupplier || head.site_name) && (
            <div className="mt-0.5 truncate text-[11px] text-slate-400">
              {showSupplier && head.supplier_name}
              {showSupplier && head.site_name && ' · '}
              {head.site_name && `현장 ${head.site_name}`}
            </div>
          )}
        </div>

        {/* 단계 트랙 — 서류→심사→검사→투입 대기→투입 중→정산 */}
        <div className="flex flex-1 items-start">
          {SET_STAGE_ORDER.map((s, i) => (
            <Fragment key={s.key}>
              {i > 0 && (
                <div className={`mt-3 h-0.5 flex-1 ${
                  stages[SET_STAGE_ORDER[i - 1].key].state === 'DONE' ? 'bg-emerald-400' : 'bg-slate-200'}`} />
              )}
              <SetStageCell index={i + 1} label={s.label} stage={stages[s.key]} current={i === cur} />
            </Fragment>
          ))}
        </div>
      </div>

      {/* 상태 요약 + 다음 액션 */}
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-2.5">
        <div className="min-w-0 text-xs">
          {curLabel ? (
            <span className="text-slate-600"><b className="text-brand-700">{curLabel}</b> — {curSummary}</span>
          ) : (
            <span className="font-medium text-emerald-600">전 단계 완료</span>
          )}
        </div>
        {action && (
          action.kind === 'link' ? (
            <Link to={action.to} className="btn-primary px-3 py-1.5 text-xs">{action.label}</Link>
          ) : action.kind === 'check' ? (
            <button
              onClick={() => onCheck({
                ownerType: action.target.resource_type,
                ownerId: action.target.resource_id,
                ownerLabel: action.target.label,
                supplierCompanyId: action.target.supplier_company_id!,
                supplierCompanyName: action.target.supplier_name,
              })}
              className="btn-primary px-3 py-1.5 text-xs"
            >
              {action.label}
            </button>
          ) : (
            <button onClick={() => onSelectResource(action.resourceKey)} className="btn-primary px-3 py-1.5 text-xs">
              {action.label}
            </button>
          )
        )}
      </div>
    </div>
  );
}

function SetStageCell({ index, label, stage, current }: { index: number; label: string; stage: Stage; current: boolean }) {
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
