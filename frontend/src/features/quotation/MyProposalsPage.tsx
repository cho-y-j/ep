import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import { api } from '../../lib/api';
import { EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory } from '../../types/equipment';
import { PERSON_ROLE_LABEL, type PersonRole } from '../../types/person';

interface ProposalRow {
  id: number;
  request_id: number;
  equipment_id?: number | null;
  equipment_label?: string | null;
  person_id?: number | null;
  person_label?: string | null;
  daily_rate?: number | null;
  monthly_rate?: number | null;
  note?: string | null;
  status: 'SUBMITTED' | 'PENDING_REVIEW' | 'FINAL_ACCEPTED' | 'REJECTED' | 'WITHDRAWN';
  created_at: string;
  finalized_at?: string | null;
  rejected_at?: string | null;
  request_bp_company_id?: number | null;
  request_bp_company_name?: string | null;
  request_requested_by_user_id?: number | null;
  request_type?: 'EQUIPMENT' | 'MANPOWER' | null;
  request_equipment_category?: string | null;
  request_manpower_role?: string | null;
  request_work_period_start?: string | null;
  request_work_period_end?: string | null;
  request_status?: 'SENT' | 'CLOSED' | 'CANCELLED' | null;
  request_mode?: 'OPEN_BID' | 'TARGETED' | null;
}

const STATUS_LABEL: Record<ProposalRow['status'], { label: string; cls: string }> = {
  SUBMITTED: { label: '검토 대기', cls: 'bg-blue-100 text-blue-700' },
  PENDING_REVIEW: { label: '재확인 요청', cls: 'bg-amber-100 text-amber-700' },
  FINAL_ACCEPTED: { label: '최종 선정', cls: 'bg-emerald-100 text-emerald-700' },
  REJECTED: { label: '거절', cls: 'bg-rose-100 text-rose-600' },
  WITHDRAWN: { label: '철회', cls: 'bg-slate-100 text-slate-500' },
};

export default function MyProposalsPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ProposalRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'ACTIVE' | 'WON' | 'DONE'>('ACTIVE');
  // 클라이언트 필터 — 탭(진행/선정/종료) 위에 검색·발주사 필터 추가.
  const [q, setQ] = useState('');
  const [bpFilter, setBpFilter] = useState('');

  useEffect(() => {
    setLoading(true);
    api.get<ProposalRow[]>('/api/quotations/proposals/mine')
      .then((r) => setRows(r.data))
      .finally(() => setLoading(false));
  }, []);

  const resourceLabel = (p: ProposalRow) => {
    if (p.request_type === 'MANPOWER') {
      return p.request_manpower_role
        ? PERSON_ROLE_LABEL[p.request_manpower_role as PersonRole] ?? p.request_manpower_role
        : '인력';
    }
    return p.request_equipment_category
      ? EQUIPMENT_CATEGORY_LABEL[p.request_equipment_category as EquipmentCategory] ?? p.request_equipment_category
      : '장비';
  };

  const counts = {
    active: rows.filter((p) => p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW').length,
    won: rows.filter((p) => p.status === 'FINAL_ACCEPTED').length,
    done: rows.filter((p) => p.status === 'REJECTED' || p.status === 'WITHDRAWN').length,
  };

  const bpOptions = useMemo(() => {
    const m = new Map<string, string>();
    rows.forEach((p) => { if (p.request_bp_company_id != null) m.set(String(p.request_bp_company_id), p.request_bp_company_name ?? `회사 #${p.request_bp_company_id}`); });
    return [...m.entries()].map(([value, label]) => ({ value, label }));
  }, [rows]);

  const qLower = q.trim().toLowerCase();
  const filtered = rows.filter((p) => {
    const inTab = tab === 'ACTIVE'
      ? (p.status === 'SUBMITTED' || p.status === 'PENDING_REVIEW')
      : tab === 'WON'
        ? p.status === 'FINAL_ACCEPTED'
        : (p.status === 'REJECTED' || p.status === 'WITHDRAWN');
    if (!inTab) return false;
    if (bpFilter && String(p.request_bp_company_id ?? '') !== bpFilter) return false;
    if (qLower) {
      const hay = `${resourceLabel(p)} ${p.request_bp_company_name ?? ''} ${p.equipment_label ?? ''} ${p.person_label ?? ''}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    return true;
  });

  const activeFilterCount = [q, bpFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setBpFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '내 견적 제안' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8 space-y-5">
        <PageHeader
          title="내 견적 제안"
          subtitle="BP 공개입찰에 제출한 단가 제안과 진행 상태(진행 중 · 선정 · 종료)입니다."
        />

        <div className="flex gap-2 border-b border-slate-200">
          {[
            { key: 'ACTIVE', label: `진행 중 (${counts.active})` },
            { key: 'WON', label: `선정 (${counts.won})` },
            { key: 'DONE', label: `종료 (${counts.done})` },
          ].map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setTab(t.key as typeof tab)}
              className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px ${
                tab === t.key ? 'border-brand-600 text-brand-700' : 'border-transparent text-slate-500 hover:text-slate-700'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '자원·발주사 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          {bpOptions.length > 0 && (
            <FilterSelect value={bpFilter} onChange={setBpFilter} placeholder="발주사(BP) 전체" options={bpOptions} />
          )}
        </FilterBar>

        {loading ? (
          <div className="card p-8 text-center text-slate-400">로딩 중...</div>
        ) : filtered.length === 0 ? (
          <div className="card p-8 text-center text-slate-400 text-sm">
            {tab === 'ACTIVE' && '진행 중인 제안이 없습니다. BP 공개입찰에서 응찰하세요.'}
            {tab === 'WON' && '선정된 제안이 없습니다.'}
            {tab === 'DONE' && '종료된 제안이 없습니다.'}
          </div>
        ) : (
          <div className="space-y-3">
            {filtered.map((p) => {
              const s = STATUS_LABEL[p.status];
              return (
                <div
                  key={p.id}
                  className="card p-4 cursor-pointer hover:shadow-sm transition"
                  onClick={() => navigate(`/quotations/${p.request_id}`)}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs font-semibold text-slate-500">BP사</span>
                        <span className="text-sm font-bold text-slate-900">
                          {p.request_bp_company_name ?? `회사 #${p.request_bp_company_id ?? '?'}`}
                        </span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${
                          p.request_mode === 'OPEN_BID' ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-700'
                        }`}>
                          {p.request_mode === 'OPEN_BID' ? '공개입찰' : '지정배차'}
                        </span>
                      </div>
                      <div className="mt-1 text-base font-bold text-brand-700">
                        {resourceLabel(p)}
                        <span className="ml-2 text-xs text-slate-500">
                          {p.equipment_label || p.person_label || ''}
                        </span>
                      </div>
                      <div className="mt-1 text-xs text-slate-500">
                        기간 {p.request_work_period_start ?? '?'} ~ {p.request_work_period_end ?? '?'}
                      </div>
                      <div className="mt-2 text-sm text-slate-700">
                        내 제안:{' '}
                        {p.daily_rate ? `일 ${p.daily_rate.toLocaleString()}원 ` : ''}
                        {p.monthly_rate ? `월 ${p.monthly_rate.toLocaleString()}원` : ''}
                        {!p.daily_rate && !p.monthly_rate && <span className="text-slate-400">단가 미입력</span>}
                      </div>
                      {p.note && (
                        <div className="mt-1 text-xs text-slate-500 italic line-clamp-1">{p.note}</div>
                      )}
                      <div className="mt-2 text-[10px] text-slate-400">
                        제출 {p.created_at?.slice(0, 16).replace('T', ' ')}
                        {p.finalized_at && <span className="ml-3 text-emerald-600">선정 {p.finalized_at.slice(0, 16).replace('T', ' ')}</span>}
                        {p.rejected_at && <span className="ml-3 text-rose-500">거절 {p.rejected_at.slice(0, 16).replace('T', ' ')}</span>}
                        <span className="ml-3">견적 #{p.request_id}</span>
                      </div>
                    </div>
                    <div className="shrink-0 flex flex-col items-end gap-2">
                      <span className={`text-xs font-semibold px-2 py-1 rounded-full ${s.cls}`}>
                        {s.label}
                      </span>
                      {p.status === 'FINAL_ACCEPTED' && (
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/outgoing-quotations/new?fromProposal=${p.id}`);
                          }}
                          className="text-xs px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 font-semibold whitespace-nowrap"
                        >
                          정식 견적서 발송 →
                        </button>
                      )}
                    </div>
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
