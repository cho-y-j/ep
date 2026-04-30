import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { COMPANY_TYPE_LABEL } from '../../types/auth';
import type { DashboardSummary, ExpiringDocumentItem } from '../../types/dashboard';
import AppHeader from '../../components/AppHeader';
import DocumentRenewDialog from '../document/DocumentRenewDialog';

export default function HomePage() {
  const { user, company } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [renewing, setRenewing] = useState<ExpiringDocumentItem | null>(null);

  const loadSummary = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get<DashboardSummary>('/api/dashboard/summary');
      setSummary(res.data);
    } catch {
      setSummary(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadSummary(); }, [loadSummary]);

  if (!user) return null;

  const isAdmin = user.role === 'ADMIN';
  const isSupplier = user.role === 'EQUIPMENT_SUPPLIER' || user.role === 'MANPOWER_SUPPLIER';
  const counts = summary?.counts ?? {};
  const canRenew = isAdmin || isSupplier;

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-6xl mx-auto px-6 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold">환영합니다, {user.name}님</h1>
          <p className="text-sm text-slate-500 mt-1">
            {isAdmin ? '시스템 관리자 대시보드입니다.' : company ? `${company.name} (${COMPANY_TYPE_LABEL[company.type]})` : ''}
          </p>
        </div>

        {(isAdmin || isSupplier) && (
          <section className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <StatCard label="등록 인원" value={counts.persons ?? 0} href="/persons" color="blue" />
            <StatCard label="등록 장비" value={counts.equipment ?? 0} href="/equipment" color="emerald" />
            {isAdmin && (
              <>
                <StatCard label="등록 회사" value={counts.companies ?? 0} href="/admin/companies" color="purple" />
                <StatCard
                  label="승인 대기 사용자"
                  value={counts.users_pending ?? 0}
                  href="/admin/users"
                  color="amber"
                  emphasize={(counts.users_pending ?? 0) > 0}
                />
              </>
            )}
            <StatCard
              label="만료 임박 (30일)"
              value={counts.documents_expiring30d ?? 0}
              color="red"
              emphasize={(counts.documents_expiring30d ?? 0) > 0}
            />
            <StatCard label="미검증 서류" value={counts.documents_unverified ?? 0} color="slate" />
          </section>
        )}

        {(isAdmin || isSupplier) && (
          <section className="card">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold">만료 임박 서류 (30일 이내)</h2>
              {(summary?.expiring_documents.length ?? 0) > 0 && (
                <span className="text-xs text-slate-500">{summary?.expiring_documents.length}건</span>
              )}
            </div>
            {loading ? (
              <p className="text-sm text-slate-400">불러오는 중...</p>
            ) : !summary || summary.expiring_documents.length === 0 ? (
              <p className="text-sm text-slate-400">만료 임박한 서류가 없습니다.</p>
            ) : (
              <ul className="divide-y divide-slate-100">
                {summary.expiring_documents.map((d) => {
                  const overdue = d.days_left < 0;
                  const tone = overdue
                    ? 'bg-red-100 text-red-700'
                    : d.days_left <= 7
                      ? 'bg-orange-100 text-orange-700'
                      : 'bg-amber-100 text-amber-700';
                  return (
                    <li key={d.id} className="py-3 flex items-center justify-between gap-3">
                      <div className="flex-1 min-w-0">
                        <Link
                          to={`/${d.owner_type === 'EQUIPMENT' ? 'equipment' : 'persons'}/${d.owner_id}`}
                          className="text-sm font-medium hover:text-brand-700"
                        >
                          {d.document_type_name}
                        </Link>
                        <div className="text-xs text-slate-500 mt-0.5">
                          {d.owner_type === 'EQUIPMENT' ? '장비' : '인원'} · {d.owner_name} · 만료 {d.expiry_date}
                        </div>
                      </div>
                      <span className={`shrink-0 inline-flex px-2 py-0.5 rounded text-xs font-medium ${tone}`}>
                        {overdue ? `${-d.days_left}일 지남` : `${d.days_left}일 남음`}
                      </span>
                      {canRenew && (
                        <button
                          type="button"
                          onClick={() => setRenewing(d)}
                          className="shrink-0 text-xs px-2 py-1 rounded-lg bg-brand-600 text-white hover:bg-brand-700"
                        >
                          재업로드
                        </button>
                      )}
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        )}

        {(isAdmin || isSupplier) && (
          <section className="card">
            <h2 className="text-base font-bold mb-3">빠른 메뉴</h2>
            <div className="flex flex-wrap gap-2">
              {(isAdmin || user.role === 'EQUIPMENT_SUPPLIER') && (
                <Link to="/equipment" className="text-sm px-3 py-1.5 rounded-lg bg-emerald-50 text-emerald-700 hover:bg-emerald-100">
                  장비 관리
                </Link>
              )}
              {(isAdmin || isSupplier) && (
                <Link to="/persons" className="text-sm px-3 py-1.5 rounded-lg bg-blue-50 text-blue-700 hover:bg-blue-100">
                  인원 관리
                </Link>
              )}
              {isAdmin && (
                <>
                  <Link to="/admin/users" className="text-sm px-3 py-1.5 rounded-lg bg-purple-50 text-purple-700 hover:bg-purple-100">
                    사용자 관리
                  </Link>
                  <Link to="/admin/companies" className="text-sm px-3 py-1.5 rounded-lg bg-slate-100 text-slate-700 hover:bg-slate-200">
                    회사 관리
                  </Link>
                </>
              )}
            </div>
          </section>
        )}

        {!isAdmin && !isSupplier && (
          <section className="card">
            <p className="text-slate-500">아직 사용 가능한 기능이 준비되지 않았습니다.</p>
          </section>
        )}
      </div>

      {renewing && (
        <DocumentRenewDialog
          open
          ownerType={renewing.owner_type}
          ownerId={renewing.owner_id}
          documentTypeId={renewing.document_type_id}
          documentTypeName={renewing.document_type_name}
          oldDocumentId={renewing.id}
          hasExpiry
          onClose={() => setRenewing(null)}
          onDone={() => { setRenewing(null); void loadSummary(); }}
        />
      )}
    </main>
  );
}

type StatColor = 'blue' | 'emerald' | 'purple' | 'amber' | 'red' | 'slate';

const COLOR_CLASS: Record<StatColor, string> = {
  blue: 'bg-blue-50 text-blue-700',
  emerald: 'bg-emerald-50 text-emerald-700',
  purple: 'bg-purple-50 text-purple-700',
  amber: 'bg-amber-50 text-amber-700',
  red: 'bg-red-50 text-red-700',
  slate: 'bg-slate-100 text-slate-600',
};

function StatCard({
  label, value, href, color = 'slate', emphasize,
}: {
  label: string;
  value: number;
  href?: string;
  color?: StatColor;
  emphasize?: boolean;
}) {
  const inner = (
    <div className={`card transition-shadow ${href ? 'hover:shadow-md cursor-pointer' : ''} ${emphasize ? 'ring-2 ring-amber-300' : ''}`}>
      <div className={`inline-flex px-2 py-0.5 rounded text-xs font-medium mb-2 ${COLOR_CLASS[color]}`}>
        {label}
      </div>
      <div className="text-3xl font-bold text-slate-900">{value.toLocaleString()}</div>
    </div>
  );
  return href ? <Link to={href} className="block">{inner}</Link> : inner;
}
