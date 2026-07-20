import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  QUOTATION_STATUS_LABEL,
  QUOTATION_TARGET_STATUS_LABEL,
  type QuotationBundleResponse,
  type QuotationRequestResponse,
  type QuotationStatus,
  type QuotationTargetStatus,
} from '../../types/quotation';
import { equipmentCategoryLabel } from '../../types/equipment';
import { PERSON_ROLE_LABEL } from '../../types/person';

/** 견적 묶음 상세 — 한 현장 한 묶음 안의 장비/인력 명세 + 공급사 target 응답 모두 한 화면. */
export default function QuotationBundleDetailPage() {
  const { bundleId } = useParams<{ bundleId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [data, setData] = useState<QuotationBundleResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const canManage = isAdmin || isBP;

  const load = useCallback(async () => {
    if (!bundleId) return;
    setLoading(true);
    try {
      const res = await api.get<QuotationBundleResponse>(`/api/quotations/bundles/${bundleId}`);
      setData(res.data);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '로드 실패');
    } finally {
      setLoading(false);
    }
  }, [bundleId]);
  useEffect(() => { load(); }, [load]);

  const respond = async (reqId: number, targetId: number, accept: boolean) => {
    const note = window.prompt(accept ? '수락 메모 (선택):' : '거부 사유 (선택):') ?? '';
    if (note === null) return;
    setBusy(true);
    try {
      await api.post(`/api/quotations/${reqId}/targets/${targetId}/respond`, { accept, note });
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '응답 실패');
    } finally {
      setBusy(false);
    }
  };
  /** Site-D: ACCEPTED target 들을 finalize → 작업계획서 작성 페이지로 이동 (견적 묶음 prefill). */
  const goToWorkPlan = async () => {
    if (!data) return;
    const accepted = data.items.flatMap((it) => (it.targets ?? [])
        .filter((t) => t.status === 'ACCEPTED')
        .map((t) => ({ reqId: it.id, targetId: t.id })));
    setBusy(true);
    try {
      for (const a of accepted) {
        await api.post(`/api/quotations/${a.reqId}/targets/${a.targetId}/finalize`, {});
      }
      navigate(`/work-plans/new?fromQuotationBundle=${bundleId}`);
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '선정 실패');
    } finally {
      setBusy(false);
    }
  };
  const cancelBundle = async () => {
    if (!bundleId) return;
    if (!window.confirm('이 묶음을 취소하시겠어요?')) return;
    setBusy(true);
    try {
      await api.post(`/api/quotations/bundles/${bundleId}/cancel`, {});
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '취소 실패');
    } finally {
      setBusy(false);
    }
  };
  const deleteBundle = async () => {
    if (!bundleId) return;
    if (!window.confirm('이 묶음을 완전히 삭제하시겠어요?')) return;
    setBusy(true);
    try {
      await api.delete(`/api/quotations/bundles/${bundleId}`);
      navigate('/quotations');
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '삭제 실패');
      setBusy(false);
    }
  };

  if (loading) return <AppShell><div className="card p-6 text-sm">로드 중...</div></AppShell>;
  if (error || !data) return (
    <AppShell breadcrumb={[{ label: '견적' }]}>
      <div className="card p-6 text-sm text-rose-600">{error ?? '데이터 없음'}</div>
    </AppShell>
  );

  const isOpen = data.aggregate_status === 'SENT';

  return (
    <AppShell breadcrumb={[{ label: '견적 요청' }, { label: `묶음 #${(data.bundle_id ?? '').slice(0, 8)}` }]}>
      <div className="mx-auto max-w-6xl space-y-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-lg font-bold">
              {data.site_name ?? `현장 #${data.site_id}`}
              <StatusChip status={data.aggregate_status} className="ml-2 align-middle" />
            </h1>
            <div className="text-sm text-slate-500 mt-1">
              {data.bp_company_name ?? '-'} · {data.work_period_start} ~ {data.work_period_end}
            </div>
            <div className="mt-2 flex items-center gap-3 text-xs text-slate-600">
              {data.items[0]?.mode === 'OPEN_BID' ? (
                <>
                  <span>받은 제안 <b>{data.proposal_count}</b>건</span>
                  {data.pending_proposal_count > 0 && (
                    <span className="text-amber-700">검토 대기 <b>{data.pending_proposal_count}</b></span>
                  )}
                  <span>확정 <b className="text-brand-700">{data.finalized_count}</b></span>
                </>
              ) : (
                <>
                  <span>전체 target <b>{data.total_targets}</b></span>
                  <span>응답 <b>{data.responded_count}</b></span>
                  <span>수락 <b className="text-emerald-700">{data.accepted_count}</b></span>
                  <span>확정 <b className="text-brand-700">{data.finalized_count}</b></span>
                </>
              )}
              {data.first_work_plan_id && (
                <button
                  type="button"
                  onClick={() => navigate(`/work-plans/${data.first_work_plan_id}`)}
                  className="text-blue-700 underline"
                >
                  → 작업계획서 #{data.first_work_plan_id} 로 이동
                </button>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {canManage && (() => {
              const isOpenBid = data.items[0]?.mode === 'OPEN_BID';
              const allResponded = data.responded_count === data.total_targets && data.total_targets > 0;
              const hasAccepted = data.accepted_count > 0;
              const allFinalized = data.finalized_count > 0 && data.finalized_count === data.accepted_count + data.finalized_count;
              if (data.first_work_plan_id && allFinalized) {
                return (
                  <button type="button" onClick={() => navigate(`/work-plans/${data.first_work_plan_id}`)}
                          className="text-sm px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 font-semibold">
                    작업계획서 바로가기 →
                  </button>
                );
              }
              if (isOpenBid) {
                // 공개입찰: 제안 받고 BP 가 직접 선정 → 견적 상세에서 액션. 진행 상태만 표시.
                if (data.finalized_count > 0) {
                  return (
                    <button type="button"
                            onClick={() => navigate(`/work-plans/new?fromQuotationBundle=${data.bundle_id}`)}
                            className="text-sm px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 font-semibold">
                      작업계획서 만들기 →
                    </button>
                  );
                }
                return (
                  <span className="text-xs text-slate-500 px-2 py-1.5">
                    {data.proposal_count > 0
                      ? `제안 검토 중 (${data.pending_proposal_count}건 대기)`
                      : '공급사 제안 대기 중'}
                  </span>
                );
              }
              if (allResponded && hasAccepted) {
                return (
                  <button type="button" onClick={goToWorkPlan} disabled={busy}
                          className="text-sm px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 font-semibold disabled:opacity-50">
                    {busy ? '작업계획서 생성 중...' : '작업계획서 바로가기 →'}
                  </button>
                );
              }
              return (
                <span className="text-xs text-slate-500 px-2 py-1.5">
                  공급사 응답 대기 중 ({data.responded_count}/{data.total_targets})
                </span>
              );
            })()}
            {canManage && isOpen && (
              <button type="button" onClick={cancelBundle} disabled={busy}
                      className="text-xs px-2 py-1 rounded border border-amber-300 text-amber-700 hover:bg-amber-50">
                묶음 취소
              </button>
            )}
            {canManage && (
              <button type="button" onClick={deleteBundle} disabled={busy}
                      className="text-xs px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50">
                묶음 삭제
              </button>
            )}
            <button type="button" onClick={() => navigate('/quotations')}
                    className="text-xs px-2 py-1 rounded border border-slate-300 hover:bg-slate-50">
              ← 목록
            </button>
          </div>
        </div>

        {data.notes && (
          <section className="card">
            <div className="text-xs font-semibold text-slate-500 mb-1">공통 메모</div>
            <div className="text-sm text-slate-800 whitespace-pre-wrap">{data.notes}</div>
          </section>
        )}

        <div className="space-y-4">
          {data.items.map((item) => (
            <ItemPanel key={item.id} item={item}
                       respond={respond}
                       isSupplier={isSupplier}
                       userCompanyId={user?.company_id} busy={busy} />
          ))}
        </div>
      </div>
    </AppShell>
  );
}

function ItemPanel({ item, respond, isSupplier, userCompanyId, busy }: {
  item: QuotationRequestResponse;
  respond: (reqId: number, targetId: number, accept: boolean) => void;
  isSupplier: boolean;
  userCompanyId?: number | null;
  busy: boolean;
}) {
  const isEq = item.request_type === 'EQUIPMENT';
  const label = isEq
    ? (item.equipment_category ? equipmentCategoryLabel(item.equipment_category) : '장비')
    : (item.manpower_role ? PERSON_ROLE_LABEL[item.manpower_role] : '인력');
  const colorBar = isEq ? 'border-brand-500' : 'border-amber-500';
  const chip = isEq ? 'bg-brand-100 text-brand-700' : 'bg-amber-100 text-amber-700';
  const targets = item.targets ?? [];

  return (
    <section className={`card border-l-4 ${colorBar} space-y-3`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded ${chip}`}>
            {isEq ? '장비' : '인력'}
          </span>
          <h3 className="text-base font-bold">{label}</h3>
          <span className="text-xs text-slate-500">× {item.count}{isEq ? '대' : '명'}</span>
        </div>
        <div className="text-xs text-slate-600">
          {item.proposed_daily_rate ? `${item.proposed_daily_rate.toLocaleString()}원/일` : ''}
          {item.proposed_daily_rate && item.proposed_monthly_rate ? ' · ' : ''}
          {item.proposed_monthly_rate ? `${item.proposed_monthly_rate.toLocaleString()}원/월` : ''}
        </div>
      </div>
      {item.spec_text && (
        <div className="text-sm text-slate-700 whitespace-pre-wrap">{item.spec_text}</div>
      )}
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-500">
            <tr>
              <th className="px-3 py-2 font-semibold">공급사</th>
              <th className="px-3 py-2 font-semibold">{isEq ? '장비' : '인원'}</th>
              <th className="px-3 py-2 font-semibold">상태</th>
              <th className="px-3 py-2 font-semibold">메모</th>
              <th className="px-3 py-2 font-semibold">액션</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {targets.length === 0 && (
              <tr><td colSpan={5} className="px-3 py-6 text-center text-xs text-slate-400">target 없음</td></tr>
            )}
            {targets.map((t) => {
              const mySupplier = isSupplier && userCompanyId === t.supplier_company_id;
              return (
                <tr key={t.id}>
                  <td className="px-3 py-2 font-medium text-slate-900">{t.supplier_company_name}</td>
                  <td className="px-3 py-2 text-slate-700">
                    {isEq ? (t.equipment_label ?? '-') : (t.person_label ?? '-')}
                  </td>
                  <td className="px-3 py-2"><TargetChip status={t.status} /></td>
                  <td className="px-3 py-2 text-xs text-slate-500 max-w-[260px]">
                    {t.response_note || '-'}
                    {t.responded_at && (
                      <div className="text-[10px] text-slate-400">
                        {new Date(t.responded_at).toLocaleString('ko-KR')}
                      </div>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      {mySupplier && t.status === 'PENDING' && (
                        <>
                          <button type="button" disabled={busy}
                                  onClick={() => respond(item.id, t.id, true)}
                                  className="text-[11px] px-2 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                            수락
                          </button>
                          <button type="button" disabled={busy}
                                  onClick={() => respond(item.id, t.id, false)}
                                  className="text-[11px] px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                            거부
                          </button>
                        </>
                      )}
                      {t.status === 'FINAL_ACCEPTED' && t.finalized_to_work_plan_id && (
                        <span className="text-[11px] text-emerald-700">
                          → WP #{t.finalized_to_work_plan_id}
                        </span>
                      )}
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function StatusChip({ status, className = '' }: { status: QuotationStatus; className?: string }) {
  const map: Record<QuotationStatus, string> = {
    DRAFT: 'bg-slate-100 text-slate-700',
    SENT: 'bg-blue-100 text-blue-700',
    CLOSED: 'bg-emerald-100 text-emerald-700',
    CANCELLED: 'bg-rose-100 text-rose-700',
  };
  return <span className={`inline-block text-[11px] font-semibold px-2 py-0.5 rounded ${map[status]} ${className}`}>
    {QUOTATION_STATUS_LABEL[status]}
  </span>;
}

function TargetChip({ status }: { status: QuotationTargetStatus }) {
  const map: Record<QuotationTargetStatus, string> = {
    PENDING: 'bg-slate-100 text-slate-700',
    ACCEPTED: 'bg-emerald-100 text-emerald-700',
    REJECTED: 'bg-rose-100 text-rose-700',
    FINAL_ACCEPTED: 'bg-brand-100 text-brand-800',
    EXPIRED: 'bg-amber-100 text-amber-700',
  };
  return <span className={`inline-block text-[11px] font-semibold px-2 py-0.5 rounded ${map[status]}`}>
    {QUOTATION_TARGET_STATUS_LABEL[status]}
  </span>;
}
