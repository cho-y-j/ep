import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';
import Avatar from '../../components/Avatar';

type Props = {
  equipment: EquipmentResponse[];
  companiesById: Map<number, CompanyResponse>;
  showSupplierColumn: boolean;
  onRowClick: (e: EquipmentResponse) => void;
};

function statusOf(e: EquipmentResponse): { label: string; cls: string; siteDot: 'green' | 'gray' | 'red' } {
  if ((e.utilization_pct ?? 0) === 0 && e.expiring_count === 0) {
    return { label: '미사용', cls: 'bg-slate-100 text-slate-600', siteDot: 'gray' };
  }
  if (e.expiring_count > 0) {
    return { label: '점검 필요', cls: 'bg-amber-100 text-amber-700', siteDot: 'red' };
  }
  return { label: '가동 중', cls: 'bg-emerald-100 text-emerald-700', siteDot: 'green' };
}

export default function EquipmentTable({ equipment, companiesById, showSupplierColumn, onRowClick }: Props) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3 w-10">
              <input type="checkbox" className="rounded border-slate-300" />
            </th>
            <th className="px-4 py-3 font-medium">장비명 / 장비코드</th>
            <th className="px-4 py-3 font-medium">종류</th>
            <th className="px-4 py-3 font-medium">현장(위치)</th>
            <th className="px-4 py-3 font-medium">상태</th>
            <th className="px-4 py-3 font-medium">가동률</th>
            <th className="px-4 py-3 font-medium">최근 점검 / 다음 점검</th>
            <th className="px-4 py-3 font-medium text-center">첨부 서류</th>
            {showSupplierColumn && <th className="px-4 py-3 font-medium">공급사</th>}
            <th className="px-4 py-3 w-10"></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {equipment.map((e) => {
            const supplier = companiesById.get(e.supplier_id);
            const st = statusOf(e);
            const util = e.utilization_pct ?? 0;
            return (
              <tr key={e.id} onClick={() => onRowClick(e)} className="cursor-pointer hover:bg-slate-50">
                <td className="px-4 py-3" onClick={(ev) => ev.stopPropagation()}>
                  <input type="checkbox" className="rounded border-slate-300" />
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <Avatar
                      fetchUrl={e.has_photo ? `/api/equipment/${e.id}/photo` : undefined}
                      fallbackText={e.vehicle_no || EQUIPMENT_CATEGORY_LABEL[e.category]}
                      alt={e.vehicle_no ?? ''}
                      size={48}
                      rounded="lg"
                    />
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-900 truncate">
                        {e.vehicle_no || e.model || EQUIPMENT_CATEGORY_LABEL[e.category]}
                      </div>
                      <div className="text-xs text-slate-500 mt-0.5">{e.code ?? '-'}</div>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="text-slate-900">{EQUIPMENT_CATEGORY_LABEL[e.category]}</div>
                  <div className="text-xs text-slate-500 mt-0.5">
                    {e.bucket_capacity ? `버킷 ${e.bucket_capacity}㎥` : (e.model ?? '')}
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="text-slate-900">서울 A현장</div>
                  <div className="mt-0.5 inline-flex items-center gap-1.5 text-xs text-slate-500">
                    <span className={`inline-block w-1.5 h-1.5 rounded-full ${st.siteDot === 'green' ? 'bg-emerald-500' : st.siteDot === 'red' ? 'bg-rose-500' : 'bg-slate-300'}`} />
                    위치 추적 중
                  </div>
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
                <td className="px-4 py-3 text-slate-700">
                  <div>2026.04.10</div>
                  <div className="text-xs text-slate-500 mt-0.5">2026.07.10</div>
                </td>
                <td className="px-4 py-3 text-center">
                  <span className="inline-flex min-w-[24px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 text-sm font-semibold">
                    {/* 첨부 서류 카운트는 추후 백엔드 추가 — 우선 - 표시 */}
                    -
                  </span>
                </td>
                {showSupplierColumn && (
                  <td className="px-4 py-3 text-slate-500">{supplier?.name ?? `id=${e.supplier_id}`}</td>
                )}
                <td className="px-4 py-3 text-slate-400 text-center">⋮</td>
              </tr>
            );
          })}
          {equipment.length === 0 && (
            <tr>
              <td colSpan={showSupplierColumn ? 10 : 9} className="px-4 py-12 text-center text-slate-400">
                장비 없음
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
