import { useAuth } from '../contexts/AuthContext';
import { ROLE_LABEL } from '../types/auth';
import { Link } from 'react-router-dom';

export default function HomePage() {
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <main className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
          <h1 className="text-xl font-bold">SKEP v2</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-500">
              {user.name} ({ROLE_LABEL[user.role]})
            </span>
            {user.role === 'ADMIN' && (
              <Link to="/admin/users" className="text-sm text-brand-600 hover:underline">
                사용자 관리
              </Link>
            )}
            <button onClick={() => logout()} className="text-sm text-slate-600 hover:text-slate-900">
              로그아웃
            </button>
          </div>
        </div>
      </header>

      <section className="max-w-5xl mx-auto px-6 py-10">
        <div className="card">
          <h2 className="text-lg font-bold mb-2">환영합니다, {user.name}님</h2>
          <p className="text-slate-500">기능은 점진적으로 추가됩니다.</p>
          <pre className="text-xs bg-slate-100 p-3 rounded mt-4">
            {JSON.stringify(user, null, 2)}
          </pre>
        </div>
      </section>
    </main>
  );
}
