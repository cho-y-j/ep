import { Link } from 'react-router-dom';
import { EmptyState, SectionCard } from './widgets';
import { WORK_PLAN_STATUS_LABEL, type WorkPlanStatus } from '../../types/workPlan';

export type DashboardWorkPlan = {
  id: number;
  title: string;
  site_id: number;
  site_name?: string | null;
  bp_company_name?: string | null;
  work_date: string;
  start_time?: string | null;
  end_time?: string | null;
  status: WorkPlanStatus;
  equipment_count: number;
  person_count: number;
};

type Props = {
  title: string;
  items: DashboardWorkPlan[];
  showBpName?: boolean;     // 공급사용: BP 회사명 노출
};

export default function WorkPlanListWidget({ title, items, showBpName }: Props) {
  return (
    <SectionCard
      title={title}
      action={<Link to="/work-plans" className="text-xs text-slate-500 hover:text-slate-900">전체 보기 ›</Link>}
    >
      {items.length === 0 ? (
        <EmptyState text="예정된 작업계획서가 없습니다." />
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((wp) => (
            <li key={wp.id} className="py-3">
              <Link to={`/work-plans/${wp.id}`} className="block group">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-slate-900 group-hover:text-brand-700 truncate">
                        {wp.title}
                      </span>
                      <StatusBadge status={wp.status} />
                    </div>
                    <div className="mt-0.5 text-xs text-slate-500 truncate">
                      {wp.work_date}
                      {wp.start_time && <> · {wp.start_time.slice(0, 5)}{wp.end_time ? `~${wp.end_time.slice(0, 5)}` : ''}</>}
                      {' · '}{wp.site_name ?? '-'}
                      {showBpName && wp.bp_company_name && <> · {wp.bp_company_name}</>}
                    </div>
                  </div>
                  <div className="shrink-0 text-xs text-slate-500 text-right">
                    <div>장비 {wp.equipment_count}</div>
                    <div>인원 {wp.person_count}</div>
                  </div>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

function StatusBadge({ status }: { status: WorkPlanStatus }) {
  const tone =
    status === 'DRAFT' ? 'bg-slate-100 text-slate-600'
    : status === 'SUBMITTED' ? 'bg-amber-100 text-amber-700'
    : status === 'APPROVED' || status === 'IN_PROGRESS' ? 'bg-brand-50 text-brand-700'
    : status === 'DONE' ? 'bg-emerald-100 text-emerald-700'
    : 'bg-rose-100 text-rose-700';
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-[10px] font-semibold ${tone}`}>{WORK_PLAN_STATUS_LABEL[status]}</span>;
}
