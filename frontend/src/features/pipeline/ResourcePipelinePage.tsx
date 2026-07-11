import { Fragment, useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';

type StageState = 'DONE' | 'PENDING' | 'NA';
type Stage = { state: StageState; summary: string };
type Stages = {
  docs: Stage;
  inspection: Stage;
  readiness: Stage;
  deployed: Stage;
  work: Stage;
  settlement: Stage;
};
type PipelineItem = {
  resource_type: 'EQUIPMENT' | 'PERSON';
  resource_id: number;
  label: string;
  stages: Stages;
};

const STAGE_ORDER: { key: keyof Stages; label: string }[] = [
  { key: 'docs', label: '서류' },
  { key: 'inspection', label: '검사' },
  { key: 'readiness', label: '투입대기' },
  { key: 'deployed', label: '투입' },
  { key: 'work', label: '작업' },
  { key: 'settlement', label: '정산' },
];

/** 첫 미완료(완료/해당없음 아닌) 단계 = 현재 단계. 없으면 -1(전부 완료). */
function currentIndex(stages: Stages): number {
  return STAGE_ORDER.findIndex((s) => {
    const st = stages[s.key].state;
    return st !== 'DONE' && st !== 'NA';
  });
}

/**
 * 자원 파이프라인 — 자원 1건의 서류→검사→투입대기→투입→작업→정산 6단계를 한 줄로(읽기전용).
 * GET /api/resources/pipeline (기존 도메인 상태 집계). endpoint 404 시 graceful 빈 화면.
 */
export default function ResourcePipelinePage() {
  const [items, setItems] = useState<PipelineItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<PipelineItem[]>('/api/resources/pipeline')
      .then((r) => { if (!cancelled) setItems(r.data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  return (
    <AppShell breadcrumb={[{ label: '자원 파이프라인' }]}>
      <div className="space-y-5">
        <div>
          <h1 className="text-xl font-bold text-slate-900">자원 파이프라인</h1>
          <p className="mt-1 text-sm text-slate-500">
            장비·인력 1건의 서류 → 검사 → 투입대기 → 투입 → 작업 → 정산 진행 상태를 한 줄로 봅니다(읽기전용 집계).
          </p>
          <Legend />
        </div>

        {loading ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : items.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">표시할 자원이 없습니다.</div>
        ) : (
          <div className="space-y-3">
            {items.map((it) => (
              <PipelineRow key={`${it.resource_type}:${it.resource_id}`} item={it} />
            ))}
          </div>
        )}
      </div>
    </AppShell>
  );
}

function Legend() {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
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

function PipelineRow({ item }: { item: PipelineItem }) {
  const cur = currentIndex(item.stages);
  return (
    <div className="card p-4">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:gap-5">
        <div className="flex w-full items-center gap-2 lg:w-44 lg:shrink-0">
          <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
            item.resource_type === 'EQUIPMENT' ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
            {item.resource_type === 'EQUIPMENT' ? '장비' : '인원'}
          </span>
          <span className="truncate font-medium text-slate-900">{item.label}</span>
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
    </div>
  );
}

function StageCell({ index, label, stage, current }: {
  index: number;
  label: string;
  stage: Stage;
  current: boolean;
}) {
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
    <div className="flex w-16 shrink-0 flex-col items-center text-center sm:w-20">
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
