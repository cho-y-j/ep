import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  QUOTATION_STATUS_LABEL,
  QUOTATION_TARGET_STATUS_LABEL,
  type QuotationRequestResponse,
} from '../../types/quotation';
import { equipmentCategoryLabel } from '../../types/equipment';
import { PERSON_ROLE_LABEL } from '../../types/person';
import DispatchSection from './dispatch/DispatchSection';
import DocumentBundleSection from './bundle/DocumentBundleSection';
import ConfirmDialog from '../../components/ConfirmDialog';

interface Proposal {
  id: number;
  supplier_company_id: number;
  supplier_company_name?: string;
  equipment_id?: number;
  equipment_label?: string;
  person_id?: number;
  person_label?: string;
  daily_rate?: number;
  monthly_rate?: number;
  note?: string;
  status: 'SUBMITTED' | 'PENDING_REVIEW' | 'FINAL_ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
  created_at: string;
  finalized_at?: string;
  rejected_at?: string;
}

/** S-10: 견적 상세 — BP/ADMIN 시점 (응답 표 + finalize 버튼) + 공급사 시점 (수락/거부). */
export default function QuotationDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [data, setData] = useState<QuotationRequestResponse | null>(null);
  const [proposals, setProposals] = useState<Proposal[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // #8: window.confirm/prompt → ConfirmDialog + 사유 입력 다이얼로그.
  const [confirmState, setConfirmState] = useState<{
    title: string; message: string; confirmLabel: string; variant: 'danger' | 'primary'; run: () => Promise<void>;
  } | null>(null);
  const [respondFor, setRespondFor] = useState<{ targetId: number; accept: boolean } | null>(null);
  const [proposalStatusFilter, setProposalStatusFilter] = useState<'ALL' | 'SUBMITTED' | 'PENDING_REVIEW' | 'FINAL_ACCEPTED' | 'REJECTED' | 'WITHDRAWN'>('ALL');
  const [proposalSort, setProposalSort] = useState<'LATEST' | 'DAILY_ASC' | 'DAILY_DESC' | 'MONTHLY_ASC' | 'MONTHLY_DESC'>('LATEST');

  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  const canFinalize = isAdmin || isBP;
  const canCancel = isAdmin || isBP;

  const load = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const res = await api.get<QuotationRequestResponse>(`/api/quotations/${id}`);
      setData(res.data);
      // 공개입찰이면 받은 제안도 로드
      if ((res.data as any).mode === 'OPEN_BID') {
        try {
          const p = await api.get<Proposal[]>(`/api/quotations/${id}/proposals`);
          setProposals(p.data);
        } catch { /* ignore */ }
      }
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '로드 실패');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [id]);

  const finalizeProposal = async (proposalId: number) => {
    setBusy(true);
    try {
      await api.post(`/api/quotations/proposals/${proposalId}/finalize`, {});
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '선정 실패');
    } finally {
      setBusy(false);
    }
  };

  const openProposalBlob = async (proposalId: number, format: 'pdf' | 'xlsx') => {
    try {
      const res = await api.get(`/api/quotations/proposals/${proposalId}/quote.${format}`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      if (format === 'pdf') {
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      } else {
        const a = document.createElement('a');
        a.href = url; a.download = `proposal-${proposalId}.xlsx`; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5_000);
      }
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '미리보기 실패');
    }
  };

  const closeOpenBid = async () => {
    setBusy(true);
    try {
      await api.post(`/api/quotations/${id}/close`, {});
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '종료 실패');
    } finally {
      setBusy(false);
    }
  };

  const submitRespond = async (note: string) => {
    if (!respondFor) return;
    setBusy(true);
    try {
      await api.post(`/api/quotations/${id}/targets/${respondFor.targetId}/respond`, {
        accept: respondFor.accept,
        note: note || undefined,
      });
      setRespondFor(null);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '응답 실패');
    } finally {
      setBusy(false);
    }
  };

  const finalize = async (targetId: number) => {
    setBusy(true);
    try {
      await api.post(`/api/quotations/${id}/targets/${targetId}/finalize`, {});
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '최종 수락 실패');
    } finally {
      setBusy(false);
    }
  };

  const cancelRequest = async () => {
    setBusy(true);
    try {
      await api.post(`/api/quotations/${id}/cancel`, {});
      await load();
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '취소 실패');
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return <AppShell breadcrumb={[{ label: '견적' }]}><p className="text-slate-400">로딩...</p></AppShell>;
  }
  if (!data) {
    return <AppShell breadcrumb={[{ label: '견적' }]}><p className="text-rose-600">{error ?? '없음'}</p></AppShell>;
  }

  const targets = data.targets ?? [];
  const isOpen = data.status === 'SENT';

  return (
    <AppShell breadcrumb={[{ label: '견적' }, { label: `#${data.id}` }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h1 className="text-lg font-bold">
              견적 #{data.id}
              <span className={`ml-2 text-xs font-semibold px-2 py-0.5 rounded ${
                data.request_type === 'MANPOWER' ? 'bg-amber-100 text-amber-700' : 'bg-brand-100 text-brand-700'
              }`}>
                {data.request_type === 'MANPOWER' ? '인력' : '장비'}
              </span>
            </h1>
            <div className="text-sm text-slate-500 mt-1">
              {data.bp_company_name} · {data.site_name} · {
                data.request_type === 'MANPOWER'
                  ? (data.manpower_role ? PERSON_ROLE_LABEL[data.manpower_role] : '-')
                  : (data.equipment_category ? equipmentCategoryLabel(data.equipment_category) : '-')
              }
            </div>
          </div>
          <div className="flex items-center gap-2">
            <StatusChip status={data.status} />
            {canCancel && isOpen && (
              <button
                type="button"
                onClick={() => setConfirmState({
                  title: '견적 취소', message: '이 견적 요청을 취소하시겠어요?',
                  confirmLabel: '견적 취소', variant: 'danger', run: cancelRequest,
                })}
                disabled={busy}
                className="text-xs px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-50"
              >
                견적 취소
              </button>
            )}
            <button
              type="button"
              onClick={() => navigate('/quotations')}
              className="text-xs px-2 py-1 rounded border border-slate-300 hover:bg-slate-50"
            >
              ← 목록
            </button>
          </div>
        </div>

        <section className="card grid gap-3 md:grid-cols-2">
          <Field label="작업 기간" value={`${data.work_period_start} ~ ${data.work_period_end}`} />
          <Field label="필요 수량" value={`${data.count}대`} />
          <Field label="제안 일대" value={data.proposed_daily_rate ? `${data.proposed_daily_rate.toLocaleString()}원/일` : '-'} />
          <Field label="제안 월대" value={data.proposed_monthly_rate ? `${data.proposed_monthly_rate.toLocaleString()}원/월` : '-'} />
          <Field label="요청자" value={data.requested_by_user_name ?? `#${data.requested_by_user_id}`} />
          <Field
            label="대행 컨텍스트"
            value={data.on_behalf_of_bp_company_id ? `BP #${data.on_behalf_of_bp_company_id} 대행` : '직접'}
          />
          {data.spec_text && (
            <div className="md:col-span-2">
              <div className="text-xs font-semibold text-slate-500 mb-1">스펙</div>
              <div className="text-sm text-slate-800 whitespace-pre-wrap">{data.spec_text}</div>
            </div>
          )}
          {data.notes && (
            <div className="md:col-span-2">
              <div className="text-xs font-semibold text-slate-500 mb-1">메모</div>
              <div className="text-sm text-slate-800 whitespace-pre-wrap">{data.notes}</div>
            </div>
          )}
        </section>

        {/* 배차 차량 + 견적서 PDF — 선정된 공급사만 send 버튼, BP/ADMIN/공급사 모두 PDF 보기. */}
        <DispatchSection
          quotationRequestId={data.id}
          requestedCategory={data.equipment_category ?? null}
          requestedManpowerRole={data.manpower_role ?? null}
          canSend={isSupplier}
          canViewPdf={true}
          canCompare={isAdmin || isBP}
        />

        {/* 서류 묶음 — 차량 send 후 노출. 공급사: send 카드 / BP+ADMIN: 받은 서류 + 보완요청. */}
        <DocumentBundleSection quotationRequestId={data.id} />

        {/* 흐름 마무리 단계 — 견적이 공급사에 보내진 후부터 항상 노출.
            BP/ADMIN 시점에서 진행률을 단계별로 보여주고 작업계획서로 진입. */}
        {canFinalize && (targets.length > 0 || proposals.length > 0) && (
          <NextStepCard
            quotationRequestId={data.id}
            hasSelectedSupplier={
              targets.some((t) => t.status === 'FINAL_ACCEPTED')
              || proposals.some((p) => p.status === 'FINAL_ACCEPTED')
            }
            onNavigate={() => navigate(`/work-plans/new?fromQuotation=${data.id}`)}
          />
        )}

        {(data as any).mode !== 'OPEN_BID' && (
        <section className="card p-0 overflow-x-auto">
          <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
            <h2 className="text-lg font-bold">
              {isSupplier ? '받은 견적 (자기 회사 행만)' : `공급사 응답 (${targets.length})`}
            </h2>
          </div>
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-2 font-semibold">공급사</th>
                <th className="px-4 py-2 font-semibold">{data.request_type === 'MANPOWER' ? '인원' : '장비'}</th>
                <th className="px-4 py-2 font-semibold">상태</th>
                <th className="px-4 py-2 font-semibold">메모</th>
                <th className="px-4 py-2 font-semibold">액션</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {targets.length === 0 && (
                <tr><td colSpan={5} className="px-4 py-6 text-center text-sm text-slate-400">target 없음</td></tr>
              )}
              {targets.map((t) => (
                <tr key={t.id}>
                  <td className="px-4 py-3 font-medium text-slate-900">{t.supplier_company_name}</td>
                  <td className="px-4 py-3 text-slate-700">
                    {data.request_type === 'MANPOWER' ? (t.person_label ?? '-') : (t.equipment_label ?? '-')}
                  </td>
                  <td className="px-4 py-3"><TargetChip status={t.status} /></td>
                  <td className="px-4 py-3 text-xs text-slate-500 max-w-[260px]">
                    {t.response_note || '-'}
                    {t.responded_at && (
                      <div className="text-[10px] text-slate-400">
                        {new Date(t.responded_at).toLocaleString('ko-KR')}
                      </div>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-1.5">
                      {/* 공급사 본인 + PENDING → 수락/거부 */}
                      {isSupplier && t.status === 'PENDING' && isOpen && (
                        <>
                          <button
                            type="button"
                            onClick={() => setRespondFor({ targetId: t.id, accept: true })}
                            disabled={busy}
                            className="text-xs px-2 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
                          >
                            수락
                          </button>
                          <button
                            type="button"
                            onClick={() => setRespondFor({ targetId: t.id, accept: false })}
                            disabled={busy}
                            className="text-xs px-2 py-1 rounded bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50"
                          >
                            거부
                          </button>
                        </>
                      )}
                      {/* BP/ADMIN + ACCEPTED → 최종 채택 */}
                      {canFinalize && t.status === 'ACCEPTED' && isOpen && (
                        <button
                          type="button"
                          onClick={() => setConfirmState({
                            title: '최종 채택', message: '이 응답을 최종 채택하시겠어요? 작업계획서는 별도로 작성해야 합니다.',
                            confirmLabel: '최종 채택', variant: 'primary', run: () => finalize(t.id),
                          })}
                          disabled={busy}
                          className="text-xs px-2 py-1 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50"
                        >
                          최종 채택
                        </button>
                      )}
                      {t.status === 'FINAL_ACCEPTED' && t.finalized_to_work_plan_id && (
                        <a
                          href={`/work-plans/${t.finalized_to_work_plan_id}`}
                          className="text-xs px-2 py-1 rounded border border-emerald-300 text-emerald-700 hover:bg-emerald-50"
                        >
                          → 작업계획서
                        </a>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
        )}

        {(data as any).mode === 'OPEN_BID' && (() => {
          const active = proposals.filter((p) => p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW' || p.status === 'FINAL_ACCEPTED');
          const dailyRates = active.map((p) => p.daily_rate).filter((v): v is number => v != null && v > 0);
          const monthlyRates = active.map((p) => p.monthly_rate).filter((v): v is number => v != null && v > 0);
          const minDaily = dailyRates.length > 0 ? Math.min(...dailyRates) : null;
          const minMonthly = monthlyRates.length > 0 ? Math.min(...monthlyRates) : null;
          const avgDaily = dailyRates.length > 0 ? Math.round(dailyRates.reduce((a, b) => a + b, 0) / dailyRates.length) : null;
          const avgMonthly = monthlyRates.length > 0 ? Math.round(monthlyRates.reduce((a, b) => a + b, 0) / monthlyRates.length) : null;
          return (
          <section className="card p-0 overflow-x-auto">
            {proposals.length > 0 && (minDaily != null || minMonthly != null) && (
              <div className="px-4 py-3 border-b border-slate-200 bg-slate-50 flex flex-wrap gap-3 text-xs">
                {minDaily != null && (
                  <div className="flex items-center gap-1.5">
                    <span className="text-slate-500">최저 일대</span>
                    <span className="font-bold text-emerald-700">{minDaily.toLocaleString()}원</span>
                    {avgDaily != null && <span className="text-slate-400">/ 평균 {avgDaily.toLocaleString()}원</span>}
                  </div>
                )}
                {minMonthly != null && (
                  <div className="flex items-center gap-1.5">
                    <span className="text-slate-500">최저 월대</span>
                    <span className="font-bold text-emerald-700">{minMonthly.toLocaleString()}원</span>
                    {avgMonthly != null && <span className="text-slate-400">/ 평균 {avgMonthly.toLocaleString()}원</span>}
                  </div>
                )}
                <div className="ml-auto text-slate-500">참여 공급사 {new Set(active.map((p) => p.supplier_company_id)).size}개사</div>
              </div>
            )}
            <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between flex-wrap gap-2">
              <h2 className="text-lg font-bold">받은 제안 ({proposals.length})</h2>
              <div className="flex items-center gap-2 flex-wrap">
                <select value={proposalStatusFilter}
                  onChange={(e) => setProposalStatusFilter(e.target.value as typeof proposalStatusFilter)}
                  className="text-xs px-2 py-1 rounded border border-slate-300 bg-white">
                  <option value="ALL">전체 상태</option>
                  <option value="SUBMITTED">제출됨</option>
                  <option value="PENDING_REVIEW">재확인 필요</option>
                  <option value="FINAL_ACCEPTED">최종 선정</option>
                  <option value="REJECTED">거절</option>
                  <option value="WITHDRAWN">철회</option>
                </select>
                <select value={proposalSort}
                  onChange={(e) => setProposalSort(e.target.value as typeof proposalSort)}
                  className="text-xs px-2 py-1 rounded border border-slate-300 bg-white">
                  <option value="LATEST">최신순</option>
                  <option value="DAILY_ASC">일대 낮은순</option>
                  <option value="DAILY_DESC">일대 높은순</option>
                  <option value="MONTHLY_ASC">월대 낮은순</option>
                  <option value="MONTHLY_DESC">월대 높은순</option>
                </select>
                {canFinalize && isOpen && proposals.length > 0 && (
                  <button onClick={() => setConfirmState({
                            title: '견적 종료', message: '견적을 종료(close)하시겠어요? 미선정 제안은 자동 거절됩니다.',
                            confirmLabel: '견적 종료', variant: 'primary', run: closeOpenBid,
                          })} disabled={busy}
                          className="text-xs px-2 py-1 rounded border border-slate-300 hover:bg-slate-50 disabled:opacity-50">
                    견적 종료
                  </button>
                )}
              </div>
            </div>
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-4 py-2 font-semibold">공급사</th>
                  <th className="px-4 py-2 font-semibold">자원</th>
                  <th className="px-4 py-2 font-semibold">일대 / 월대</th>
                  <th className="px-4 py-2 font-semibold">메모</th>
                  <th className="px-4 py-2 font-semibold">상태</th>
                  <th className="px-4 py-2 font-semibold">액션</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {(() => {
                  let list = proposals.slice();
                  if (proposalStatusFilter !== 'ALL') {
                    list = list.filter((p) => p.status === proposalStatusFilter);
                  }
                  const key = proposalSort === 'DAILY_ASC' || proposalSort === 'DAILY_DESC' ? 'daily_rate'
                    : proposalSort === 'MONTHLY_ASC' || proposalSort === 'MONTHLY_DESC' ? 'monthly_rate' : null;
                  if (key) {
                    const asc = proposalSort.endsWith('_ASC');
                    list.sort((a, b) => {
                      const av = (a as any)[key] ?? Number.MAX_SAFE_INTEGER;
                      const bv = (b as any)[key] ?? Number.MAX_SAFE_INTEGER;
                      return asc ? av - bv : bv - av;
                    });
                  } else {
                    list.sort((a, b) => b.id - a.id);
                  }
                  if (list.length === 0) {
                    return <tr><td colSpan={6} className="px-4 py-6 text-center text-sm text-slate-400">조건에 맞는 제안 없음</td></tr>;
                  }
                  return list.map((p) => (
                  <tr key={p.id}>
                    <td className="px-4 py-3 font-medium text-slate-900">{p.supplier_company_name ?? `#${p.supplier_company_id}`}</td>
                    <td className="px-4 py-3 text-slate-700">{p.equipment_label ?? p.person_label ?? '-'}</td>
                    <td className="px-4 py-3 text-slate-700">
                      <span className={p.daily_rate != null && minDaily != null && p.daily_rate === minDaily ? 'font-bold text-emerald-700' : ''}>
                        {p.daily_rate ? `${p.daily_rate.toLocaleString()}` : '-'}
                      </span>
                      {p.daily_rate != null && minDaily != null && p.daily_rate === minDaily && (
                        <span className="ml-1 text-[10px] px-1 py-0.5 rounded bg-emerald-100 text-emerald-700">최저</span>
                      )}
                      {' / '}
                      <span className={p.monthly_rate != null && minMonthly != null && p.monthly_rate === minMonthly ? 'font-bold text-emerald-700' : ''}>
                        {p.monthly_rate ? `${p.monthly_rate.toLocaleString()}` : '-'}
                      </span>
                      {p.monthly_rate != null && minMonthly != null && p.monthly_rate === minMonthly && (
                        <span className="ml-1 text-[10px] px-1 py-0.5 rounded bg-emerald-100 text-emerald-700">최저</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500 max-w-[240px] whitespace-pre-wrap">{p.note ?? '-'}</td>
                    <td className="px-4 py-3">
                      <ProposalStatusChip status={p.status} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1.5">
                        <button onClick={() => openProposalBlob(p.id, 'pdf')}
                                className="text-xs px-2 py-1 rounded border border-brand-300 text-brand-700 hover:bg-brand-50">
                          미리보기 (PDF)
                        </button>
                        <button onClick={() => openProposalBlob(p.id, 'xlsx')}
                                className="text-xs px-2 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                          엑셀
                        </button>
                        {canFinalize && (p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW') && isOpen && (
                          <button onClick={() => setConfirmState({
                                    title: '제안 선정', message: '이 제안을 최종 선정하시겠어요? 작업계획서는 별도로 작성해야 합니다.',
                                    confirmLabel: '선정', variant: 'primary', run: () => finalizeProposal(p.id),
                                  })} disabled={busy}
                                  className="text-xs px-2 py-1 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
                            선정
                          </button>
                        )}
                        {p.status === 'FINAL_ACCEPTED' && (
                          <span className="text-xs text-emerald-700 self-center">최종 선정됨</span>
                        )}
                      </div>
                    </td>
                  </tr>
                  ));
                })()}
              </tbody>
            </table>
          </section>
          );
        })()}
      </div>

      <ConfirmDialog
        open={!!confirmState}
        title={confirmState?.title ?? ''}
        message={confirmState?.message ?? ''}
        confirmLabel={confirmState?.confirmLabel}
        variant={confirmState?.variant}
        busy={busy}
        onConfirm={async () => {
          const s = confirmState;
          if (!s) return;
          await s.run();
          setConfirmState(null);
        }}
        onCancel={() => setConfirmState(null)}
      />
      {respondFor && (
        <RespondNoteDialog
          accept={respondFor.accept}
          busy={busy}
          onClose={() => setRespondFor(null)}
          onConfirm={submitRespond}
        />
      )}
    </AppShell>
  );
}

/** 견적 target 수락/거부 시 메모·사유를 받는 다이얼로그 (BpInboxPage RejectDialog 패턴). */
function RespondNoteDialog({ accept, busy, onClose, onConfirm }: {
  accept: boolean;
  busy: boolean;
  onClose: () => void;
  onConfirm: (note: string) => void;
}) {
  const [note, setNote] = useState('');
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">{accept ? '견적 수락' : '견적 거부'}</h3>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          <div>
            <label className="text-xs font-semibold text-slate-500">{accept ? '수락 메모 (선택)' : '거부 사유 (선택)'}</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2}
                      className="input mt-1 w-full" />
          </div>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={() => onConfirm(note)} disabled={busy}
                  className={`px-3 py-1.5 text-sm rounded text-white disabled:opacity-50 ${
                    accept ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-rose-600 hover:bg-rose-700'
                  }`}>
            {busy ? '처리 중…' : accept ? '수락' : '거부'}
          </button>
        </div>
      </div>
    </div>
  );
}

function NextStepCard({ quotationRequestId, hasSelectedSupplier, onNavigate }: {
  quotationRequestId: number;
  hasSelectedSupplier: boolean;
  onNavigate: () => void;
}) {
  const [sentEqCount, setSentEqCount] = useState<number | null>(null);
  const [sentPCount, setSentPCount] = useState<number | null>(null);
  const [bundleCount, setBundleCount] = useState<number | null>(null);

  useEffect(() => {
    Promise.all([
      api.get<any[]>(`/api/quotations/${quotationRequestId}/dispatched`).catch(() => ({ data: [] as any[] })),
      api.get<any[]>(`/api/quotations/${quotationRequestId}/dispatched-persons`).catch(() => ({ data: [] as any[] })),
      api.get<any[]>(`/api/quotations/${quotationRequestId}/document-bundle`).catch(() => ({ data: [] as any[] })),
    ]).then(([eq, pp, bd]) => {
      setSentEqCount((eq as any).data?.length ?? 0);
      setSentPCount((pp as any).data?.length ?? 0);
      setBundleCount((bd as any).data?.length ?? 0);
    });
  }, [quotationRequestId]);

  const hasDispatch = (sentEqCount ?? 0) + (sentPCount ?? 0) > 0;
  const hasBundle = (bundleCount ?? 0) > 0;
  const allReady = hasSelectedSupplier && hasDispatch && hasBundle;
  const doneCount = [hasSelectedSupplier, hasDispatch, hasBundle].filter(Boolean).length;

  const items: Array<{ done: boolean; label: string; sub: string }> = [
    {
      done: hasSelectedSupplier,
      label: '공급사 선정',
      sub: hasSelectedSupplier ? '최종 선정 완료' : '아직 선정된 공급사가 없습니다',
    },
    {
      done: hasDispatch,
      label: '차량/인원 단가 발송',
      sub: hasDispatch
        ? `차량 ${sentEqCount ?? 0}대 · 인원 ${sentPCount ?? 0}명 받음`
        : (sentEqCount === null ? '확인 중...' : '아직 발송된 자원이 없습니다'),
    },
    {
      done: hasBundle,
      label: '공급사 서류 묶음 수신',
      sub: hasBundle
        ? `공급사 서류 묶음 ${bundleCount}건 수신 — 차량/인원의 등록증·자격증 등 묶어서 받음`
        : (bundleCount === null
          ? '확인 중...'
          : '아직 서류 묶음이 도착하지 않았습니다 (공급사가 발송 후 도착)'),
    },
  ];

  return (
    <section className={`card ${allReady ? 'border-emerald-300 bg-emerald-50/40' : 'border-blue-200 bg-blue-50/30'}`}>
      <div className="flex items-start justify-between gap-4 flex-wrap mb-3">
        <div>
          <h2 className="text-base font-bold text-slate-900">다음 단계 — 작업계획서 작성</h2>
          <p className="mt-1 text-xs text-slate-600">
            {allReady
              ? '모든 항목 확인 완료. 작업계획서로 진행할 수 있습니다.'
              : `완료 ${doneCount}/3 — 일부 항목이 비어 있어도 작업계획서를 시작할 수 있습니다.`}
          </p>
        </div>
        <span className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
          allReady ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'
        }`}>
          {allReady ? '준비 완료' : `${doneCount}/3 진행`}
        </span>
      </div>

      <ul className="space-y-1.5 mb-4">
        {items.map((it) => (
          <li key={it.label} className="flex items-start gap-2">
            <span className={`mt-0.5 inline-flex items-center justify-center w-5 h-5 rounded-full text-xs font-bold ${
              it.done ? 'bg-emerald-500 text-white' : 'bg-slate-200 text-slate-500'
            }`}>
              {it.done ? '✓' : '·'}
            </span>
            <div className="flex-1">
              <div className={`text-sm font-medium ${it.done ? 'text-slate-900' : 'text-slate-500'}`}>
                {it.label}
              </div>
              <div className="text-[11px] text-slate-500">{it.sub}</div>
            </div>
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-end pt-3 border-t border-slate-200">
        <button
          type="button"
          onClick={onNavigate}
          className={`px-4 py-2 rounded-md text-sm font-medium whitespace-nowrap text-white ${
            allReady ? 'bg-emerald-600 hover:bg-emerald-700' : 'bg-blue-600 hover:bg-blue-700'
          }`}
        >
          작업계획서 만들기 →
        </button>
      </div>
    </section>
  );
}

function ProposalStatusChip({ status }: { status: string }) {
  const cls: Record<string, string> = {
    SUBMITTED: 'bg-blue-100 text-blue-700',
    PENDING_REVIEW: 'bg-amber-100 text-amber-700',
    FINAL_ACCEPTED: 'bg-emerald-100 text-emerald-700',
    REJECTED: 'bg-rose-100 text-rose-700',
    WITHDRAWN: 'bg-slate-100 text-slate-500',
  };
  const labels: Record<string, string> = {
    SUBMITTED: '제출됨',
    PENDING_REVIEW: '재확인 필요',
    FINAL_ACCEPTED: '최종 선정',
    REJECTED: '거절',
    WITHDRAWN: '철회',
  };
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls[status] ?? 'bg-slate-100'}`}>
      {labels[status] ?? status}
    </span>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs font-semibold text-slate-500">{label}</div>
      <div className="text-sm text-slate-800">{value}</div>
    </div>
  );
}

function StatusChip({ status }: { status: QuotationRequestResponse['status'] }) {
  const cls = {
    SENT: 'bg-blue-100 text-blue-700',
    CLOSED: 'bg-emerald-100 text-emerald-700',
    CANCELLED: 'bg-slate-100 text-slate-600',
    DRAFT: 'bg-amber-100 text-amber-700',
  } as const;
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls[status]}`}>
      {QUOTATION_STATUS_LABEL[status]}
    </span>
  );
}

function TargetChip({ status }: { status: keyof typeof QUOTATION_TARGET_STATUS_LABEL }) {
  const cls = {
    PENDING: 'bg-slate-100 text-slate-700',
    ACCEPTED: 'bg-emerald-100 text-emerald-700',
    REJECTED: 'bg-rose-100 text-rose-700',
    FINAL_ACCEPTED: 'bg-brand-100 text-brand-700',
    EXPIRED: 'bg-amber-100 text-amber-700',
  } as const;
  return (
    <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${cls[status]}`}>
      {QUOTATION_TARGET_STATUS_LABEL[status]}
    </span>
  );
}
