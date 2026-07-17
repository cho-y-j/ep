import { useMemo, useState } from 'react';
import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';
import Avatar from '../../components/Avatar';

type Props = {
  equipment: EquipmentResponse[];
  companiesById: Map<number, CompanyResponse>;
  showSupplierColumn: boolean;
  onRowClick: (e: EquipmentResponse) => void;
  /** 로그인 회사 id — 공급사 컬럼이 없을 때 하위공급사 소유 장비에 소속 라벨을 붙이는 용도. */
  selfCompanyId?: number | null;
};

function statusOf(e: EquipmentResponse): { label: string; cls: string; rank: number } {
  if ((e.utilization_pct ?? 0) === 0 && e.expiring_count === 0) {
    return { label: '미사용', cls: 'bg-slate-100 text-slate-600', rank: 2 };
  }
  if (e.expiring_count > 0) {
    return { label: '점검 필요', cls: 'bg-amber-100 text-amber-700', rank: 1 };
  }
  return { label: '가동 중', cls: 'bg-emerald-100 text-emerald-700', rank: 0 };
}

type SortKey = 'name' | 'category' | 'site' | 'status' | 'util' | 'expiring' | 'supplier';
type SortDir = 'asc' | 'desc';

function compareBy(
  a: EquipmentResponse, b: EquipmentResponse, key: SortKey,
  companiesById: Map<number, CompanyResponse>
): number {
  const cmp = (x: string | number | null | undefined, y: string | number | null | undefined): number => {
    if (x == null && y == null) return 0;
    if (x == null) return 1;
    if (y == null) return -1;
    if (typeof x === 'number' && typeof y === 'number') return x - y;
    return String(x).localeCompare(String(y), 'ko');
  };
  switch (key) {
    case 'name': return cmp(a.vehicle_no || a.model || '', b.vehicle_no || b.model || '');
    case 'category': return cmp(equipmentCategoryLabel(a.category), equipmentCategoryLabel(b.category));
    case 'site': return cmp(a.current_site_name ?? '', b.current_site_name ?? '');
    case 'status': return cmp(statusOf(a).rank, statusOf(b).rank);
    case 'util': return cmp(a.utilization_pct ?? 0, b.utilization_pct ?? 0);
    case 'expiring': return cmp(a.expiring_count ?? 0, b.expiring_count ?? 0);
    case 'supplier': return cmp(
      a.supplier_name ?? companiesById.get(a.supplier_id)?.name ?? '',
      b.supplier_name ?? companiesById.get(b.supplier_id)?.name ?? ''
    );
  }
}

export default function EquipmentTable({ equipment, companiesById, showSupplierColumn, onRowClick, selfCompanyId }: Props) {
  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const sortedEquipment = useMemo(() => {
    if (!sortKey) return equipment;
    const arr = equipment.slice();
    arr.sort((a, b) => {
      const r = compareBy(a, b, sortKey, companiesById);
      return sortDir === 'asc' ? r : -r;
    });
    return arr;
  }, [equipment, sortKey, sortDir, companiesById]);

  const toggleSort = (key: SortKey) => {
    if (sortKey !== key) { setSortKey(key); setSortDir('asc'); return; }
    if (sortDir === 'asc') { setSortDir('desc'); return; }
    setSortKey(null);
  };

  const SortHeader = ({ k, label }: { k: SortKey; label: string }) => {
    const active = sortKey === k;
    const arrow = !active ? '↕' : sortDir === 'asc' ? '↑' : '↓';
    return (
      <button type="button" onClick={() => toggleSort(k)}
        className={`inline-flex items-center gap-1 font-medium ${active ? 'text-slate-900' : 'text-slate-500 hover:text-slate-700'}`}>
        <span>{label}</span>
        <span className={`text-[10px] ${active ? 'text-brand-600' : 'text-slate-300'}`}>{arrow}</span>
      </button>
    );
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3"><SortHeader k="name" label="장비명 / 장비코드" /></th>
            <th className="px-4 py-3"><SortHeader k="category" label="종류" /></th>
            <th className="px-4 py-3"><SortHeader k="site" label="현장(위치)" /></th>
            <th className="px-4 py-3"><SortHeader k="status" label="상태" /></th>
            <th className="px-4 py-3"><SortHeader k="util" label="가동률" /></th>
            <th className="px-4 py-3 text-center"><SortHeader k="expiring" label="서류 위험" /></th>
            {showSupplierColumn && <th className="px-4 py-3"><SortHeader k="supplier" label="공급사" /></th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {sortedEquipment.map((e) => {
            const supplier = companiesById.get(e.supplier_id);
            const st = statusOf(e);
            const util = e.utilization_pct ?? 0;
            return (
              <tr key={e.id} onClick={() => onRowClick(e)} className="cursor-pointer hover:bg-slate-50">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <Avatar
                      fetchUrl={e.has_photo ? `/api/equipment/${e.id}/photo` : undefined}
                      fallbackText={e.vehicle_no || equipmentCategoryLabel(e.category)}
                      alt={e.vehicle_no ?? ''}
                      size={48}
                      rounded="lg"
                    />
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-900 truncate">
                        {e.vehicle_no || e.model || equipmentCategoryLabel(e.category)}
                      </div>
                      <div className="text-xs text-slate-500 mt-0.5">{e.code ?? '-'}</div>
                      {!showSupplierColumn && selfCompanyId != null && e.supplier_id !== selfCompanyId && e.supplier_name && (
                        <span className="inline-flex mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-100 text-amber-800 border border-amber-200">
                          소속: {e.supplier_name}
                        </span>
                      )}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="text-slate-900">{equipmentCategoryLabel(e.category)}</div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    {e.bucket_capacity ? `버킷 ${e.bucket_capacity}㎥` : (e.model ?? '')}
                  </div>
                </td>
                <td className="px-4 py-3">
                  {e.current_site_id ? (
                    <>
                      <div className="text-slate-900 truncate">{e.current_site_name ?? `현장 #${e.current_site_id}`}</div>
                      <div className="mt-0.5 inline-flex items-center gap-1.5 text-xs text-slate-500">
                        <span className="inline-block w-1.5 h-1.5 rounded-full bg-blue-500" />
                        배치 중
                      </div>
                    </>
                  ) : (
                    <span className="text-xs text-slate-400">미배치</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-1 rounded-full text-xs font-semibold ${st.cls}`}>{st.label}</span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-slate-900 w-10 text-right">{util}%</span>
                    <div className="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden min-w-[80px]">
                      <div
                        className={`h-full ${util === 0 ? 'bg-slate-300' : util >= 70 ? 'bg-emerald-500' : util >= 40 ? 'bg-amber-500' : 'bg-rose-500'}`}
                        style={{ width: `${util}%` }}
                      />
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 text-center">
                  {e.expiring_count > 0 ? (
                    <span className="inline-flex min-w-[24px] px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-xs font-semibold">
                      {e.expiring_count}
                    </span>
                  ) : (
                    <span className="text-xs text-slate-400">-</span>
                  )}
                </td>
                {showSupplierColumn && (
                  <td className="px-4 py-3 text-slate-500">{e.supplier_name ?? supplier?.name ?? `id=${e.supplier_id}`}</td>
                )}
              </tr>
            );
          })}
          {equipment.length === 0 && (
            <tr>
              <td colSpan={showSupplierColumn ? 7 : 6} className="px-4 py-12 text-center text-slate-400">
                장비 없음
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
