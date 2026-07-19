import { useEffect, useMemo, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';
import MoneyInput from '../../components/MoneyInput';
import { useAuth } from '../auth/AuthContext';

interface Bid {
  id: number;
  equipment_category?: string;
  manpower_role?: string;
  spec_text?: string;
  client_org_id?: number;
  work_location_text?: string;
  count: number;
  work_period_start: string;
  work_period_end: string;
  proposed_daily_rate?: number;
  proposed_monthly_rate?: number;
  notes?: string;
  bp_company_id?: number;
  bp_company_name?: string;
  requested_by_user_name?: string;
  client_org_name?: string;
  created_at?: string;
}

/** 등록일 ISO → "오늘 / 어제 / N일 전 / M월 D일" 상대 라벨. */
function dateLabel(iso?: string): string {
  if (!iso) return '날짜 미상';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '날짜 미상';
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const that = new Date(d); that.setHours(0, 0, 0, 0);
  const diff = Math.round((today.getTime() - that.getTime()) / 86400000);
  if (diff <= 0) return '오늘';
  if (diff === 1) return '어제';
  if (diff <= 6) return `${diff}일 전`;
  return `${d.getMonth() + 1}월 ${d.getDate()}일`;
}

export default function OpenBidsBoardPage() {
  const { user } = useAuth();
  const [bids, setBids] = useState<Bid[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Bid | null>(null);
  /** 내가 활성 제안을 이미 보낸 견적 id 집합 — "이미 제출" chip + 다이얼로그 차단. */
  const [submittedIds, setSubmittedIds] = useState<Set<number>>(new Set());
  // 클라이언트 필터 — 로드된 공개입찰을 좁힘.
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [bpFilter, setBpFilter] = useState('');

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<Bid[]>('/api/quotations/open-bids');
      setBids(res.data);
      try {
        const mine = await api.get<Array<{ request_id: number; status: string }>>('/api/quotations/proposals/mine');
        const set = new Set<number>();
        mine.data.forEach((p) => {
          if (p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW' || p.status === 'FINAL_ACCEPTED') {
            set.add(p.request_id);
          }
        });
        setSubmittedIds(set);
      } catch { /* 공급사 아닌 사용자 등 — 무시 */ }
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  // 필터 옵션 — 로드된 데이터에서 파생(발주사).
  const bpOptions = useMemo(() => {
    const m = new Map<string, string>();
    bids.forEach((b) => { if (b.bp_company_id != null) m.set(String(b.bp_company_id), b.bp_company_name ?? `회사 #${b.bp_company_id}`); });
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [bids]);

  const qLower = q.trim().toLowerCase();
  const filteredBids = useMemo(() => bids.filter((b) => {
    if (typeFilter === 'EQUIPMENT' && !b.equipment_category) return false;
    if (typeFilter === 'MANPOWER' && !b.manpower_role) return false;
    if (bpFilter && String(b.bp_company_id ?? '') !== bpFilter) return false;
    if (qLower) {
      const name = b.equipment_category
        ? (EQUIPMENT_CATEGORY_LABEL[b.equipment_category as EquipmentCategory] ?? b.equipment_category)
        : (b.manpower_role ? (PERSON_ROLE_LABEL[b.manpower_role as PersonRole] ?? b.manpower_role) : '');
      const hay = `${name} ${b.bp_company_name ?? ''} ${b.work_location_text ?? ''} ${b.spec_text ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [bids, typeFilter, bpFilter, qLower]);

  const activeFilterCount = [q, typeFilter, bpFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setTypeFilter(''); setBpFilter(''); };

  // 등록일(created_at) 기준 최신순 + 날짜별 그룹핑 — "언제 올라왔는지" 구분.
  const dateGroups = useMemo(() => {
    const sorted = [...filteredBids].sort((a, b) => (b.created_at ?? '').localeCompare(a.created_at ?? ''));
    const m = new Map<string, Bid[]>();
    for (const b of sorted) {
      const key = (b.created_at ?? '').slice(0, 10) || 'unknown';
      if (!m.has(key)) m.set(key, []);
      m.get(key)!.push(b);
    }
    return Array.from(m.entries());
  }, [filteredBids]);

  return (
    <AppShell breadcrumb={[{ label: '공개입찰' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <PageHeader
          title="공개입찰 게시판"
          subtitle="BP 사가 올린 공개입찰. 행 클릭 → 자기 자원으로 단가 제안 작성."
        />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원·발주사·현장 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="자원종류 전체"
            options={[{ value: 'EQUIPMENT', label: '장비' }, { value: 'MANPOWER', label: '인력' }]} />
          {bpOptions.length > 0 && (
            <FilterSelect value={bpFilter} onChange={setBpFilter} placeholder="발주사(BP) 전체" options={bpOptions} />
          )}
        </FilterBar>

        {loading ? <div className="text-slate-400">로딩 중…</div> : bids.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">아직 올라온 공개입찰이 없습니다.</div>
        ) : dateGroups.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">조건에 맞는 공개입찰이 없습니다.</div>
        ) : (
          <div className="space-y-6">
            {dateGroups.map(([dateKey, groupBids]) => (
              <section key={dateKey}>
                {/* 날짜 그룹 헤더 — 언제 올라왔는지 */}
                <div className="flex items-center gap-2 mb-2">
                  <h2 className="text-sm font-bold text-slate-800">{dateLabel(groupBids[0].created_at)}</h2>
                  {dateKey !== 'unknown' && <span className="text-xs text-slate-400">{dateKey}</span>}
                  <span className="text-xs text-slate-400">· {groupBids.length}건</span>
                  <div className="flex-1 h-px bg-slate-100" />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {groupBids.map((b) => {
                    const alreadySubmitted = submittedIds.has(b.id);
                    const resourceName = b.equipment_category
                      ? (EQUIPMENT_CATEGORY_LABEL[b.equipment_category as EquipmentCategory] ?? b.equipment_category)
                      : (b.manpower_role ? (PERSON_ROLE_LABEL[b.manpower_role as PersonRole] ?? b.manpower_role) : '자원');
                    return (
                    <div key={b.id}
                         className={`rounded-xl border bg-white p-4 shadow-sm transition ${alreadySubmitted ? 'opacity-60 cursor-not-allowed bg-slate-50 border-slate-200' : 'cursor-pointer border-slate-200 hover:shadow-md hover:border-brand-300'}`}
                         onClick={() => { if (!alreadySubmitted) setSelected(b); }}>
                      {/* 상단: 자원 종류 칩 + 수량 | 상태 칩 */}
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-brand-50 text-brand-700 text-sm font-bold truncate">
                            {resourceName}
                          </span>
                          <span className="text-sm text-slate-500 shrink-0">× {b.count}</span>
                        </div>
                        <div className="flex flex-col items-end gap-1 shrink-0">
                          {alreadySubmitted && (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 font-semibold">제출됨</span>
                          )}
                          {b.client_org_name && (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-medium">원청 · {b.client_org_name}</span>
                          )}
                        </div>
                      </div>

                      {/* 어디서 — BP사 + 현장 위치 강조 */}
                      <div className="mt-3 space-y-1">
                        <div className="flex items-center gap-1.5 text-sm font-bold text-slate-900 min-w-0">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0 text-slate-400"><path d="M3 21h18M5 21V7l8-4v18M19 21V11l-6-4" /></svg>
                          <span className="truncate">{b.bp_company_name ?? `회사 #${b.bp_company_id ?? '?'}`}</span>
                        </div>
                        {b.work_location_text && (
                          <div className="flex items-center gap-1.5 text-sm text-slate-700 min-w-0">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="shrink-0 text-rose-400"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" /><circle cx="12" cy="10" r="3" /></svg>
                            <span className="truncate">{b.work_location_text}</span>
                          </div>
                        )}
                      </div>

                      {b.spec_text && (
                        <div className="mt-2 text-xs text-slate-600 line-clamp-2">{b.spec_text}</div>
                      )}

                      <div className="mt-2 pt-2 border-t border-slate-100 text-xs text-slate-600 space-y-0.5">
                        <div><span className="text-slate-400">기간</span> {b.work_period_start} ~ {b.work_period_end}</div>
                        {(b.proposed_daily_rate || b.proposed_monthly_rate) && (
                          <div className="text-slate-700">
                            <span className="text-slate-400">BP 예산</span>{' '}
                            {b.proposed_daily_rate ? `일 ${b.proposed_daily_rate.toLocaleString()}원 ` : ''}
                            {b.proposed_monthly_rate ? `월 ${b.proposed_monthly_rate.toLocaleString()}원` : ''}
                          </div>
                        )}
                        {b.notes && <div className="text-slate-500 italic line-clamp-1">{b.notes}</div>}
                      </div>

                      <div className="mt-2 flex items-center justify-between text-[10px] text-slate-400">
                        <span>견적 #{b.id}{b.requested_by_user_name ? ` · 담당 ${b.requested_by_user_name}` : ''}</span>
                        {b.created_at && b.created_at.length > 11 && <span>{b.created_at.slice(11, 16)}</span>}
                      </div>
                    </div>
                    );
                  })}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>

      {selected && (user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER') && (
        <ProposalDialog bid={selected} onClose={() => setSelected(null)} onSubmitted={load} />
      )}
    </AppShell>
  );
}

function RateNoteRow({ label, value, note, onValue, onNote }: {
  label: string; value: string; note: string;
  onValue: (v: string | number) => void; onNote: (v: string) => void;
}) {
  return (
    <div className="grid grid-cols-12 gap-2 items-end">
      <div className="col-span-4">
        <span className="text-xs text-slate-600">{label} (원)</span>
        <MoneyInput value={value} onChange={onValue} />
      </div>
      <div className="col-span-8">
        <span className="text-xs text-slate-600">{label} 비고 (양식 비고란에 표시)</span>
        <input type="text" value={note} onChange={(e) => onNote(e.target.value)}
               placeholder="예: 운반비 별도, 8시간 기준 등" className="input" />
      </div>
    </div>
  );
}

function ProposalDialog({ bid, onClose, onSubmitted }: { bid: Bid; onClose: () => void; onSubmitted: () => void }) {
  const [dailyRate, setDailyRate] = useState('');
  const [otDailyRate, setOtDailyRate] = useState('');
  const [monthlyRate, setMonthlyRate] = useState('');
  const [otMonthlyRate, setOtMonthlyRate] = useState('');
  const [dailyNote, setDailyNote] = useState('');
  const [otDailyNote, setOtDailyNote] = useState('');
  const [monthlyNote, setMonthlyNote] = useState('');
  const [otMonthlyNote, setOtMonthlyNote] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [previewing, setPreviewing] = useState(false);

  const hasAnyRate = !!(dailyRate || otDailyRate || monthlyRate || otMonthlyRate);
  const num = (v: string): number | null => (v ? Number(v) : null);

  async function preview(format: 'pdf' | 'xlsx') {
    if (!hasAnyRate) { setError('단가를 1개 이상 입력하세요'); return; }
    setError(null); setPreviewing(true);
    try {
      const res = await api.post(
        `/api/quotations/${bid.id}/quote-preview.${format}`,
        {
          daily_price: num(dailyRate),
          ot_daily_price: num(otDailyRate),
          monthly_price: num(monthlyRate),
          ot_monthly_price: num(otMonthlyRate),
          notes: note || null,
          daily_note: dailyNote || null,
          ot_daily_note: otDailyNote || null,
          monthly_note: monthlyNote || null,
          ot_monthly_note: otMonthlyNote || null,
        },
        { responseType: 'blob' },
      );
      const url = URL.createObjectURL(res.data as Blob);
      if (format === 'pdf') {
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      } else {
        const a = document.createElement('a');
        a.href = url; a.download = `preview-${bid.id}.xlsx`; a.click();
        setTimeout(() => URL.revokeObjectURL(url), 5_000);
      }
    } catch (e: any) {
      setError(e?.response?.data?.message || '미리보기 실패');
    } finally { setPreviewing(false); }
  }

  async function submit() {
    setError(null);
    if (!hasAnyRate) { setError('단가를 1개 이상 입력하세요'); return; }
    setSubmitting(true);
    try {
      await api.post(`/api/quotations/${bid.id}/proposals`, {
        equipment_id: null,
        person_id: null,
        daily_rate: num(dailyRate),
        ot_daily_rate: num(otDailyRate),
        monthly_rate: num(monthlyRate),
        ot_monthly_rate: num(otMonthlyRate),
        note: note || null,
        daily_note: dailyNote || null,
        ot_daily_note: otDailyNote || null,
        monthly_note: monthlyNote || null,
        ot_monthly_note: otMonthlyNote || null,
      });
      onSubmitted();
      onClose();
    } catch (e: any) {
      setError(e?.response?.data?.message || '제출 실패');
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-xl p-6 max-w-lg w-full mx-4" onClick={(e) => e.stopPropagation()}>
        <h2 className="text-lg font-bold mb-1">응찰 제출</h2>
        <p className="text-xs text-slate-500 mb-3">
          단가만 입력하고 보내세요. 차량/인원은 선정 후에 별도로 보냅니다.
        </p>
        <div className="space-y-3">
          <div className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-600">
            {bid.equipment_category
              ? `${EQUIPMENT_CATEGORY_LABEL[bid.equipment_category as EquipmentCategory] ?? bid.equipment_category}`
              : bid.manpower_role
              ? `${PERSON_ROLE_LABEL[bid.manpower_role as PersonRole] ?? bid.manpower_role}`
              : ''}
            {bid.spec_text && ` · ${bid.spec_text}`}
          </div>
          <div className="space-y-1.5">
            <RateNoteRow label="일대" value={dailyRate} note={dailyNote}
                         onValue={(v) => setDailyRate(v === '' ? '' : String(v))} onNote={setDailyNote} />
            <RateNoteRow label="OT 일대" value={otDailyRate} note={otDailyNote}
                         onValue={(v) => setOtDailyRate(v === '' ? '' : String(v))} onNote={setOtDailyNote} />
            <RateNoteRow label="월대" value={monthlyRate} note={monthlyNote}
                         onValue={(v) => setMonthlyRate(v === '' ? '' : String(v))} onNote={setMonthlyNote} />
            <RateNoteRow label="OT 월대" value={otMonthlyRate} note={otMonthlyNote}
                         onValue={(v) => setOtMonthlyRate(v === '' ? '' : String(v))} onNote={setOtMonthlyNote} />
          </div>
          <label className="block">
            <span className="text-xs text-slate-600">전체 메모 (선택)</span>
            <textarea rows={2} className="input" value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={() => preview('pdf')} disabled={previewing || !hasAnyRate}
                    className="px-3 py-1.5 rounded border border-brand-300 text-brand-700 hover:bg-brand-50 text-sm disabled:opacity-50">
              {previewing ? '미리보기...' : '미리보기 (PDF)'}
            </button>
            <button type="button" onClick={() => preview('xlsx')} disabled={previewing || !hasAnyRate}
                    className="px-3 py-1.5 rounded border border-slate-300 text-slate-700 hover:bg-slate-50 text-sm disabled:opacity-50">
              엑셀 다운로드
            </button>
          </div>
          {error && <div className="text-sm text-rose-600">{error}</div>}
          <div className="flex justify-end gap-2 pt-2 border-t">
            <button onClick={onClose} className="btn-ghost">취소</button>
            <button onClick={submit} disabled={submitting || !hasAnyRate} className="btn-primary">응찰 제출</button>
          </div>
        </div>
      </div>
    </div>
  );
}
