import { Link } from 'react-router-dom';
import DeployCheckCard from '../readiness/DeployCheckCard';
import {
  type PipelineItem,
  STAGE_ORDER,
  STAGE_ACTION,
  STAGE_LINK,
  currentIndex,
  currentStage,
  ownerType,
  resourcePath,
} from './pipeline';

/**
 * 개별 자원 1건의 파이프라인 큰 뷰(상세 패널) —
 * 서류→검사→투입대기→투입→작업→정산 세로 타임라인 + 부족 항목(deploy-check 재사용) + 다음 행동.
 */
export default function PipelineDetail({ item, onBack }: { item: PipelineItem; onBack: () => void }) {
  const cur = currentIndex(item.stages);
  const action = STAGE_ACTION[currentStage(item.stages)];
  const isEquip = item.resource_type === 'EQUIPMENT';

  return (
    <div className="space-y-4">
      <button
        onClick={onBack}
        className="inline-flex items-center gap-1 text-sm font-medium text-slate-500 hover:text-slate-800"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        전체 목록으로
      </button>

      <div className="card p-5">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded px-1.5 py-0.5 text-[10px] font-semibold ${
            isEquip ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'}`}>
            {isEquip ? '장비' : '인원'}
          </span>
          <h1 className="text-xl font-bold text-slate-900">{item.label}</h1>
          {item.supplier_name && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">{item.supplier_name}</span>
          )}
          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
            {item.site_name ? `현장 · ${item.site_name}` : '현장 미배정'}
          </span>
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Link to={action.to(item)} className="btn-primary">{action.label}</Link>
          <Link to={resourcePath(item)} className="btn-ghost">자원 상세 열기</Link>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {/* 세로 타임라인 — 전 단계 진행 */}
        <div className="card p-5">
          <h2 className="text-sm font-bold text-slate-900">진행 타임라인</h2>
          <p className="mt-0.5 text-xs text-slate-500">서류부터 정산까지 6단계. 각 단계에서 관련 화면으로 이동합니다.</p>
          <ol className="mt-4 space-y-0">
            {STAGE_ORDER.map((s, i) => (
              <TimelineStep
                key={s.key}
                index={i}
                label={s.label}
                state={item.stages[s.key].state}
                summary={item.stages[s.key].summary}
                current={i === cur}
                last={i === STAGE_ORDER.length - 1}
                to={STAGE_LINK[s.key](item)}
              />
            ))}
          </ol>
        </div>

        {/* 부족 항목 — deploy-check 재사용 */}
        <div>
          <DeployCheckCard ownerType={ownerType(item)} ownerId={item.resource_id} />
        </div>
      </div>
    </div>
  );
}

function TimelineStep({ index, label, state, summary, current, last, to }: {
  index: number;
  label: string;
  state: 'DONE' | 'PENDING' | 'NA';
  summary: string;
  current: boolean;
  last: boolean;
  to: string;
}) {
  const done = state === 'DONE';
  const na = state === 'NA';
  const circle = done
    ? 'bg-emerald-500 text-white border-emerald-500'
    : current
      ? 'bg-white text-brand-700 border-2 border-brand-500'
      : na
        ? 'bg-slate-50 text-slate-300 border border-dashed border-slate-300'
        : 'bg-slate-100 text-slate-400 border border-slate-200';

  return (
    <li className="flex gap-3">
      <div className="flex flex-col items-center">
        <span className={`inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${circle}`}>
          {done ? <IconCheck /> : na ? '–' : index + 1}
        </span>
        {!last && <span className={`w-0.5 flex-1 ${done ? 'bg-emerald-400' : 'bg-slate-200'}`} style={{ minHeight: 18 }} />}
      </div>
      <div className={`pb-4 ${last ? '' : ''}`}>
        <div className="flex items-center gap-2">
          <span className={`text-sm font-semibold ${current ? 'text-brand-700' : 'text-slate-800'}`}>{label}</span>
          {current && <span className="rounded bg-brand-50 px-1.5 py-0.5 text-[10px] font-semibold text-brand-600">현재 단계</span>}
        </div>
        <div className={`mt-0.5 text-xs ${done ? 'text-emerald-600' : na ? 'text-slate-400' : 'text-slate-500'}`}>{summary}</div>
        {!na && (
          <Link to={to} className="mt-1 inline-block text-xs font-medium text-brand-600 hover:underline">화면 이동 →</Link>
        )}
      </div>
    </li>
  );
}

function IconCheck() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="20 6 9 17 4 12" />
    </svg>
  );
}
