import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { labelAction, labelTarget, labelRole } from '../../lib/auditLabels';

type AuditLog = {
  id: number;
  actor_user_id?: number | null;
  actor_role?: string | null;
  actor_company_id?: number | null;
  action: string;
  target_type: string;
  target_id?: number | null;
  target_company_id?: number | null;
  site_id?: number | null;
  before_json?: string | null;
  after_json?: string | null;
  created_at: string;
};

type Page<T> = {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};

export default function AuditLogPage() {
  const [pg, setPg] = useState<Page<AuditLog> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    api.get<Page<AuditLog>>('/api/audit-logs', { params: { page, size: 30 } })
      .then((res) => setPg(res.data))
      .catch(() => setPg(null))
      .finally(() => setLoading(false));
  }, [page]);

  const logs = pg?.content ?? [];

  return (
    <AppShell breadcrumb={[{ label: '로그' }]}>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold">활동 로그</h1>
          <p className="text-sm text-slate-500 mt-1">권한 범위 내의 주요 데이터 변경 이력을 확인하세요.</p>
        </div>

        <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr className="text-left text-slate-500 text-xs">
                <th className="px-4 py-3 font-medium">시각</th>
                <th className="px-4 py-3 font-medium">행위자</th>
                <th className="px-4 py-3 font-medium">액션</th>
                <th className="px-4 py-3 font-medium">대상</th>
                <th className="px-4 py-3 font-medium">현장</th>
                <th className="px-4 py-3 font-medium">변경</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={6} className="px-4 py-12 text-center text-slate-400">불러오는 중...</td></tr>
              ) : logs.length === 0 ? (
                <tr><td colSpan={6} className="px-4 py-12 text-center text-slate-400">표시할 로그가 없습니다</td></tr>
              ) : logs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3 text-slate-700 whitespace-nowrap">
                    {new Date(log.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)}
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {labelRole(log.actor_role)}
                    {log.actor_user_id ? <div className="text-xs text-slate-400">사용자 #{log.actor_user_id}</div> : null}
                  </td>
                  <td className="px-4 py-3">
                    <span className="inline-flex px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-semibold">
                      {labelAction(log.action)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-700">
                    {labelTarget(log.target_type)} #{log.target_id ?? '-'}
                    {log.target_company_id ? <div className="text-xs text-slate-400">회사 #{log.target_company_id}</div> : null}
                  </td>
                  <td className="px-4 py-3 text-slate-700">{log.site_id ?? '-'}</td>
                  <td className="px-4 py-3 text-xs text-slate-500 max-w-[420px]">
                    {(log.before_json || log.after_json) ? (
                      <details className="group">
                        <summary className="cursor-pointer text-slate-500 hover:text-slate-700 select-none list-none">
                          <span className="group-open:hidden">변경 내용 보기 ▾</span>
                          <span className="hidden group-open:inline">변경 내용 접기 ▴</span>
                        </summary>
                        <div className="mt-2 space-y-1 font-mono text-[11px] leading-relaxed whitespace-pre-wrap break-all">
                          {log.before_json && (
                            <div><span className="text-slate-400">변경 전:</span> {log.before_json}</div>
                          )}
                          {log.after_json && (
                            <div><span className="text-emerald-600">변경 후:</span> {log.after_json}</div>
                          )}
                        </div>
                      </details>
                    ) : (
                      <span className="text-slate-300">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
