import { useEffect, useMemo, useState } from 'react';
import { api } from '../lib/api';
import { ROLE_LABEL, type CompanyResponse, type UserResponse } from '../types/auth';
import UserDetailPanel from '../components/UserDetailPanel';
import AppHeader from '../components/AppHeader';

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<UserResponse | null>(null);

  const companiesById = useMemo(() => {
    const map = new Map<number, CompanyResponse>();
    companies.forEach((c) => map.set(c.id, c));
    return map;
  }, [companies]);

  async function load() {
    setLoading(true);
    try {
      const [usersRes, companiesRes] = await Promise.all([
        api.get<UserResponse[]>('/api/users'),
        api.get<CompanyResponse[]>('/api/companies'),
      ]);
      setUsers(usersRes.data);
      setCompanies(companiesRes.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  function handleChange(updated: UserResponse) {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
    setSelected(updated);
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-5xl mx-auto px-6 py-8">
        <h1 className="text-2xl font-bold mb-6">사용자 관리</h1>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
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
                    <tr
                      key={u.id}
                      onClick={() => setSelected(u)}
                      className="cursor-pointer hover:bg-slate-50"
                    >
                      <td className="px-4 py-3">{u.email}</td>
                      <td className="px-4 py-3">{u.name}</td>
                      <td className="px-4 py-3 text-slate-600">{ROLE_LABEL[u.role]}</td>
                      <td className="px-4 py-3 text-slate-600">
                        {company ? (
                          <>
                            {company.name}
                            {u.is_company_admin && (
                              <span className="ml-2 inline-flex px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 text-xs">관리자</span>
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
                    <td colSpan={5} className="px-4 py-8 text-center text-slate-400">
                      사용자 없음
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <UserDetailPanel
        user={selected}
        company={selected?.company_id ? companiesById.get(selected.company_id) ?? null : null}
        onClose={() => setSelected(null)}
        onChange={handleChange}
      />
    </main>
  );
}
