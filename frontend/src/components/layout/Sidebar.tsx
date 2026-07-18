import { useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { useUnreadCount } from '../../lib/useUnreadCount';
import { useSupplierIncomingCounts } from '../../lib/useSupplierIncomingCounts';
import { useBpReceivedReviewCount } from '../../lib/useBpReceivedReviewCount';

type Props = {
  collapsed: boolean;
  onToggle: () => void;
  /** 모바일 드로어 열림 여부 + 닫기 (md 미만에서만 사용). */
  mobileOpen: boolean;
  onMobileClose: () => void;
};

type NavItem = {
  label: string;
  to: string;
  icon: ReactNode;
  badge?: number;
  disabled?: boolean;
  /** `/quotations` 와 `/quotations/open-bids` 같은 prefix 충돌 회피용 — true 면 정확 매칭일 때만 active. */
  end?: boolean;
};

type NavSection = {
  label: string;
  items: NavItem[];
  /** true 면 헤더 클릭으로 펼치고 접을 수 있다. localStorage 에 상태 저장. */
  collapsible?: boolean;
  /** 초기 펼침 상태 (기본 닫힘). 현재 라우트가 속한 허브는 이 값과 무관하게 자동 펼침. */
  defaultOpen?: boolean;
};

export default function Sidebar({ collapsed, onToggle, mobileOpen, onMobileClose }: Props) {
  const { user } = useAuth();
  const role = user?.role;

  const unread = useUnreadCount();
  const supplierCounts = useSupplierIncomingCounts(
    role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER' ? user?.company_id : null,
  );
  const bpReviewCount = useBpReceivedReviewCount(role === 'BP');

  const dashboardTo = role === 'ADMIN' ? '/admin/dashboard'
    : role === 'BP' ? '/bp/dashboard'
    : role === 'EQUIPMENT_SUPPLIER' ? '/equipment-supplier/dashboard'
    : role === 'MANPOWER_SUPPLIER' ? '/manpower-supplier/dashboard'
    : role === 'CLIENT' ? '/client/dashboard'
    : '/';

  // P4b 메뉴 다이어트 — 좌측 사이드바 = 매일 쓰는 대표 링크만(역할당 ≤12).
  // 형제 URL 은 SubNav 공유 탭바로, 저빈도 관리(내 회사·직원·하위공급사·DOCX·알림톡·공지·로그)는 우상단 ⚙ 로 이동.
  const home: NavItem[] = [
    { label: '대시보드', to: dashboardTo, icon: <IconGrid /> },
    { label: '알림', to: '/notifications', icon: <IconBell />, badge: unread > 0 ? unread : undefined },
  ];

  let hubSections: NavSection[];

  if (role === 'ADMIN') {
    hubSections = [
      { label: '대시보드', collapsible: true, defaultOpen: true, items: [
        ...home,
      ]},
      { label: '견적·계약', collapsible: true, defaultOpen: false, items: [
        { label: '견적 관리', to: '/quotations', icon: <IconClipboard /> },
      ]},
      { label: '서류', collapsible: true, defaultOpen: false, items: [
        { label: '서류 검토', to: '/admin/document-review', icon: <IconShield /> },
        // 서류관리·수집 요청·이행지시는 SubNav "서류함" 탭으로 통합 — 대표 1링크.
        { label: '서류함', to: '/document-management', icon: <IconDoc /> },
      ]},
      { label: '현장 운영', collapsible: true, defaultOpen: true, items: [
        { label: '작업계획서', to: '/work-plans', icon: <IconClipboard /> },
        { label: '현장 관리', to: '/sites', icon: <IconBuilding /> },
        { label: '전체 장비', to: '/equipment', icon: <IconTruck /> },
        { label: '전체 인원', to: '/persons', icon: <IconUsers /> },
      ]},
      { label: '정산', collapsible: true, defaultOpen: false, items: [
        { label: '월별 작업확인서', to: '/work-confirmations/monthly', icon: <IconClipboard /> },
      ]},
      // 안전 조각(설정·점검·알림·보고서)은 안전 상황판 탭으로 흡수 — 대표 1링크. (기존 라우트는 유지 → 딥링크 무해)
      { label: '안전', collapsible: true, defaultOpen: false, items: [
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
      ]},
      // 관리자 특성상 시스템 관리는 좌측 유지(§4-B). 발송·템플릿·로그 유틸은 우상단 ⚙ 로.
      { label: '시스템 관리', collapsible: true, defaultOpen: false, items: [
        { label: 'BP사 관리', to: '/admin/bp', icon: <IconBriefcase /> },
        { label: '공급사 관리', to: '/admin/suppliers', icon: <IconTruck /> },
        { label: '원청기관', to: '/admin/client-orgs', icon: <IconBuilding /> },
        { label: '사용자 관리', to: '/admin/users', icon: <IconUserCheck /> },
        { label: '서류종류 관리', to: '/admin/document-types', icon: <IconDoc /> },
        { label: '장비종류 서류', to: '/admin/equipment-type-docs', icon: <IconTruck /> },
        { label: '인력역할 서류', to: '/admin/person-role-docs', icon: <IconUsers /> },
        { label: '법정점검 템플릿', to: '/admin/safety-check-templates', icon: <IconShield /> },
      ]},
    ];
  } else if (role === 'BP') {
    hubSections = [
      { label: '대시보드', collapsible: true, defaultOpen: true, items: [
        ...home,
      ]},
      // 견적: 공개입찰·수신함은 SubNav 탭으로. 계약 조회는 유지 1링크.
      { label: '견적·계약', collapsible: true, defaultOpen: false, items: [
        { label: '견적', to: '/quotations', icon: <IconClipboard />, end: true },
        { label: '계약 조회', to: '/contracts', icon: <IconBriefcase /> },
      ]},
      // 서류 심사(받은+소급 탭) / 서류함(관리+수집+이행 탭) 2링크.
      { label: '서류', collapsible: true, defaultOpen: false, items: [
        { label: '서류 심사', to: '/document-reviews/received', icon: <IconShield />,
          badge: bpReviewCount || undefined },
        { label: '서류함', to: '/document-management', icon: <IconDoc /> },
      ]},
      // 투입 관리(대기·현황·장비·인원·받은요청·보낸점검 탭) / 자원(장비·인원·업체변경 탭).
      { label: '현장 운영', collapsible: true, defaultOpen: true, items: [
        { label: '작업 계획서', to: '/work-plans', icon: <IconClipboard />, end: true },
        { label: '투입 관리', to: '/work-plans/pending', icon: <IconClipboard /> },
        { label: '자원', to: '/equipment', icon: <IconTruck /> },
        { label: '현장 관리', to: '/sites', icon: <IconBuilding /> },
      ]},
      // 정산: 월별 작업확인서·작업확인 원장 탭.
      { label: '정산', collapsible: true, defaultOpen: false, items: [
        { label: '정산', to: '/work-confirmations/monthly', icon: <IconClipboard /> },
      ]},
      // 안전 조각(설정·점검·알림·보고서)은 안전 상황판 탭으로 흡수 — 대표 1링크. (기존 라우트는 유지 → 딥링크 무해)
      { label: '안전', collapsible: true, defaultOpen: false, items: [
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
      ]},
    ];
  } else if (role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER') {
    const isEquip = role === 'EQUIPMENT_SUPPLIER';
    const receivedBadge = (supplierCounts.supplements + supplierCounts.checks + supplierCounts.compliance) || undefined;
    hubSections = [
      { label: '대시보드', collapsible: true, defaultOpen: true, items: [
        ...home,
      ]},
      // 견적: 입찰·제안·발송·받은견적·템플릿·계약을 SubNav 탭으로 — 대표 1링크.
      { label: '견적·계약', collapsible: true, defaultOpen: false, items: [
        { label: '견적', to: '/quotations/open-bids', icon: <IconClipboard /> },
      ]},
      // 서류 허브 = 보완요청/자원점검/이행지시/서류수집/서류심사 5채널 통합(받은 요청·심사 보내기 포함) — 1링크.
      { label: '서류', collapsible: true, defaultOpen: false, items: [
        { label: '서류 허브', to: '/supplier/document-hub', icon: <IconDoc />, badge: receivedBadge },
      ]},
      // 자원(장비·인원·기투입·업체변경 탭) / 투입 요청(투입요청·작업일정·현장관리 탭). 파이프라인·일일확인서는 대표 링크.
      { label: '현장 운영', collapsible: true, defaultOpen: true, items: [
        { label: '자원 파이프라인', to: '/resource-pipeline', icon: <IconClipboard /> },
        { label: '자원', to: isEquip ? '/equipment' : '/persons', icon: isEquip ? <IconTruck /> : <IconUsers /> },
        { label: '일일 확인서', to: '/daily-work-logs', icon: <IconClipboard /> },
        { label: '투입 요청', to: '/field-deployments/supplier', icon: <IconClipboard /> },
      ]},
      // 정산: 투입 정산·월별 확인서(+장비 통계) 탭.
      { label: '정산', collapsible: true, defaultOpen: false, items: [
        { label: '정산', to: '/settlements', icon: <IconClipboard /> },
      ]},
      { label: '안전', collapsible: true, defaultOpen: false, items: [
        { label: '안전점검', to: '/safety-inspections', icon: <IconShield /> },
      ]},
    ];
  } else if (role === 'CLIENT') {
    // 원청(관제) 전용 간소 사이드바 — 읽기전용 관제 허브 + 알림.
    hubSections = [
      { label: '관제', collapsible: true, defaultOpen: true, items: [
        { label: '현장 관제', to: '/client/dashboard', icon: <IconGrid /> },
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
        { label: '알림', to: '/notifications', icon: <IconBell />, badge: unread > 0 ? unread : undefined },
      ]},
    ];
  } else {
    hubSections = [
      { label: '대시보드', collapsible: true, defaultOpen: true, items: home },
    ];
  }

  return (
    <>
      {/* 모바일 드로어 백드롭 — 열렸을 때만, 클릭 시 닫힘 (md+ 에선 숨김) */}
      {mobileOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 md:hidden" onClick={onMobileClose} aria-hidden="true" />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-[240px] flex-col bg-white border-r border-slate-200 transition-transform
          ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}
          md:static md:z-auto md:translate-x-0 md:shrink-0 md:transition-all ${collapsed ? 'md:w-[72px]' : 'md:w-[240px]'}`}
      >
      <div className="flex items-center gap-2 h-[68px] px-5 border-b border-slate-100">
        <div className="shrink-0 inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white">
          <IconHelmet />
        </div>
        {!collapsed && (
          <span className="text-base font-bold text-slate-900 truncate">현장관리 <span className="text-brand-600">Pro</span></span>
        )}
        <button type="button" onClick={onMobileClose} aria-label="메뉴 닫기"
          className="ml-auto shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 md:hidden">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-4 space-y-3">
        {hubSections.map((sec) => (
          <NavSectionItem key={sec.label} sec={sec} collapsed={collapsed} />
        ))}
      </nav>

      <button
        type="button"
        onClick={onToggle}
        className="border-t border-slate-100 px-5 py-3 text-sm text-slate-500 hover:bg-slate-50 hidden md:flex items-center gap-2"
      >
        <span className={`inline-block transition-transform ${collapsed ? 'rotate-180' : ''}`}>
          <IconChevronLeft />
        </span>
        {!collapsed && <span>메뉴 접기</span>}
      </button>
      </aside>
    </>
  );
}

function NavItemsList({ items, collapsed }: { items: NavItem[]; collapsed: boolean }) {
  const location = useLocation();
  const matchActive = (to: string) => {
    const [p, q] = to.split('?');
    if (location.pathname !== p) return false;
    if (q) return location.search === '?' + q;
    return location.search === '';
  };
  return (
    <ul className="space-y-0.5">
      {items.map((it) => (
        <li key={it.label + ':' + it.to}>
          <NavLink
            to={it.disabled ? '#' : it.to}
            end={it.end ?? it.to === '/'}
            onClick={(e) => { if (it.disabled) e.preventDefault(); }}
            className={() => {
              const isActive = !it.disabled && matchActive(it.to);
              return `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                it.disabled
                  ? 'text-slate-400 cursor-not-allowed'
                  : isActive
                    ? 'bg-brand-50 text-brand-700'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
              }`;
            }}
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
  );
}

function NavSectionItem({ sec, collapsed }: { sec: NavSection; collapsed: boolean }) {
  const location = useLocation();
  const storageKey = `sidebar.section.${sec.label}.open`;
  // null = 사용자가 아직 토글 안 함(자동 규칙 적용), true/false = 사용자 선택(localStorage 저장).
  const [userSet, setUserSet] = useState<boolean | null>(() => {
    if (!sec.collapsible) return true;
    const raw = window.localStorage.getItem(storageKey);
    return raw === '1' ? true : raw === '0' ? false : null;
  });

  // 현재 라우트가 이 허브에 속하면 자동 펼침(딥링크·이동 모두). 쿼리 무시하고 경로만 비교.
  const hasActive = sec.items.some((it) => {
    if (it.disabled) return false;
    const p = it.to.split('?')[0];
    return location.pathname === p || location.pathname.startsWith(p + '/');
  });

  // 허브 헤더 배지 = 그룹 내 항목 배지 합산.
  const badgeSum = sec.items.reduce((s, it) => s + (it.badge ?? 0), 0);

  const open = userSet !== null ? userSet : (hasActive || !!sec.defaultOpen);

  const toggle = () => {
    const next = !open;
    setUserSet(next);
    window.localStorage.setItem(storageKey, next ? '1' : '0');
  };

  if (collapsed) {
    return <NavItemsList items={sec.items} collapsed={true} />;
  }

  return (
    <div>
      <button type="button" onClick={toggle}
              className="w-full px-3 mb-1 flex items-center gap-2 text-[11px] font-semibold text-slate-500 hover:text-slate-800">
        <span className={`inline-block transition-transform ${open ? 'rotate-90' : ''}`}>
          <IconChevronRight />
        </span>
        <span className="uppercase tracking-wide">{sec.label}</span>
        <span className="flex-1 h-px bg-slate-200" />
        {badgeSum > 0 && (
          <span className="inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-brand-600 text-white text-[10px] font-bold">
            {badgeSum}
          </span>
        )}
        <span className="text-[10px] text-slate-400">{sec.items.length}</span>
      </button>
      {open && <NavItemsList items={sec.items} collapsed={false} />}
    </div>
  );
}

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
function IconChevronLeft() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="15 18 9 12 15 6" />
    </svg>
  );
}
function IconChevronRight() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="9 18 15 12 9 6" />
    </svg>
  );
}
