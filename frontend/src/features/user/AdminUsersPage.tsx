import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import type { CompanyResponse, UserResponse } from '../../types/auth';
import AppHeader from '../../components/AppHeader';
import UserDetailPanel from './UserDetailPanel';
import UserTable from './UserTable';

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
          <UserTable users={users} companiesById={companiesById} onRowClick={setSelected} />
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
