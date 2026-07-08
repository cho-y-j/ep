import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';

/** 출근 중 작업자 — 작업시간 지정/수정 (ADMIN/BP). 휴식 알림 타이머의 기준. */
type OpenSession = {
  id: number;
  person_id: number;
  person_name?: string;
  site_id?: number | null;
  site_name?: string | null;
  work_plan_title?: string | null;
  check_in_at?: string | null;
  work_start_at?: string | null;
  work_end_at?: string | null;
};

// 백엔드 LocalDateTime(ISO) → <input type=datetime-local> 값(yyyy-MM-ddTHH:mm)
function toInput(v?: string | null): string {
  return v ? v.slice(0, 16) : '';
}

export default function WorkTimeSection() {
  const [rows, setRows] = useState<OpenSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [edit, setEdit] = useState<Record<number, { start: string; end: string }>>({});
  const [saving, setSaving] = useState<number | null>(null);

  const load = () => {
    setLoading(true);
    api.get<OpenSession[]>('/api/attendance/open')
      .then((r) => {
        setRows(r.data);
        const e: Record<number, { start: string; end: string }> = {};
        for (const s of r.data) e[s.id] = { start: toInput(s.work_start_at), end: toInput(s.work_end_at) };
        setEdit(e);
      })
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, []);

  const setField = (id: number, key: 'start' | 'end', v: string) =>
    setEdit((p) => ({ ...p, [id]: { ...p[id], [key]: v } }));

  const save = async (id: number) => {
    const e = edit[id]; if (!e) return;
    setSaving(id);
    try {
      await api.patch(`/api/attendance/${id}/work-time`, {
        work_start_at: e.start ? e.start + ':00' : null,
        work_end_at: e.end ? e.end + ':00' : null,
      });
      toast.success('작업시간 저장됨 — 휴식 알림이 이 시간 기준으로 동작합니다');
      load();
    } catch (err: any) {
      toast.error(err?.response?.data?.message ?? '저장 실패');
    } finally { setSaving(null); }
  };

  if (loading || rows.length === 0) return null;

  return (
    <div className="rounded border border-slate-200 bg-white overflow-hidden">
      <div className="px-3 py-2 border-b border-slate-100 text-sm font-semibold text-slate-800">
        출근 중 작업자 — 작업시간 ({rows.length})
        <span className="ml-1 font-normal text-slate-500 text-xs">· 휴식 알림은 출근시각이 아니라 이 작업시간 기준</span>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-600">
            <tr>
              <th className="px-3 py-2">작업자</th>
              <th className="px-3 py-2">현장</th>
              <th className="px-3 py-2">출근</th>
              <th className="px-3 py-2">작업 시작</th>
              <th className="px-3 py-2">작업 종료</th>
              <th className="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((s) => {
              const e = edit[s.id] ?? { start: '', end: '' };
              return (
                <tr key={s.id} className="border-t border-slate-100">
                  <td className="px-3 py-2 font-medium text-slate-900">{s.person_name ?? `#${s.person_id}`}</td>
                  <td className="px-3 py-2 text-slate-600 text-xs">{s.site_name ?? (s.site_id ? `현장 #${s.site_id}` : '미지정')}</td>
                  <td className="px-3 py-2 text-slate-500 text-xs whitespace-nowrap">
                    {s.check_in_at
                      ? new Date(s.check_in_at).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
                      : '-'}
                  </td>
                  <td className="px-3 py-2">
                    <input type="datetime-local" value={e.start}
                      onChange={(ev) => setField(s.id, 'start', ev.target.value)}
                      className="input h-8 text-xs" />
                  </td>
                  <td className="px-3 py-2">
                    <input type="datetime-local" value={e.end}
                      onChange={(ev) => setField(s.id, 'end', ev.target.value)}
                      className="input h-8 text-xs" />
                  </td>
                  <td className="px-3 py-2">
                    <button onClick={() => save(s.id)} disabled={saving === s.id}
                      className="rounded bg-brand-600 px-2.5 py-1 text-xs font-semibold text-white hover:bg-brand-700 disabled:opacity-50">
                      {saving === s.id ? '저장…' : '저장'}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
