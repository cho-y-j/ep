import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { AxiosError } from 'axios';

type Location = { from?: string };

const TEST_ACCOUNTS = [
  { label: 'ADMIN 로그인', email: 'admin@skep.local', password: 'change-me-now', color: 'bg-slate-900 hover:bg-slate-800' },
  { label: 'BP 로그인', email: 'bp1@example.com', password: 'testpass123', color: 'bg-brand-600 hover:bg-brand-700' },
];

function isLocalEnv(): boolean {
  if (typeof window === 'undefined') return false;
  const host = window.location.hostname;
  return host === 'localhost' || host === '127.0.0.1' || host.endsWith('.local');
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as Location | null)?.from ?? '/';

  async function doLogin(loginEmail: string, loginPassword: string) {
    setError(null);
    setSubmitting(true);
    try {
      await login(loginEmail, loginPassword);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) {
        const code = err.response?.data?.code;
        if (code === 'ACCOUNT_DISABLED') {
          navigate('/pending-approval', { replace: true });
          return;
        }
        setError(err.response?.data?.message ?? '로그인 실패');
      } else {
        setError('로그인 실패');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void doLogin(email, password);
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <form onSubmit={onSubmit} className="card w-full max-w-md space-y-4">
        <div>
          <h1 className="text-2xl font-bold">SKEP 로그인</h1>
          <p className="text-sm text-slate-500 mt-1">이메일과 비밀번호를 입력하세요.</p>
        </div>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">이메일</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">비밀번호</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-brand-500"
          />
        </label>

        {error && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
        )}

        <button type="submit" disabled={submitting} className="btn-primary w-full disabled:opacity-50">
          {submitting ? '로그인 중...' : '로그인'}
        </button>

        <p className="text-sm text-center text-slate-500">
          계정이 없으신가요?{' '}
          <Link to="/signup" className="text-brand-600 hover:underline">
            회원가입
          </Link>
        </p>

        {isLocalEnv() && (
          <div className="pt-4 border-t border-dashed border-slate-300">
            <p className="text-xs text-slate-400 mb-2">테스트 빠른 로그인 (개발 환경 전용)</p>
            <div className="grid grid-cols-2 gap-2">
              {TEST_ACCOUNTS.map((acc) => (
                <button
                  key={acc.email}
                  type="button"
                  disabled={submitting}
                  onClick={() => doLogin(acc.email, acc.password)}
                  className={`py-2 rounded-lg text-white text-sm font-medium transition-colors disabled:opacity-50 ${acc.color}`}
                >
                  {acc.label}
                </button>
              ))}
            </div>
          </div>
        )}
      </form>
    </main>
  );
}
