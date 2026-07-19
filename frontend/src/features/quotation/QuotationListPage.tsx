import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import {
  QUOTATION_STATUS_LABEL,
  type QuotationBundleResponse,
  type QuotationStatus,
} from '../../types/quotation';
import { equipmentCategoryLabel } from '../../types/equipment';
import { PERSON_ROLE_LABEL } from '../../types/person';

/** 견적 목록 chip 용 단계 집계 (GET /api/quotations/stage-summary). id = 견적(quotation request) id. */
type StageSummary = { id: number; selected: boolean; dispatched: boolean; bundle: boolean };

/** "방금", "n분/시간/일 전" 형태로 상대시간 표시. */
function formatRelative(iso: string | undefined): string {
  if (!iso) return '';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '';
  const diff = Date.now() - t;
  const sec = Math.floor(diff / 1000);
  if (sec < 60) return '방금';
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  const day = Math.floor(hr / 24);
  if (day < 30) return `${day}일 전`;
  const mo = Math.floor(day / 30);
  if (mo < 12) return `${mo}개월 전`;
  return `${Math.floor(mo / 12)}년 전`;
}

/** 묶음의 현장 표시명 — 지정배차는 현장명, 공개입찰은 작업위치 텍스트. 현장 필터/검색 공용. */
function siteLabelOf(b: QuotationBundleResponse): string {
  return b.site_name ?? b.items[0]?.work_location_text ?? '';
}

/** 묶음 안 장비/인력 요약 문자열 — 카드 제목 + 검색 대상. */
function summarizeItems(b: QuotationBundleResponse): string {
  const eq = b.items.filter((i) => i.request_type === 'EQUIPMENT');
  const mp = b.items.filter((i) => i.request_type === 'MANPOWER');
  const parts: string[] = [];
  if (eq.length) {
    const cats = eq.map((i) => i.equipment_category ? equipmentCategoryLabel(i.equipment_category) : '장비').join(', ');
    parts.push(`장비 ${cats}`);
  }
  if (mp.length) {
    const roles = mp.map((i) => i.manpower_role ? PERSON_ROLE_LABEL[i.manpower_role] : '인력').join(', ');
    parts.push(`인력 ${roles}`);
  }
  return parts.join(' · ');
}

/** 견적 묶음 목록 — 현장 1건 = 카드 1개. 안에 장비/인력 요약 + 진행률. */
export default function QuotationListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [bundles, setBundles] = useState<QuotationBundleResponse[]>([]);
  const [stageMap, setStageMap] = useState<Map<number, StageSummary>>(new Map());
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<QuotationStatus | ''>('');
  const [modeTab, setModeTab] = useState<'OPEN_BID' | 'TARGETED'>('OPEN_BID');
  // 클라이언트 필터 — 로드된 묶음을 좁힘.
  const [q, setQ] = useState('');
  const [siteFilter, setSiteFilter] = useState('');
  const [bpFilter, setBpFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [error, setError] = useState<string | null>(null);

  const canCreate = user?.role === 'ADMIN' || user?.role === 'BP';
  const canManage = canCreate;
  const isSupplier = user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';
  // 사이드바 라벨과 일치: ADMIN '견적 관리' / BP '장비 견적 공개 입찰' / 공급사 '받은 견적'
  const pageTitle = user?.role === 'ADMIN' ? '견적 관리' : isSupplier ? '받은 견적' : '장비 견적 공개 입찰';

  const load = () => {
    setLoading(true);
    api.get<QuotationBundleResponse[]>('/api/quotations/bundles')
      .then((res) => setBundles(res.data))
      .catch((err) => setError(err?.response?.data?.message ?? '견적 목록 로드 실패'))
      .finally(() => setLoading(false));
    // 단계 chip — 목록 1회 조회로 전체 견적 매핑. 실패해도 목록엔 영향 없음(칩만 숨김).
    api.get<StageSummary[]>('/api/quotations/stage-summary')
      .then((res) => setStageMap(new Map(res.data.map((s) => [s.id, s]))))
      .catch(() => setStageMap(new Map()));
  };
  useEffect(() => { load(); }, []);

  const cancelBundle = async (bundleId?: string | null, fallbackItemId?: number) => {
    if (!window.confirm('이 견적 묶음을 취소하시겠어요?')) return;
    try {
      if (bundleId) await api.post(`/api/quotations/bundles/${bundleId}/cancel`, {});
      else if (fallbackItemId) await api.post(`/api/quotations/${fallbackItemId}/cancel`, {});
      load();
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '취소 실패');
    }
  };
  const deleteBundle = async (bundleId?: string | null, fallbackItemId?: number) => {
    if (!window.confirm('이 견적 묶음을 완전히 삭제하시겠어요? 되돌릴 수 없습니다.')) return;
    try {
      if (bundleId) await api.delete(`/api/quotations/bundles/${bundleId}`);
      else if (fallbackItemId) await api.delete(`/api/quotations/${fallbackItemId}`);
      load();
    } catch (err: any) {
      alert(err?.response?.data?.message ?? '삭제 실패');
    }
  };

  // bundle 의 첫 item 의 mode 로 분기. 공개입찰은 bundle 없이 단건일 수 있음.
  const modeFor = (b: QuotationBundleResponse): 'OPEN_BID' | 'TARGETED' =>
    (b.items?.[0]?.mode === 'OPEN_BID') ? 'OPEN_BID' : 'TARGETED';

  const byMode = useMemo(() => {
    const open = bundles.filter((b) => modeFor(b) === 'OPEN_BID');
    const tgt = bundles.filter((b) => modeFor(b) === 'TARGETED');
    return { open, tgt };
  }, [bundles]);

  // 현재 탭의 묶음(필터 옵션·목록의 분모).
  const base = useMemo(() => (modeTab === 'OPEN_BID' ? byMode.open : byMode.tgt), [modeTab, byMode]);

  const siteOptions = useMemo(() => {
    const m = new Map<string, string>();
    base.forEach((b) => { const l = siteLabelOf(b); if (l) m.set(l, l); });
    return [...m.values()].map((v) => ({ value: v, label: v }));
  }, [base]);
  const bpOptions = useMemo(() => {
    const m = new Map<string, string>();
    base.forEach((b) => { if (b.bp_company_id != null) m.set(String(b.bp_company_id), b.bp_company_name ?? `발주사 #${b.bp_company_id}`); });
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [base]);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => base.filter((b) => {
    if (statusFilter && b.aggregate_status !== statusFilter) return false;
    if (siteFilter && siteLabelOf(b) !== siteFilter) return false;
    if (bpFilter && String(b.bp_company_id ?? '') !== bpFilter) return false;
    const created = b.created_at?.slice(0, 10) ?? '';
    if (dateFrom && created < dateFrom) return false;
    if (dateTo && created > dateTo) return false;
    if (qLower) {
      const hay = `${summarizeItems(b)} ${siteLabelOf(b)} ${b.bp_company_name ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  }), [base, statusFilter, siteFilter, bpFilter, dateFrom, dateTo, qLower]);

  const activeFilterCount =
    [statusFilter, siteFilter, bpFilter, q].filter(Boolean).length + (dateFrom || dateTo ? 1 : 0);
  const resetFilters = () => {
    setStatusFilter(''); setSiteFilter(''); setBpFilter(''); setQ(''); setDateFrom(''); setDateTo('');
  };

  const stats = useMemo(() => {
    const counts = { SENT: 0, CLOSED: 0, CANCELLED: 0, DRAFT: 0 } as Record<QuotationStatus, number>;
    bundles.forEach((b) => { counts[b.aggregate_status] = (counts[b.aggregate_status] ?? 0) + 1; });
    return counts;
  }, [bundles]);

  const navigateToBundle = (b: QuotationBundleResponse) => {
    if (b.bundle_id) navigate(`/quotations/bundles/${b.bundle_id}`);
    else if (b.items[0]) navigate(`/quotations/${b.items[0].id}`); // 호환성 (구버전 묶음 없는 견적)
  };

  /** 번들의 3단계 완료 여부 — 집계에 존재하는 번들 내 모든 견적이 완료해야 그 단계 완료. 없으면 null(칩 숨김). */
  const stageFor = (b: QuotationBundleResponse) => {
    const sums = b.items.map((i) => stageMap.get(i.id)).filter((s): s is StageSummary => !!s);
    if (sums.length === 0) return null;
    return {
      selected: sums.every((s) => s.selected),
      dispatched: sums.every((s) => s.dispatched),
      bundle: sums.every((s) => s.bundle),
    };
  };

  return (
    <AppShell breadcrumb={[{ label: pageTitle }]}>
      <div className="mx-auto max-w-7xl space-y-6">
        <PageHeader
          title={pageTitle}
          subtitle={isSupplier
            ? '받은 견적 요청을 검토하고 수락 또는 거부 응답을 보냅니다.'
            : '현장 단위 묶음으로 발송된 견적과 공급사 응답을 검토합니다.'}
          actions={canCreate ? (
            <button
              type="button"
              onClick={() => navigate(modeTab === 'OPEN_BID' ? '/quotations/new/open-bid' : '/quotations/new')}
              className="btn-primary"
            >
              + 새 {modeTab === 'OPEN_BID' ? '공개입찰' : '지정배차'} 견적
            </button>
          ) : null}
        />

        {/* 모드 탭 */}
        <div className="flex gap-1 border-b border-slate-200">
          <button onClick={() => setModeTab('OPEN_BID')}
                  className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
                    modeTab === 'OPEN_BID' ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`}>
            공개입찰 <span className="text-slate-400 text-xs ml-1">({byMode.open.length})</span>
          </button>
          <button onClick={() => setModeTab('TARGETED')}
                  className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
                    modeTab === 'TARGETED' ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500 hover:text-slate-700'
                  }`}>
            지정배차 <span className="text-slate-400 text-xs ml-1">({byMode.tgt.length})</span>
          </button>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <Stat label="응답 대기" value={stats.SENT ?? 0} tone="blue" />
          <Stat label="완료" value={stats.CLOSED ?? 0} tone="emerald" />
          <Stat label="취소됨" value={stats.CANCELLED ?? 0} tone="slate" />
          <Stat label="전체" value={bundles.length} tone="brand" />
        </div>

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '장비·인력·현장·발주사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={siteFilter} onChange={setSiteFilter} placeholder="현장 전체" options={siteOptions} />
          <FilterSelect value={bpFilter} onChange={setBpFilter} placeholder="발주사(BP) 전체" options={bpOptions} />
          <FilterSelect
            value={statusFilter}
            onChange={(v) => setStatusFilter(v as QuotationStatus | '')}
            placeholder="상태 전체"
            options={[
              { value: 'SENT', label: '응답 대기' },
              { value: 'CLOSED', label: '완료' },
              { value: 'CANCELLED', label: '취소됨' },
            ]}
          />
          <div className="flex items-center gap-1">
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} className="input w-auto" />
            <span className="text-xs text-slate-400">~</span>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} className="input w-auto" />
          </div>
        </FilterBar>

        {error && (
          <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>
        )}

        {loading ? (
          <div className="card p-8 text-center text-sm text-slate-500">로드 중...</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-500">
            견적 묶음이 없습니다.
          </div>
        ) : (
          <div className="space-y-3">
            {filtered.map((b) => {
              const key = b.bundle_id ?? `solo-${b.items[0]?.id}`;
              const progressPct = b.total_targets > 0
                ? Math.round((b.responded_count / b.total_targets) * 100) : 0;
              const st = stageFor(b);
              return (
                <div
                  key={key}
                  className="card hover:shadow-md transition cursor-pointer"
                  onClick={() => navigateToBundle(b)}
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="text-base font-bold text-slate-900 truncate">
                          {summarizeItems(b) || '견적 요청'}
                        </h3>
                        <StatusChip status={b.aggregate_status} />
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                          b.items[0]?.mode === 'OPEN_BID' ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-700'
                        }`}>
                          {b.items[0]?.mode === 'OPEN_BID' ? '공개입찰' : '지정배차'}
                        </span>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">
                        {b.items[0]?.mode === 'OPEN_BID' ? (b.items[0]?.work_location_text || '현장 미정') : (b.site_name ?? '현장 미정')}
                        {' · '}작업 {b.work_period_start} ~ {b.work_period_end}
                      </div>
                      <div className="mt-2 flex items-center gap-3 text-xs text-slate-600 flex-wrap">
                        {b.items[0]?.mode === 'OPEN_BID' ? (
                          <>
                            <span>받은 제안 <b>{b.proposal_count}</b>건</span>
                            {b.pending_proposal_count > 0 && (
                              <span className="text-amber-700">검토 대기 <b>{b.pending_proposal_count}</b></span>
                            )}
                            <span>확정 <b className="text-brand-700">{b.finalized_count}</b></span>
                          </>
                        ) : (
                          <>
                            <span>공급사 응답 <b>{b.responded_count}/{b.total_targets}</b></span>
                            <span>수락 <b className="text-emerald-700">{b.accepted_count}</b></span>
                            <span>확정 <b className="text-brand-700">{b.finalized_count}</b></span>
                          </>
                        )}
                        {b.first_work_plan_id && (
                          <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); navigate(`/work-plans/${b.first_work_plan_id}`); }}
                            className="text-blue-700 underline"
                          >
                            → 작업계획서 #{b.first_work_plan_id}
                          </button>
                        )}
                      </div>
                      {st && (
                        <StageChips selected={st.selected} dispatched={st.dispatched} bundle={st.bundle} />
                      )}
                      {b.items[0]?.mode !== 'OPEN_BID' && (
                        <div className="mt-2 h-1.5 w-full max-w-md rounded bg-slate-100 overflow-hidden">
                          <div className="h-full bg-emerald-500" style={{ width: `${progressPct}%` }} />
                        </div>
                      )}
                      <div className="mt-2 text-[10px] text-slate-400 flex items-center gap-2">
                        <span>견적 #{b.items[0]?.id ?? '?'}</span>
                        <span>·</span>
                        <span>{formatRelative(b.created_at)}</span>
                        <span className="text-slate-300">({b.created_at?.slice(0, 16).replace('T', ' ')})</span>
                      </div>
                    </div>
                    {canManage && (
                      <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                        {b.aggregate_status === 'SENT' && (
                          <button
                            type="button"
                            onClick={() => cancelBundle(b.bundle_id, b.items[0]?.id)}
                            className="text-xs px-2 py-1 rounded border border-amber-300 text-amber-700 hover:bg-amber-50"
                          >
                            취소
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => deleteBundle(b.bundle_id, b.items[0]?.id)}
                          className="text-xs px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50"
                        >
                          삭제
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </AppShell>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone: 'blue' | 'emerald' | 'slate' | 'brand' }) {
  const map = {
    blue: 'border-blue-200 bg-blue-50 text-blue-900',
    emerald: 'border-emerald-200 bg-emerald-50 text-emerald-900',
    slate: 'border-slate-200 bg-slate-50 text-slate-700',
    brand: 'border-brand-200 bg-brand-50 text-brand-900',
  };
  return (
    <div className={`rounded-lg border px-4 py-3 ${map[tone]}`}>
      <div className="text-xs font-semibold opacity-80">{label}</div>
      <div className="text-2xl font-bold mt-0.5">{value}</div>
    </div>
  );
}

/** 3단계 진행 chip — 선정 → 배차 → 서류. 완료 emerald, 미완 회색. (상세 NextStepCard 시각 참고.) */
function StageChips({ selected, dispatched, bundle }: { selected: boolean; dispatched: boolean; bundle: boolean }) {
  const steps: Array<{ done: boolean; label: string }> = [
    { done: selected, label: '선정' },
    { done: dispatched, label: '배차' },
    { done: bundle, label: '서류' },
  ];
  return (
    <div className="mt-2 flex items-center gap-1">
      {steps.map((s, i) => (
        <div key={s.label} className="flex items-center gap-1">
          <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded-full ${
            s.done ? 'bg-emerald-100 text-emerald-700' : 'bg-slate-100 text-slate-400'
          }`}>
            <span className={`inline-flex items-center justify-center w-3.5 h-3.5 rounded-full text-[8px] leading-none ${
              s.done ? 'bg-emerald-500 text-white' : 'bg-slate-300 text-white'
            }`}>{s.done ? '✓' : i + 1}</span>
            {s.label}
          </span>
          {i < steps.length - 1 && <span className="text-slate-300 text-[10px]">→</span>}
        </div>
      ))}
    </div>
  );
}

function StatusChip({ status }: { status: QuotationStatus }) {
  const map: Record<QuotationStatus, string> = {
    DRAFT: 'bg-slate-100 text-slate-700',
    SENT: 'bg-blue-100 text-blue-700',
    CLOSED: 'bg-emerald-100 text-emerald-700',
    CANCELLED: 'bg-rose-100 text-rose-700',
  };
  return (
    <span className={`inline-block text-[10px] font-semibold px-2 py-0.5 rounded ${map[status]}`}>
      {QUOTATION_STATUS_LABEL[status]}
    </span>
  );
}
