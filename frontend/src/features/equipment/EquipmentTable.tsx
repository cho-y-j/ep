import { EQUIPMENT_CATEGORY_LABEL, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

type Props = {
  equipment: EquipmentResponse[];
  companiesById: Map<number, CompanyResponse>;
  showSupplierColumn: boolean;
  onRowClick: (e: EquipmentResponse) => void;
};

export default function EquipmentTable({ equipment, companiesById, showSupplierColumn, onRowClick }: Props) {
  return (
    <div className="card overflow-x-auto p-0">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3 font-medium">차량번호</th>
            <th className="px-4 py-3 font-medium">분류</th>
            <th className="px-4 py-3 font-medium">제조사 / 모델</th>
            <th className="px-4 py-3 font-medium">년도</th>
            {showSupplierColumn && <th className="px-4 py-3 font-medium">공급사</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {equipment.map((e) => {
            const supplier = companiesById.get(e.supplier_id);
            return (
              <tr key={e.id} onClick={() => onRowClick(e)} className="cursor-pointer hover:bg-slate-50">
                <td className="px-4 py-3 font-medium">
                  <span>{e.vehicle_no || <span className="text-slate-400">—</span>}</span>
                  {e.expiring_count > 0 && (
                    <span
                      className="ml-2 inline-flex px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 text-xs font-normal"
                      title={`만료 임박 서류 ${e.expiring_count}건`}
                    >
                      만료 {e.expiring_count}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-slate-600">{EQUIPMENT_CATEGORY_LABEL[e.category]}</td>
                <td className="px-4 py-3 text-slate-600">
                  {[e.manufacturer, e.model].filter(Boolean).join(' / ') || <span className="text-slate-400">—</span>}
                </td>
                <td className="px-4 py-3 text-slate-600">
                  {e.year ?? <span className="text-slate-400">—</span>}
                </td>
                {showSupplierColumn && (
                  <td className="px-4 py-3 text-slate-500">{supplier?.name ?? `id=${e.supplier_id}`}</td>
                )}
              </tr>
            );
          })}
          {equipment.length === 0 && (
            <tr>
              <td colSpan={showSupplierColumn ? 5 : 4} className="px-4 py-8 text-center text-slate-400">
                장비 없음
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
