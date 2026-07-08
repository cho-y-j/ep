import { useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { SignaturePadDialog } from '../workPlan/create/components/SignaturePadDialog';

interface Outgoing {
  id: number;
  supplier_company_name?: string;
  equipment_label?: string;
  person_label?: string;
  daily_rate?: number;
  monthly_rate?: number;
  note?: string;
  period_start?: string;
  period_end?: string;
  sent_at: string;
  bp_signed?: boolean;
  bp_signer_name?: string;
  bp_signed_at?: string;
}

export default function InboxPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState<Outgoing[]>([]);
  const [loading, setLoading] = useState(true);
  const [signing, setSigning] = useState<Outgoing | null>(null);

  const load = () => {
    setLoading(true);
    api.get<Outgoing[]>('/api/outgoing-quotations/inbox')
      .then((r) => setRows(r.data))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const submitSign = async (pngBase64: string) => {
    if (!signing) return;
    try {
      await api.post(`/api/outgoing-quotations/${signing.id}/sign-bp`, {
        png_base64: pngBase64,
        signer_name: user?.name ?? undefined,
      });
      setSigning(null);
      load();
    } catch (e: any) {
      alert('사인 실패: ' + (e?.response?.data?.message || e?.message || e));
    }
  };

  return (
    <AppShell breadcrumb={[{ label: '수신함' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="mb-4">
          <h1 className="text-2xl font-bold">수신 견적 (영업 자료)</h1>
          <p className="text-sm text-slate-500 mt-1">
            장비/인력 공급사가 우리 회사에 보낸 견적서. 내용 확인 후 수락 사인을 남길 수 있습니다.
          </p>
        </div>

        {loading ? <div className="text-slate-400">로딩 중…</div> : rows.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">수신된 견적이 없습니다.</div>
        ) : (
          <div className="space-y-3">
            {rows.map((o) => (
              <div key={o.id} className="card p-4">
                <div className="flex items-center justify-between mb-2">
                  <div>
                    <span className="font-semibold">{o.supplier_company_name}</span>
                    <span className="text-slate-500 mx-2">·</span>
                    <span className="text-sm">{o.equipment_label ?? o.person_label}</span>
                  </div>
                  <span className="text-xs text-slate-400">{new Date(o.sent_at).toLocaleString('ko-KR')}</span>
                </div>
                <div className="text-sm text-slate-700">
                  {o.daily_rate && <span>일대 <strong>{o.daily_rate.toLocaleString()}</strong>원 </span>}
                  {o.monthly_rate && <span>월대 <strong>{o.monthly_rate.toLocaleString()}</strong>원</span>}
                  {!o.daily_rate && !o.monthly_rate && <span className="text-slate-400">단가 협의</span>}
                </div>
                {(o.period_start || o.period_end) && (
                  <div className="text-xs text-slate-500 mt-1">
                    가용 기간: {o.period_start ?? '?'} ~ {o.period_end ?? '?'}
                  </div>
                )}
                {o.note && <div className="text-xs text-slate-600 mt-2 whitespace-pre-wrap">{o.note}</div>}

                {/* 사인 영역 */}
                <div className="mt-3 pt-3 border-t border-slate-100 flex items-center justify-between gap-2">
                  {o.bp_signed ? (
                    <div className="flex items-center gap-3">
                      <img src={`/api/outgoing-quotations/${o.id}/bp-signature`} alt="사인"
                           className="h-12 border border-slate-200 bg-white rounded" />
                      <div className="text-xs">
                        <div className="text-emerald-700 font-semibold">✓ 수락 사인 완료</div>
                        <div className="text-slate-500">
                          {o.bp_signer_name ?? '담당자'}
                          {o.bp_signed_at && ` · ${new Date(o.bp_signed_at).toLocaleString('ko-KR')}`}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <span className="text-xs text-slate-400">미사인</span>
                  )}
                  {!o.bp_signed && (user?.role === 'BP' || user?.role === 'ADMIN') && (
                    <button
                      type="button"
                      onClick={() => setSigning(o)}
                      className="text-sm px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 font-semibold"
                    >
                      수락 사인
                    </button>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {signing && (
        <SignaturePadDialog
          open
          title="견적서 수락 사인"
          signerName={user?.name}
          onClose={() => setSigning(null)}
          onConfirm={submitSign}
        />
      )}
    </AppShell>
  );
}
