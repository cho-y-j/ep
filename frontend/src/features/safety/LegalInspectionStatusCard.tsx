import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

/** S2′ 법정점검 현황 — BP/ADMIN 안전 허브. 현장 오늘 배치 장비 중 완료/대상 + 미점검 목록. */
type BpStatus = {
  target: number;
  done: number;
  pending: Array<{ equipment_id: number; label: string }>;
};

export default function LegalInspectionStatusCard({ siteId }: { siteId: number }) {
  const [status, setStatus] = useState<BpStatus | null>(null);

  useEffect(() => {
    let live = true;
    api.get<BpStatus>('/api/legal-inspections/bp-status', { params: { siteId } })
      .then((r) => { if (live) setStatus(r.data); })
      .catch(() => { if (live) setStatus(null); });
    return () => { live = false; };
  }, [siteId]);

  if (!status) return null;
  const pct = status.target > 0 ? Math.round((status.done / status.target) * 100) : 0;

  return (
    <div className="card border-l-4 border-indigo-500">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-sm font-bold text-slate-900">법정점검 (안전점검원 NFC)</h2>
          <p className="text-xs text-slate-500">오늘 배치 장비 법정점검 현황 · 조종원 일일점검과 별도 트랙</p>
        </div>
        <span className="text-2xl font-bold tabular-nums text-slate-800">
          {status.done}<span className="text-sm font-medium text-slate-400"> / {status.target}대</span>
        </span>
      </div>
      {status.target > 0 && (
        <div className="mt-2 h-2 overflow-hidden rounded bg-slate-100">
          <div className={`h-full rounded ${pct >= 100 ? 'bg-emerald-500' : 'bg-indigo-500'}`} style={{ width: `${pct}%` }} />
        </div>
      )}
      {status.pending.length > 0 && (
        <div className="mt-3 border-t border-slate-100 pt-2">
          <div className="mb-1 text-[11px] font-semibold text-slate-500">미점검 장비 ({status.pending.length})</div>
          <div className="flex flex-wrap gap-1.5">
            {status.pending.map((p) => (
              <span key={p.equipment_id} className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">
                {p.label}
              </span>
            ))}
          </div>
        </div>
      )}
      {status.target === 0 && (
        <p className="mt-2 text-xs text-slate-400">배치된 점검 대상 장비가 없습니다.</p>
      )}
    </div>
  );
}
