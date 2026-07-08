import AppShell from '../../components/layout/AppShell';
import { useAuth } from '../auth/AuthContext';

/**
 * WORKER 역할은 모바일 앱 단계에서 본격 구현된다. 현재는 안내 placeholder.
 * (DashboardRedirect 가 자기 자신으로 redirect 하지 않도록 별도 페이지로 둠.)
 */
export default function WorkerDashboardPage() {
  const { user } = useAuth();
  return (
    <AppShell breadcrumb={[{ label: '대시보드' }]}>
      <div className="max-w-2xl mx-auto py-12">
        <div className="rounded-xl border border-slate-200 bg-white p-10 text-center">
          <h1 className="text-2xl font-bold text-slate-900 mb-3">작업자 대시보드</h1>
          <p className="text-slate-500 mb-6">
            {user?.name}님 환영합니다. 작업자 화면은 모바일 앱 단계에서 본격 구현됩니다.<br />
            현재 단계에서는 별도로 사용하는 기능이 없습니다.
          </p>
          <p className="text-xs text-slate-400">
            출결, 본인 일정, 안전 정보는 다음 Phase 에서 추가될 예정입니다.
          </p>
        </div>
      </div>
    </AppShell>
  );
}
