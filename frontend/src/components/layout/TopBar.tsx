import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { ROLE_LABEL } from '../../types/auth';
import { useUnreadCount } from '../../lib/useUnreadCount';

export type BreadcrumbItem = { label: string; to?: string };

type Props = {
  breadcrumb?: BreadcrumbItem[];
  /** 모바일 햄버거 → 사이드바 드로어 열기 (md 미만에서만 노출). */
  onMenuClick: () => void;
};

type UtilItem = { label: string; to: string; icon: React.ReactNode };
type UtilSection = { title: string; items: UtilItem[] };

// 24x24 stroke 아이콘 — 공통 svg 래퍼 안의 path 만 정의.
const ICON = {
  building: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M9 9h.01M15 9h.01M9 13h.01M15 13h.01M9 17h6" /></>,
  users: <><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></>,
  network: <><circle cx="18" cy="5" r="3" /><circle cx="6" cy="12" r="3" /><circle cx="18" cy="19" r="3" /><line x1="8.59" y1="13.51" x2="15.42" y2="17.49" /><line x1="15.41" y1="6.51" x2="8.59" y2="10.49" /></>,
  file: <><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" /><polyline points="14 2 14 8 20 8" /><line x1="16" y1="13" x2="8" y2="13" /><line x1="16" y1="17" x2="8" y2="17" /></>,
  message: <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />,
  megaphone: <><path d="M3 11l18-5v12L3 14v-3z" /><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6" /></>,
  list: <><line x1="8" y1="6" x2="21" y2="6" /><line x1="8" y1="12" x2="21" y2="12" /><line x1="8" y1="18" x2="21" y2="18" /><line x1="3" y1="6" x2="3.01" y2="6" /><line x1="3" y1="12" x2="3.01" y2="12" /><line x1="3" y1="18" x2="3.01" y2="18" /></>,
  truck: <><rect x="1" y="3" width="15" height="13" /><polygon points="16 8 20 8 23 11 23 16 16 16 16 8" /><circle cx="5.5" cy="18.5" r="2.5" /><circle cx="18.5" cy="18.5" r="2.5" /></>,
} as const;

/** ⚙ 유틸 소메뉴 — 저빈도 관리 기능을 섹션으로 묶어 슬라이드 패널에 노출. 역할별 필터. */
function utilSections(role: string, isMaster: boolean): UtilSection[] {
  const isBp = role === 'BP';
  const isEquip = role === 'EQUIPMENT_SUPPLIER';
  const isSupplier = isEquip || role === 'MANPOWER_SUPPLIER';

  const company: UtilItem[] = [];
  if (isBp || isSupplier) company.push({ label: '내 회사', to: '/my-company', icon: ICON.building });
  if ((isBp || isSupplier) && isMaster) company.push({ label: '직원 관리', to: '/company/users', icon: ICON.users });
  if (isEquip && isMaster) company.push({ label: '협력업체 관리', to: '/sub-suppliers', icon: ICON.network });
  if (isEquip && isMaster) company.push({ label: '취급 장비종류', to: '/settings/equipment-types', icon: ICON.truck });

  const tools: UtilItem[] = [];
  if (role === 'ADMIN' || isBp) {
    tools.push({ label: 'DOCX 템플릿', to: '/admin/docx-templates', icon: ICON.file });
    tools.push({ label: '알림톡 발송', to: '/alimtalk', icon: ICON.message });
    tools.push({ label: '공지사항 발송', to: '/admin/announcements', icon: ICON.megaphone });
  }

  const records: UtilItem[] = [];
  if (role === 'ADMIN' || isBp || isSupplier) records.push({ label: '감사 로그', to: '/audit-logs', icon: ICON.list });

  return [
    { title: '회사 관리', items: company },
    { title: '도구', items: tools },
    { title: '기록', items: records },
  ].filter((s) => s.items.length > 0);
}

export default function TopBar({ breadcrumb, onMenuClick }: Props) {
  const { user, company, logout } = useAuth();
  const navigate = useNavigate();
  // shared hook — Sidebar 와 같은 module-level cache + interval 1개 공유 (중복 polling 제거)
  const unread = useUnreadCount();
  const [panelOpen, setPanelOpen] = useState(false);

  // ESC 로 패널 닫힘.
  useEffect(() => {
    if (!panelOpen) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setPanelOpen(false); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [panelOpen]);

  if (!user) return null;

  const sections = utilSections(user.role, !!user.is_company_admin);

  return (
    <header className="sticky top-0 z-30 bg-white border-b border-slate-200">
      <div className="flex items-center gap-3 md:gap-4 h-[68px] px-4 md:px-8">
        <button
          type="button"
          onClick={onMenuClick}
          aria-label="메뉴 열기"
          className="shrink-0 -ml-1 rounded-lg p-2 text-slate-600 hover:bg-slate-100 md:hidden"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 12h18M3 6h18M3 18h18" /></svg>
        </button>
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

        {/* 프로필 = 슬라이드 패널 트리거 (기존 ⚙ 소메뉴 통합) */}
        <button
          type="button"
          onClick={() => setPanelOpen(true)}
          className="flex items-center gap-2 pl-3 pr-2 py-1.5 rounded-lg hover:bg-slate-50"
          aria-label="내 메뉴 열기"
          aria-haspopup="dialog"
          aria-expanded={panelOpen}
          title="내 메뉴 · 관리"
        >
          <span className="inline-flex h-9 w-9 items-center justify-center rounded-full bg-slate-200 text-slate-600 text-sm font-semibold">
            {user.name.charAt(0)}
          </span>
          <div className="text-left max-w-[120px] hidden sm:block">
            <div className="text-sm font-semibold text-slate-900 truncate">{user.name}</div>
            <div className="text-xs text-slate-500 truncate">{ROLE_LABEL[user.role]}</div>
          </div>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-400">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>
      </div>

      {/* 백드롭 */}
      <div
        onClick={() => setPanelOpen(false)}
        className={`fixed inset-0 z-40 bg-black/40 transition-opacity duration-200 ${
          panelOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
        aria-hidden="true"
      />

      {/* 우측 슬라이드-인 패널 */}
      <aside
        role="dialog"
        aria-label="내 메뉴"
        className={`fixed top-0 right-0 z-50 h-full w-full sm:max-w-[320px] bg-slate-50 shadow-2xl transition-transform duration-200 ease-out flex flex-col ${
          panelOpen ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <div className="flex items-center justify-between px-4 h-[68px] shrink-0">
          <span className="text-sm font-semibold text-slate-500">내 메뉴</span>
          <button
            type="button"
            onClick={() => setPanelOpen(false)}
            aria-label="닫기"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-200"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-4 pb-6 space-y-5">
          {/* 프로필 카드 */}
          <div className="rounded-2xl bg-white p-4 shadow-sm">
            <div className="flex items-center gap-3">
              <span className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700 text-lg font-bold">
                {user.name.charAt(0)}
              </span>
              <div className="min-w-0">
                <div className="text-base font-bold text-slate-900 truncate">{user.name}</div>
                <div className="mt-0.5 flex items-center gap-1.5">
                  <span className="inline-flex items-center rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-semibold text-brand-700">
                    {ROLE_LABEL[user.role]}
                  </span>
                </div>
              </div>
            </div>
            {company?.name && (
              <div className="mt-3 flex items-center gap-1.5 text-sm text-slate-500">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{ICON.building}</svg>
                <span className="truncate">{company.name}</span>
              </div>
            )}
            <button
              type="button"
              onClick={() => { setPanelOpen(false); logout(); }}
              className="mt-4 w-full rounded-xl border border-slate-200 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 flex items-center justify-center gap-2"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" /></svg>
              로그아웃
            </button>
          </div>

          {/* 섹션 그룹 */}
          {sections.map((section) => (
            <div key={section.title}>
              <div className="px-1 mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">{section.title}</div>
              <div className="rounded-2xl bg-white p-1.5 shadow-sm">
                {section.items.map((it) => (
                  <Link
                    key={it.to}
                    to={it.to}
                    onClick={() => setPanelOpen(false)}
                    className="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-100 transition-colors"
                  >
                    <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{it.icon}</svg>
                    </span>
                    <span className="flex-1 truncate">{it.label}</span>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="text-slate-300"><polyline points="9 18 15 12 9 6" /></svg>
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>
      </aside>
    </header>
  );
}
