import { useState } from 'react';
import ResourceCheckSupplierInbox from '../resourceCheck/SupplierInboxPage';
import ComplianceOrdersPage from '../compliance/ComplianceOrdersPage';
import DocumentManagementPage from '../compliance/DocumentManagementPage';
import { useSupplierIncomingCounts } from '../../lib/useSupplierIncomingCounts';
import { useAuth } from '../auth/AuthContext';

type Tab = 'checks' | 'compliance' | 'supplements';

/**
 * 공급사 "받은 요청" — 점검요청 / 이행지시 / 보완요청을 한 화면 탭으로 통합.
 * 각 탭은 기존 페이지 컴포넌트를 그대로 렌더 (기능 동일, 기존 라우트도 유지).
 */
export default function SupplierReceivedPage() {
  const { user } = useAuth();
  const counts = useSupplierIncomingCounts(user?.company_id ?? null);
  const [tab, setTab] = useState<Tab>('checks');

  const tabs: { key: Tab; label: string; badge: number }[] = [
    { key: 'checks', label: '점검 요청', badge: counts.checks },
    { key: 'compliance', label: '이행지시', badge: counts.compliance },
    { key: 'supplements', label: '보완요청', badge: counts.supplements },
  ];

  return (
    <div>
      <div className="px-6 pt-5">
        <h1 className="text-xl font-bold text-slate-900 mb-3">받은 요청</h1>
        <div className="flex gap-1 border-b border-slate-200">
          {tabs.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key)}
              className={`relative -mb-px px-4 py-2 text-sm font-semibold border-b-2 transition-colors ${
                tab === t.key
                  ? 'border-brand-600 text-brand-700'
                  : 'border-transparent text-slate-500 hover:text-slate-800'
              }`}
            >
              {t.label}
              {t.badge > 0 && (
                <span className="ml-1.5 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-brand-600 text-white text-[11px] font-semibold align-middle">
                  {t.badge}
                </span>
              )}
            </button>
          ))}
        </div>
      </div>

      <div>
        {tab === 'checks' && <ResourceCheckSupplierInbox />}
        {tab === 'compliance' && <ComplianceOrdersPage />}
        {tab === 'supplements' && <DocumentManagementPage />}
      </div>
    </div>
  );
}
