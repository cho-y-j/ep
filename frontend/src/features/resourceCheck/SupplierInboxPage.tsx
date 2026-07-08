import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import {
  CHECK_TYPE_LABEL, CHECK_STATUS_LABEL, CHECK_STATUS_CHIP_CLS,
  type ResourceCheckResponse,
} from '../../types/resourceCheck';

export default function ResourceCheckSupplierInbox() {
  const [items, setItems] = useState<ResourceCheckResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get<ResourceCheckResponse[]>('/api/resource-checks/supplier-list');
      setItems(res.data);
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const onUpload = async (req: ResourceCheckResponse, file: File) => {
    setBusy(req.id);
    try {
      const fd = new FormData();
      fd.append('file', file);
      await api.post(`/api/resource-checks/${req.id}/submit-file`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('회신 완료 — BP 검토 대기');
      void load();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '회신 실패');
    } finally { setBusy(null); }
  };

  return (
    <AppShell>
      <header className="mb-4">
        <h1 className="text-xl font-bold text-slate-900">받은 점검 요청</h1>
        <p className="text-xs text-slate-500 mt-1">BP 가 보낸 자동차 안전점검·건강검진·안전교육 등 요청</p>
      </header>
      {loading ? (
        <div className="text-sm text-slate-400">로딩…</div>
      ) : items.length === 0 ? (
        <div className="card p-8 text-center text-slate-400">받은 요청이 없습니다.</div>
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
                  {r.owner_label}
                  {r.due_date && <span className="ml-2 text-xs text-rose-700">마감 {r.due_date}</span>}
                </div>
                {r.notes && <div className="mt-0.5 text-xs text-slate-500 truncate">{r.notes}</div>}
                {r.review_note && (
                  <div className="mt-1 px-2 py-1 rounded bg-rose-50 border border-rose-200 text-xs text-rose-800">
                    BP 메모: {r.review_note}
                  </div>
                )}
              </div>
              <div className="shrink-0 flex items-center gap-2">
                {(r.status === 'REQUESTED' || r.status === 'REJECTED') && (
                  <label className={`px-3 py-1.5 text-xs rounded border border-blue-500 text-blue-700 hover:bg-blue-50 cursor-pointer ${busy === r.id ? 'opacity-50 pointer-events-none' : ''}`}>
                    {busy === r.id ? '업로드 중…' : '서류 첨부 회신'}
                    <input type="file" accept="image/*,application/pdf" className="hidden"
                           onChange={(e) => {
                             const f = e.target.files?.[0];
                             if (f) void onUpload(r, f);
                             e.target.value = '';
                           }} />
                  </label>
                )}
                {r.document_id && (
                  <a href={`/api/documents/${r.document_id}/file`} target="_blank" rel="noopener noreferrer"
                     className="px-3 py-1.5 text-xs rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
                    제출 서류 보기
                  </a>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </AppShell>
  );
}
