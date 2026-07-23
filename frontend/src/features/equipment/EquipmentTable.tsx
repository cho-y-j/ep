import { equipmentCategoryLabel, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';
import Avatar from '../../components/Avatar';
import { useTableSort } from '../../components/ui';

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

export default function EquipmentTable({ equipment, companiesById, showSupplierColumn, onRowClick, selfCompanyId }: Props) {
  const sort = useTableSort<SortKey>();
  const sortedEquipment = sort.apply(equipment, (e, key) => {
    switch (key) {
      case 'name': return e.vehicle_no || e.model || '';
      case 'category': return equipmentCategoryLabel(e.category);
      case 'site': return e.current_site_name ?? '';
      case 'status': return statusOf(e).rank;
      case 'util': return e.utilization_pct ?? 0;
      case 'expiring': return e.expiring_count ?? 0;
      case 'supplier': return e.supplier_name ?? companiesById.get(e.supplier_id)?.name ?? '';
    }
  });

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3">{sort.header('name', '장비명 / 장비코드')}</th>
            <th className="px-4 py-3">{sort.header('category', '종류')}</th>
            <th className="px-4 py-3">{sort.header('site', '현장(위치)')}</th>
            <th className="px-4 py-3">{sort.header('status', '상태')}</th>
            <th className="px-4 py-3">{sort.header('util', '가동률')}</th>
            <th className="px-4 py-3 text-center">{sort.header('expiring', '서류 위험')}</th>
            {showSupplierColumn && <th className="px-4 py-3">{sort.header('supplier', '공급사')}</th>}
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
