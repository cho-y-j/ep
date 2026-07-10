import { useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { toast } from '../../../lib/toast';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse, type EquipmentCategory } from '../../../types/equipment';
import { PERSON_ROLE_LABEL, type PersonResponse, type PersonRole } from '../../../types/person';
import type { DispatchRequestPayload, DispatchPersonPayload } from '../../../types/dispatch';

type EqRow = {
  equipmentId: number;
  selected: boolean;
  dailyPrice: string;
  otDailyPrice: string;
  monthlyPrice: string;
  otMonthlyPrice: string;
  notes: string;
  dailyNote: string;
  otDailyNote: string;
  monthlyNote: string;
  otMonthlyNote: string;
};
type PRow = {
  personId: number;
  selected: boolean;
};

type Props = {
  open: boolean;
  quotationRequestId: number;
  requestedCategory?: string | null;
  requestedManpowerRole?: string | null;
  onClose: () => void;
  onSent: () => void;
};

const emptyEq = (id: number, selected: boolean): EqRow => ({
  equipmentId: id, selected,
  dailyPrice: '', otDailyPrice: '', monthlyPrice: '', otMonthlyPrice: '', notes: '',
  dailyNote: '', otDailyNote: '', monthlyNote: '', otMonthlyNote: '',
});

type Defaults = Pick<EqRow, 'dailyPrice'|'otDailyPrice'|'monthlyPrice'|'otMonthlyPrice'|'dailyNote'|'otDailyNote'|'monthlyNote'|'otMonthlyNote'>;

export default function DispatchSendDialog({ open, quotationRequestId, requestedCategory, requestedManpowerRole, onClose, onSent }: Props) {
  const { user } = useAuth();
  const selfCompanyId = user?.company_id ?? null;
  const [allEquipment, setAllEquipment] = useState<EquipmentResponse[]>([]);
  const [allPersons, setAllPersons] = useState<PersonResponse[]>([]);
  const [eqRows, setEqRows] = useState<Record<number, EqRow>>({});
  const [pRows, setPRows] = useState<Record<number, PRow>>({});
  const [globalNotes, setGlobalNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [previewing, setPreviewing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [proposalDefaults, setProposalDefaults] = useState<Defaults | null>(null);

  useEffect(() => {
    if (!open) return;
    setLoading(true);
    setError(null);
    Promise.all([
      api.get<EquipmentResponse[] | { content: EquipmentResponse[] }>('/api/equipment'),
      api.get<PersonResponse[] | { content: PersonResponse[] }>('/api/persons'),
      api.get<any[]>(`/api/quotations/${quotationRequestId}/proposals`).catch(() => ({ data: [] as any[] })),
    ])
      .then(([eqRes, pRes, propRes]) => {
        setAllEquipment(Array.isArray(eqRes.data) ? eqRes.data : (eqRes.data.content ?? []));
        setAllPersons(Array.isArray(pRes.data) ? pRes.data : (pRes.data.content ?? []));
        const proposals: any[] = Array.isArray((propRes as any).data) ? (propRes as any).data : ((propRes as any).data?.content ?? []);
        const active = proposals.find((p) =>
          p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW' || p.status === 'FINAL_ACCEPTED'
        );
        if (active) {
          setProposalDefaults({
            dailyPrice: active.daily_rate != null ? String(active.daily_rate) : '',
            otDailyPrice: active.ot_daily_rate != null ? String(active.ot_daily_rate) : '',
            monthlyPrice: active.monthly_rate != null ? String(active.monthly_rate) : '',
            otMonthlyPrice: active.ot_monthly_rate != null ? String(active.ot_monthly_rate) : '',
            dailyNote: active.daily_note ?? '',
            otDailyNote: active.ot_daily_note ?? '',
            monthlyNote: active.monthly_note ?? '',
            otMonthlyNote: active.ot_monthly_note ?? '',
          });
        } else {
          setProposalDefaults(null);
        }
      })
      .catch((err) => {
        setError(err instanceof AxiosError ? (err.response?.data?.message ?? '목록 조회 실패') : '목록 조회 실패');
      })
      .finally(() => setLoading(false));
  }, [open, quotationRequestId]);

  const equipment = useMemo(() => {
    if (!requestedCategory) return allEquipment;
    return allEquipment.filter((e) => e.category === requestedCategory);
  }, [allEquipment, requestedCategory]);

  const persons = useMemo(() => {
    if (!requestedManpowerRole) return allPersons;
    return allPersons.filter((p) => p.roles?.includes(requestedManpowerRole as PersonRole));
  }, [allPersons, requestedManpowerRole]);

  const eqSelectedCount = Object.values(eqRows).filter((r) => r.selected).length;
  const pSelectedCount = Object.values(pRows).filter((r) => r.selected).length;
  const firstSelectedEq = Object.values(eqRows).find((r) => r.selected);

  const toggleEq = (id: number) => setEqRows((prev) => {
    if (prev[id]) return { ...prev, [id]: { ...prev[id], selected: !prev[id].selected } };
    const base = emptyEq(id, true);
    if (proposalDefaults) {
      base.dailyPrice = proposalDefaults.dailyPrice;
      base.otDailyPrice = proposalDefaults.otDailyPrice;
      base.monthlyPrice = proposalDefaults.monthlyPrice;
      base.otMonthlyPrice = proposalDefaults.otMonthlyPrice;
      base.dailyNote = proposalDefaults.dailyNote;
      base.otDailyNote = proposalDefaults.otDailyNote;
      base.monthlyNote = proposalDefaults.monthlyNote;
      base.otMonthlyNote = proposalDefaults.otMonthlyNote;
    }
    return { ...prev, [id]: base };
  });
  const setEqField = (id: number, key: keyof EqRow, val: string) => setEqRows((prev) => {
    const row = prev[id] ?? emptyEq(id, true);
    return { ...prev, [id]: { ...row, [key]: val } };
  });

  const toggleP = (id: number) => setPRows((prev) => ({
    ...prev,
    [id]: prev[id] ? { ...prev[id], selected: !prev[id].selected }
      : { personId: id, selected: true },
  }));

  const num = (v: string): number | null => (v ? Number(v) : null);

  async function preview(format: 'pdf' | 'xlsx') {
    if (!firstSelectedEq) { setError('차량 1대 이상 선택 + 단가 입력 후 미리보기'); return; }
    setError(null); setPreviewing(true);
    try {
      const r = firstSelectedEq;
      const res = await api.post(
        `/api/quotations/${quotationRequestId}/quote-preview.${format}`,
        {
          daily_price: num(r.dailyPrice),
          ot_daily_price: num(r.otDailyPrice),
          monthly_price: num(r.monthlyPrice),
          ot_monthly_price: num(r.otMonthlyPrice),
          notes: r.notes || globalNotes || null,
          daily_note: r.dailyNote || null,
          ot_daily_note: r.otDailyNote || null,
          monthly_note: r.monthlyNote || null,
          ot_monthly_note: r.otMonthlyNote || null,
        },
        { responseType: 'blob' },
      );
      const url = URL.createObjectURL(res.data as Blob);
      if (format === 'pdf') {
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      } else {
        const a = document.createElement('a');
        a.href = url; a.download = `preview-${quotationRequestId}.xlsx`; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5_000);
      }
    } catch (err) {
      const msg = err instanceof AxiosError ? (err.response?.data?.message ?? '미리보기 실패') : '미리보기 실패';
      setError(msg);
    } finally { setPreviewing(false); }
  }

  async function submit() {
    const eqItems = Object.values(eqRows).filter((r) => r.selected).map((r) => ({
      equipment_id: r.equipmentId,
      daily_price: num(r.dailyPrice),
      ot_daily_price: num(r.otDailyPrice),
      monthly_price: num(r.monthlyPrice),
      ot_monthly_price: num(r.otMonthlyPrice),
      notes: r.notes || null,
      daily_note: r.dailyNote || null,
      ot_daily_note: r.otDailyNote || null,
      monthly_note: r.monthlyNote || null,
      ot_monthly_note: r.otMonthlyNote || null,
    }));
    // 인원은 단가 없이 선택만 (BP가 인원 단가 견적은 필요 없음)
    const pItems = Object.values(pRows).filter((r) => r.selected).map((r) => ({
      person_id: r.personId,
      daily_price: null,
      monthly_price: null,
      notes: null,
    }));
    if (eqItems.length === 0 && pItems.length === 0) {
      setError('차량 또는 인원을 1개 이상 선택하세요');
      return;
    }
    setSending(true); setError(null);
    try {
      let eqN = 0; let pN = 0;
      if (eqItems.length > 0) {
        const payload: DispatchRequestPayload = { items: eqItems, notes: globalNotes || null };
        const res = await api.post(`/api/quotations/${quotationRequestId}/dispatched`, payload);
        eqN = Array.isArray(res.data) ? res.data.length : eqItems.length;
      }
      if (pItems.length > 0) {
        const payload: DispatchPersonPayload = { items: pItems, notes: globalNotes || null };
        const res = await api.post(`/api/quotations/${quotationRequestId}/dispatched-persons`, payload);
        pN = Array.isArray(res.data) ? res.data.length : pItems.length;
      }
      toast.success(`견적서 발송 — 차량 ${eqN}대 · 인원 ${pN}명`);
      onSent();
      onClose();
    } catch (err) {
      const msg = err instanceof AxiosError ? (err.response?.data?.message ?? '발송 실패') : '발송 실패';
      setError(msg);
    } finally { setSending(false); }
  }

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl w-full max-w-4xl max-h-[92vh] flex flex-col" onClick={(e) => e.stopPropagation()}>
        <div className="flex items-center justify-between px-5 py-3 border-b">
          <div>
            <h2 className="text-base font-bold">차량·인원 선택 후 보내기</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {proposalDefaults
                ? '응찰 시 입력한 단가/비고가 자동 채워집니다. 필요 시 수정 가능. (1회 발송)'
                : '차량마다 단가 4행(일대/OT일대/월대/OT월대)과 비고를 입력. (1회 발송)'}
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700 text-xl">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {loading && <p className="text-sm text-slate-400">목록 로딩...</p>}

          {/* 차량 섹션 — 다중 선택 + 단가 4행 + 비고 4 */}
          {!loading && (
            <div className="space-y-2">
              <h3 className="text-sm font-bold text-slate-800">차량 {equipment.length > 0 && <span className="text-slate-400">({equipment.length})</span>}</h3>
              {equipment.length === 0 ? (
                <p className="text-xs text-slate-400">표시할 장비가 없습니다.</p>
              ) : equipment.map((e) => {
                const row = eqRows[e.id];
                const selected = !!row?.selected;
                const match = requestedCategory && e.category === requestedCategory;
                return (
                  <div key={e.id} className={`border rounded-lg p-3 ${selected ? 'border-brand-500 bg-brand-50/30' : 'border-slate-200'}`}>
                    <label className="flex items-start gap-3 cursor-pointer">
                      <input type="checkbox" checked={selected} onChange={() => toggleEq(e.id)} className="mt-1" />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-slate-900">{e.vehicle_no ?? e.model ?? `#${e.id}`}</span>
                          <span className={`text-xs px-1.5 py-0.5 rounded ${match ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>
                            {EQUIPMENT_CATEGORY_LABEL[e.category as EquipmentCategory] ?? e.category}
                          </span>
                          {selfCompanyId != null && e.supplier_id !== selfCompanyId && e.supplier_name && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-200 font-semibold">
                              소속: {e.supplier_name}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5">{e.model ?? '-'}{e.manufacturer ? ` · ${e.manufacturer}` : ''}</div>
                      </div>
                    </label>
                    {selected && row && (
                      <div className="mt-3 pl-7 space-y-1.5">
                        <RateRow label="일대" value={row.dailyPrice} note={row.dailyNote}
                                 onValue={(v) => setEqField(e.id, 'dailyPrice', v)} onNote={(v) => setEqField(e.id, 'dailyNote', v)} />
                        <RateRow label="OT 일대" value={row.otDailyPrice} note={row.otDailyNote}
                                 onValue={(v) => setEqField(e.id, 'otDailyPrice', v)} onNote={(v) => setEqField(e.id, 'otDailyNote', v)} />
                        <RateRow label="월대" value={row.monthlyPrice} note={row.monthlyNote}
                                 onValue={(v) => setEqField(e.id, 'monthlyPrice', v)} onNote={(v) => setEqField(e.id, 'monthlyNote', v)} />
                        <RateRow label="OT 월대" value={row.otMonthlyPrice} note={row.otMonthlyNote}
                                 onValue={(v) => setEqField(e.id, 'otMonthlyPrice', v)} onNote={(v) => setEqField(e.id, 'otMonthlyNote', v)} />
                        <TextField label={`${e.vehicle_no ?? e.model ?? '#' + e.id} 메모`} value={row.notes}
                                   onChange={(v) => setEqField(e.id, 'notes', v)} />
                      </div>
                    )}
                  </div>
                );
              })}
              {firstSelectedEq && (
                <div className="flex flex-wrap gap-2 pt-1">
                  <button type="button" onClick={() => preview('pdf')} disabled={previewing}
                          className="px-3 py-1.5 rounded-md border border-brand-300 text-brand-700 hover:bg-brand-50 text-sm disabled:opacity-50">
                    {previewing ? '미리보기...' : '미리보기 (PDF, 첫 차량)'}
                  </button>
                  <button type="button" onClick={() => preview('xlsx')} disabled={previewing}
                          className="px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm disabled:opacity-50">
                    엑셀 다운로드 (첫 차량)
                  </button>
                  <span className="text-[11px] text-slate-400 self-center">전체 미리보기는 발송 후 견적서 보기 사용</span>
                </div>
              )}
            </div>
          )}

          {/* 인원 섹션 */}
          {!loading && persons.length > 0 && (
            <div className="space-y-2">
              <h3 className="text-sm font-bold text-slate-800">
                인원 <span className="text-slate-400">({persons.length})</span>
                <span className="ml-2 text-[11px] font-normal text-slate-400">운전수·오퍼레이터·작업자</span>
              </h3>
              {persons.map((p) => {
                const row = pRows[p.id];
                const selected = !!row?.selected;
                return (
                  <div key={p.id} className={`border rounded-lg p-2 ${selected ? 'border-brand-500 bg-brand-50/40' : 'border-slate-200'}`}>
                    <label className="flex items-start gap-3 cursor-pointer">
                      <input type="checkbox" checked={selected} onChange={() => toggleP(p.id)} className="mt-1" />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-slate-900">{p.name}</span>
                          {p.job_title && <span className="text-xs text-slate-500">{p.job_title}</span>}
                          {(p.roles ?? []).map((r) => (
                            <span key={r} className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-600">{PERSON_ROLE_LABEL[r] ?? r}</span>
                          ))}
                          {selfCompanyId != null && p.supplier_id !== selfCompanyId && p.supplier_name && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 border border-amber-200 font-semibold">
                              소속: {p.supplier_name}
                            </span>
                          )}
                        </div>
                      </div>
                    </label>
                  </div>
                );
              })}
            </div>
          )}

          <div className="pt-3 border-t">
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">전체 비고 (선택)</span>
              <textarea value={globalNotes} onChange={(e) => setGlobalNotes(e.target.value)}
                        rows={2} placeholder="배차 조건, 특이사항 등"
                        className="mt-1 w-full px-2 py-1.5 text-sm border border-slate-300 rounded" />
            </label>
          </div>

        </div>

        {error && <div className="mx-5 mb-3 px-3 py-2 rounded bg-rose-50 border border-rose-200 text-sm text-rose-700">{error}</div>}

        <div className="flex justify-between items-center px-5 py-3 border-t bg-slate-50">
          <span className="text-sm text-slate-600">
            차량 <strong className="text-slate-900">{eqSelectedCount}</strong>대 · 인원 <strong className="text-slate-900">{pSelectedCount}</strong>명
          </span>
          <div className="flex gap-2">
            <button onClick={onClose} className="px-4 py-2 rounded-lg text-slate-700 hover:bg-slate-100">취소</button>
            <button onClick={submit} disabled={sending || (eqSelectedCount === 0 && pSelectedCount === 0)}
                    className="btn-primary disabled:opacity-50">
              {sending ? '발송 중...' : '보내기'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function TextField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <label className="block">
      <span className="text-[11px] text-slate-500">{label}</span>
      <input type="text" value={value} onChange={(e) => onChange(e.target.value)}
             className="mt-0.5 w-full px-2 py-1 text-sm border border-slate-300 rounded" />
    </label>
  );
}

function RateRow({ label, value, note, onValue, onNote }: {
  label: string; value: string; note: string;
  onValue: (v: string) => void; onNote: (v: string) => void;
}) {
  return (
    <div className="grid grid-cols-12 gap-2 items-end">
      <div className="col-span-4">
        <span className="text-[11px] text-slate-500">{label} (원)</span>
        <input type="number" inputMode="numeric" value={value} onChange={(e) => onValue(e.target.value)}
               className="mt-0.5 w-full px-2 py-1 text-sm border border-slate-300 rounded text-right" />
      </div>
      <div className="col-span-8">
        <span className="text-[11px] text-slate-500">{label} 비고 (양식 H 컬럼 표시)</span>
        <input type="text" value={note} onChange={(e) => onNote(e.target.value)}
               placeholder="예: 운반비 별도, 8시간 기준 등"
               className="mt-0.5 w-full px-2 py-1 text-sm border border-slate-300 rounded" />
      </div>
    </div>
  );
}
