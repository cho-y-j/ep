import { useAuth } from '../auth/AuthContext';
import { COMPANY_TYPE_LABEL } from '../../types/auth';
import { Link } from 'react-router-dom';
import AppHeader from '../../components/AppHeader';

export default function HomePage() {
  const { user, company } = useAuth();
  if (!user) return null;

  return (
    <div className="min-h-screen bg-slate-50">
      <AppHeader />

      <section className="max-w-5xl mx-auto px-6 py-10 space-y-6">
        <div className="card">
          <h2 className="text-lg font-bold mb-2">환영합니다, {user.name}님</h2>
          <p className="text-slate-500">기능은 점진적으로 추가됩니다.</p>
        </div>

        {company && (
          <div className="card">
            <h3 className="text-base font-bold mb-3">소속 회사</h3>
            <dl className="grid grid-cols-[100px_1fr] gap-y-2 text-sm">
              <dt className="text-slate-500">회사명</dt>
              <dd className="text-slate-900">{company.name}</dd>
              <dt className="text-slate-500">사업자번호</dt>
              <dd className="text-slate-900">{company.business_number}</dd>
              <dt className="text-slate-500">유형</dt>
              <dd className="text-slate-900">{COMPANY_TYPE_LABEL[company.type]}</dd>
              <dt className="text-slate-500">권한</dt>
              <dd className="text-slate-900">
                {user.is_company_admin ? (
                  <span className="inline-flex px-2 py-0.5 rounded bg-blue-100 text-blue-700 text-xs font-medium">
                    회사 관리자
                  </span>
                ) : (
                  <span className="text-slate-500">일반 직원</span>
                )}
              </dd>
            </dl>
          </div>
        )}

        {user.role === 'ADMIN' && (
          <div className="card">
            <h3 className="text-base font-bold mb-2">관리자 메뉴</h3>
            <div className="flex flex-wrap gap-2 mt-3">
              <Link to="/admin/users" className="btn-primary">사용자 관리</Link>
              <Link to="/admin/companies" className="btn-primary">회사 관리</Link>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
