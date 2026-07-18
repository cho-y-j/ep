import { NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';

/**
 * P4b — 통합 화면 공유 탭바 (라우트 링크 바).
 * 기존 페이지들이 각자 <AppShell> 을 자기래핑하므로 콘텐츠 추출은 위험 → 대신
 * 사이드바 1대표링크 + "탭 = 라우트 링크" 패턴으로 형제 URL 을 묶는다.
 * AppShell 콘텐츠 상단에서 1회 렌더 → 개별 페이지 파일(파이프라인 포함) 무접촉.
 * 현재 경로가 어느 그룹에도 없으면 아무것도 렌더하지 않는다.
 */
type Tab = { label: string; to: string };

function tabGroupsForRole(role: string | undefined, isEquip: boolean): Tab[][] {
  if (role === 'BP') {
    return [
      [ { label: '공개 입찰', to: '/quotations' }, { label: '수신함', to: '/inbox' } ],
      [ { label: '받은 심사', to: '/document-reviews/received' }, { label: '소급 승인', to: '/resource-onboardings/bp' } ],
      [ { label: '서류 관리', to: '/document-management' }, { label: '수집 요청', to: '/document-collections' }, { label: '이행지시', to: '/compliance-orders' } ],
      [
        { label: '투입 대기', to: '/work-plans/pending' },
        { label: '투입 현황', to: '/work-plans/active' },
        { label: '투입 장비', to: '/dispatched-equipment' },
        { label: '투입 인원', to: '/dispatched-persons' },
        { label: '받은 투입 요청', to: '/field-deployments/bp' },
        { label: '보낸 점검 요청', to: '/resource-checks/bp' },
      ],
      [ { label: '장비', to: '/equipment' }, { label: '인원', to: '/persons' }, { label: '업체변경 신청서', to: '/resource-change-requests' } ],
      [ { label: '월별 작업확인서', to: '/work-confirmations/monthly' }, { label: '작업확인 원장', to: '/daily-work-logs/bp' } ],
    ];
  }
  if (role === 'ADMIN') {
    return [
      [ { label: '서류 관리', to: '/document-management' }, { label: '수집 요청', to: '/document-collections' }, { label: '이행지시', to: '/compliance-orders' } ],
    ];
  }
  if (role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER') {
    return [
      [
        { label: '공개 입찰', to: '/quotations/open-bids' },
        { label: '내 제안', to: '/my-proposals' },
        { label: '내 발송', to: '/outgoing-quotations' },
        { label: '받은 견적', to: '/quotations' },
        { label: '견적 템플릿', to: '/quote-templates' },
        { label: '계약 관리', to: '/contracts' },
      ],
      isEquip
        ? [ { label: '내 장비', to: '/equipment' }, { label: '내 조종원', to: '/persons' }, { label: '기투입 등록', to: '/resource-onboardings' }, { label: '업체변경 신청서', to: '/resource-change-requests' } ]
        : [ { label: '내 인원', to: '/persons' }, { label: '기투입 등록', to: '/resource-onboardings' }, { label: '업체변경 신청서', to: '/resource-change-requests' } ],
      [ { label: '현장 투입 요청', to: '/field-deployments/supplier' }, { label: '작업 일정', to: '/work-plans' }, { label: '현장 관리', to: '/sites' } ],
      isEquip
        ? [ { label: '투입 정산', to: '/settlements' }, { label: '월별 작업확인서', to: '/work-confirmations/monthly' }, { label: '장비 투입 통계', to: '/equipment-stats' } ]
        : [ { label: '투입 정산', to: '/settlements' }, { label: '월별 작업확인서', to: '/work-confirmations/monthly' } ],
    ];
  }
  return [];
}

export default function SubNav() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  const groups = tabGroupsForRole(user?.role, user?.role === 'EQUIPMENT_SUPPLIER');
  const active = groups.find((g) => g.some((t) => t.to === pathname));
  if (!active) return null;

  return (
    <div className="mx-auto max-w-7xl mb-4 border-b border-slate-200">
      <div className="flex gap-1 overflow-x-auto">
        {active.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            end
            className={({ isActive }) =>
              `relative -mb-px whitespace-nowrap px-4 py-2 text-sm font-semibold border-b-2 transition-colors ${
                isActive
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`
            }
          >
            {t.label}
          </NavLink>
        ))}
      </div>
    </div>
  );
}
