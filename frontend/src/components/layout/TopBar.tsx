import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { ROLE_LABEL } from '../../types/auth';
import { useUnreadCount } from '../../lib/useUnreadCount';

export type BreadcrumbItem = { label: string; to?: string };

type Props = {
  breadcrumb?: BreadcrumbItem[];
};

export default function TopBar({ breadcrumb }: Props) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  // shared hook — Sidebar 와 같은 module-level cache + interval 1개 공유 (중복 polling 제거)
  const unread = useUnreadCount();

  if (!user) return null;

  return (
    <header className="sticky top-0 z-30 bg-white border-b border-slate-200">
      <div className="flex items-center gap-4 h-[68px] px-8">
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

        <button
          type="button"
          onClick={() => navigate('/notifications')}
          className="relative shrink-0 p-2 rounded-lg hover:bg-slate-100"
          aria-label="알림"
          title="알림 보기"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-600">
            <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
          </svg>
          {unread > 0 && (
            <span className="absolute top-1 right-1 inline-flex items-center justify-center min-w-[16px] h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-bold">
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </button>

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
