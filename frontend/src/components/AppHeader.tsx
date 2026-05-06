import { Link, NavLink } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthContext';
import { ROLE_LABEL } from '../types/auth';

export default function AppHeader() {
  const { user, logout } = useAuth();
  if (!user) return null;

  const navLinkClass = ({ isActive }: { isActive: boolean }) =>
    `text-sm transition-colors ${
      isActive ? 'text-brand-700 font-medium' : 'text-slate-600 hover:text-slate-900'
    }`;

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-30">
      <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
        <Link to="/" className="text-xl font-bold hover:text-brand-700 transition-colors">
          SKEP v2
        </Link>
        <nav className="flex items-center gap-5">
          <NavLink to="/" end className={navLinkClass}>
            홈
          </NavLink>
          {(user.role === 'ADMIN' || user.role === 'EQUIPMENT_SUPPLIER') && (
            <NavLink to="/equipment" className={navLinkClass}>
              장비 관리
            </NavLink>
          )}
          {(user.role === 'ADMIN' || user.role === 'EQUIPMENT_SUPPLIER' || user.role === 'MANPOWER_SUPPLIER') && (
            <NavLink to="/persons" className={navLinkClass}>
              {user.role === 'EQUIPMENT_SUPPLIER' ? '조종원 관리'
                : user.role === 'MANPOWER_SUPPLIER' ? '작업자 관리'
                : '인원 관리'}
            </NavLink>
          )}
          {user.role === 'ADMIN' && (
            <>
              <NavLink to="/admin/users" className={navLinkClass}>
                사용자 관리
              </NavLink>
              <NavLink to="/admin/companies" className={navLinkClass}>
                회사 관리
              </NavLink>
            </>
          )}
          <span className="text-sm text-slate-400 truncate max-w-[180px]" title={`${user.name} · ${ROLE_LABEL[user.role]}`}>
            {user.name} · {ROLE_LABEL[user.role]}
          </span>
          <button
            onClick={() => logout()}
            className="text-sm text-slate-600 hover:text-slate-900"
          >
            로그아웃
          </button>
        </nav>
      </div>
    </header>
  );
}
