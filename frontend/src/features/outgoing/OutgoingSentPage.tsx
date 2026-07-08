import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
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

  useEffect(() => {
    api.get<Outgoing[]>('/api/outgoing-quotations/sent')
      .then((r) => setRows(r.data))
      .finally(() => setLoading(false));
  }, []);

  return (
    <AppShell breadcrumb={[{ label: '내 견적' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h1 className="text-2xl font-bold">내 견적 (발송함)</h1>
            <p className="text-sm text-slate-500 mt-1">내가 BP 또는 외부 이메일로 발송한 견적서.</p>
          </div>
          <Link to="/outgoing-quotations/new" className="btn-primary">+ 새 견적 발송</Link>
        </div>

        {loading ? <div className="text-slate-400">로딩 중…</div> : rows.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">발송한 견적이 없습니다.</div>
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
              {rows.map((o) => (
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
