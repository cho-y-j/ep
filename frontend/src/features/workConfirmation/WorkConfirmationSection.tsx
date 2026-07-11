import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { SignaturePadDialog } from '../workPlan/create/components/SignaturePadDialog';

type IssuingType = 'EQUIPMENT' | 'MANPOWER';
type WCStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'INVALIDATED';

interface WorkConfirmation {
  id: number;
  work_plan_id: number;
  work_date: string;
  person_id: number;
  issuing_supplier_company_id: number;
  issuing_supplier_type: IssuingType;
  bp_company_id: number;
  work_content?: string | null;
  remarks?: string | null;
  morning_time?: string | null;     morning_hours?: number | null;
  afternoon_time?: string | null;   afternoon_hours?: number | null;
  overtime_time?: string | null;    overtime_hours?: number | null;
  night_time?: string | null;       night_hours?: number | null;
  total_hours?: number | null;
  supplier_signer_name?: string | null;
  supplier_signer_person_id?: number | null;
  supplier_signed: boolean;
  supplier_signed_at?: string | null;
  bp_signer_name?: string | null;
  bp_signed: boolean;
  bp_signed_at?: string | null;
  status: WCStatus;
}

interface AssignedPerson {
  id: number;
  name: string;
  supplier_id?: number;
}

interface Props {
  workPlanId: number;
  bpCompanyId: number;
  workDate: string;
  workEndDate?: string | null;
  assignedPersons?: AssignedPerson[];
}

const STATUS_CHIP: Record<WCStatus, { label: string; cls: string }> = {
  PENDING:     { label: '대기',     cls: 'bg-amber-100 text-amber-800' },
  COMPLETED:   { label: '완료',     cls: 'bg-emerald-100 text-emerald-800' },
  CANCELLED:   { label: '취소',     cls: 'bg-slate-100 text-slate-600' },
  INVALIDATED: { label: '무효',     cls: 'bg-rose-100 text-rose-800' },
};

/** 작업계획서 detail 안에 들어가는 작업확인서 섹션. 인원당 1건. */
export default function WorkConfirmationSection({ workPlanId, bpCompanyId, assignedPersons = [] }: Props) {
  const { user } = useAuth();
  const [items, setItems] = useState<WorkConfirmation[]>([]);
  const [loading, setLoading] = useState(false);
  const [requestingPersonId, setRequestingPersonId] = useState<number | null>(null);

  const [padOpen, setPadOpen] = useState<null | { id: number; side: 'supplier' | 'bp'; signerName?: string }>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState<Record<string, any>>({});
  const [invalidateConfirm, setInvalidateConfirm] = useState<{ id: number; pendingPatch: any } | null>(null);
  const [suggestedOt, setSuggestedOt] = useState<number | null>(null);

  const role = user?.role;
  const myCompany = user?.company_id ?? null;
  const isAdmin = role === 'ADMIN';
  const isBpUser = role === 'BP' && myCompany === bpCompanyId;
  const isSupplierUser = role === 'EQUIPMENT_SUPPLIER' || role === 'MANPOWER_SUPPLIER';

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workPlanId]);

  const refresh = async () => {
    setLoading(true);
    try {
      const res = await api.get<WorkConfirmation[]>(`/api/work-plans/${workPlanId}/work-confirmations`);
      setItems(res.data ?? []);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  };

  const wcByPersonId = useMemo(() => {
    const map = new Map<number, WorkConfirmation>();
    for (const wc of items) {
      // CANCELLED 도 같이 보관 — 발급하면 재오픈됨
      map.set(wc.person_id, wc);
    }
    return map;
  }, [items]);

  const onRequest = async (personId: number) => {
    setRequestingPersonId(personId);
    try {
      await api.post(`/api/work-plans/${workPlanId}/work-confirmations/request`, { person_id: personId });
      await refresh();
    } catch (e: any) {
      alert('발급 실패: ' + (e?.response?.data?.message || e?.response?.data?.error || e?.message || e));
    } finally {
      setRequestingPersonId(null);
    }
  };

  const openEdit = async (wc: WorkConfirmation) => {
    setEditingId(wc.id);
    setSuggestedOt(null);
    setEditForm({
      workContent: wc.work_content ?? '',
      remarks: wc.remarks ?? '',
      morningTime: wc.morning_time ?? '',  morningHours: wc.morning_hours ?? '',
      afternoonTime: wc.afternoon_time ?? '', afternoonHours: wc.afternoon_hours ?? '',
      overtimeTime: wc.overtime_time ?? '', overtimeHours: wc.overtime_hours ?? '',
      nightTime: wc.night_time ?? '',       nightHours: wc.night_hours ?? '',
    });
    // A: OT 제안값 프리필(비어있을 때만). suggested_overtime_hours 는 상세(GET /{id})에서만 값이 온다.
    // 프리필만 — 자동 저장/자동 반영 없음. 저장은 기존대로 [저장] 으로 사람이 확정.
    if (wc.overtime_hours == null) {
      try {
        const res = await api.get<{ suggested_overtime_hours?: number | null }>(`/api/work-confirmations/${wc.id}`);
        const s = res.data?.suggested_overtime_hours;
        if (s != null) {
          setSuggestedOt(s);
          setEditForm((p) => (p.overtimeHours === '' || p.overtimeHours == null ? { ...p, overtimeHours: s } : p));
        }
      } catch {
        // 신규 엔드포인트 미배포(404) 등 — 프리필 생략
      }
    }
  };

  const saveEdit = async (invalidate: boolean) => {
    if (editingId == null) return;
    try {
      // 백엔드 Jackson SNAKE_CASE — DTO 필드를 snake_case 로 전송.
      const str = (v: any) => (v === '' || v == null ? null : v);
      const num = (v: any) => (v === '' || v == null ? null : Number(v));
      const patch = {
        work_content: str(editForm.workContent),
        remarks: str(editForm.remarks),
        morning_time: str(editForm.morningTime),
        morning_hours: num(editForm.morningHours),
        afternoon_time: str(editForm.afternoonTime),
        afternoon_hours: num(editForm.afternoonHours),
        overtime_time: str(editForm.overtimeTime),
        overtime_hours: num(editForm.overtimeHours),
        night_time: str(editForm.nightTime),
        night_hours: num(editForm.nightHours),
      };
      await api.patch(`/api/work-confirmations/${editingId}?invalidate=${invalidate}`, patch);
      setEditingId(null);
      setEditForm({});
      setInvalidateConfirm(null);
      await refresh();
    } catch (e: any) {
      alert('저장 실패: ' + (e?.response?.data?.message || e?.response?.data?.error || e?.message || e));
    }
  };

  const handleSaveClick = () => {
    if (editingId == null) return;
    const wc = items.find((i) => i.id === editingId);
    if (!wc) return;
    if (wc.supplier_signed || wc.bp_signed) {
      setInvalidateConfirm({ id: wc.id, pendingPatch: editForm });
    } else {
      void saveEdit(false);
    }
  };

  const submitSign = async (pngBase64: string) => {
    if (!padOpen) return;
    try {
      const url = padOpen.side === 'supplier'
        ? `/api/work-confirmations/${padOpen.id}/sign-supplier`
        : `/api/work-confirmations/${padOpen.id}/sign-bp`;
      await api.post(url, { pngBase64 });
      setPadOpen(null);
      await refresh();
    } catch (e: any) {
      alert('사인 실패: ' + (e?.response?.data?.message || e?.response?.data?.error || e?.message || e));
    }
  };

  const cancel = async (id: number) => {
    if (!confirm('이 작업확인서를 취소하시겠습니까?')) return;
    try {
      await api.post(`/api/work-confirmations/${id}/cancel`);
      await refresh();
    } catch (e: any) {
      alert('취소 실패: ' + (e?.response?.data?.message || e?.message || e));
    }
  };

  // 발급 가능한 인원 = 자기 회사 소속 (ADMIN 은 전부) + 아직 발급 안 된 인원
  const myPersons = useMemo(() => {
    if (isAdmin) return assignedPersons;
    if (isSupplierUser && myCompany != null) {
      return assignedPersons.filter((p) => p.supplier_id === myCompany);
    }
    return [];
  }, [assignedPersons, myCompany, isAdmin, isSupplierUser]);

  const personsWithoutWc = myPersons.filter((p) => !wcByPersonId.has(p.id));

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-bold text-slate-900">일별 작업확인서</h3>
        {loading && <span className="text-xs text-slate-400">로딩...</span>}
      </div>

      {/* 미발급 인원 — 인원당 발급 버튼 */}
      {(isSupplierUser || isAdmin) && personsWithoutWc.length > 0 && (
        <div className="mb-3 p-3 rounded-lg bg-slate-50 border border-slate-200">
          <div className="text-[11px] text-slate-600 mb-2">발급 대상 인원 (인원당 1건)</div>
          <div className="flex flex-wrap gap-2">
            {personsWithoutWc.map((p) => (
              <button
                key={p.id}
                type="button"
                onClick={() => void onRequest(p.id)}
                disabled={requestingPersonId === p.id}
                className="inline-flex items-center gap-1.5 text-xs px-2.5 py-1.5 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
              >
                {requestingPersonId === p.id ? '발급 중…' : `+ ${p.name}`}
              </button>
            ))}
          </div>
        </div>
      )}

      {items.length === 0 ? (
        <div className="text-xs text-slate-400 italic py-4 text-center border border-dashed border-slate-200 rounded">
          발급된 작업확인서가 없습니다
        </div>
      ) : (
        <ul className="space-y-2">
          {items.map((wc) => {
            const chip = STATUS_CHIP[wc.status];
            const editing = editingId === wc.id;
            const person = assignedPersons.find((p) => p.id === wc.person_id);
            const canSupplierSign = (isAdmin || (isSupplierUser && myCompany === wc.issuing_supplier_company_id))
              && wc.status !== 'CANCELLED' && !wc.supplier_signed;
            const canBpSign = (isAdmin || isBpUser) && wc.status !== 'CANCELLED' && !wc.bp_signed;
            const canEdit = (isAdmin || (isSupplierUser && myCompany === wc.issuing_supplier_company_id) || isBpUser)
              && wc.status !== 'CANCELLED';
            return (
              <li key={wc.id} className="border border-slate-200 rounded-lg p-3">
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-slate-900">
                      {person?.name ?? `인원 #${wc.person_id}`}
                    </span>
                    <span className="text-[10px] text-slate-500">{wc.work_date}</span>
                    <span className="text-[10px] text-slate-500">
                      {wc.issuing_supplier_type === 'EQUIPMENT' ? '장비공급사' : '인력공급사'}
                    </span>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-medium ${chip.cls}`}>
                      {chip.label}
                    </span>
                  </div>
                  <div className="flex items-center gap-1">
                    {canEdit && !editing && (
                      <button type="button" onClick={() => openEdit(wc)} className="text-[11px] px-2 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                        수정
                      </button>
                    )}
                    {(isAdmin || (isSupplierUser && myCompany === wc.issuing_supplier_company_id)) && wc.status !== 'CANCELLED' && (
                      <button type="button" onClick={() => cancel(wc.id)} className="text-[11px] px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50">
                        취소
                      </button>
                    )}
                  </div>
                </div>

                {editing ? (
                  <div className="space-y-2">
                    <textarea
                      value={editForm.workContent ?? ''}
                      onChange={(e) => setEditForm((p) => ({ ...p, workContent: e.target.value }))}
                      rows={2}
                      placeholder="작업 내용"
                      className="w-full text-xs border border-slate-300 rounded px-2 py-1"
                    />
                    <div className="grid grid-cols-4 gap-2">
                      {[['오전', 'morning'], ['오후', 'afternoon'], ['연장', 'overtime'], ['철야', 'night']].map(([label, key]) => (
                        <div key={key as string}>
                          <label className="text-[10px] text-slate-600">
                            {label}
                            {key === 'overtime' && suggestedOt != null && (
                              <span className="ml-1 text-[9px] text-blue-600">제안값 (수정 가능)</span>
                            )}
                          </label>
                          <input
                            type="text"
                            value={editForm[`${key}Time`] ?? ''}
                            onChange={(e) => setEditForm((p) => ({ ...p, [`${key}Time`]: e.target.value }))}
                            placeholder="08:00-12:00"
                            className="w-full text-xs border border-slate-300 rounded px-1.5 py-0.5"
                          />
                          <input
                            type="number"
                            step="0.5"
                            min={0}
                            max={24}
                            value={editForm[`${key}Hours`] ?? ''}
                            onChange={(e) => setEditForm((p) => ({ ...p, [`${key}Hours`]: e.target.value }))}
                            placeholder="시간"
                            className="w-full text-xs border border-slate-300 rounded px-1.5 py-0.5 mt-0.5"
                          />
                        </div>
                      ))}
                    </div>
                    <textarea
                      value={editForm.remarks ?? ''}
                      onChange={(e) => setEditForm((p) => ({ ...p, remarks: e.target.value }))}
                      rows={1}
                      placeholder="비고"
                      className="w-full text-xs border border-slate-300 rounded px-2 py-1"
                    />
                    <div className="flex items-center justify-end gap-2">
                      <button type="button" onClick={() => { setEditingId(null); setEditForm({}); }}
                        className="text-xs px-2 py-1 rounded border border-slate-300 text-slate-700">취소</button>
                      <button type="button" onClick={handleSaveClick}
                        className="text-xs px-3 py-1 rounded bg-blue-600 text-white font-medium hover:bg-blue-700">저장</button>
                    </div>
                  </div>
                ) : (
                  <div className="text-xs text-slate-700 space-y-0.5">
                    {wc.work_content && <div>작업: {wc.work_content}</div>}
                    {wc.total_hours != null && wc.total_hours > 0 && (
                      <div>
                        총 {Number(wc.total_hours).toFixed(1)}h
                        {wc.morning_hours ? ` · 오전 ${wc.morning_time ?? ''} (${wc.morning_hours}h)` : ''}
                        {wc.afternoon_hours ? ` · 오후 ${wc.afternoon_time ?? ''} (${wc.afternoon_hours}h)` : ''}
                        {wc.overtime_hours ? ` · 연장 ${wc.overtime_time ?? ''} (${wc.overtime_hours}h)` : ''}
                        {wc.night_hours ? ` · 철야 ${wc.night_time ?? ''} (${wc.night_hours}h)` : ''}
                      </div>
                    )}
                    {wc.remarks && <div className="text-slate-500">비고: {wc.remarks}</div>}
                  </div>
                )}

                <div className="grid grid-cols-2 gap-2 mt-2 pt-2 border-t border-slate-100">
                  {/* 공급사 사인 — 그 인원 본인 */}
                  <div className="flex items-center justify-between">
                    <div className="text-[11px]">
                      <div className="text-slate-500">{person?.name ?? '인원'} 사인</div>
                      {wc.supplier_signed ? (
                        <div className="text-emerald-700 font-medium">✓ {wc.supplier_signer_name ?? '완료'}</div>
                      ) : (
                        <div className="text-slate-400">미사인</div>
                      )}
                    </div>
                    {canSupplierSign && (
                      <button
                        type="button"
                        onClick={() => setPadOpen({ id: wc.id, side: 'supplier', signerName: person?.name })}
                        className="text-[11px] px-2 py-1 rounded bg-blue-600 text-white"
                      >사인</button>
                    )}
                  </div>
                  {/* BP 사인 */}
                  <div className="flex items-center justify-between">
                    <div className="text-[11px]">
                      <div className="text-slate-500">BP 사인</div>
                      {wc.bp_signed ? (
                        <div className="text-emerald-700 font-medium">✓ {wc.bp_signer_name ?? '완료'}</div>
                      ) : (
                        <div className="text-slate-400">미사인</div>
                      )}
                    </div>
                    {canBpSign && (
                      <button
                        type="button"
                        onClick={() => setPadOpen({ id: wc.id, side: 'bp' })}
                        className="text-[11px] px-2 py-1 rounded bg-blue-600 text-white"
                      >사인</button>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {padOpen && (
        <SignaturePadDialog
          open
          title={padOpen.side === 'supplier' ? '본인 사인' : 'BP측 사인'}
          signerName={padOpen.signerName}
          onClose={() => setPadOpen(null)}
          onConfirm={submitSign}
        />
      )}

      {invalidateConfirm && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-5">
            <h3 className="text-base font-bold text-slate-900 mb-2">기존 사인 처리</h3>
            <p className="text-sm text-slate-700 mb-4">이 작업확인서에 이미 사인이 있습니다. 내용을 수정하면 기존 사인이 무효화될 수 있습니다.</p>
            <div className="flex items-center justify-end gap-2">
              <button type="button" onClick={() => setInvalidateConfirm(null)} className="text-sm px-3 py-1.5 rounded-md border border-slate-300 text-slate-700">취소</button>
              <button type="button" onClick={() => saveEdit(false)} className="text-sm px-3 py-1.5 rounded-md border border-slate-300 text-slate-700">사인 유지</button>
              <button type="button" onClick={() => saveEdit(true)} className="text-sm px-4 py-1.5 rounded-md bg-rose-600 text-white font-medium">무효화 + 저장</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
