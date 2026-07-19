import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { PageHeader } from '../../components/ui';
import AuditLogWidget from './AuditLogWidget';
import WorkPlanListWidget, { type DashboardWorkPlan } from './WorkPlanListWidget';
import { SectionCard, StatCard, EmptyState } from './widgets';
import TodayTasksRow from './TodayTasksRow';

type AdminSummary = {
  counts: Record<string, number>;
  recent_audit_logs: any[];
  today_work_plans: DashboardWorkPlan[];
  recent_notifications: any[];
};

export default function AdminDashboardPage() {
  const { user } = useAuth();
  const [summary, setSummary] = useState<AdminSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    api.get<AdminSummary>('/api/dashboards/admin/summary')
      .then((res) => { if (!cancelled) setSummary(res.data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const c = summary?.counts ?? {};

  return (
    <AppShell breadcrumb={[{ label: '대시보드' }]}>
      <div className="space-y-6">
        <PageHeader
          title="관리자 대시보드"
          subtitle={`${user?.name ?? ''}님, 전체 시스템 현황과 위험 관제를 확인하세요.`}
        />

        <TodayTasksRow
          loading={loading}
          tasks={[
            { label: '승인 대기 사용자', count: c.users_pending ?? 0, to: '/admin/users' },
            { label: '만료 임박 서류', count: c.documents_expiring30d ?? 0, to: '/admin/expiring-documents' },
          ]}
        />

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <>
            <section className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
              <StatCard label="회사" value={c.companies ?? 0} href="/admin/companies" tone="blue" />
              <StatCard label="현장" value={c.sites ?? 0} href="/sites" tone="emerald" />
              <StatCard label="장비" value={c.equipment ?? 0} href="/equipment" tone="slate" />
              <StatCard label="인원" value={c.persons ?? 0} href="/persons" tone="slate" />
              <StatCard label="만료 임박 (30일)" value={c.documents_expiring30d ?? 0} href="/admin/expiring-documents" tone="amber" emphasize={(c.documents_expiring30d ?? 0) > 0} />
              <StatCard label="승인 대기 사용자" value={c.users_pending ?? 0} href="/admin/users" tone="rose" emphasize={(c.users_pending ?? 0) > 0} />
            </section>

            <WorkPlanListWidget title="이번 주 작업계획서" items={summary?.today_work_plans ?? []} showBpName />

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <AuditLogWidget preloaded={summary?.recent_audit_logs ?? []} />
              <SectionCard title="알림"
                action={<Link to="/notifications" className="text-sm font-medium text-brand-600 hover:underline">전체 보기</Link>}>
                {(summary?.recent_notifications ?? []).length === 0 ? (
                  <EmptyState text="최근 알림이 없습니다." />
                ) : (
                  <ul className="divide-y divide-slate-100">
                    {(summary?.recent_notifications ?? []).slice(0, 5).map((n) => (
                      <li key={n.id} className="py-2">
                        <Link to="/notifications" className="group block">
                          <div className="flex items-start justify-between gap-2">
                            <span className="truncate text-sm font-medium text-slate-800 group-hover:text-brand-700">{n.title}</span>
                            <span className="shrink-0 text-xs text-slate-400">
                              {new Date(n.created_at).toLocaleDateString('ko-KR', { month: 'numeric', day: 'numeric' })}
                            </span>
                          </div>
                          {n.message && <p className="mt-0.5 truncate text-xs text-slate-500">{n.message}</p>}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </SectionCard>
            </div>
          </>
        )}
      </div>
    </AppShell>
  );
}
