import { PERSON_ROLE_LABEL, type PersonResponse } from '../../types/person';
import type { CompanyResponse } from '../../types/auth';
import Avatar from '../../components/Avatar';

type Props = {
  persons: PersonResponse[];
  companiesById: Map<number, CompanyResponse>;
  showSupplierColumn: boolean;
  onRowClick: (p: PersonResponse) => void;
};

export default function PersonTable({ persons, companiesById, showSupplierColumn, onRowClick }: Props) {
  return (
    <div className="card overflow-hidden p-0">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3 font-medium">이름</th>
            <th className="px-4 py-3 font-medium">생년월일</th>
            <th className="px-4 py-3 font-medium">휴대폰</th>
            <th className="px-4 py-3 font-medium">역할</th>
            {showSupplierColumn && <th className="px-4 py-3 font-medium">공급사</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {persons.map((p) => {
            const supplier = companiesById.get(p.supplier_id);
            return (
              <tr key={p.id} onClick={() => onRowClick(p)} className="cursor-pointer hover:bg-slate-50">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <Avatar
                      fetchUrl={p.has_photo ? `/api/persons/${p.id}/photo` : undefined}
                      fallbackText={p.name}
                      alt={p.name}
                      size={36}
                    />
                    <span className="font-medium">{p.name}</span>
                  </div>
                </td>
                <td className="px-4 py-3 text-slate-600">{p.birth ?? <span className="text-slate-400">—</span>}</td>
                <td className="px-4 py-3 text-slate-600">{p.phone ?? <span className="text-slate-400">—</span>}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {p.roles.map((r) => (
                      <span key={r} className="inline-flex px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs">
                        {PERSON_ROLE_LABEL[r]}
                      </span>
                    ))}
                  </div>
                </td>
                {showSupplierColumn && (
                  <td className="px-4 py-3 text-slate-500">{supplier?.name ?? `id=${p.supplier_id}`}</td>
                )}
              </tr>
            );
          })}
          {persons.length === 0 && (
            <tr>
              <td colSpan={showSupplierColumn ? 5 : 4} className="px-4 py-8 text-center text-slate-400">
                인원 없음
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
