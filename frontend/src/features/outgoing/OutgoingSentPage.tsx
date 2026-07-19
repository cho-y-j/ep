import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';

interface Outgoing {
  id: number;
  supplier_company_name?: string;
  equipment_label?: string;
  person_label?: string;
  daily_rate?: number;
  monthly_rate?: number;
  recipient_type: string;
  recipient_company_name?: string;
  recipient_email?: string;
  sent_at: string;
  mail_sent: boolean;
  mail_error?: string;
  bp_signed?: boolean;
  bp_signer_name?: string;
  bp_signed_at?: string;
}

export default function OutgoingSentPage() {
  const [rows, setRows] = useState<Outgoing[]>([]);
  const [loading, setLoading] = useState(true);
  // 클라이언트 필터 — 로드된 발송 견적을 좁힘.
  const [q, setQ] = useState('');
  const [signedFilter, setSignedFilter] = useState('');

  useEffect(() => {
    api.get<Outgoing[]>('/api/outgoing-quotations/sent')
      .then((r) => setRows(r.data))
      .finally(() => setLoading(false));
  }, []);

  const qLower = q.trim().toLowerCase();
  const filtered = rows.filter((o) => {
    if (signedFilter === 'SIGNED' && !o.bp_signed) return false;
    if (signedFilter === 'PENDING' && o.bp_signed) return false;
    if (qLower) {
      const hay = `${o.equipment_label ?? ''} ${o.person_label ?? ''} ${o.recipient_company_name ?? ''} ${o.recipient_email ?? ''} ${o.supplier_company_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  });
  const activeFilterCount = [q, signedFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setSignedFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '내 견적' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <PageHeader
          title="내 견적 (발송함)"
          subtitle="내가 BP 또는 외부 이메일로 발송한 견적서."
          actions={<Link to="/outgoing-quotations/new" className="btn-primary">+ 새 견적 발송</Link>}
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원·수신처 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={signedFilter} onChange={setSignedFilter} placeholder="수락상태 전체"
            options={[{ value: 'SIGNED', label: '수락됨' }, { value: 'PENDING', label: '대기' }]} />
        </FilterBar>

        {loading ? <div className="text-slate-400">로딩 중…</div> : rows.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">발송한 견적이 없습니다.</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">조건에 맞는 견적이 없습니다.</div>
        ) : (
          <table className="card w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr className="text-left text-slate-500">
                <th className="px-4 py-3 font-medium">발송일</th>
                <th className="px-4 py-3 font-medium">자원</th>
                <th className="px-4 py-3 font-medium">단가</th>
                <th className="px-4 py-3 font-medium">수신</th>
                <th className="px-4 py-3 font-medium">메일</th>
                <th className="px-4 py-3 font-medium">BP 수락</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filtered.map((o) => (
                <tr key={o.id}>
                  <td className="px-4 py-3 text-slate-500">{new Date(o.sent_at).toLocaleString('ko-KR')}</td>
                  <td className="px-4 py-3">{o.equipment_label ?? o.person_label}</td>
                  <td className="px-4 py-3">
                    {o.daily_rate ? `일 ${o.daily_rate.toLocaleString()} ` : ''}
                    {o.monthly_rate ? `월 ${o.monthly_rate.toLocaleString()}` : ''}
                    {!o.daily_rate && !o.monthly_rate && <span className="text-slate-400">협의</span>}
                  </td>
                  <td className="px-4 py-3">
                    {o.recipient_type === 'REGISTERED_BP'
                      ? (o.recipient_company_name ?? '(BP 사용자)')
                      : o.recipient_email}
                  </td>
                  <td className="px-4 py-3">
                    {o.mail_sent
                      ? <span className="text-xs px-2 py-0.5 rounded bg-emerald-100 text-emerald-700">발송됨</span>
                      : <span className="text-xs px-2 py-0.5 rounded bg-rose-100 text-rose-700" title={o.mail_error}>실패</span>}
                  </td>
                  <td className="px-4 py-3">
                    {o.bp_signed ? (
                      <div className="flex items-center gap-2">
                        <img src={`/api/outgoing-quotations/${o.id}/bp-signature`} alt="사인"
                             className="h-8 border border-slate-200 bg-white rounded" />
                        <div className="text-[10px] text-slate-500">
                          <div className="text-emerald-700 font-semibold">✓ {o.bp_signer_name ?? '수락'}</div>
                          {o.bp_signed_at && <div>{new Date(o.bp_signed_at).toLocaleDateString('ko-KR')}</div>}
                        </div>
                      </div>
                    ) : (
                      <span className="text-xs text-slate-400">대기</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </AppShell>
  );
}
