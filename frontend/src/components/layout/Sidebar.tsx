import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';

type Props = {
  collapsed: boolean;
  onToggle: () => void;
};

type NavItem = {
  label: string;
  to: string;
  icon: ReactNode;
  badge?: number;
  disabled?: boolean;
};

export default function Sidebar({ collapsed, onToggle }: Props) {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const topItems: NavItem[] = [
    { label: '대시보드', to: '/', icon: <IconGrid /> },
  ];

  const manageItems: NavItem[] = [
    { label: '현장 관리', to: '/sites', icon: <IconBuilding />, disabled: true },
    { label: '장비 관리', to: '/equipment', icon: <IconTruck /> },
    { label: '인원 관리', to: '/persons', icon: <IconUsers /> },
    { label: '출입 관리', to: '/access', icon: <IconDoor />, disabled: true },
    { label: '작업 관리', to: '/works', icon: <IconClipboard />, disabled: true },
    { label: '점검 관리', to: '/inspections', icon: <IconShield />, disabled: true },
  ];

  const adminItems: NavItem[] = isAdmin
    ? [
        { label: '사용자 관리', to: '/admin/users', icon: <IconUserCheck /> },
        { label: '회사 관리', to: '/admin/companies', icon: <IconBriefcase /> },
      ]
    : [];

  const supportItems: NavItem[] = [
    { label: '알림', to: '/notifications', icon: <IconBell />, badge: 3, disabled: true },
    { label: '보고서', to: '/reports', icon: <IconDoc />, disabled: true },
    { label: '설정', to: '/settings', icon: <IconCog />, disabled: true },
  ];

  return (
    <aside
      className={`flex shrink-0 flex-col bg-white border-r border-slate-200 transition-all ${
        collapsed ? 'w-[72px]' : 'w-[240px]'
      }`}
    >
      {/* 브랜드 */}
      <div className="flex items-center gap-2 h-[68px] px-5 border-b border-slate-100">
        <div className="shrink-0 inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white">
          <IconHelmet />
        </div>
        {!collapsed && (
          <span className="text-base font-bold text-slate-900 truncate">현장관리 <span className="text-brand-600">Pro</span></span>
        )}
      </div>

      {/* 메뉴 */}
      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-6">
        <NavGroup items={topItems} collapsed={collapsed} />
        <NavGroup label="관리" items={[...manageItems, ...adminItems]} collapsed={collapsed} />
        <NavGroup label="지원" items={supportItems} collapsed={collapsed} />
      </nav>

      {/* 메뉴 접기 */}
      <button
        type="button"
        onClick={onToggle}
        className="border-t border-slate-100 px-5 py-3 text-sm text-slate-500 hover:bg-slate-50 flex items-center gap-2"
      >
        <span className={`inline-block transition-transform ${collapsed ? 'rotate-180' : ''}`}>
          <IconChevronLeft />
        </span>
        {!collapsed && <span>메뉴 접기</span>}
      </button>
    </aside>
  );
}

function NavGroup({ label, items, collapsed }: { label?: string; items: NavItem[]; collapsed: boolean }) {
  if (items.length === 0) return null;
  return (
    <div>
      {label && !collapsed && (
        <div className="px-3 mb-2 text-xs font-semibold uppercase tracking-wider text-slate-400">{label}</div>
      )}
      <ul className="space-y-0.5">
        {items.map((it) => (
          <li key={it.to}>
            <NavLink
              to={it.disabled ? '#' : it.to}
              end={it.to === '/'}
              onClick={(e) => { if (it.disabled) e.preventDefault(); }}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  it.disabled
                    ? 'text-slate-400 cursor-not-allowed'
                    : isActive
                      ? 'bg-brand-50 text-brand-700'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                }`
              }
              title={collapsed ? it.label : undefined}
            >
              <span className="shrink-0 w-5 h-5">{it.icon}</span>
              {!collapsed && (
                <>
                  <span className="flex-1 truncate">{it.label}</span>
                  {it.badge != null && it.badge > 0 && (
                    <span className="inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-brand-600 text-white text-xs font-semibold">
                      {it.badge}
                    </span>
                  )}
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </div>
  );
}

/* ---------- 아이콘 (heroicons style 단순 SVG) ---------- */
function IconHelmet() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 18h18" /><path d="M5 18a7 7 0 0 1 14 0" /><path d="M9 5h6v6H9z" />
    </svg>
  );
}
function IconGrid() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" /><rect x="14" y="3" width="7" height="7" /><rect x="3" y="14" width="7" height="7" /><rect x="14" y="14" width="7" height="7" />
    </svg>
  );
}
function IconBuilding() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="4" y="2" width="16" height="20" /><path d="M9 22v-4h6v4M8 6h.01M16 6h.01M8 10h.01M16 10h.01M8 14h.01M16 14h.01" />
    </svg>
  );
}
function IconTruck() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="6" width="15" height="11" /><polygon points="16 8 20 8 23 11 23 17 16 17 16 8" /><circle cx="5.5" cy="18.5" r="2.5" /><circle cx="18.5" cy="18.5" r="2.5" />
    </svg>
  );
}
function IconUsers() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}
function IconDoor() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 22V2h16v20" /><path d="M4 22h16" /><circle cx="15" cy="12" r="1" />
    </svg>
  );
}
function IconClipboard() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" /><rect x="8" y="2" width="8" height="4" rx="1" />
    </svg>
  );
}
function IconShield() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" /><path d="m9 12 2 2 4-4" />
    </svg>
  );
}
function IconUserCheck() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="8.5" cy="7" r="4" /><polyline points="17 11 19 13 23 9" />
    </svg>
  );
}
function IconBriefcase() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="7" width="20" height="14" rx="2" /><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
    </svg>
  );
}
function IconBell() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </svg>
  );
}
function IconDoc() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="9" y1="13" x2="15" y2="13" /><line x1="9" y1="17" x2="13" y2="17" />
    </svg>
  );
}
function IconCog() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  );
}
function IconChevronLeft() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="15 18 9 12 15 6" />
    </svg>
  );
}
