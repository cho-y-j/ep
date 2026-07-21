import { useState, useEffect, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { AxiosError } from 'axios';

type Location = { from?: string };

// 로그인 정보 저장(localStorage) — 내부용 B2B라 편의 우선. 저장 체크 시 이메일+비번 보관.
const LS_EMAIL = 'oneon.login.email';
const LS_PW = 'oneon.login.pw';

const TEST_ACCOUNTS = import.meta.env.DEV ? [
  { label: 'ADMIN', email: 'admin@skep.local', password: 'test1234', color: 'bg-slate-900 hover:bg-slate-800' },
  { label: 'BP', email: 'bp1@example.com', password: 'test1234', color: 'bg-brand-600 hover:bg-brand-700' },
  { label: '장비공급사', email: 'equipment1@example.com', password: 'test1234', color: 'bg-emerald-600 hover:bg-emerald-700' },
  { label: '인력공급사', email: 'manpower1@example.com', password: 'test1234', color: 'bg-amber-600 hover:bg-amber-700' },
] : [];

function EyeIcon({ off }: { off?: boolean }) {
  const base = { width: 18, height: 18, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  return off ? (
    <svg {...base}>
      <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
      <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68" />
      <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61" />
      <line x1="2" x2="22" y1="2" y2="22" />
    </svg>
  ) : (
    <svg {...base}>
      <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as Location | null)?.from ?? '/';

  // 저장된 로그인 정보 자동 채움
  useEffect(() => {
    const savedEmail = localStorage.getItem(LS_EMAIL);
    if (savedEmail) {
      setEmail(savedEmail);
      setPassword(localStorage.getItem(LS_PW) ?? '');
      setRemember(true);
    }
  }, []);

  async function doLogin(loginEmail: string, loginPassword: string) {
    setError(null);
    setSubmitting(true);
    try {
      await login(loginEmail, loginPassword);
      if (remember) {
        localStorage.setItem(LS_EMAIL, loginEmail);
        localStorage.setItem(LS_PW, loginPassword);
      } else {
        localStorage.removeItem(LS_EMAIL);
        localStorage.removeItem(LS_PW);
      }
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
          <h1 className="text-2xl font-bold">원온 로그인</h1>
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
          <div className="relative mt-1">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              className="w-full rounded-lg border border-slate-300 px-3 py-2 pr-10 focus:outline-none focus:ring-2 focus:ring-brand-500"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-400 hover:text-slate-600"
              aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
            >
              <EyeIcon off={showPassword} />
            </button>
          </div>
        </label>

        <label className="flex items-center gap-2 text-sm text-slate-600 select-none">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
          />
          로그인 정보 저장 (다음부터 자동 입력)
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

        {import.meta.env.DEV && (
          <div className="pt-4 border-t border-dashed border-slate-300">
            <p className="text-xs text-slate-400 mb-2">테스트 빠른 로그인 (시연용)</p>
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
