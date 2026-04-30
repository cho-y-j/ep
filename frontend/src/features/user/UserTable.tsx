import { ROLE_LABEL, type CompanyResponse, type UserResponse } from '../../types/auth';

type Props = {
  users: UserResponse[];
  companiesById: Map<number, CompanyResponse>;
  onRowClick: (u: UserResponse) => void;
};

export default function UserTable({ users, companiesById, onRowClick }: Props) {
  return (
    <div className="card overflow-hidden p-0">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 border-b border-slate-200">
          <tr className="text-left text-slate-500">
            <th className="px-4 py-3 font-medium">이메일</th>
            <th className="px-4 py-3 font-medium">이름</th>
            <th className="px-4 py-3 font-medium">역할</th>
            <th className="px-4 py-3 font-medium">소속 회사</th>
            <th className="px-4 py-3 font-medium">상태</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {users.map((u) => {
            const company = u.company_id ? companiesById.get(u.company_id) : null;
            return (
              <tr key={u.id} onClick={() => onRowClick(u)} className="cursor-pointer hover:bg-slate-50">
                <td className="px-4 py-3">{u.email}</td>
                <td className="px-4 py-3">{u.name}</td>
                <td className="px-4 py-3 text-slate-600">{ROLE_LABEL[u.role]}</td>
                <td className="px-4 py-3 text-slate-600">
                  {company ? (
                    <>
                      {company.name}
                      {u.is_company_admin && (
                        <span className="ml-2 inline-flex px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 text-xs">
                          관리자
                        </span>
                      )}
                    </>
                  ) : (
                    <span className="text-slate-400">—</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  {u.enabled ? (
                    <span className="inline-flex px-2 py-0.5 rounded bg-green-100 text-green-700 text-xs font-medium">
                      활성
                    </span>
                  ) : (
                    <span className="inline-flex px-2 py-0.5 rounded bg-amber-100 text-amber-700 text-xs font-medium">
                      승인 대기
                    </span>
                  )}
                </td>
              </tr>
            );
          })}
          {users.length === 0 && (
            <tr>
              <td colSpan={5} className="px-4 py-8 text-center text-slate-400">사용자 없음</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
