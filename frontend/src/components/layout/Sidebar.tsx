import { Fragment, useEffect, useState, type ReactNode } from 'react';
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
  /** 2단 패널 내 소제목 그룹. 같은 값 연속이면 첫 항목 위에만 소제목 렌더(긴 대분류 가독용). */
  section?: string;
};

/**
 * 미리캔버스식 2단 내비의 대분류.
 * - `to` 있음 → 단독 대분류: 클릭 시 바로 라우트 이동(2단 패널 없음).
 * - `items` 있음 → 패널 대분류: 클릭 시 오른쪽 2단에 세부 항목 노출(옆으로 펼침).
 */
type NavGroup = {
  label: string;
  icon: ReactNode;
  to?: string;
  badge?: number;
  end?: boolean;
  items?: NavItem[];
};

export default function Sidebar({ collapsed, onToggle, mobileOpen, onMobileClose }: Props) {
  const { user } = useAuth();
  const role = user?.role;
  const isMaster = !!user?.is_company_admin;
  const location = useLocation();

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

  // 단독 대분류(대시보드·알림) — 1단에서 클릭 시 바로 이동. 알림 badge(unread) 유지.
  const standalone: NavGroup[] = [
    { label: '대시보드', to: dashboardTo, icon: <IconGrid /> },
    { label: '알림', to: '/notifications', icon: <IconBell />, badge: unread > 0 ? unread : undefined },
  ];

  // 기존 사이드바 섹션(그룹)을 그대로 대분류로 승격. 라우트·항목 보존(데드링크 0).
  let groups: NavGroup[];

  if (role === 'ADMIN') {
    groups = [
      ...standalone,
      { label: '견적·계약', icon: <IconBriefcase />, items: [
        { label: '견적 관리', to: '/quotations', icon: <IconClipboard /> },
      ]},
      { label: '서류', icon: <IconDoc />, items: [
        { label: '서류 검토', to: '/admin/document-review', icon: <IconShield /> },
        { label: '서류 관리', to: '/document-management', icon: <IconDoc /> },
        { label: '수집 요청', to: '/document-collections', icon: <IconDoc /> },
        { label: '이행지시', to: '/compliance-orders', icon: <IconShield /> },
      ]},
      { label: '현장 운영', icon: <IconBuilding />, items: [
        { label: '작업계획서', to: '/work-plans', icon: <IconClipboard /> },
        { label: '현장 관리', to: '/sites', icon: <IconBuilding /> },
        { label: '장비', to: '/equipment', icon: <IconTruck /> },
        { label: '인원', to: '/persons', icon: <IconUsers /> },
      ]},
      { label: '정산', icon: <IconMoney />, items: [
        { label: '월별 확인서(폰 서명분)', to: '/work-confirmations/monthly', icon: <IconClipboard /> },
      ]},
      { label: '안전', icon: <IconShield />, items: [
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
        { label: '혈압 체크인', to: '/bp-checkins', icon: <IconShield /> },
      ]},
      { label: '시스템 관리', icon: <IconSettings />, items: [
        { label: 'BP사 관리', to: '/admin/bp', icon: <IconBriefcase /> },
        { label: '공급사 관리', to: '/admin/suppliers', icon: <IconTruck /> },
        { label: '원청기관', to: '/admin/client-orgs', icon: <IconBuilding /> },
        { label: '가입 승인', to: '/admin/signup-approvals', icon: <IconUserCheck /> },
        { label: '상담 요청', to: '/admin/consultations', icon: <IconClipboard /> },
        { label: '사용자 관리', to: '/admin/users', icon: <IconUserCheck /> },
        { label: '서류종류 관리', to: '/admin/document-types', icon: <IconDoc /> },
        { label: '장비종류 서류', to: '/admin/equipment-type-docs', icon: <IconTruck /> },
        { label: '인력역할 서류', to: '/admin/person-role-docs', icon: <IconUsers /> },
        { label: '법정점검 템플릿', to: '/admin/safety-check-templates', icon: <IconShield /> },
      ]},
    ];
  } else if (role === 'BP') {
    groups = [
      ...standalone,
      { label: '견적·계약', icon: <IconBriefcase />, items: [
        { label: '견적', to: '/quotations', icon: <IconClipboard />, end: true },
        { label: '수신함', to: '/inbox', icon: <IconClipboard /> },
        { label: '계약 조회', to: '/contracts', icon: <IconBriefcase /> },
      ]},
      { label: '서류', icon: <IconDoc />, items: [
        { label: '서류 심사', to: '/document-reviews/received', icon: <IconShield />,
          badge: bpReviewCount || undefined },
        { label: '소급 승인', to: '/resource-onboardings/bp', icon: <IconUserCheck /> },
        { label: '서류 관리', to: '/document-management', icon: <IconDoc /> },
        { label: '수집 요청', to: '/document-collections', icon: <IconDoc /> },
        { label: '이행지시', to: '/compliance-orders', icon: <IconShield /> },
      ]},
      { label: '현장 운영', icon: <IconBuilding />, items: [
        { label: '작업 계획서', to: '/work-plans', icon: <IconClipboard />, end: true, section: '작업·현장' },
        { label: '현장 관리', to: '/sites', icon: <IconBuilding />, section: '작업·현장' },
        { label: '투입 대기', to: '/work-plans/pending', icon: <IconClipboard />, section: '투입' },
        { label: '투입 현황', to: '/work-plans/active', icon: <IconClipboard />, section: '투입' },
        { label: '투입 장비', to: '/dispatched-equipment', icon: <IconTruck />, section: '투입' },
        { label: '투입 인원', to: '/dispatched-persons', icon: <IconUsers />, section: '투입' },
        { label: '받은 투입 요청', to: '/field-deployments/bp', icon: <IconClipboard />, section: '투입' },
        { label: '보낸 점검 요청', to: '/resource-checks/bp', icon: <IconShield />, section: '자원' },
        { label: '장비', to: '/equipment', icon: <IconTruck />, section: '자원' },
        { label: '인원', to: '/persons', icon: <IconUsers />, section: '자원' },
        { label: '업체변경 신청서', to: '/resource-change-requests', icon: <IconClipboard />, section: '자원' },
      ]},
      { label: '정산', icon: <IconMoney />, items: [
        { label: '작업확인 원장', to: '/daily-work-logs/bp', icon: <IconClipboard /> },
        { label: '월별 확인서(폰 서명분)', to: '/work-confirmations/monthly', icon: <IconClipboard /> },
      ]},
      { label: '안전', icon: <IconShield />, items: [
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
        { label: '혈압 체크인', to: '/bp-checkins', icon: <IconShield /> },
      ]},
      { label: '회사 관리', icon: <IconBuilding />, items: [
        { label: '내 회사', to: '/my-company', icon: <IconBuilding /> },
        ...(isMaster ? [{ label: '직원 관리', to: '/company/users', icon: <IconUsers /> }] : []),
      ]},
    ];
  } else if (role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER') {
    const isEquip = role === 'EQUIPMENT_SUPPLIER';
    // 서류 5채널을 2단으로 직접 노출(구 "서류 허브" 해체). 각 채널 배지 개별 표기.
    const resourceItems: NavItem[] = isEquip
      ? [
          { label: '장비', to: '/equipment', icon: <IconTruck />, section: '자원' },
          { label: '조종원', to: '/persons', icon: <IconUsers />, section: '자원' },
        ]
      : [
          { label: '인원', to: '/persons', icon: <IconUsers />, section: '자원' },
        ];
    const settlementItems: NavItem[] = [
      { label: '정산', to: '/settlements', icon: <IconClipboard /> },
      { label: '월별 확인서(폰 서명분)', to: '/work-confirmations/monthly', icon: <IconClipboard /> },
      ...(isEquip ? [{ label: '장비 투입 통계', to: '/equipment-stats', icon: <IconTruck /> }] : []),
    ];
    groups = [
      ...standalone,
      { label: '견적·계약', icon: <IconBriefcase />, items: [
        { label: '공개 입찰', to: '/quotations/open-bids', icon: <IconClipboard /> },
        { label: '내 제안', to: '/my-proposals', icon: <IconClipboard /> },
        { label: '내 발송', to: '/outgoing-quotations', icon: <IconClipboard /> },
        { label: '받은 견적', to: '/quotations', icon: <IconClipboard />, end: true },
        { label: '견적 템플릿', to: '/quote-templates', icon: <IconClipboard /> },
        { label: '계약 관리', to: '/contracts', icon: <IconBriefcase /> },
      ]},
      { label: '서류', icon: <IconDoc />, items: [
        { label: '서류 관리(만료·검증)', to: '/document-management', icon: <IconDoc />, badge: supplierCounts.supplements || undefined },
        { label: '서류심사', to: '/document-review-send', icon: <IconDoc /> },
        { label: '서류수집', to: '/document-collections', icon: <IconDoc />, badge: supplierCounts.collections || undefined },
        { label: '자원점검', to: '/resource-checks/supplier', icon: <IconShield />, badge: supplierCounts.checks || undefined },
        { label: '검사 관리', to: '/resource-checks/bp', icon: <IconShield /> },
        { label: '이행지시', to: '/compliance-orders', icon: <IconShield />, badge: supplierCounts.compliance || undefined },
      ]},
      { label: '현장 운영', icon: <IconBuilding />, items: [
        ...resourceItems,
        { label: '기투입 등록', to: '/resource-onboardings', icon: <IconClipboard />, section: '자원' },
        { label: '업체변경 신청서', to: '/resource-change-requests', icon: <IconClipboard />, section: '자원' },
        { label: '투입 요청', to: '/field-deployments/supplier', icon: <IconClipboard />, section: '투입' },
        { label: '자원 파이프라인', to: '/resource-pipeline', icon: <IconClipboard />, section: '투입' },
        { label: '작업 일정', to: '/work-plans', icon: <IconClipboard />, section: '투입' },
        { label: '현장 관리', to: '/sites', icon: <IconBuilding />, section: '투입' },
        { label: '일일 확인서', to: '/daily-work-logs', icon: <IconClipboard />, section: '기록' },
        { label: '인원 공지', to: '/announcements', icon: <IconBell />, section: '소통' },
      ]},
      { label: '정산', icon: <IconMoney />, items: settlementItems },
      { label: '안전', icon: <IconShield />, items: [
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
        { label: '안전점검', to: '/safety-inspections', icon: <IconShield /> },
        { label: '혈압 체크인', to: '/bp-checkins', icon: <IconShield /> },
      ]},
      { label: '회사 관리', icon: <IconBuilding />, items: [
        { label: '내 회사', to: '/my-company', icon: <IconBuilding /> },
        ...(isMaster ? [{ label: '직원 관리', to: '/company/users', icon: <IconUsers /> }] : []),
        ...(isEquip && isMaster ? [{ label: '협력업체 관리', to: '/sub-suppliers', icon: <IconTruck /> }] : []),
        ...(isEquip && isMaster ? [{ label: '취급 장비종류', to: '/settings/equipment-types', icon: <IconTruck /> }] : []),
      ]},
    ];
  } else if (role === 'CLIENT') {
    // 원청(관제) 전용 간소 사이드바 — 읽기전용 관제 허브 + 알림(단독).
    groups = [
      { label: '관제', icon: <IconGrid />, items: [
        { label: '현장 관제', to: '/client/dashboard', icon: <IconGrid /> },
        { label: '안전 상황판', to: '/safety-board', icon: <IconShield /> },
      ]},
      { label: '알림', to: '/notifications', icon: <IconBell />, badge: unread > 0 ? unread : undefined },
    ];
  } else {
    groups = [...standalone];
  }

  const panelGroups = groups.filter((g) => g.items && g.items.length > 0);

  // 현재 라우트가 속한 패널 대분류 = 자동 선택 대상(딥링크·이동 모두). 쿼리 무시, 경로만 비교.
  let routeGroupLabel: string | null = null;
  for (const g of panelGroups) {
    const hit = g.items!.some((it) => {
      if (it.disabled) return false;
      const p = it.to.split('?')[0];
      return location.pathname === p || location.pathname.startsWith(p + '/');
    });
    if (hit) { routeGroupLabel = g.label; break; }
  }

  // 2단에 펼쳐 보일 패널 대분류. null = 2단 닫힘(단독 라우트).
  // 진입/이동 시 활성 라우트의 대분류가 자동 펼침(routeGroupLabel), 단독 라우트면 자동 닫힘.
  const [selected, setSelected] = useState<string | null>(routeGroupLabel);
  useEffect(() => { setSelected(routeGroupLabel); }, [routeGroupLabel]);
  const selectedGroup = panelGroups.find((g) => g.label === selected) ?? null;
  const panelOpen = !!selectedGroup;

  const matchActive = (to: string) => {
    const [p, q] = to.split('?');
    if (location.pathname !== p) return false;
    if (q) return location.search === '?' + q;
    return location.search === '';
  };

  // 패널 대분류 클릭 → 2단 펼침. collapsed 상태면 먼저 펼쳐 2단이 보이도록 복원.
  const openPanel = (label: string) => {
    setSelected(label);
    if (collapsed) onToggle();
  };

  return (
    <>
      {/* 모바일 드로어 백드롭 — 열렸을 때만, 클릭 시 닫힘 (md+ 에선 숨김) */}
      {mobileOpen && (
        <div className="fixed inset-0 z-30 bg-black/40 md:hidden" onClick={onMobileClose} aria-hidden="true" />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-40 flex ${panelOpen ? 'w-[272px]' : 'w-[76px]'} max-w-[92vw] flex-col bg-white border-r border-slate-200 transition-transform
          ${mobileOpen ? 'translate-x-0' : '-translate-x-full'}
          md:static md:z-auto md:translate-x-0 md:shrink-0 md:max-w-none md:transition-all ${collapsed ? 'md:w-[60px]' : panelOpen ? 'md:w-[272px]' : 'md:w-[76px]'}`}
      >
        {/* 로고 헤더 — 좁은 레일이면 심볼만, 2단 펼치면 전체 로고 */}
        <div className={`flex items-center h-[60px] border-b border-slate-100 shrink-0 ${panelOpen ? 'gap-2 px-4' : 'justify-center px-0'}`}>
          <div className="shrink-0 inline-flex h-8 w-8 items-center justify-center rounded-lg bg-brand-600 text-white">
            <IconHelmet />
          </div>
          <span className={`text-base font-bold text-slate-900 truncate ${panelOpen && !collapsed ? '' : 'hidden'}`}>원온 <span className="text-brand-600">ONEON</span></span>
          <button type="button" onClick={onMobileClose} aria-label="메뉴 닫기"
            className="ml-auto shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 md:hidden">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18M6 6l12 12" /></svg>
          </button>
        </div>

        <div className="flex-1 flex min-h-0 overflow-hidden">
          {/* 1단 — 대분류(아이콘 위 + 이름 아래, 좁은 세로 레일) */}
          <div className={`shrink-0 flex flex-col border-r border-slate-100 w-[76px] ${collapsed ? 'md:w-[60px]' : 'md:w-[76px]'}`}>
            <nav className="flex-1 overflow-y-auto px-1.5 py-2 space-y-1">
              {groups.map((g) => {
                const badge = g.to ? (g.badge ?? 0) : g.items!.reduce((s, it) => s + (it.badge ?? 0), 0);
                const isRouteHere = g.to ? matchActive(g.to) : routeGroupLabel === g.label;
                const isOpen = !g.to && selectedGroup?.label === g.label;
                const cls = `relative flex w-full flex-col items-center justify-center gap-1 px-0.5 py-2 rounded-lg text-[10.5px] font-medium leading-[1.15] transition-colors ${
                  isRouteHere
                    ? 'bg-brand-50 text-brand-700'
                    : isOpen
                      ? 'bg-slate-100 text-slate-900'
                      : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900'
                }`;
                const inner = (
                  <>
                    {/* 아이콘이 위치 기준(relative) — 배지를 아이콘 우상단에 종속시켜 항목 폭과 무관하게 정렬 일관 */}
                    <span className="relative shrink-0 w-6 h-6 flex items-center justify-center">
                      {g.icon}
                      {badge > 0 && (
                        <span className={`absolute -top-1.5 -right-2 inline-flex items-center justify-center min-w-[15px] h-[15px] px-1 rounded-full bg-brand-600 text-white text-[9px] font-bold leading-none ${collapsed ? 'md:hidden' : ''}`}>
                          {badge > 99 ? '99+' : badge}
                        </span>
                      )}
                      {badge > 0 && (
                        <span className={`absolute -top-1 -right-1 h-2 w-2 rounded-full bg-brand-600 ${collapsed ? 'hidden md:block' : 'hidden'}`} aria-hidden="true" />
                      )}
                    </span>
                    <span className={`w-full text-center break-keep ${collapsed ? 'md:hidden' : ''}`}>{g.label}</span>
                  </>
                );
                return g.to ? (
                  <NavLink key={g.label} to={g.to} end={g.end ?? g.to === '/'} onClick={() => setSelected(null)} className={cls} title={collapsed ? g.label : undefined}>
                    {inner}
                  </NavLink>
                ) : (
                  <button key={g.label} type="button" onClick={() => openPanel(g.label)} className={cls} title={collapsed ? g.label : undefined}>
                    {inner}
                  </button>
                );
              })}
            </nav>

            <button
              type="button"
              onClick={onToggle}
              title={collapsed ? '메뉴 펼치기' : '메뉴 접기'}
              className="border-t border-slate-100 py-3 text-slate-400 hover:bg-slate-50 hover:text-slate-600 hidden md:flex items-center justify-center"
            >
              <span className={`inline-block transition-transform ${collapsed ? 'rotate-180' : ''}`}>
                <IconChevronLeft />
              </span>
            </button>
          </div>

          {/* 2단 — 선택된 대분류의 세부 항목 패널(옆으로 펼침). 단독 라우트/collapsed(md) 에서는 숨김. */}
          {selectedGroup && (
            <div className={`flex-col w-[196px] bg-slate-50/40 flex ${collapsed ? 'md:hidden' : 'md:flex'}`}>
              <div className="h-[44px] shrink-0 flex items-center px-4 text-[13px] font-bold text-slate-800 border-b border-slate-100">
                {selectedGroup.label}
              </div>
              <div className="flex-1 overflow-y-auto p-2">
                <NavItemsList items={selectedGroup.items!} collapsed={false} />
              </div>
            </div>
          )}
        </div>
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
  let prevSection: string | undefined;
  return (
    <ul className="space-y-0.5">
      {items.map((it) => {
        const showHeader = !collapsed && !!it.section && it.section !== prevSection;
        prevSection = it.section;
        return (
          <Fragment key={it.label + ':' + it.to}>
            {showHeader && (
              <li className="px-3 pt-3 pb-1 text-[11px] font-semibold text-slate-400 first:pt-1">
                {it.section}
              </li>
            )}
            <li>
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
          </Fragment>
        );
      })}
    </ul>
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
function IconMoney() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="6" width="20" height="12" rx="2" /><circle cx="12" cy="12" r="2.5" /><path d="M6 12h.01M18 12h.01" />
    </svg>
  );
}
function IconSettings() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
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
