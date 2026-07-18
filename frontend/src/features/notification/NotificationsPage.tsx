import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import {
  NOTIFICATION_TYPE_LABEL,
  NOTIFICATION_GROUPS,
  type NotificationGroupKey,
  type NotificationResponse,
} from '../../types/notification';

type Page<T> = {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};

type ReadFilter = 'all' | 'unread' | 'read';
type PeriodFilter = 'all' | 'today' | '7d' | '30d' | 'custom';

/** 로컬(KST) 기준 YYYY-MM-DD. toISOString(UTC) 은 자정 넘겨 날짜가 밀리므로 로컬 필드로 조립. */
function localIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** 기간 필터 → 서버에 넘길 시작일(fromDate). 없으면 undefined(무제한). */
function fromDateFor(period: PeriodFilter, customFrom: string): string | undefined {
  if (period === 'all') return undefined;
  if (period === 'custom') return customFrom || undefined;
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  if (period === '7d') d.setDate(d.getDate() - 7);
  else if (period === '30d') d.setDate(d.getDate() - 30);
  return localIso(d);
}

export default function NotificationsPage() {
  const [pg, setPg] = useState<Page<NotificationResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  // 필터
  const [groupKey, setGroupKey] = useState<'all' | NotificationGroupKey>('all');
  const [readFilter, setReadFilter] = useState<ReadFilter>('all');
  const [period, setPeriod] = useState<PeriodFilter>('all');
  const [customFrom, setCustomFrom] = useState('');
  const [qInput, setQInput] = useState('');
  const [q, setQ] = useState('');

  const filtersActive =
    groupKey !== 'all' || readFilter !== 'all' || period !== 'all' || q !== '';

  // 검색어 디바운스 — 입력 후 300ms 뒤 적용, 페이지 0 리셋.
  useEffect(() => {
    const t = setTimeout(() => {
      setQ(qInput.trim());
      setPage(0);
    }, 300);
    return () => clearTimeout(t);
  }, [qInput]);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const params: Record<string, string | number | boolean> = { page: p, size: 30 };
      if (readFilter === 'unread') params.unread = true;
      else if (readFilter === 'read') params.unread = false;
      const group = NOTIFICATION_GROUPS.find((g) => g.key === groupKey);
      if (group) params.types = group.types.join(',');
      const from = fromDateFor(period, customFrom);
      if (from) params.fromDate = from;
      if (q) params.q = q;
      const res = await api.get<Page<NotificationResponse>>('/api/notifications', { params });
      setPg(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(page);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, groupKey, readFilter, period, customFrom, q]);

  async function markRead(id: number) {
    await api.post(`/api/notifications/${id}/read`);
    void load(page);
  }

  async function markAllRead() {
    if (!window.confirm('미읽음 알림을 모두 읽음 처리할까요?')) return;
    await api.post('/api/notifications/read-all', {});
    void load(page);
  }

  function resetFilters() {
    setGroupKey('all');
    setReadFilter('all');
    setPeriod('all');
    setCustomFrom('');
    setQInput('');
    setQ('');
    setPage(0);
  }

  const items = pg?.content ?? [];
  const total = pg?.total_elements ?? 0;

  return (
    <AppShell breadcrumb={[{ label: '알림' }]}>
      <div className="space-y-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold">알림</h1>
            <p className="text-sm text-slate-500 mt-1">권한 범위 내 알림을 유형·기간·발신자로 찾아보세요.</p>
          </div>
          <button
            type="button"
            onClick={() => void markAllRead()}
            className="px-3 py-2 rounded-lg text-sm font-semibold bg-slate-900 text-white hover:bg-slate-800"
          >
            전체 읽음
          </button>
        </div>

        {/* 필터 바 */}
        <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
          <div className="flex flex-wrap gap-1.5">
            <GroupPill active={groupKey === 'all'} onClick={() => { setGroupKey('all'); setPage(0); }}>전체</GroupPill>
            {NOTIFICATION_GROUPS.map((g) => (
              <GroupPill key={g.key} active={groupKey === g.key} onClick={() => { setGroupKey(g.key); setPage(0); }}>
                {g.label}
              </GroupPill>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={readFilter}
              onChange={(e) => { setReadFilter(e.target.value as ReadFilter); setPage(0); }}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700"
            >
              <option value="all">읽음 상태 전체</option>
              <option value="unread">안읽음</option>
              <option value="read">읽음</option>
            </select>
            <select
              value={period}
              onChange={(e) => { setPeriod(e.target.value as PeriodFilter); setPage(0); }}
              className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700"
            >
              <option value="all">기간 전체</option>
              <option value="today">오늘</option>
              <option value="7d">최근 7일</option>
              <option value="30d">최근 30일</option>
              <option value="custom">직접 지정</option>
            </select>
            {period === 'custom' && (
              <input
                type="date"
                value={customFrom}
                onChange={(e) => { setCustomFrom(e.target.value); setPage(0); }}
                className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700"
              />
            )}
            <div className="relative flex-1 min-w-[200px]">
              <input
                type="search"
                value={qInput}
                onChange={(e) => setQInput(e.target.value)}
                placeholder="제목·내용·발신자 검색"
                className="w-full rounded-lg border border-slate-300 bg-white pl-9 pr-3 py-2 text-sm"
              />
              <svg className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
            </div>
            {filtersActive && (
              <button
                type="button"
                onClick={resetFilters}
                className="px-3 py-2 rounded-lg text-sm font-medium border border-slate-200 text-slate-600 hover:bg-slate-50"
              >
                필터 초기화
              </button>
            )}
          </div>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white">
          {loading ? (
            <p className="text-sm text-slate-400 px-6 py-12 text-center">불러오는 중...</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-slate-400 px-6 py-12 text-center">
              {filtersActive ? '조건에 맞는 알림이 없습니다.' : '알림이 없습니다.'}
            </p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {items.map((n) => {
                const unread = !n.read_at;
                const label = NOTIFICATION_TYPE_LABEL[n.type] ?? n.type;
                const linkTo = n.link_type === 'EQUIPMENT' && n.link_id ? `/equipment/${n.link_id}`
                  : n.link_type === 'PERSON' && n.link_id ? `/persons/${n.link_id}`
                  : n.link_type === 'SITE' && n.link_id ? `/sites/${n.link_id}`
                  : n.link_type === 'QUOTATION_REQUEST' && n.link_id ? `/quotations/${n.link_id}`
                  : n.link_type === 'QUOTATION_PROPOSAL' && n.link_id ? `/my-proposals`
                  : n.link_type === 'OUTGOING_QUOTATION' && n.link_id ? `/inbox`
                  : n.link_type === 'WORK_PLAN' && n.link_id ? `/work-plans/${n.link_id}`
                  : n.link_type === 'WORK_CONFIRMATION' && n.link_id ? `/work-plans/${n.link_id}`
                  : n.link_type === 'DOCUMENT_REVIEW' ? `/document-reviews/received`
                  : n.link_type === 'SUB_SUPPLIER' ? `/sub-suppliers`
                  : n.link_type === 'SETTLEMENT_STATEMENT' ? `/settlements`
                  : null;
                return (
                  <li key={n.id} className={`p-4 flex items-start gap-3 ${unread ? 'bg-blue-50/30' : ''}`}>
                    <span className={`shrink-0 w-2 h-2 rounded-full mt-2 ${unread ? 'bg-blue-500' : 'bg-slate-300'}`} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-semibold">{label}</span>
                        <span className="text-sm font-semibold text-slate-900">{n.title}</span>
                      </div>
                      <p className="text-sm text-slate-600 mt-1">{n.message}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs text-slate-400 flex-wrap">
                        <span className="inline-flex items-center gap-1" title="발신자">
                          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
                          <span className="text-slate-500">{n.sender_label ?? '—'}</span>
                        </span>
                        <span>{new Date(n.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)}</span>
                        {linkTo && (
                          <Link to={linkTo} className="text-brand-700 hover:text-brand-800">자원 보기 →</Link>
                        )}
                      </div>
                    </div>
                    {unread && (
                      <button type="button" onClick={() => void markRead(n.id)}
                              className="text-xs px-2 py-1 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50 shrink-0">
                        읽음
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {pg && pg.total_pages > 1 && (
          <div className="flex items-center justify-between text-sm">
            <span className="text-slate-500">전체 {total}건</span>
            <div className="flex items-center gap-1">
              <button onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}
                      className="px-3 py-1 rounded text-slate-600 hover:bg-slate-100 disabled:opacity-30">이전</button>
              <span className="px-3 py-1 text-slate-700">{page + 1} / {pg.total_pages}</span>
              <button onClick={() => setPage((p) => Math.min(pg.total_pages - 1, p + 1))} disabled={page >= pg.total_pages - 1}
                      className="px-3 py-1 rounded text-slate-600 hover:bg-slate-100 disabled:opacity-30">다음</button>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}

function GroupPill({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 rounded-full text-sm font-semibold border transition ${
        active
          ? 'bg-brand-600 text-white border-brand-600'
          : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
      }`}
    >
      {children}
    </button>
  );
}
