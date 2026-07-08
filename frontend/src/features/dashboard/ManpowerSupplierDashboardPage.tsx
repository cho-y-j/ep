import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import AuditLogWidget from './AuditLogWidget';
import WorkPlanListWidget, { type DashboardWorkPlan } from './WorkPlanListWidget';
import { EmptyState, SectionCard, StatCard } from './widgets';
import DocumentRiskWidget, { type DocumentRisk } from './DocumentRiskWidget';
import IncomingRequestsWidget from './IncomingRequestsWidget';

type Summary = {
  counts: Record<string, number>;
  sites: Array<{ id: number; name: string; status: string; bp_company_id: number }>;
  recent_audit_logs: any[];
  upcoming_work_plans: DashboardWorkPlan[];
  document_risks: DocumentRisk[];
};

export default function ManpowerSupplierDashboardPage() {
  const { user, company } = useAuth();
  const isMaster = !!user?.is_company_admin;
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<Summary>('/api/dashboards/manpower-supplier/summary')
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
          <h1 className="text-2xl font-bold">{company?.name ?? '인력공급사'} 대시보드</h1>
          <p className="text-sm text-slate-500 mt-1">
            {user?.name}님, 내 인원 운영과 참여 현장을 확인하세요.
          </p>
        </div>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <>
            {isMaster ? (
              <section className="grid grid-cols-2 md:grid-cols-4 gap-3">
                <StatCard label="내 인원" value={c.my_persons ?? 0} href="/persons" tone="blue" />
                <StatCard label="배치 중 인원" value={c.persons_on_duty ?? 0} tone="emerald" />
                <StatCard label="참여 현장" value={c.participated_sites ?? 0} href="/sites" tone="slate" />
                <StatCard
                  label="만료 임박 서류"
                  value={c.documents_expiring30d ?? 0}
                  tone="amber"
                  emphasize={(c.documents_expiring30d ?? 0) > 0}
                  onClick={() => document.getElementById('doc-risks')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}
                />
              </section>
            ) : (
              <div className="card text-xs text-slate-500">
                회사 전체 통계는 관리자만 볼 수 있습니다. 아래 현장/활동/작업계획서는 모두 확인 가능합니다.
              </div>
            )}

            <IncomingRequestsWidget />

            <SectionCard title="참여 현장" action={
              <Link to="/sites" className="text-xs text-slate-500 hover:text-slate-900">전체 보기 ›</Link>
            }>
              {sites.length === 0 ? (
                <EmptyState text="참여 중인 현장이 없습니다. BP사가 우리 회사를 현장 참여업체로 추가해야 합니다." />
              ) : (
                <ul className="divide-y divide-slate-100">
                  {sites.map((s) => (
                    <li key={s.id} className="py-3 flex items-center justify-between gap-3">
                      <Link to={`/sites/${s.id}`} className="flex-1 min-w-0">
                        <div className="font-semibold text-slate-900 hover:text-brand-700 truncate">{s.name}</div>
                        <div className="text-xs text-slate-500 mt-0.5">BP 회사 #{s.bp_company_id}</div>
                      </Link>
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${
                        s.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {s.status === 'ACTIVE' ? '진행중' : s.status}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </SectionCard>

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <AuditLogWidget preloaded={summary?.recent_audit_logs ?? []} />
              <WorkPlanListWidget title="내 인원이 포함된 작업계획서" items={summary?.upcoming_work_plans ?? []} showBpName />
            </div>

            <DocumentRiskWidget id="doc-risks" title="인원 서류 위험" items={summary?.document_risks ?? []} />
          </>
        )}
      </div>
    </AppShell>
  );
}
