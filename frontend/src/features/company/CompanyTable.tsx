import { COMPANY_TYPE_LABEL, type CompanyResponse } from '../../types/auth';

type Props = {
  companies: CompanyResponse[];
  onRowClick: (c: CompanyResponse) => void;
};

export default function CompanyTable({ companies, onRowClick }: Props) {
  return (
    <div className="card overflow-x-auto p-0">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3 font-medium">회사명</th>
            <th className="px-4 py-3 font-medium">사업자번호</th>
            <th className="px-4 py-3 font-medium">유형</th>
            <th className="px-4 py-3 font-medium">등록일</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {companies.map((c) => (
            <tr key={c.id} onClick={() => onRowClick(c)} className="cursor-pointer hover:bg-slate-50">
              <td className="px-4 py-3 font-medium">{c.name}</td>
              <td className="px-4 py-3">{c.business_number}</td>
              <td className="px-4 py-3 text-slate-600">{COMPANY_TYPE_LABEL[c.type]}</td>
              <td className="px-4 py-3 text-slate-500">
                {new Date(c.created_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}
              </td>
            </tr>
          ))}
          {companies.length === 0 && (
            <tr>
              <td colSpan={4} className="px-4 py-8 text-center text-slate-400">회사 없음</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
