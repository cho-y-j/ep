import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import AuditLogWidget from './AuditLogWidget';
import WorkPlanListWidget, { type DashboardWorkPlan } from './WorkPlanListWidget';
import { EmptyState, SectionCard, StatCard } from './widgets';
import DocumentRiskWidget, { type DocumentRisk } from './DocumentRiskWidget';
import BpPendingQueueWidget from './BpPendingQueueWidget';
import BpSitePipelineWidget from './BpSitePipelineWidget';

type BpSummary = {
  counts: Record<string, number>;
  sites: Array<{ id: number; name: string; status: string; participant_count: number; equipment_count: number; person_count: number }>;
  recent_audit_logs: any[];
  today_work_plans: DashboardWorkPlan[];
  document_risks: DocumentRisk[];
};

export default function BpDashboardPage() {
  const { user, company } = useAuth();
  const isMaster = !!user?.is_company_admin;
  const [summary, setSummary] = useState<BpSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<BpSummary>('/api/dashboards/bp/summary')
      .then((res) => { if (!cancelled) setSummary(res.data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const c = summary?.counts ?? {};
  const sites = summary?.sites ?? [];

  return (
    <AppShell breadcrumb={[{ label: '대시보드' }]}>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">{company?.name ?? 'BP'} 대시보드</h1>
          <p className="text-sm text-slate-500 mt-1">
            {user?.name}님, 현장 운영과 배치 현황을 확인하세요.
          </p>
        </div>

        <BpPendingQueueWidget />

        <BpSitePipelineWidget />

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <>
            {isMaster ? (
              <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <StatCard label="내 현장" value={c.my_sites ?? 0} href="/sites" tone="blue" />
                <StatCard label="활성 참여 업체" value={c.active_participants ?? 0} tone="slate" />
                <StatCard label="배치 장비" value={c.equipment_on_my_sites ?? 0} tone="emerald" />
                <StatCard label="배치 인원" value={c.persons_on_my_sites ?? 0} tone="emerald" />
              </section>
            ) : (
              <div className="card text-xs text-slate-500">
                회사 전체 통계는 관리자만 볼 수 있습니다. 아래 현장/활동/작업계획서는 모두 확인 가능합니다.
              </div>
            )}

            <SectionCard title="내 현장 현황" action={
              <Link to="/sites" className="text-xs text-slate-500 hover:text-slate-900">전체 보기 ›</Link>
            }>
              {sites.length === 0 ? (
                <EmptyState text="등록된 현장이 없습니다. 현장 관리에서 새 현장을 만드세요." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {sites.map((s) => (
                    <li key={s.id} className="py-3 flex items-center justify-between gap-3">
                      <Link to={`/sites/${s.id}`} className="flex-1 min-w-0">
                        <div className="font-semibold text-slate-900 hover:text-brand-700 truncate">{s.name}</div>
                        <div className="text-xs text-slate-500 mt-0.5">
                          참여 {s.participant_count}개사 · 장비 {s.equipment_count} · 인원 {s.person_count}
                        </div>
                      </Link>
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${
                        s.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700'
                          : s.status === 'PAUSED' ? 'bg-amber-100 text-amber-700'
                            : 'bg-slate-100 text-slate-600'
                      }`}>
                        {s.status === 'ACTIVE' ? '진행중' : s.status === 'PAUSED' ? '일시중지' : s.status === 'COMPLETED' ? '완료' : '보관'}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </SectionCard>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <AuditLogWidget preloaded={summary?.recent_audit_logs ?? []} />
              <WorkPlanListWidget title="오늘/이번 주 작업계획서" items={summary?.today_work_plans ?? []} />
            </div>

            <DocumentRiskWidget id="doc-risks" title="공급사 서류 위험" items={summary?.document_risks ?? []} />
          </>
        )}
      </div>
    </AppShell>
  );
}
