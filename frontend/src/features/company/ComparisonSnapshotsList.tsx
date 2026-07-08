import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../lib/api';

type SnapshotEntry = {
  proposalId: number;
  supplierId: number;
  supplierName: string;
  status: string;
  dailyRate?: number | null;
  monthlyRate?: number | null;
  note?: string | null;
  submittedAt?: string | null;
};

type Snapshot = {
  id: number;
  quotation_request_id: number;
  selected_proposal_id?: number | null;
  selected_at: string;
  snapshot_json: string;
  selection_reason?: string | null;
};

export default function ComparisonSnapshotsList({ companyId }: { companyId: number }) {
  const [list, setList] = useState<Snapshot[]>([]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    api.get<Snapshot[]>(`/api/companies/${companyId}/comparison-snapshots`)
      .then((res) => setList(res.data))
      .catch(() => setList([]))
      .finally(() => setLoading(false));
  }, [companyId]);

  const toggle = (id: number) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  if (loading) return <p className="text-sm text-slate-400 p-4">로딩...</p>;
  if (list.length === 0) {
    return <p className="text-sm text-slate-400 p-4">아직 선정한 견적이 없습니다.</p>;
  }

  return (
    <div className="space-y-2">
      {list.map((s) => {
        const isOpen = expanded.has(s.id);
        let entries: SnapshotEntry[] = [];
        try {
          const parsed = JSON.parse(s.snapshot_json);
          entries = (parsed?.entries ?? []) as SnapshotEntry[];
        } catch {/* ignore */}
        const selected = entries.find((e) => e.proposalId === s.selected_proposal_id);
        const others = entries.filter((e) => e.proposalId !== s.selected_proposal_id);
        return (
          <div key={s.id} className="rounded-lg border border-slate-200 bg-white">
            <button onClick={() => toggle(s.id)}
                    className="w-full flex items-center justify-between px-4 py-3 hover:bg-slate-50">
              <div className="text-left">
                <div className="text-sm font-semibold text-slate-900">
                  견적 <Link to={`/quotations/${s.quotation_request_id}`} onClick={(e) => e.stopPropagation()}
                              className="text-brand-700 hover:underline">#{s.quotation_request_id}</Link>
                  <span className="ml-2 text-xs text-slate-500">— 비교 {entries.length}건</span>
                </div>
                <div className="text-xs text-slate-500 mt-0.5">
                  선정: {selected?.supplierName ?? '?'} · {new Date(s.selected_at).toLocaleString('ko-KR')}
                </div>
              </div>
              <span className="text-slate-400">{isOpen ? '▾' : '▸'}</span>
            </button>
            {isOpen && (
              <div className="px-4 py-3 border-t border-slate-100 space-y-2">
                {selected && (
                  <div className="rounded border border-emerald-300 bg-emerald-50/50 px-3 py-2">
                    <div className="text-xs font-bold text-emerald-700 mb-1">선정</div>
                    <SnapshotEntryRow e={selected} />
                  </div>
                )}
                {others.length > 0 && (
                  <div>
                    <div className="text-xs font-semibold text-slate-500 mb-1">비교 대상 {others.length}건</div>
                    <div className="space-y-1.5">
                      {others.map((e) => (
                        <div key={e.proposalId} className="rounded border border-slate-200 px-3 py-2">
                          <SnapshotEntryRow e={e} />
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {s.selection_reason && (
                  <div className="text-xs text-slate-600 mt-2">사유: {s.selection_reason}</div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

function SnapshotEntryRow({ e }: { e: SnapshotEntry }) {
  return (
    <div className="flex items-center justify-between gap-4 text-sm">
      <div className="flex-1 min-w-0">
        <div className="font-medium text-slate-900 truncate">{e.supplierName}</div>
        {e.note && <div className="text-xs text-slate-500 mt-0.5 truncate">{e.note}</div>}
      </div>
      <div className="text-right text-xs font-mono shrink-0">
        {e.dailyRate != null ? <div>일대 {e.dailyRate.toLocaleString()}원</div> : null}
        {e.monthlyRate != null ? <div>월대 {e.monthlyRate.toLocaleString()}원</div> : null}
      </div>
      <span className={`text-[10px] px-1.5 py-0.5 rounded shrink-0 ${
        e.status === 'FINAL_ACCEPTED' ? 'bg-emerald-100 text-emerald-700' :
        e.status === 'REJECTED' ? 'bg-rose-100 text-rose-700' :
        'bg-slate-100 text-slate-600'
      }`}>{e.status}</span>
    </div>
  );
}
