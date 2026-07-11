import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { CompanyResponse } from '../../types/auth';
import {
  EQUIPMENT_CATEGORIES,
  EQUIPMENT_CATEGORY_LABEL,
  type EquipmentCategory,
} from '../../types/equipment';
import { ALL_PERSON_ROLES, PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';
import MoneyInput from '../../components/MoneyInput';
import AlimTalkSendBox from '../../components/AlimTalkSendBox';
import type {
  QuotationCandidateGroup,
  QuotationManpowerCandidateGroup,
  QuotationRequestResponse,
} from '../../types/quotation';

type BundlePayload = {
  work_period_start: string;
  work_period_end: string;
  notes?: string;
  on_behalf_of_bp_company_id?: number;
  equipment?: {
    category: EquipmentCategory;
    spec_text?: string;
    proposed_daily_rate?: number;
    proposed_monthly_rate?: number;
    count?: number;
    targets: { supplier_company_id: number; equipment_id: number }[];
  };
  manpower: {
    role: PersonRole;
    spec_text?: string;
    proposed_daily_rate?: number;
    proposed_monthly_rate?: number;
    count?: number;
    targets: { supplier_company_id: number; person_id: number }[];
  }[];
  alimtalk_phones?: string[];
};

/**
 * 견적 요청 생성 — 한 마법사에서 장비 견적 1건 + 인력 견적 N건 동시 발송.
 *
 *   - 장비 체크: 카테고리 1개 → 장비 N대 선택
 *   - 인력 체크: 역할 행을 N개 추가 가능, 각 행마다 역할 + 인원 후보 선택 + 가격
 *   - 발송: backend 에 (장비 0|1) + (인력 0..N) 만큼 POST 자동 호출
 */

type ManpowerItem = {
  uid: string; // local list key
  role: PersonRole | '';
  dailyRate: number | '';
  monthlyRate: number | '';
  specText: string;
  count: number;
  groups: QuotationManpowerCandidateGroup[];
  loading: boolean;
  selected: Map<number, number>; // personId → supplierId
};

const newManpowerItem = (): ManpowerItem => ({
  uid: Math.random().toString(36).slice(2, 10),
  role: '',
  dailyRate: '',
  monthlyRate: '',
  specText: '',
  count: 1,
  groups: [],
  loading: false,
  selected: new Map(),
});

/** 후보 배지 — 이전투입/신규 + 만료임박/서류완비. previouslyDispatched=null(ADMIN)이면 투입 배지 생략. */
function CandidateBadges({
  previouslyDispatched,
  expiringDocsCount,
}: {
  previouslyDispatched?: boolean | null;
  expiringDocsCount?: number | null;
}) {
  const expiring = (expiringDocsCount ?? 0) > 0;
  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {previouslyDispatched === true && (
        <span className="rounded px-1.5 py-0.5 text-[10px] font-semibold bg-brand-50 text-brand-700">이전 투입</span>
      )}
      {previouslyDispatched === false && (
        <span className="rounded px-1.5 py-0.5 text-[10px] font-medium bg-slate-100 text-slate-500">신규</span>
      )}
      {expiring ? (
        <span className="rounded px-1.5 py-0.5 text-[10px] font-semibold bg-amber-100 text-amber-700">만료임박</span>
      ) : (
        <span className="rounded px-1.5 py-0.5 text-[10px] font-medium bg-emerald-100 text-emerald-700">서류완비</span>
      )}
    </div>
  );
}

export default function QuotationCreatePage() {
  const { user, company } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ADMIN';
  const isBP = user?.role === 'BP';
  if (!isAdmin && !isBP) {
    return (
      <AppShell breadcrumb={[{ label: '견적 요청' }]}>
        <p className="text-sm text-rose-600">권한이 없습니다.</p>
      </AppShell>
    );
  }

  const [bpCompanies, setBpCompanies] = useState<CompanyResponse[]>([]);
  const [bpId, setBpId] = useState<number | ''>(isBP ? company?.id ?? '' : '');

  const [workStart, setWorkStart] = useState('');
  const [workEnd, setWorkEnd] = useState('');
  const [notes, setNotes] = useState('');

  // 장비 (단일)
  const [useEq, setUseEq] = useState(true);
  const [category, setCategory] = useState<EquipmentCategory | ''>('');
  const [eqSpecText, setEqSpecText] = useState('');
  const [eqDailyRate, setEqDailyRate] = useState<number | ''>('');
  const [eqMonthlyRate, setEqMonthlyRate] = useState<number | ''>('');
  const [eqCount, setEqCount] = useState<number>(1);
  const [eqGroups, setEqGroups] = useState<QuotationCandidateGroup[]>([]);
  const [eqSelected, setEqSelected] = useState<Map<number, number>>(new Map());
  const [loadingEq, setLoadingEq] = useState(false);

  // 인력 (역할별 N행)
  const [useMp, setUseMp] = useState(false);
  const [mpItems, setMpItems] = useState<ManpowerItem[]>([newManpowerItem()]);

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 장비 투입 알림톡 수신번호 (장비 포함 시에만 발송)
  const [alimtalkPhones, setAlimtalkPhones] = useState<string[]>([]);

  useEffect(() => {
    if (!isAdmin) return;
    api.get<CompanyResponse[]>('/api/companies', { params: { type: 'BP' } })
      .then((res) => setBpCompanies(res.data))
      .catch(() => {});
  }, [isAdmin]);

  // 카테고리 선택 시 장비 후보 자동 조회 (버튼 없이).
  useEffect(() => {
    if (!category) { setEqGroups([]); setEqSelected(new Map()); return; }
    let cancelled = false;
    setError(null);
    setLoadingEq(true);
    api.get<QuotationCandidateGroup[]>('/api/quotations/equipment-candidates', { params: { category } })
      .then((res) => { if (!cancelled) { setEqGroups(res.data); setEqSelected(new Map()); } })
      .catch((err) => { if (!cancelled && err instanceof AxiosError) setError(err.response?.data?.message ?? '장비 후보 조회 실패'); })
      .finally(() => { if (!cancelled) setLoadingEq(false); });
    return () => { cancelled = true; };
  }, [category]);

  const updateMp = (uid: string, patch: Partial<ManpowerItem>) => {
    setMpItems((items) => items.map((it) => (it.uid === uid ? { ...it, ...patch } : it)));
  };

  // 역할 선택 시 인력 후보 자동 조회 (버튼 없이). 행별 최신 role 을 인자로 받는다.
  const fetchMpCandidates = async (uid: string, role: PersonRole) => {
    setError(null);
    updateMp(uid, { loading: true });
    try {
      const res = await api.get<QuotationManpowerCandidateGroup[]>('/api/quotations/manpower-candidates', {
        params: { role },
      });
      updateMp(uid, { groups: res.data, selected: new Map(), loading: false });
    } catch (err) {
      updateMp(uid, { loading: false });
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '인력 후보 조회 실패');
    }
  };

  const toggleEq = (id: number, supId: number) => {
    setEqSelected((prev) => {
      const next = new Map(prev);
      if (next.has(id)) next.delete(id); else next.set(id, supId);
      return next;
    });
  };
  const toggleMpPerson = (uid: string, personId: number, supId: number) => {
    setMpItems((items) => items.map((it) => {
      if (it.uid !== uid) return it;
      const next = new Map(it.selected);
      if (next.has(personId)) next.delete(personId); else next.set(personId, supId);
      return { ...it, selected: next };
    }));
  };

  const addMpItem = () => setMpItems((items) => [...items, newManpowerItem()]);
  const removeMpItem = (uid: string) => setMpItems((items) => (items.length === 1 ? items : items.filter((it) => it.uid !== uid)));

  const onBehalfOf = isAdmin && bpId ? Number(bpId) : undefined;
  const periodValid = !!(workStart && workEnd && workStart <= workEnd);
  const validMpItems = mpItems.filter((it) => it.role && (it.dailyRate || it.monthlyRate) && it.selected.size > 0);

  // 인원 중복 차단 — 한 행에서 다른 행에 이미 선택된 person 은 disable
  const personsTakenByOtherRow = (uid: string): Set<number> => {
    const taken = new Set<number>();
    for (const it of mpItems) {
      if (it.uid === uid) continue;
      for (const id of it.selected.keys()) taken.add(id);
    }
    return taken;
  };
  const usedRolesOtherRow = (uid: string): Set<PersonRole> => {
    const taken = new Set<PersonRole>();
    for (const it of mpItems) {
      if (it.uid === uid) continue;
      if (it.role) taken.add(it.role);
    }
    return taken;
  };

  const submit = async () => {
    if (!workStart || !workEnd) { setError('작업 기간을 입력하세요.'); return; }
    if (isAdmin && !bpId) { setError('BP 회사를 선택하세요.'); return; }
    if (!useEq && !useMp) { setError('장비 또는 인력 중 최소 1개를 체크하세요.'); return; }
    if (useEq) {
      if (!category) { setError('장비 카테고리를 선택하세요.'); return; }
      if (!eqDailyRate && !eqMonthlyRate) { setError('장비 일대/월대 중 1개 입력하세요.'); return; }
      if (eqSelected.size === 0) { setError('최소 1대 장비를 선택하세요.'); return; }
    }
    if (useMp) {
      if (validMpItems.length === 0) {
        setError('인력 항목 1개 이상에 역할/가격/인원을 모두 입력하세요.');
        return;
      }
      // 같은 role 중복 차단
      const roles = validMpItems.map((it) => it.role);
      if (new Set(roles).size !== roles.length) {
        setError('인력 역할이 중복되었습니다. 같은 역할은 1개 행으로 합치세요.');
        return;
      }
    }
    setBusy(true);
    setError(null);
    try {
      const payload: BundlePayload = {
        work_period_start: workStart,
        work_period_end: workEnd,
        notes: notes || undefined,
        on_behalf_of_bp_company_id: onBehalfOf,
        equipment: useEq ? {
          category: category as EquipmentCategory,
          spec_text: eqSpecText || undefined,
          proposed_daily_rate: eqDailyRate ? Number(eqDailyRate) : undefined,
          proposed_monthly_rate: eqMonthlyRate ? Number(eqMonthlyRate) : undefined,
          count: eqCount,
          targets: Array.from(eqSelected.entries()).map(([eqId, supId]) => ({
            supplier_company_id: supId, equipment_id: eqId,
          })),
        } : undefined,
        manpower: useMp ? validMpItems.map((it) => ({
          role: it.role as PersonRole,
          spec_text: it.specText || undefined,
          proposed_daily_rate: it.dailyRate ? Number(it.dailyRate) : undefined,
          proposed_monthly_rate: it.monthlyRate ? Number(it.monthlyRate) : undefined,
          count: it.count,
          targets: Array.from(it.selected.entries()).map(([pId, supId]) => ({
            supplier_company_id: supId, person_id: pId,
          })),
        })) : [],
        alimtalk_phones: useEq ? alimtalkPhones : undefined,
      };
      const res = await api.post<QuotationRequestResponse[]>('/api/quotations/bundle', payload);
      const createdIds = res.data.map((q) => q.id);
      if (createdIds.length === 1) navigate(`/quotations/${createdIds[0]}`);
      else navigate('/quotations');
    } catch (err) {
      if (err instanceof AxiosError) setError(err.response?.data?.message ?? '발송 실패');
    } finally {
      setBusy(false);
    }
  };

  const totalCount = (useEq ? 1 : 0) + (useMp ? validMpItems.length : 0);

  return (
    <AppShell breadcrumb={[{ label: '견적 요청' }, { label: '새 요청' }]}>
      <div className="mx-auto max-w-5xl space-y-6">
        <h1 className="text-2xl font-bold">새 견적 요청</h1>

        {/* Step 1 — 공통 (Site-C: 현장은 작업계획서 단계에서 결정) */}
        <section className="card space-y-3">
          <h2 className="text-lg font-bold">1. 작업 기간</h2>
          {isAdmin && (
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">BP 회사 (대행 컨텍스트)</span>
              <select
                value={bpId}
                onChange={(e) => setBpId(e.target.value ? Number(e.target.value) : '')}
                className="input mt-1 bg-white"
                required
              >
                <option value="">— BP 선택 —</option>
                {bpCompanies.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </label>
          )}
          <div className="grid gap-3 md:grid-cols-2">
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">작업 시작일</span>
              <input type="date" value={workStart} onChange={(e) => setWorkStart(e.target.value)} className="input mt-1" required />
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">작업 종료일</span>
              <input type="date" value={workEnd} onChange={(e) => setWorkEnd(e.target.value)} className="input mt-1" required />
            </label>
            <label className="block md:col-span-2">
              <span className="text-xs font-semibold text-slate-500">공통 메모</span>
              <textarea value={notes} onChange={(e) => setNotes(e.target.value)} rows={2} className="input mt-1 resize-y" />
            </label>
          </div>
        </section>

        {/* Step 2 — 발송 항목 */}
        <section className="card space-y-3">
          <h2 className="text-lg font-bold">2. 견적 발송 항목</h2>
          <div className="flex flex-wrap gap-4">
            <label className="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={useEq} onChange={(e) => setUseEq(e.target.checked)} className="h-4 w-4" />
              <span className="text-sm font-semibold">장비 견적</span>
            </label>
            <label className="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" checked={useMp} onChange={(e) => setUseMp(e.target.checked)} className="h-4 w-4" />
              <span className="text-sm font-semibold">인력 견적</span>
            </label>
          </div>
          <p className="text-[10px] text-slate-500">
            ※ 인력은 역할별로 여러 행을 추가할 수 있습니다 (역할당 별도 견적 요청).
          </p>
        </section>

        {/* 장비 섹션 */}
        {useEq && (
          <section className="card space-y-3 border-l-4 border-brand-500">
            <h2 className="text-lg font-bold">장비 견적</h2>
            <div className="grid gap-3 md:grid-cols-2">
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">장비 카테고리</span>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as EquipmentCategory | '')}
                  className="input mt-1 bg-white"
                  required
                >
                  <option value="">— 카테고리 —</option>
                  {EQUIPMENT_CATEGORIES.map((c) => (
                    <option key={c} value={c}>{EQUIPMENT_CATEGORY_LABEL[c]}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">필요 수량 (메모)</span>
                <input
                  type="number" min={1}
                  value={eqCount}
                  onChange={(e) => setEqCount(Number(e.target.value) || 1)}
                  className="input mt-1"
                />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">제안 일대 (원/일)</span>
                <MoneyInput
                  value={eqDailyRate}
                  onChange={(v) => setEqDailyRate(v === '' ? '' : v)}
                  placeholder="예: 800,000"
                  className="input mt-1"
                />
              </label>
              <label className="block">
                <span className="text-xs font-semibold text-slate-500">제안 월대 (원/월)</span>
                <MoneyInput
                  value={eqMonthlyRate}
                  onChange={(v) => setEqMonthlyRate(v === '' ? '' : v)}
                  className="input mt-1"
                />
              </label>
              <label className="block md:col-span-2">
                <span className="text-xs font-semibold text-slate-500">장비 스펙</span>
                <textarea
                  value={eqSpecText}
                  onChange={(e) => setEqSpecText(e.target.value)}
                  rows={2}
                  placeholder="예: 25톤 이상 크레인 / 붐 길이 20m 이상"
                  className="input mt-1 resize-y"
                />
              </label>
            </div>
            <div className="flex items-center justify-between pt-1">
              <p className="text-xs text-slate-500">카테고리 매칭되는 전체 장비공급사의 장비를 표시합니다.</p>
              {loadingEq && <span className="text-sm text-slate-500">조회 중...</span>}
            </div>
            {eqGroups.length === 0 ? (
              <div className="text-center text-xs text-slate-400 py-4 border border-dashed border-slate-300 rounded-lg">
                {loadingEq ? '장비 후보를 불러오는 중...' : category ? '해당 카테고리의 장비 후보가 없습니다.' : '카테고리를 선택하면 장비 후보가 표시됩니다.'}
              </div>
            ) : (
              <div className="space-y-3">
                {eqGroups.map((g) => (
                  <details key={g.supplier_id} className="border border-slate-200 rounded-lg overflow-hidden" open>
                    <summary className="cursor-pointer px-3 py-2 bg-slate-50 hover:bg-slate-100 flex items-center justify-between list-none">
                      <span className="text-sm font-semibold text-slate-700">
                        {g.supplier_name}
                        <span className="ml-2 text-xs text-slate-500">{g.equipments.length}대</span>
                      </span>
                      <span className="text-xs text-emerald-700">
                        선택 {g.equipments.filter((e) => eqSelected.has(e.id)).length}대
                      </span>
                    </summary>
                    {g.equipments.length === 0 ? (
                      <div className="px-3 py-3 text-xs text-slate-400 italic">가용 장비 없음</div>
                    ) : (
                      <ul className="divide-y divide-slate-100">
                        {g.equipments.map((e) => {
                          const isSel = eqSelected.has(e.id);
                          return (
                            <li
                              key={e.id}
                              onClick={() => toggleEq(e.id, g.supplier_id)}
                              className={`px-3 py-2 cursor-pointer transition ${isSel ? 'bg-emerald-50' : 'hover:bg-slate-50'}`}
                            >
                              <label className="flex items-center gap-3 cursor-pointer">
                                <input type="checkbox" checked={isSel} onChange={() => {}} className="shrink-0" />
                                <div className="flex-1 min-w-0">
                                  <div className="text-sm font-medium text-slate-900 truncate">
                                    {e.vehicle_no || e.model || `장비 #${e.id}`}
                                    {e.manufacturer ? ` · ${e.manufacturer}` : ''}
                                    {e.year ? ` · ${e.year}` : ''}
                                  </div>
                                  <div className="text-[10px] text-slate-500">
                                    {EQUIPMENT_CATEGORY_LABEL[e.category]}
                                    {e.serial_number ? ` · S/N ${e.serial_number}` : ''}
                                  </div>
                                  <CandidateBadges
                                    previouslyDispatched={e.previously_dispatched}
                                    expiringDocsCount={e.expiring_docs_count}
                                  />
                                </div>
                              </label>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </details>
                ))}
              </div>
            )}
          </section>
        )}

        {/* 인력 섹션 — N개 행 */}
        {useMp && (
          <section className="card space-y-4 border-l-4 border-amber-500">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold">인력 견적 ({mpItems.length}건)</h2>
              <button
                type="button"
                onClick={addMpItem}
                className="text-sm px-3 py-1.5 rounded-md bg-amber-600 text-white hover:bg-amber-700"
              >
                + 역할 추가
              </button>
            </div>

            {mpItems.map((it, idx) => {
              const usedRoles = usedRolesOtherRow(it.uid);
              const takenPersons = personsTakenByOtherRow(it.uid);
              return (
              <div key={it.uid} className="border border-amber-200 rounded-lg p-3 space-y-3 bg-amber-50/30">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold text-amber-700">#{idx + 1}</div>
                  {mpItems.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeMpItem(it.uid)}
                      className="text-xs px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50"
                    >
                      삭제
                    </button>
                  )}
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-500">인력 역할</span>
                    <select
                      value={it.role}
                      onChange={(e) => {
                        const role = e.target.value as PersonRole | '';
                        updateMp(it.uid, { role, groups: [], selected: new Map() });
                        if (role) void fetchMpCandidates(it.uid, role);
                      }}
                      className="input mt-1 bg-white"
                      required
                    >
                      <option value="">— 역할 —</option>
                      {ALL_PERSON_ROLES.map((r) => (
                        <option key={r} value={r} disabled={usedRoles.has(r)}>
                          {PERSON_ROLE_LABEL[r]}{usedRoles.has(r) ? ' (다른 행에서 선택됨)' : ''}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-500">필요 인원 수</span>
                    <input
                      type="number" min={1}
                      value={it.count}
                      onChange={(e) => updateMp(it.uid, { count: Number(e.target.value) || 1 })}
                      className="input mt-1"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-500">제안 일대 (원/일)</span>
                    <MoneyInput
                      value={it.dailyRate}
                      onChange={(v) => updateMp(it.uid, { dailyRate: v === '' ? '' : v })}
                      placeholder="예: 200,000"
                      className="input mt-1"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-semibold text-slate-500">제안 월대 (원/월)</span>
                    <MoneyInput
                      value={it.monthlyRate}
                      onChange={(v) => updateMp(it.uid, { monthlyRate: v === '' ? '' : v })}
                      className="input mt-1"
                    />
                  </label>
                  <label className="block md:col-span-2">
                    <span className="text-xs font-semibold text-slate-500">요구사항</span>
                    <textarea
                      value={it.specText}
                      onChange={(e) => updateMp(it.uid, { specText: e.target.value })}
                      rows={2}
                      placeholder="예: 신호수 자격 보유 / 야간 작업 가능"
                      className="input mt-1 resize-y"
                    />
                  </label>
                </div>
                <div className="flex items-center justify-between pt-1">
                  <p className="text-xs text-slate-500">역할 가능한 전체 인력공급사의 인원을 표시합니다.</p>
                  {it.loading && <span className="text-sm text-slate-500">조회 중...</span>}
                </div>
                {it.groups.length === 0 ? (
                  <div className="text-center text-xs text-slate-400 py-4 border border-dashed border-slate-300 rounded-lg">
                    {it.loading ? '인력 후보를 불러오는 중...' : it.role ? '해당 역할의 인력 후보가 없습니다.' : '역할을 선택하면 인력 후보가 표시됩니다.'}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {it.groups.map((g) => (
                      <details key={g.supplier_id} className="border border-slate-200 rounded-lg overflow-hidden bg-white" open>
                        <summary className="cursor-pointer px-3 py-2 bg-slate-50 hover:bg-slate-100 flex items-center justify-between list-none">
                          <span className="text-sm font-semibold text-slate-700">
                            {g.supplier_name}
                            <span className="ml-2 text-xs text-slate-500">{g.persons.length}명</span>
                          </span>
                          <span className="text-xs text-amber-700">
                            선택 {g.persons.filter((p) => it.selected.has(p.id)).length}명
                          </span>
                        </summary>
                        {g.persons.length === 0 ? (
                          <div className="px-3 py-3 text-xs text-slate-400 italic">가용 인원 없음</div>
                        ) : (
                          <ul className="divide-y divide-slate-100">
                            {g.persons.map((p) => {
                              const isSel = it.selected.has(p.id);
                              const isTaken = takenPersons.has(p.id);
                              return (
                                <li
                                  key={p.id}
                                  onClick={() => { if (!isTaken) toggleMpPerson(it.uid, p.id, g.supplier_id); }}
                                  className={`px-3 py-2 transition ${
                                    isTaken
                                      ? 'bg-slate-100 opacity-50 cursor-not-allowed'
                                      : (isSel ? 'bg-amber-50 cursor-pointer' : 'hover:bg-slate-50 cursor-pointer')
                                  }`}
                                >
                                  <label className={`flex items-center gap-3 ${isTaken ? 'cursor-not-allowed' : 'cursor-pointer'}`}>
                                    <input type="checkbox" checked={isSel} disabled={isTaken} onChange={() => {}} className="shrink-0" />
                                    <div className="flex-1 min-w-0">
                                      <div className="text-sm font-medium text-slate-900 truncate">
                                        {p.name}
                                        {p.job_title ? ` · ${p.job_title}` : ''}
                                        {p.employee_no ? ` · ${p.employee_no}` : ''}
                                        {isTaken && <span className="ml-2 text-[10px] text-rose-600">[다른 역할에 이미 선택]</span>}
                                      </div>
                                      <div className="text-[10px] text-slate-500">
                                        {p.roles.map((r) => PERSON_ROLE_LABEL[r]).join(', ')}
                                        {p.phone ? ` · ${p.phone}` : ''}
                                      </div>
                                      <CandidateBadges
                                        previouslyDispatched={p.previously_dispatched}
                                        expiringDocsCount={p.expiring_docs_count}
                                      />
                                    </div>
                                  </label>
                                </li>
                              );
                            })}
                          </ul>
                        )}
                      </details>
                    ))}
                  </div>
                )}
              </div>
              );
            })}
          </section>
        )}

        {error && (
          <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>
        )}

        {/* 장비 투입 알림톡 (장비 포함 시) */}
        {useEq && (
          <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-2">
            <h2 className="text-sm font-bold text-slate-800">장비 투입 알림톡 (선택)</h2>
            <p className="text-xs text-slate-500">공급사 담당자에게 카카오 알림톡으로 장비 투입 요청을 함께 보낼 수 있습니다. (실패 시 SMS)</p>
            <AlimTalkSendBox value={alimtalkPhones} onChange={setAlimtalkPhones} />
          </div>
        )}

        {/* 발송 */}
        <div className="sticky bottom-0 -mx-4 px-4 py-3 bg-white border-t border-slate-200 flex justify-between items-center shadow-lg">
          <div className="text-xs text-slate-500 flex flex-wrap gap-2">
            {useEq && <span>장비 <b>{eqSelected.size}대</b></span>}
            {useMp && (
              <span>
                인력 행 <b>{validMpItems.length}/{mpItems.length}</b>건 ·
                총 <b>{mpItems.reduce((s, it) => s + it.selected.size, 0)}명</b>
              </span>
            )}
            <span>기간 {periodValid ? '✓' : '?'}</span>
            <span>총 발송 <b>{totalCount}건</b></span>
          </div>
          <div className="flex gap-2">
            <button type="button" onClick={() => navigate('/quotations')} className="btn-ghost">취소</button>
            <button
              type="button"
              onClick={submit}
              disabled={
                busy
                || (!useEq && !useMp)
                || !periodValid
                || (useEq && (!category || (!eqDailyRate && !eqMonthlyRate) || eqSelected.size === 0))
                || (useMp && validMpItems.length === 0)
              }
              className="btn-primary disabled:opacity-50"
            >
              {busy ? '발송 중...' : `발송 (${totalCount}건)`}
            </button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
