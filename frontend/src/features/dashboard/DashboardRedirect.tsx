import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const ROUTE_BY_ROLE: Record<string, string> = {
  ADMIN: '/admin/dashboard',
  BP: '/bp/dashboard',
  EQUIPMENT_SUPPLIER: '/equipment-supplier/dashboard',
  MANPOWER_SUPPLIER: '/manpower-supplier/dashboard',
  WORKER: '/worker/dashboard',
};

/**
 * `/` 또는 `/dashboard` 진입 시 사용자 역할에 맞는 dashboard 로 redirect.
 * AuthProvider 가 user 를 로드 중이면 짧게 로딩만 보여준다.
 */
export default function DashboardRedirect() {
  const { user, loading } = useAuth();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <p className="text-sm text-slate-400">불러오는 중...</p>
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const target = ROUTE_BY_ROLE[user.role] ?? '/admin/dashboard';
  return <Navigate to={target} replace />;
}
