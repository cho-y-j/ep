import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import type { ReactNode } from 'react';
import type { Role } from '../types/auth';

type Props = {
  children: ReactNode;
  roles?: Role[];
};

export default function ProtectedRoute({ children, roles }: Props) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center text-slate-400">
        loading...
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  // 로그인은 됐는데 역할이 안 맞으면(예: 다른 역할 계정으로 재로그인해 이전 위치로 복귀한 경우)
  // 막다른 "권한 없음" 대신 본인 역할 대시보드로 보낸다. (/ → DashboardRedirect → 역할별 대시보드)
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
