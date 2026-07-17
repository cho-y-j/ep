import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import type { PersonResponse } from '../../types/person';
import {
  COMPLIANCE_STATUS_LABEL,
  type AddEquipmentPayload,
  type AddPersonPayload,
  type ComplianceCheckResponse,
  type WorkPlanResponse,
} from '../../types/workPlan';
import type { DocxTemplateResponse } from '../../types/docxTemplate';
import { WorkPlanStatusBadge } from './WorkPlanPage';
import WorkConfirmationSection from '../workConfirmation/WorkConfirmationSection';
import { SignaturePanel } from './create/components/SignaturePanel';
import MissingDocsDialog from './create/components/MissingDocsDialog';
import IssueResourceCheckDialog from '../resourceCheck/IssueResourceCheckDialog';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';

/** 자원 행 아래 점검 요청 list — 회신 서류 미리보기 + BP 승인/반려. */
function ResourceCheckRows({ checks, ownerType, ownerId, onChanged }: {
  checks: ResourceCheckResponse[];
  ownerType: 'EQUIPMENT' | 'PERSON';
  ownerId: number;
  onChanged: () => void;
}) {
  const mine = checks.filter((c) => c.owner_type === ownerType && c.owner_id === ownerId);
  if (mine.length === 0) return null;
  const review = async (id: number, action: 'approve' | 'reject') => {
    let note: string | null = null;
    if (action === 'reject') note = window.prompt('반려 사유 (선택)') ?? '';
    try {
      await api.post(`/api/resource-checks/${id}/${action}`, { note });
      onChanged();
    } catch (e: any) {
      alert(e?.response?.data?.message ?? '실패');
    }
  };
  const previewDoc = async (docId: number) => {
    try {
      const res = await api.get(`/api/documents/${docId}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(res.data as Blob);
      window.open(url, '_blank');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (e: any) {
      alert(e?.response?.data?.message ?? '미리보기 실패');
    }
  };
  return (
    <div className="mt-2 ml-1 space-y-1.5">
      {mine.map((c) => {
        const cls = c.status === 'APPROVED' ? 'bg-emerald-50 border-emerald-200'
          : c.status === 'SUBMITTED' ? 'bg-blue-50 border-blue-200'
          : c.status === 'REJECTED' ? 'bg-rose-50 border-rose-200'
          : 'bg-amber-50 border-amber-200';
        return (
          <div key={c.id} className={`rounded border ${cls} px-2 py-1.5 text-xs flex items-center gap-2 flex-wrap`}>
            <span className="font-semibold text-slate-800">{CHECK_TYPE_LABEL[c.check_type]}</span>
            <span className="text-slate-600">· {CHECK_STATUS_LABEL[c.status]}</span>
            {c.due_date && <span className="text-rose-700">· 마감 {c.due_date}</span>}
            {c.document_id && (
              <button onClick={() => void previewDoc(c.document_id!)}
                      className="ml-auto px-2 py-0.5 rounded border border-blue-300 text-blue-700 hover:bg-blue-50">
                회신 서류 미리보기
              </button>
            )}
            {c.status === 'SUBMITTED' && (
              <>
                <button onClick={() => void review(c.id, 'approve')}
                        className="px-2 py-0.5 rounded bg-emerald-600 text-white hover:bg-emerald-700">
                  승인
                </button>
                <button onClick={() => void review(c.id, 'reject')}
                        className="px-2 py-0.5 rounded border border-rose-400 text-rose-700 hover:bg-rose-50">
                  반려
                </button>
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

/** 자원 행 옆 status chip — 점검 상태 + wp 상태 조합. */
function resourceStatusChip(checks: ResourceCheckResponse[] | undefined, ownerType: 'EQUIPMENT' | 'PERSON', ownerId: number, wpStatus: string) {
  if (wpStatus === 'IN_PROGRESS') {
    return { label: '투입됨', cls: 'bg-blue-100 text-blue-800' };
  }
  if (wpStatus === 'DONE') {
    return { label: '작업 완료', cls: 'bg-slate-200 text-slate-700' };
  }
  // SUBMITTED / APPROVED — 작업계획서 제출/승인 완료, 시작 전. 자원은 "투입 대기".
  if (wpStatus === 'SUBMITTED' || wpStatus === 'APPROVED') {
    return { label: '투입 대기', cls: 'bg-emerald-100 text-emerald-800' };
  }
  const mine = (checks ?? []).filter((c) => c.owner_type === ownerType && c.owner_id === ownerId);
  if (mine.length === 0) {
    return { label: '점검 미요청', cls: 'bg-slate-100 text-slate-500' };
  }
  // 최신부터 우선. APPROVED 하나라도 있으면 투입 대기.
  if (mine.some((c) => c.status === 'APPROVED')) {
    return { label: '투입 대기', cls: 'bg-emerald-100 text-emerald-800' };
  }
  const pending = mine.find((c) => c.status === 'REQUESTED');
  if (pending) return { label: '회신 대기 (' + CHECK_TYPE_LABEL[pending.check_type] + ')', cls: 'bg-amber-100 text-amber-800' };
  const submitted = mine.find((c) => c.status === 'SUBMITTED');
  if (submitted) return { label: '검토 대기 (' + CHECK_TYPE_LABEL[submitted.check_type] + ')', cls: 'bg-blue-100 text-blue-800' };
  const rejected = mine.find((c) => c.status === 'REJECTED');
  if (rejected) return { label: '재제출 필요 (' + CHECK_TYPE_LABEL[rejected.check_type] + ')', cls: 'bg-rose-100 text-rose-800' };
  return { label: '점검 진행', cls: 'bg-slate-100 text-slate-600' };
}

export default function WorkPlanDetailPage() {
  const { id } = useParams();
  const planId = id ? Number(id) : NaN;
  const navigate = useNavigate();
  const { user } = useAuth();

  const [wp, setWp] = useState<WorkPlanResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [allSigned, setAllSigned] = useState(false);
  const [missingDocsOpen, setMissingDocsOpen] = useState(false);
  const [checks, setChecks] = useState<ResourceCheckResponse[]>([]);

  const loadChecks = useCallback(async () => {
    if (Number.isNaN(planId)) return;
    try {
      const res = await api.get<ResourceCheckResponse[]>(`/api/resource-checks/work-plan/${planId}`);
      setChecks(res.data);
    } catch { setChecks([]); }
  }, [planId]);
  useEffect(() => { void loadChecks(); }, [loadChecks]);

  const isAdmin = user?.role === 'ADMIN';
  const isBp = user?.role === 'BP';
  const canManage = isAdmin || (isBp && wp != null && wp.bp_company_id === user?.company_id);
  const isDraft = wp?.status === 'DRAFT';

  const load = useCallback(async () => {
    setError(null);
    try {
      const planRes = await api.get<WorkPlanResponse>(`/api/work-plans/${planId}`);
      setWp(planRes.data);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '불러오기 실패');
    }
  }, [planId]);

  useEffect(() => { if (!Number.isNaN(planId)) void load(); }, [planId, load]);

  async function transition(action: 'submit' | 'approve' | 'start' | 'complete' | 'cancel', body?: object) {
    if (!wp) return;
    setBusy(true);
    setError(null);
    try {
      const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/${action}`, body ?? {});
      setWp(res.data);
    } catch (err) {
      if (err instanceof AxiosError) {
        const code = err.response?.data?.code;
        const msg = err.response?.data?.message ?? '작업 실패';
        // 제출 시 필수 서류 미비 — 그 자리에서 보완요청 모달 띄움.
        if (action === 'submit' && code === 'DOCUMENTS_BLOCKED_AT_SUBMIT') {
          setMissingDocsOpen(true);
          setBusy(false);
          return;
        }
        // S-8.5: start 시 다른 현장 충돌 → ADMIN 만 강제 진행
        if (action === 'start' && code === 'RESOURCE_CONFLICTS' && isAdmin) {
          if (window.confirm(`${msg}\n\nADMIN 권한으로 강제 시작하시겠습니까?`)) {
            const reason = window.prompt('강제 시작 사유');
            if (!reason || !reason.trim()) { setBusy(false); return; }
            try {
              const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/start`, {
                force: true, force_reason: reason.trim(),
              });
              setWp(res.data);
            } catch (err2) {
              if (err2 instanceof AxiosError) setError(err2.response?.data?.message ?? '강제 시작 실패');
            }
            setBusy(false);
            return;
          }
        }
        setError(msg);
      }
    } finally {
      setBusy(false);
    }
  }

  async function removeEquipment(equipmentId: number) {
    if (!wp) return;
    setBusy(true);
    try {
      const res = await api.delete<WorkPlanResponse>(`/api/work-plans/${wp.id}/equipment/${equipmentId}`);
      setWp(res.data);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '삭제 실패');
    } finally {
      setBusy(false);
    }
  }

  async function removePerson(personId: number) {
    if (!wp) return;
    setBusy(true);
    try {
      const res = await api.delete<WorkPlanResponse>(`/api/work-plans/${wp.id}/persons/${personId}`);
      setWp(res.data);
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '삭제 실패');
    } finally {
      setBusy(false);
    }
  }

  if (Number.isNaN(planId)) {
    return <AppShell><p className="text-rose-600">잘못된 경로</p></AppShell>;
  }

  if (!wp) {
    return <AppShell breadcrumb={[{ label: '작업계획서', to: '/work-plans' }, { label: '상세' }]}>
      {error ? <p className="text-rose-600">{error}</p> : <p className="text-slate-400">불러오는 중...</p>}
    </AppShell>;
  }

  return (
    <AppShell breadcrumb={[{ label: '작업계획서', to: '/work-plans' }, { label: wp.title }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <header className="card">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-2">
              <div className="flex items-center gap-3">
                <h1 className="text-2xl font-bold text-slate-950">{wp.title}</h1>
                <WorkPlanStatusBadge status={wp.status} />
              </div>
              <p className="text-sm text-slate-500">
                <span className="font-semibold text-slate-700">{wp.site_name}</span>
                <span className="mx-2 text-slate-300">·</span>
                {wp.bp_company_name}
                <span className="mx-2 text-slate-300">·</span>
                {wp.work_date}
                {wp.start_time && (<><span className="mx-1 text-slate-300">·</span>{wp.start_time.slice(0, 5)} ~ {wp.end_time?.slice(0, 5) ?? ''}</>)}
              </p>
              {wp.work_location && <p className="text-sm text-slate-600">위치: {wp.work_location}</p>}
              {wp.description && <p className="text-sm text-slate-600 whitespace-pre-line">{wp.description}</p>}
            </div>
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={() => navigate('/work-plans')} className="btn-secondary">목록</button>
              <button
                type="button"
                onClick={() => window.open(`/work-plans/${wp.id}/print`, '_blank', 'noopener')}
                className="rounded border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >인쇄</button>
              <DocxExportButton planId={wp.id} title={wp.title} />
              <OnlyOfficeButton planId={wp.id} title={wp.title} />
              {canManage && (
                <button
                  type="button"
                  disabled={busy}
                  onClick={async () => {
                    const defaultDate = new Date(new Date(wp.work_date).getTime() + 86400000)
                      .toISOString().slice(0, 10);
                    const newDate = window.prompt('새 작업일 (YYYY-MM-DD)', defaultDate);
                    if (!newDate) return;
                    setBusy(true);
                    setError(null);
                    try {
                      const res = await api.post<WorkPlanResponse>(
                        `/api/work-plans/${wp.id}/clone`,
                        { work_date: newDate },
                      );
                      navigate(`/work-plans/${res.data.id}`);
                    } catch (err) {
                      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '복제 실패');
                    } finally {
                      setBusy(false);
                    }
                  }}
                  className="rounded border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
                >복제</button>
              )}
              {/* DRAFT 상태의 "제출" 버튼은 하단 박스로 통합 — 헤더에는 두지 않음 */}
              {canManage && wp.status === 'SUBMITTED' && (
                <button type="button" disabled={busy} onClick={() => void transition('approve')} className="btn-primary disabled:opacity-50">승인</button>
              )}
              {canManage && wp.status === 'APPROVED' && (
                <button type="button" disabled={busy} onClick={() => void transition('start')} className="btn-primary disabled:opacity-50">작업 시작</button>
              )}
              {canManage && wp.status === 'IN_PROGRESS' && (
                <button type="button" disabled={busy} onClick={() => void transition('complete')} className="btn-primary disabled:opacity-50">작업 완료</button>
              )}
              {canManage && wp.status !== 'CANCELLED' && wp.status !== 'DONE' && (
                <button type="button" disabled={busy} onClick={() => {
                  const reason = window.prompt('취소 사유를 입력하세요');
                  if (!reason || !reason.trim()) return;
                  void transition('cancel', { reason: reason.trim() });
                }} className="rounded border border-rose-200 bg-white px-3 py-1.5 text-sm font-semibold text-rose-600 hover:bg-rose-50">취소</button>
              )}
            </div>
          </div>
          {wp.cancel_reason && (
            <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              취소 사유: {wp.cancel_reason}
            </p>
          )}
          {error && <p className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
        </header>

        <section className="grid gap-4 lg:grid-cols-2">
          <EquipmentBlock
            wp={wp}
            isDraft={Boolean(canManage && isDraft)}
            isAdmin={isAdmin}
            canIssueCheck={Boolean(canManage)}
            checks={checks}
            onCheckIssued={loadChecks}
            onChanged={setWp}
            onRemove={removeEquipment}
            busy={busy}
            setError={setError}
          />
          <PersonBlock
            wp={wp}
            isDraft={Boolean(canManage && isDraft)}
            isAdmin={isAdmin}
            canIssueCheck={Boolean(canManage)}
            checks={checks}
            onCheckIssued={loadChecks}
            onChanged={setWp}
            onRemove={removePerson}
            busy={busy}
            setError={setError}
          />
        </section>

        {/* 전자서명 상태 패널 — 작업계획서 상세에서 진행 상황 확인 + 사인 추가 가능. */}
        <section className="space-y-3">
          <SignaturePanel
            workPlanId={wp.id}
            onAllSignedChange={setAllSigned}
          />
          {/* DRAFT 상태일 때 — 사인 5/5 + 점검 모두 APPROVED 면 최종 제출 활성화. 둘 중 하나라도 안 되면 안내. */}
          {wp.status === 'DRAFT' && canManage && (() => {
            const pendingChecks = checks.filter((c) => c.status !== 'APPROVED' && c.status !== 'CANCELLED');
            const allChecksOk = pendingChecks.length === 0;
            const ready = allSigned && allChecksOk;
            return (
              <div className={`rounded-lg border p-3 flex items-center justify-between ${ready ? 'border-emerald-300 bg-emerald-50' : 'border-amber-300 bg-amber-50'}`}>
                <div className="text-sm">
                  {ready ? (
                    <span className="text-emerald-800">사인 5/5 + 자원 점검 모두 승인 완료 — 작업계획서를 최종 제출할 수 있습니다.</span>
                  ) : (
                    <span className="text-amber-800">
                      최종 제출 전 확인 필요 —
                      {!allSigned && <> 사인 미완료</>}
                      {!allChecksOk && (
                        <> · 점검 {pendingChecks.length}건 미승인 (
                          {pendingChecks.slice(0, 3).map((c) => c.owner_label).join(', ')}
                          {pendingChecks.length > 3 ? ' 외' : ''})
                        </>
                      )}
                    </span>
                  )}
                </div>
              <button
                type="button"
                onClick={() => void transition('submit')}
                disabled={busy || !ready}
                className="rounded-md bg-emerald-600 px-4 py-1.5 text-sm font-bold text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                {busy ? '제출 중…' : '최종 제출'}
              </button>
            </div>
            );
          })()}
        </section>

        <WorkConfirmationSection
          workPlanId={wp.id}
          bpCompanyId={wp.bp_company_id}
          workDate={wp.work_date}
          workEndDate={(wp as any).work_end_date}
          assignedPersons={(wp.persons ?? []).map((p: any) => ({
            id: p.person_id,
            name: p.person_name,
            supplier_id: p.supplier_company_id ?? p.supplier_id,
          }))}
        />

        <ComplianceHistory items={wp.compliance_checks ?? []} />
      </div>
      <MissingDocsDialog open={missingDocsOpen} workPlanId={wp.id} onClose={() => setMissingDocsOpen(false)} />
    </AppShell>
  );
}

function EquipmentBlock({ wp, isDraft, isAdmin, canIssueCheck, checks, onCheckIssued, onChanged, onRemove, busy, setError }: {
  canIssueCheck?: boolean;
  checks?: ResourceCheckResponse[];
  onCheckIssued?: () => void;
  wp: WorkPlanResponse;
  isDraft: boolean;
  isAdmin: boolean;
  onChanged: (wp: WorkPlanResponse) => void;
  onRemove: (equipmentId: number) => void;
  busy: boolean;
  setError: (s: string | null) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [candidates, setCandidates] = useState<EquipmentResponse[]>([]);
  const [form, setForm] = useState<AddEquipmentPayload>({ equipment_id: 0 });
  const [checkTarget, setCheckTarget] = useState<{
    ownerId: number;
    ownerLabel: string;
    supplierCompanyId: number;
    supplierCompanyName?: string | null;
  } | null>(null);

  useEffect(() => {
    if (!adding) { setCandidates([]); return; }
    api.get<EquipmentResponse[]>(`/api/work-plans/${wp.id}/candidates/equipment`)
      .then((res) => setCandidates(res.data))
      .catch(() => setCandidates([]));
  }, [adding, wp.id]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!form.equipment_id) return;
    try {
      const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/equipment`, form);
      onChanged(res.data);
      setAdding(false);
      setForm({ equipment_id: 0 });
    } catch (err) {
      if (err instanceof AxiosError) {
        const msg = err.response?.data?.message ?? '추가 실패';
        const code = err.response?.data?.code;
        if (code === 'DOCUMENTS_BLOCKED' && isAdmin) {
          if (window.confirm(`${msg}\n\n관리자 권한으로 강제 진행하시겠습니까?`)) {
            const reason = window.prompt('강제 진행 사유');
            if (!reason || !reason.trim()) return;
            try {
              const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/equipment`, {
                ...form, override: true, override_reason: reason.trim(),
              });
              onChanged(res.data);
              setAdding(false);
            } catch (err2) {
              if (err2 instanceof AxiosError) setError(err2.response?.data?.message ?? '강제 진행 실패');
            }
            return;
          }
        }
        setError(msg);
      }
    }
  }

  const items = wp.equipment ?? [];

  return (
    <div className="card space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-slate-900">장비 ({items.length})</h2>
        {isDraft && !adding && (
          <button type="button" onClick={() => setAdding(true)} className="text-sm font-semibold text-brand-700 hover:text-brand-800">+ 장비 추가</button>
        )}
      </div>

      {adding && (
        <form onSubmit={add} className="rounded-lg border border-slate-200 p-3 space-y-2 bg-slate-50">
          <select
            value={form.equipment_id || ''}
            onChange={(e) => setForm((p) => ({ ...p, equipment_id: Number(e.target.value) }))}
            required
            className="input bg-white"
          >
            <option value="">장비 선택 (참여 공급사 소유)</option>
            {candidates.map((eq) => (
              <option key={eq.id} value={eq.id}>
                {eq.vehicle_no ?? eq.model ?? `장비#${eq.id}`} ({equipmentCategoryLabel(eq.category)})
              </option>
            ))}
          </select>
          <input
            placeholder="용도 (예: 토사 굴착)"
            value={form.purpose ?? ''}
            onChange={(e) => setForm((p) => ({ ...p, purpose: e.target.value }))}
            className="input bg-white"
          />
          <input
            placeholder="비고"
            value={form.note ?? ''}
            onChange={(e) => setForm((p) => ({ ...p, note: e.target.value }))}
            className="input bg-white"
          />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setAdding(false)} className="text-sm text-slate-500">취소</button>
            <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">추가</button>
          </div>
        </form>
      )}

      {items.length === 0 ? (
        <p className="text-sm text-slate-400">등록된 장비 없음</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((it) => (
            <li key={it.id} className="flex items-center justify-between py-2.5">
              <div className="min-w-0">
                <div className="font-semibold text-slate-900 truncate flex items-center gap-2">
                  <span className="truncate">{it.equipment_name ?? `장비#${it.equipment_id}`}</span>
                  {(() => {
                    const c = resourceStatusChip(checks, 'EQUIPMENT', it.equipment_id, wp.status);
                    return <span className={`shrink-0 px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${c.cls}`}>{c.label}</span>;
                  })()}
                  {it.category && <span className="ml-2 text-xs font-normal text-slate-500">{equipmentCategoryLabel(it.category)}</span>}
                </div>
                <div className="text-xs text-slate-500 truncate">
                  {it.supplier_company_name ?? '-'}{it.purpose ? ` · ${it.purpose}` : ''}
                  {it.daily_rate != null && ` · 일대 ${it.daily_rate.toLocaleString()}원`}
                  {it.monthly_rate != null && ` · 월대 ${it.monthly_rate.toLocaleString()}원`}
                  {it.source_quotation_target_id != null && (
                    <span className="ml-1 inline-block px-1.5 py-0.5 text-[10px] font-semibold bg-indigo-50 text-indigo-700 rounded">견적</span>
                  )}
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {canIssueCheck && (
                  <button type="button"
                          onClick={() => setCheckTarget({
                            ownerId: it.equipment_id,
                            ownerLabel: it.equipment_name ?? `장비#${it.equipment_id}`,
                            supplierCompanyId: it.supplier_company_id ?? 0,
                            supplierCompanyName: it.supplier_company_name ?? null,
                          })}
                          className="text-[11px] px-2 py-1 rounded border border-amber-400 text-amber-700 hover:bg-amber-50">
                    점검 요청
                  </button>
                )}
                {isDraft && (
                  <button type="button" disabled={busy} onClick={() => onRemove(it.equipment_id)} className="text-xs text-rose-600 hover:text-rose-800 disabled:opacity-50">제거</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={() => { onCheckIssued?.(); }}
          workPlanId={wp.id}
          ownerType="EQUIPMENT"
          ownerId={checkTarget.ownerId}
          ownerLabel={checkTarget.ownerLabel}
          supplierCompanyId={checkTarget.supplierCompanyId}
          supplierCompanyName={checkTarget.supplierCompanyName}
        />
      )}
      {/* 자원별 점검 요청 list + 회신 미리보기 + 승인/반려 */}
      {(checks ?? []).filter((c) => c.owner_type === 'EQUIPMENT').length > 0 && items.length > 0 && (
        <div className="mt-3 space-y-2">
          {items.map((it) => (
            <ResourceCheckRows key={`rc-${it.equipment_id}`} checks={checks ?? []} ownerType="EQUIPMENT"
                               ownerId={it.equipment_id} onChanged={() => onCheckIssued?.()} />
          ))}
        </div>
      )}
    </div>
  );
}

function PersonBlock({ wp, isDraft, isAdmin, canIssueCheck, checks, onCheckIssued, onChanged, onRemove, busy, setError }: {
  wp: WorkPlanResponse;
  isDraft: boolean;
  isAdmin: boolean;
  canIssueCheck?: boolean;
  checks?: ResourceCheckResponse[];
  onCheckIssued?: () => void;
  onChanged: (wp: WorkPlanResponse) => void;
  onRemove: (personId: number) => void;
  busy: boolean;
  setError: (s: string | null) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [candidates, setCandidates] = useState<PersonResponse[]>([]);
  const [form, setForm] = useState<AddPersonPayload>({ person_id: 0 });
  const [checkTarget, setCheckTarget] = useState<{
    ownerId: number;
    ownerLabel: string;
    supplierCompanyId: number;
    supplierCompanyName?: string | null;
  } | null>(null);

  useEffect(() => {
    if (!adding) { setCandidates([]); return; }
    api.get<PersonResponse[]>(`/api/work-plans/${wp.id}/candidates/persons`)
      .then((res) => setCandidates(res.data))
      .catch(() => setCandidates([]));
  }, [adding, wp.id]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!form.person_id) return;
    try {
      const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/persons`, form);
      onChanged(res.data);
      setAdding(false);
      setForm({ person_id: 0 });
    } catch (err) {
      if (err instanceof AxiosError) {
        const msg = err.response?.data?.message ?? '추가 실패';
        const code = err.response?.data?.code;
        if (code === 'DOCUMENTS_BLOCKED' && isAdmin) {
          if (window.confirm(`${msg}\n\n관리자 권한으로 강제 진행하시겠습니까?`)) {
            const reason = window.prompt('강제 진행 사유');
            if (!reason || !reason.trim()) return;
            try {
              const res = await api.post<WorkPlanResponse>(`/api/work-plans/${wp.id}/persons`, {
                ...form, override: true, override_reason: reason.trim(),
              });
              onChanged(res.data);
              setAdding(false);
            } catch (err2) {
              if (err2 instanceof AxiosError) setError(err2.response?.data?.message ?? '강제 진행 실패');
            }
            return;
          }
        }
        setError(msg);
      }
    }
  }

  const items = wp.persons ?? [];

  return (
    <div className="card space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-bold text-slate-900">인원 ({items.length})</h2>
        {isDraft && !adding && (
          <button type="button" onClick={() => setAdding(true)} className="text-sm font-semibold text-brand-700 hover:text-brand-800">+ 인원 추가</button>
        )}
      </div>

      {adding && (
        <form onSubmit={add} className="rounded-lg border border-slate-200 p-3 space-y-2 bg-slate-50">
          <select
            value={form.person_id || ''}
            onChange={(e) => setForm((p) => ({ ...p, person_id: Number(e.target.value) }))}
            required
            className="input bg-white"
          >
            <option value="">인원 선택 (참여 공급사 소속)</option>
            {candidates.map((p) => (<option key={p.id} value={p.id}>{p.name}{p.job_title ? ` (${p.job_title})` : ''}</option>))}
          </select>
          <select
            value={form.equipment_id ?? ''}
            onChange={(e) => setForm((p) => ({ ...p, equipment_id: e.target.value ? Number(e.target.value) : undefined }))}
            className="input bg-white"
          >
            <option value="">매칭 장비 (선택)</option>
            {(wp.equipment ?? []).map((eq) => (
              <option key={eq.equipment_id} value={eq.equipment_id}>{eq.equipment_name ?? `장비#${eq.equipment_id}`}</option>
            ))}
          </select>
          <input
            placeholder="역할 (조종원/신호수/유도자 등)"
            value={form.role ?? ''}
            onChange={(e) => setForm((p) => ({ ...p, role: e.target.value }))}
            className="input bg-white"
          />
          <input
            placeholder="비고"
            value={form.note ?? ''}
            onChange={(e) => setForm((p) => ({ ...p, note: e.target.value }))}
            className="input bg-white"
          />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setAdding(false)} className="text-sm text-slate-500">취소</button>
            <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">추가</button>
          </div>
        </form>
      )}

      {items.length === 0 ? (
        <p className="text-sm text-slate-400">등록된 인원 없음</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {items.map((it) => (
            <li key={it.id} className="flex items-center justify-between py-2.5">
              <div className="min-w-0">
                <div className="font-semibold text-slate-900 truncate flex items-center gap-2">
                  <span className="truncate">{it.person_name ?? `인원#${it.person_id}`}</span>
                  {(() => {
                    const c = resourceStatusChip(checks, 'PERSON', it.person_id, wp.status);
                    return <span className={`shrink-0 px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${c.cls}`}>{c.label}</span>;
                  })()}
                  {it.role && <span className="ml-2 text-xs font-normal text-slate-500">{it.role}</span>}
                </div>
                <div className="text-xs text-slate-500 truncate">
                  {it.supplier_company_name ?? '-'}
                  {it.equipment_id ? ` · 매칭 장비#${it.equipment_id}` : ''}
                </div>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {canIssueCheck && (
                  <button type="button"
                          onClick={() => setCheckTarget({
                            ownerId: it.person_id,
                            ownerLabel: it.person_name ?? `인원#${it.person_id}`,
                            supplierCompanyId: it.supplier_company_id ?? 0,
                            supplierCompanyName: it.supplier_company_name ?? null,
                          })}
                          className="text-[11px] px-2 py-1 rounded border border-amber-400 text-amber-700 hover:bg-amber-50">
                    점검 요청
                  </button>
                )}
                {isDraft && (
                  <button type="button" disabled={busy} onClick={() => onRemove(it.person_id)} className="text-xs text-rose-600 hover:text-rose-800 disabled:opacity-50">제거</button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      {checkTarget && (
        <IssueResourceCheckDialog
          open
          onClose={() => setCheckTarget(null)}
          onIssued={() => { onCheckIssued?.(); }}
          workPlanId={wp.id}
          ownerType="PERSON"
          ownerId={checkTarget.ownerId}
          ownerLabel={checkTarget.ownerLabel}
          supplierCompanyId={checkTarget.supplierCompanyId}
          supplierCompanyName={checkTarget.supplierCompanyName}
        />
      )}
      {(checks ?? []).filter((c) => c.owner_type === 'PERSON').length > 0 && items.length > 0 && (
        <div className="mt-3 space-y-2">
          {items.map((it) => (
            <ResourceCheckRows key={`rc-${it.person_id}`} checks={checks ?? []} ownerType="PERSON"
                               ownerId={it.person_id} onChanged={() => onCheckIssued?.()} />
          ))}
        </div>
      )}
    </div>
  );
}

function ComplianceHistory({ items }: { items: ComplianceCheckResponse[] }) {
  if (items.length === 0) {
    return (
      <section className="card">
        <h2 className="text-lg font-bold text-slate-900 mb-3">서류 컴플라이언스 이력</h2>
        <p className="text-sm text-slate-400">기록 없음</p>
      </section>
    );
  }
  return (
    <section className="card">
      <h2 className="text-lg font-bold text-slate-900 mb-3">서류 컴플라이언스 이력</h2>
      <table className="w-full text-sm">
        <thead className="text-left text-slate-500">
          <tr>
            <th className="py-2 font-semibold">시각</th>
            <th className="py-2 font-semibold">대상</th>
            <th className="py-2 font-semibold">상태</th>
            <th className="py-2 font-semibold">사유</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {items.map((c) => (
            <tr key={c.id}>
              <td className="py-2 text-slate-500">{c.checked_at.replace('T', ' ').slice(0, 16)}</td>
              <td className="py-2 text-slate-700">{c.target_type === 'EQUIPMENT' ? '장비' : '인원'}#{c.target_id}</td>
              <td className="py-2"><ComplianceBadge status={c.status} /></td>
              <td className="py-2 text-slate-600">
                {c.reason ?? '-'}
                {c.override_reason && <div className="text-xs text-rose-600">강제 사유: {c.override_reason}</div>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function ComplianceBadge({ status }: { status: ComplianceCheckResponse['status'] }) {
  const tone = status === 'OK' ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    : status === 'WARNING' ? 'bg-amber-50 text-amber-700 ring-amber-200'
    : status === 'OVERRIDDEN' ? 'bg-rose-50 text-rose-700 ring-rose-200'
    : 'bg-slate-100 text-slate-600 ring-slate-200';
  return <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ${tone}`}>{COMPLIANCE_STATUS_LABEL[status]}</span>;
}

/** DOCX 출력 — 사용 가능한 템플릿이 있으면 select 노출, 클릭 시 DOCX 다운로드. */
function DocxExportButton({ planId, title }: { planId: number; title: string }) {
  const [templates, setTemplates] = useState<DocxTemplateResponse[]>([]);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) return;
    api.get<DocxTemplateResponse[]>('/api/docx-templates', { params: { targetType: 'WORK_PLAN' } })
      .then((res) => setTemplates(res.data))
      .catch(() => setTemplates([]));
  }, [open]);

  async function download(templateId: number) {
    setBusy(true);
    try {
      const res = await api.get(`/api/work-plans/${planId}/export/docx`, {
        params: { templateId },
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(res.data as Blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${title.replace(/[\\\/:*?"<>|]/g, '_')}.docx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
      setOpen(false);
    } catch (err) {
      const ax = err as { response?: { data?: { message?: string } } };
      alert(ax?.response?.data?.message ?? 'DOCX 출력 실패');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="rounded border border-slate-200 bg-white px-3 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
      >DOCX 출력</button>
      {open && (
        <div className="absolute right-0 z-10 mt-1 w-72 rounded-lg border border-slate-200 bg-white shadow-lg">
          <div className="border-b border-slate-100 p-2 text-xs font-semibold text-slate-500">템플릿 선택</div>
          {templates.length === 0 ? (
            <div className="p-3 text-xs text-slate-500">
              사용 가능한 템플릿이 없습니다. ADMIN 또는 BP 회사 관리자가 <code>/admin/docx-templates</code> 에서 업로드하세요.
            </div>
          ) : (
            <ul className="max-h-72 overflow-y-auto py-1">
              {templates.map((t) => (
                <li key={t.id}>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => download(t.id)}
                    className="flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-slate-50 disabled:opacity-50"
                  >
                    <span className="truncate">{t.name}</span>
                    {t.company_id == null && (
                      <span className="shrink-0 text-[10px] font-semibold text-emerald-600">전역</span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

/** OnlyOffice 인플레이스 편집 — 서버에 ONLYOFFICE_URL 이 설정되었을 때만 활성화 (S-8.4). */
function OnlyOfficeButton({ planId, title: _title }: { planId: number; title: string }) {
  const [available, setAvailable] = useState<boolean | null>(null);
  useEffect(() => {
    api.get<{ enabled: boolean }>('/api/onlyoffice/status')
      .then((res) => setAvailable(res.data.enabled))
      .catch(() => setAvailable(false));
  }, []);
  if (available !== true) return null;
  return (
    <button
      type="button"
      onClick={() => window.open(`/work-plans/${planId}/edit`, '_blank', 'noopener')}
      className="rounded border border-brand-200 bg-white px-3 py-1.5 text-sm font-semibold text-brand-700 hover:bg-brand-50"
    >OnlyOffice 편집</button>
  );
}

