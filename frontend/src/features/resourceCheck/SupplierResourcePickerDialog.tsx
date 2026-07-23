import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import type { ResourceOwnerType } from '../../types/resourceCheck';

export type PickedResource = {
  ownerType: ResourceOwnerType;
  ownerId: number;
  ownerLabel: string;
  supplierCompanyId: number;
  supplierCompanyName?: string | null;
};

type PickRow = PickedResource & { key: string };

type Props = {
  open: boolean;
  /** 장비공급사만 장비 목록 포함(인력공급사는 인원만). */
  includeEquipment: boolean;
  onClose: () => void;
  onPick: (target: PickedResource) => void;
};

/**
 * 공급사 "검사 통보" 대상 자원 선택 — /api/equipment·/api/persons 는 V77 로
 * 본인 + 직속 자식(협력사) 자원까지 반환하므로 그대로 사용.
 */
export default function SupplierResourcePickerDialog({ open, includeEquipment, onClose, onPick }: Props) {
  const [rows, setRows] = useState<PickRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [q, setQ] = useState('');

  useEffect(() => {
    if (!open) return;
    let alive = true;
    setLoading(true);
    setQ('');
    const loads: Promise<PickRow[]>[] = [];
    if (includeEquipment) {
      loads.push(api.get<any[]>('/api/equipment').then((r) => r.data.map((e) => ({
        key: `EQUIPMENT:${e.id}`,
        ownerType: 'EQUIPMENT' as const,
        ownerId: e.id as number,
        ownerLabel: (e.vehicle_no || e.model || `장비 #${e.id}`) as string,
        supplierCompanyId: e.supplier_id as number,
        supplierCompanyName: (e.supplier_name ?? null) as string | null,
      }))).catch(() => []));
    }
    loads.push(api.get<any[]>('/api/persons').then((r) => r.data.map((p) => ({
      key: `PERSON:${p.id}`,
      ownerType: 'PERSON' as const,
      ownerId: p.id as number,
      ownerLabel: (p.name || `인원 #${p.id}`) as string,
      supplierCompanyId: p.supplier_id as number,
      supplierCompanyName: (p.supplier_name ?? null) as string | null,
    }))).catch(() => []));
    void Promise.all(loads).then((lists) => {
      if (!alive) return;
      setRows(lists.flat());
      setLoading(false);
    });
    return () => { alive = false; };
  }, [open, includeEquipment]);

  if (!open) return null;

  const qLower = q.trim().toLowerCase();
  const filtered = qLower
    ? rows.filter((r) => `${r.ownerLabel} ${r.supplierCompanyName ?? ''}`.toLowerCase().includes(qLower))
    : rows;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-5 py-3 border-b">
          <h3 className="font-bold text-slate-900">검사 통보 대상 선택</h3>
          <p className="text-xs text-slate-500 mt-0.5">자기 회사(협력사 포함) 장비·인원에 검사 날짜를 통보합니다.</p>
        </div>
        <div className="px-5 py-3 border-b">
          <input
            type="text"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="차량번호·이름 검색"
            autoFocus
            className="w-full px-2.5 py-1.5 text-sm border border-slate-300 rounded"
          />
        </div>
        <div className="max-h-[50vh] overflow-y-auto px-2 py-2">
          {loading ? (
            <div className="p-6 text-center text-sm text-slate-400">불러오는 중…</div>
          ) : filtered.length === 0 ? (
            <div className="p-6 text-center text-sm text-slate-400">
              {rows.length === 0 ? '선택할 자원이 없습니다.' : '조건에 맞는 자원이 없습니다.'}
            </div>
          ) : (
            filtered.map((r) => (
              <button
                key={r.key}
                type="button"
                onClick={() => onPick(r)}
                className="w-full flex items-center justify-between gap-2 px-3 py-2 rounded-lg text-left hover:bg-slate-50"
              >
                <span className="min-w-0">
                  <span className="block text-sm font-medium text-slate-900 truncate">{r.ownerLabel}</span>
                  {r.supplierCompanyName && (
                    <span className="block text-[11px] text-slate-500 truncate">{r.supplierCompanyName}</span>
                  )}
                </span>
                <span className={`shrink-0 px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${
                  r.ownerType === 'EQUIPMENT' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                }`}>
                  {r.ownerType === 'EQUIPMENT' ? '장비' : '인원'}
                </span>
              </button>
            ))
          )}
        </div>
        <div className="px-5 py-3 border-t flex justify-end">
          <button onClick={onClose} className="px-3 py-1.5 rounded text-sm text-slate-700 hover:bg-slate-100">
            취소
          </button>
        </div>
      </div>
    </div>
  );
}
