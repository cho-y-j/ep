import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { ROLE_LABEL, SIGNUP_ROLES, roleRequiresCompany, type Role } from '../types/auth';
import CompanyFields from '../components/forms/CompanyFields';
import PhoneInput from '../components/forms/PhoneInput';
import { AxiosError } from 'axios';

export default function SignupPage() {
  const [form, setForm] = useState({
    email: '',
    password: '',
    name: '',
    phone: '',
    role: 'BP' as Role,
    companyName: '',
    businessNumber: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { signup } = useAuth();
  const navigate = useNavigate();

  const needsCompany = roleRequiresCompany(form.role);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signup({
        email: form.email,
        password: form.password,
        name: form.name,
        phone: form.phone || undefined,
        role: form.role,
        companyName: needsCompany ? form.companyName : undefined,
        businessNumber: needsCompany ? form.businessNumber : undefined,
      });
      navigate('/pending-approval', { replace: true });
    } catch (err) {
      if (err instanceof AxiosError) {
        setError(err.response?.data?.message ?? '회원가입 실패');
      } else {
        setError('회원가입 실패');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function update<K extends keyof typeof form>(key: K, value: (typeof form)[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50 px-4 py-8">
      <form onSubmit={onSubmit} className="card w-full max-w-md space-y-4">
        <div>
          <h1 className="text-2xl font-bold">회원가입</h1>
          <p className="text-sm text-slate-500 mt-1">가입 후 관리자 승인이 필요합니다.</p>
        </div>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">이메일</span>
          <input
            type="email"
            value={form.email}
            onChange={(e) => update('email', e.target.value)}
            required
            className="input mt-1"
          />
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">비밀번호 (8자 이상)</span>
          <input
            type="password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            required
            minLength={8}
            className="input mt-1"
          />
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">이름</span>
          <input
            type="text"
            value={form.name}
            onChange={(e) => update('name', e.target.value)}
            required
            className="input mt-1"
          />
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">휴대폰 (선택)</span>
          <div className="mt-1">
            <PhoneInput value={form.phone} onChange={(v) => update('phone', v)} />
          </div>
        </label>

        <label className="block">
          <span className="text-sm font-medium text-slate-700">역할</span>
          <select
            value={form.role}
            onChange={(e) => update('role', e.target.value as Role)}
            className="input mt-1 bg-white"
          >
            {SIGNUP_ROLES.map((r) => (
              <option key={r} value={r}>
                {ROLE_LABEL[r]}
              </option>
            ))}
          </select>
        </label>

        {needsCompany && (
          <div className="border-t border-dashed border-slate-300 pt-4 space-y-4">
            <p className="text-xs text-slate-500">
              회사 정보 — 같은 사업자번호로 다른 사람이 이미 가입했다면 그 회사에 합류합니다.
            </p>
            <CompanyFields
              values={{ name: form.companyName, businessNumber: form.businessNumber }}
              onChange={(next) => setForm((prev) => ({ ...prev, companyName: next.name, businessNumber: next.businessNumber }))}
              required
            />
          </div>
        )}

        {error && (
          <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>
        )}

        <button type="submit" disabled={submitting} className="btn-primary w-full disabled:opacity-50">
          {submitting ? '가입 중...' : '가입하기'}
        </button>

        <p className="text-sm text-center text-slate-500">
          이미 계정이 있으신가요?{' '}
          <Link to="/login" className="text-brand-600 hover:underline">
            로그인
          </Link>
        </p>
      </form>
    </main>
  );
}
