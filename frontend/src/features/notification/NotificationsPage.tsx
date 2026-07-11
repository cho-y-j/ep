import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { NOTIFICATION_TYPE_LABEL, type NotificationResponse } from '../../types/notification';

type Page<T> = {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};

export default function NotificationsPage() {
  const [pg, setPg] = useState<Page<NotificationResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const res = await api.get<Page<NotificationResponse>>('/api/notifications', { params: { page: p, size: 30 } });
      setPg(res.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(page); }, [page]);

  async function markRead(id: number) {
    await api.post(`/api/notifications/${id}/read`);
    load(page);
  }

  async function markAllRead() {
    if (!window.confirm('미읽음 알림을 모두 읽음 처리할까요?')) return;
    await api.post('/api/notifications/read-all', {});
    load(page);
  }

  const items = pg?.content ?? [];
  const unreadCount = useMemo(() => items.filter((n) => !n.read_at).length, [items]);

  return (
    <AppShell breadcrumb={[{ label: '알림' }]}>
      <div className="space-y-6">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h1 className="text-2xl font-bold">알림</h1>
            <p className="text-sm text-slate-500 mt-1">권한 범위 내 알림을 확인하세요.</p>
          </div>
          {unreadCount > 0 && (
            <button
              type="button"
              onClick={() => void markAllRead()}
              className="px-3 py-2 rounded-lg text-sm font-semibold bg-slate-900 text-white hover:bg-slate-800"
            >
              전체 읽음 ({unreadCount})
            </button>
          )}
        </div>

        <div className="rounded-xl border border-slate-200 bg-white">
          {loading ? (
            <p className="text-sm text-slate-400 px-6 py-12 text-center">불러오는 중...</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-slate-400 px-6 py-12 text-center">알림이 없습니다.</p>
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
                  : null;
                return (
                  <li key={n.id} className={`p-4 flex items-start gap-3 ${unread ? 'bg-blue-50/30' : ''}`}>
                    {unread && <span className="shrink-0 w-2 h-2 rounded-full bg-blue-500 mt-2" />}
                    {!unread && <span className="shrink-0 w-2 h-2 rounded-full bg-slate-300 mt-2" />}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-xs px-2 py-0.5 rounded-full bg-slate-100 text-slate-600 font-semibold">{label}</span>
                        <span className="text-sm font-semibold text-slate-900">{n.title}</span>
                      </div>
                      <p className="text-sm text-slate-600 mt-1">{n.message}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs text-slate-400">
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
            <span className="text-slate-500">전체 {pg.total_elements}건</span>
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
