import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import AuditLogWidget from './AuditLogWidget';
import WorkPlanListWidget, { type DashboardWorkPlan } from './WorkPlanListWidget';
import { SectionCard, StatCard, TodoBanner } from './widgets';

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
        <div>
          <h1 className="text-2xl font-bold">관리자 대시보드</h1>
          <p className="text-sm text-slate-500 mt-1">
            {user?.name}님, 전체 시스템 현황과 위험 관제를 확인하세요.
          </p>
        </div>

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
              <SectionCard title="알림">
                <TodoBanner text="알림 도메인은 Phase S-4 이후에 추가됩니다." />
              </SectionCard>
            </div>

            <SectionCard title="회사별/현장별 위험 요약">
              <TodoBanner text="회사·현장별 위험 요약은 Phase S-4 서류 정책 강화 후 채워집니다." />
            </SectionCard>
          </>
        )}
      </div>
    </AppShell>
  );
}
