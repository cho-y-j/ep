import { Link } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { ROLE_LABEL } from '../../types/auth';

export type BreadcrumbItem = { label: string; to?: string };

type Props = {
  breadcrumb?: BreadcrumbItem[];
  searchPlaceholder?: string;
};

export default function TopBar({ breadcrumb, searchPlaceholder = '검색어를 입력하세요.' }: Props) {
  const { user, logout } = useAuth();
  if (!user) return null;

  return (
    <header className="sticky top-0 z-30 bg-white border-b border-slate-200">
      <div className="flex items-center gap-4 h-[68px] px-8">
        {/* breadcrumb */}
        <nav className="flex items-center gap-2 text-sm flex-1 min-w-0 truncate">
          {(breadcrumb ?? []).map((it, i) => {
            const isLast = i === (breadcrumb?.length ?? 0) - 1;
            return (
              <span key={`${it.label}-${i}`} className="flex items-center gap-2">
                {i > 0 && <span className="text-slate-300">›</span>}
                {it.to && !isLast ? (
                  <Link to={it.to} className="text-slate-500 hover:text-slate-900">{it.label}</Link>
                ) : (
                  <span className={isLast ? 'text-slate-900 font-semibold' : 'text-slate-500'}>
                    {it.label}
                  </span>
                )}
              </span>
            );
          })}
        </nav>

        {/* 검색창 */}
        <div className="relative max-w-sm w-full">
          <input
            type="text"
            placeholder={searchPlaceholder}
            className="w-full pl-9 pr-12 py-2 rounded-lg border border-slate-200 bg-slate-50 focus:bg-white focus:border-brand-300 focus:ring-2 focus:ring-brand-100 outline-none text-sm"
          />
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">⌕</span>
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 bg-white border border-slate-200 rounded px-1.5 py-0.5 font-mono">⌘K</span>
        </div>

        {/* 알림 */}
        <button type="button" className="relative shrink-0 p-2 rounded-lg hover:bg-slate-100" aria-label="알림">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-600">
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          <span className="absolute top-1 right-1 inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-bold">3</span>
        </button>

        {/* 도움말 */}
        <button type="button" className="shrink-0 p-2 rounded-lg hover:bg-slate-100" aria-label="도움말">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-500">
            <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
          </svg>
        </button>

        {/* 사용자 프로필 */}
        <button
          type="button"
          onClick={() => logout()}
          className="flex items-center gap-2 pl-3 pr-2 py-1.5 rounded-lg hover:bg-slate-50"
          title="로그아웃"
        >
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-slate-200 text-slate-600 text-sm font-semibold">
            {user.name.charAt(0)}
          </span>
          <div className="text-left max-w-[120px]">
            <div className="text-sm font-semibold text-slate-900 truncate">{user.name}</div>
            <div className="text-xs text-slate-500 truncate">{ROLE_LABEL[user.role]}</div>
          </div>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>
      </div>
    </header>
  );
}
