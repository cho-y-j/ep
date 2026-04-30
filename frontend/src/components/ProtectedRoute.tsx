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

  if (roles && !roles.includes(user.role)) {
    return (
      <main className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="card max-w-md text-center">
          <h1 className="text-xl font-bold mb-2">권한 없음</h1>
          <p className="text-slate-500">이 페이지에 접근할 권한이 없습니다.</p>
        </div>
      </main>
    );
  }

  return <>{children}</>;
}
