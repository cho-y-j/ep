import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { SectionCard, EmptyState } from './widgets';
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

type Props = {
  /** 미리 받아둔 데이터를 우선 사용. 없으면 직접 fetch. */
  preloaded?: AuditLog[];
  limit?: number;
};

export default function AuditLogWidget({ preloaded, limit = 10 }: Props) {
  const [logs, setLogs] = useState<AuditLog[]>(preloaded ?? []);
  const [loading, setLoading] = useState(!preloaded);

  useEffect(() => {
    if (preloaded) { setLogs(preloaded); return; }
    let cancelled = false;
    api.get<AuditLog[]>('/api/audit-logs/recent', { params: { limit } })
      .then((res) => { if (!cancelled) setLogs(res.data); })
      .catch(() => {})
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [preloaded, limit]);

  return (
    <SectionCard title="최근 활동" action={
      <Link to="/audit-logs" className="text-xs text-slate-500 hover:text-slate-900">전체 보기 ›</Link>
    }>
      {loading ? (
        <p className="text-sm text-slate-400">불러오는 중...</p>
      ) : logs.length === 0 ? (
        <EmptyState text="최근 활동 없음" />
      ) : (
        <ul className="divide-y divide-slate-100">
          {logs.slice(0, limit).map((log) => (
            <li key={log.id} className="py-3 flex items-start gap-3">
              <div className="shrink-0 w-2 h-2 rounded-full bg-blue-400 mt-2" />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-slate-900">
                  {labelAction(log.action)}
                  <span className="ml-2 text-xs text-slate-400">{labelTarget(log.target_type)} #{log.target_id ?? '-'}</span>
                </div>
                <div className="text-xs text-slate-500 mt-0.5">
                  {labelRole(log.actor_role)}
                  {log.actor_user_id ? ` · 사용자 ${log.actor_user_id}` : ''}
                  {log.site_id ? ` · 현장 ${log.site_id}` : ''}
                  {' · '}
                  {new Date(log.created_at).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }).slice(0, 16)}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}
