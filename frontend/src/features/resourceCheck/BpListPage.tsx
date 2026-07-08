import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';

export default function ResourceCheckBpList() {
  const [items, setItems] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get<ResourceCheckResponse[]>('/api/resource-checks/bp-list');
      setItems(res.data);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const review = async (id: number, action: 'approve' | 'reject') => {
    let note: string | null = null;
    if (action === 'reject') {
      note = window.prompt('반려 사유 (선택)') ?? '';
    }
    setBusy(id);
    try {
      await api.post(`/api/resource-checks/${id}/${action}`, { note });
      toast.success(action === 'approve' ? '승인됨' : '반려됨');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '실패');
    } finally { setBusy(null); }
  };

  return (
    <AppShell>
      <header className="mb-4">
        <h1 className="text-xl font-bold text-slate-900">보낸 점검 요청</h1>
        <p className="text-xs text-slate-500 mt-1">공급사에 보낸 자동차 안전점검·건강검진·안전교육 등 — 회신 검토</p>
      </header>
      {loading ? (
        <div className="text-sm text-slate-400">로딩…</div>
      ) : items.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">보낸 요청이 없습니다.</div>
      ) : (
        <div className="space-y-2">
          {items.map((r) => (
            <div key={r.id} className="card p-3 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-bold text-slate-900">{CHECK_TYPE_LABEL[r.check_type]}</span>
                  <span className={`px-1.5 py-0.5 text-[10px] rounded-full font-semibold ${CHECK_STATUS_CHIP_CLS[r.status]}`}>
                    {CHECK_STATUS_LABEL[r.status]}
                  </span>
                </div>
                <div className="mt-0.5 text-sm text-slate-700 truncate">
                  {r.owner_label} → {r.supplier_company_name ?? '공급사'}
                  {r.due_date && <span className="ml-2 text-xs text-rose-700">마감 {r.due_date}</span>}
                </div>
                {r.notes && <div className="mt-0.5 text-xs text-slate-500 truncate">{r.notes}</div>}
              </div>
              <div className="shrink-0 flex items-center gap-2">
                {r.document_id && (
                  <a href={`/api/documents/${r.document_id}/file`} target="_blank" rel="noopener noreferrer"
                     className="px-3 py-1.5 text-xs rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                    제출 서류 보기
                  </a>
                )}
                {r.status === 'SUBMITTED' && (
                  <>
                    <button onClick={() => void review(r.id, 'approve')} disabled={busy === r.id}
                            className="px-3 py-1.5 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                      승인 → 투입 대기
                    </button>
                    <button onClick={() => void review(r.id, 'reject')} disabled={busy === r.id}
                            className="px-3 py-1.5 text-xs rounded border border-rose-500 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                      반려
                    </button>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </AppShell>
  );
}
