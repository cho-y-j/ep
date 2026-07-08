import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import AppShell from '../../components/layout/AppShell';
import type { FieldDeploymentResponse } from '../../types/fieldDeployment';

export default function FieldDeploymentBpInbox() {
  const [items, setItems] = useState<FieldDeploymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [acceptingFor, setAcceptingFor] = useState<FieldDeploymentResponse | null>(null);
  const [busy, setBusy] = useState<number | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const r = await api.get<FieldDeploymentResponse[]>('/api/field-deployments/bp');
      setItems(r.data.filter((x) => x.status === 'REQUESTED'));
    } finally { setLoading(false); }
  };
  useEffect(() => { void load(); }, []);

  const reject = async (id: number) => {
    const note = prompt('반려 사유 (선택)');
    if (note === null) return;
    setBusy(id);
    try {
      await api.post(`/api/field-deployments/${id}/reject`, { note });
      toast.success('반려됨');
      void load();
    } catch (e: any) { toast.error(e?.response?.data?.message ?? '실패'); }
    finally { setBusy(null); }
  };

  return (
    <AppShell breadcrumb={[{ label: '받은 투입 요청' }]}>
      <div className="mx-auto max-w-7xl space-y-4">
        <header>
          <h1 className="text-2xl font-bold text-slate-950">받은 투입 요청</h1>
          <p className="mt-1 text-sm text-slate-500">
            공급사가 "현장으로 보낼게요" 요청한 자원. 수락하면 현장에 배치되고 투입 현황으로 이동합니다.
          </p>
        </header>

        {loading ? <div className="text-sm text-slate-400">불러오는 중…</div>
         : items.length === 0 ? (
          <div className="card p-8 text-center text-slate-400">받은 요청이 없습니다.</div>
        ) : (
          <div className="card overflow-x-auto p-0">
            <table className="w-full text-sm">
              <thead className="border-b border-slate-200 bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-3 py-2 font-semibold">자원</th>
                  <th className="px-3 py-2 font-semibold">공급사</th>
                  <th className="px-3 py-2 font-semibold">희망 현장</th>
                  <th className="px-3 py-2 font-semibold">시작</th>
                  <th className="px-3 py-2 font-semibold">메모</th>
                  <th className="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {items.map((r) => (
                  <tr key={r.id}>
                    <td className="px-3 py-2 font-semibold">
                      <span className="text-[10px] text-slate-500 mr-1">{r.resource_type === 'EQUIPMENT' ? '장비' : '인원'}</span>
                      {r.resource_label}
                    </td>
                    <td className="px-3 py-2">{r.supplier_company_name ?? '#' + r.supplier_company_id}</td>
                    <td className="px-3 py-2 text-slate-700">{r.target_site_name ?? '미지정'}</td>
                    <td className="px-3 py-2 text-xs tabular-nums">{r.start_date ?? '-'}</td>
                    <td className="px-3 py-2 text-xs text-slate-500 max-w-[200px] truncate">{r.note ?? '-'}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      <div className="flex gap-1">
                        <button disabled={busy === r.id} onClick={() => setAcceptingFor(r)}
                                className="px-2 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50">
                          수락 + 배치
                        </button>
                        <button disabled={busy === r.id} onClick={() => reject(r.id)}
                                className="px-2 py-1 text-xs rounded border border-rose-300 text-rose-700 hover:bg-rose-50 disabled:opacity-50">
                          반려
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {acceptingFor && (
          <AcceptDialog row={acceptingFor} onClose={() => setAcceptingFor(null)}
                        onSaved={() => { setAcceptingFor(null); void load(); }} />
        )}
      </div>
    </AppShell>
  );
}

function AcceptDialog({ row, onClose, onSaved }:
  { row: FieldDeploymentResponse; onClose: () => void; onSaved: () => void }) {
  const [targetSiteId, setTargetSiteId] = useState<number | ''>(row.target_site_id ?? '');
  const [note, setNote] = useState('');
  const [sites, setSites] = useState<Array<{ id: number; name: string }>>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get<any[]>('/api/sites').then((r) => setSites((r.data ?? []).map((s) => ({ id: s.id, name: s.name }))))
      .catch(() => setSites([]));
  }, []);

  const submit = async () => {
    if (!targetSiteId) { toast.error('배치할 현장을 선택하세요'); return; }
    setBusy(true);
    try {
      await api.post(`/api/field-deployments/${row.id}/accept`, {
        note: note || null,
        target_site_id: targetSiteId,
      });
      toast.success('수락 — 현장 운영 시작');
      onSaved();
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '수락 실패');
    } finally { setBusy(false); }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">투입 요청 수락 + 현장 배치</h3>
        </div>
        <div className="px-5 py-4 space-y-3 text-sm">
          <div className="card p-2 bg-slate-50 text-xs">
            <div>자원: <span className="font-semibold">{row.resource_label}</span> <span className="text-slate-400">({row.resource_type === 'EQUIPMENT' ? '장비' : '인원'})</span></div>
            <div>공급사: <span className="font-semibold">{row.supplier_company_name ?? '#' + row.supplier_company_id}</span></div>
            {row.target_site_name && (
              <div>공급사 희망: <span className="font-semibold">{row.target_site_name}</span></div>
            )}
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">배치 현장 *</label>
            <select value={targetSiteId} onChange={(e) => setTargetSiteId(e.target.value ? Number(e.target.value) : '')}
                    className="input mt-1 w-full" required>
              <option value="">선택</option>
              {sites.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select>
            <p className="text-[10px] text-slate-400 mt-1">공급사 희망과 다른 현장으로 배치할 수 있습니다.</p>
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-500">메모 (선택)</label>
            <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2}
                      className="input mt-1 w-full" />
          </div>
        </div>
        <div className="px-5 py-3 border-t flex justify-end gap-2">
          <button onClick={onClose} className="px-3 py-1.5 text-sm hover:bg-slate-100 rounded">취소</button>
          <button onClick={submit} disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '처리 중…' : '수락 + 배치'}
          </button>
        </div>
      </div>
    </div>
  );
}
