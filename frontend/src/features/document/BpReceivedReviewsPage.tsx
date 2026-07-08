import { useCallback, useEffect, useState } from 'react';
import AppShell from '../../components/layout/AppShell';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';

type ReviewItem = {
  owner_type: 'EQUIPMENT' | 'PERSON';
  owner_id: number;
  label: string;
  doc_count: number;
};
type Review = {
  id: number;
  supplier_company_id: number;
  supplier_company_name: string | null;
  message: string | null;
  sent_at: string;
  read_at: string | null;
  total_docs: number;
  items: ReviewItem[];
};

/** BP사 계정 "받은 서류 심사" — 공급사가 보낸 서류 묶음을 조회/다운로드. */
export default function BpReceivedReviewsPage() {
  const [rows, setRows] = useState<Review[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkBusy, setBulkBusy] = useState(false);

  const refresh = useCallback(() => {
    setLoading(true);
    api.get<Review[]>('/api/document-reviews/received')
      .then((r) => setRows(r.data))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  function toggleAll() {
    setSelected((prev) => (prev.size === rows.length ? new Set() : new Set(rows.map((r) => r.id))));
  }

  function saveBlob(blob: Blob, name: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
  function markReadLocal(ids: number[]) {
    const now = new Date().toISOString();
    setRows((prev) => prev.map((x) => (ids.includes(x.id) && !x.read_at ? { ...x, read_at: now } : x)));
  }

  async function download(rev: Review) {
    setBusy(rev.id);
    try {
      const res = await api.get(`/api/document-reviews/${rev.id}/download`, { responseType: 'blob', timeout: 120_000 });
      saveBlob(res.data as Blob, `서류심사-${rev.supplier_company_name ?? rev.supplier_company_id}-${rev.id}.zip`);
      if (!rev.read_at) {
        await api.post(`/api/document-reviews/${rev.id}/read`).catch(() => {});
        markReadLocal([rev.id]);
      }
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '다운로드 실패');
    } finally {
      setBusy(null);
    }
  }

  async function downloadSelected() {
    const ids = Array.from(selected);
    if (ids.length === 0) return;
    setBulkBusy(true);
    try {
      const res = await api.post('/api/document-reviews/download', { ids }, { responseType: 'blob', timeout: 300_000 });
      saveBlob(res.data as Blob, `서류심사-모음-${ids.length}건.zip`);
      markReadLocal(ids); // 서버가 일괄 다운로드 시 읽음 처리
      setSelected(new Set());
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '다운로드 실패');
    } finally {
      setBulkBusy(false);
    }
  }

  const allChecked = rows.length > 0 && selected.size === rows.length;

  return (
    <AppShell breadcrumb={[{ label: '받은 서류 심사' }]}>
      <div className="max-w-4xl mx-auto px-6 py-8 space-y-4">
        <div>
          <h1 className="text-2xl font-bold">받은 서류 심사</h1>
          <p className="text-sm text-slate-500 mt-1">
            공급사가 보낸 자원별 서류 묶음입니다. 다운로드하면 자원별 폴더로 묶인 압축파일(zip)을 받습니다.
            여러 건을 체크해서 한 번에 받을 수도 있습니다.
          </p>
        </div>

        {loading ? (
          <div className="card p-8 text-center text-sm text-slate-400">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="card p-8 text-center text-sm text-slate-400">받은 서류 심사가 없습니다.</div>
        ) : (
          <>
            {/* 선택 도구 막대 */}
            <div className="flex items-center justify-between px-1">
              <label className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 cursor-pointer select-none">
                <input type="checkbox" checked={allChecked} onChange={toggleAll}
                       className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500" />
                전체 선택 {selected.size > 0 && <span className="text-slate-400">({selected.size}건)</span>}
              </label>
              <button
                type="button"
                onClick={downloadSelected}
                disabled={selected.size === 0 || bulkBusy}
                className="px-3 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-40"
              >
                {bulkBusy ? '받는 중…' : `선택 다운로드${selected.size > 0 ? ` (${selected.size}건)` : ''}`}
              </button>
            </div>

            <div className="space-y-3">
              {rows.map((rev) => (
                <div key={rev.id} className={`card p-4 ${selected.has(rev.id) ? 'ring-2 ring-brand-200 border-brand-300' : ''}`}>
                  <div className="flex items-start gap-3">
                    <input
                      type="checkbox"
                      checked={selected.has(rev.id)}
                      onChange={() => toggle(rev.id)}
                      className="mt-1 h-4 w-4 shrink-0 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                    />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-bold text-slate-900 truncate">
                              {rev.supplier_company_name ?? `공급사 #${rev.supplier_company_id}`}
                            </span>
                            {!rev.read_at && (
                              <span className="inline-flex items-center px-1.5 h-5 rounded-full bg-brand-600 text-white text-[11px] font-semibold">
                                NEW
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-slate-500 mt-0.5">
                            {new Date(rev.sent_at).toLocaleString('ko-KR')} · 자원 {rev.items.length}건 · 서류 {rev.total_docs}건
                          </div>
                        </div>
                        <button
                          type="button"
                          onClick={() => download(rev)}
                          disabled={busy === rev.id}
                          className="shrink-0 px-3 py-2 rounded-lg bg-slate-100 text-slate-700 text-sm font-semibold hover:bg-slate-200 disabled:opacity-50"
                        >
                          {busy === rev.id ? '받는 중…' : '다운로드'}
                        </button>
                      </div>

                      {rev.message && (
                        <div className="mt-2 text-sm text-slate-700 bg-slate-50 rounded-lg px-3 py-2 whitespace-pre-wrap">
                          {rev.message}
                        </div>
                      )}

                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {rev.items.map((it) => (
                          <span
                            key={`${it.owner_type}:${it.owner_id}`}
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-medium ${
                              it.owner_type === 'EQUIPMENT' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                            }`}
                          >
                            {it.label} <span className="opacity-70">· {it.doc_count}건</span>
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </AppShell>
  );
}
