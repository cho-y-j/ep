import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { ROLE_LABEL, type CompanyResponse, type UserResponse } from '../../types/auth';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
import UserDetailPanel from './UserDetailPanel';
import UserTable from './UserTable';
import CreateClientUserForm from './CreateClientUserForm';

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<UserResponse | null>(null);
  // 클라이언트 필터 — 로드된 목록을 좁힘.
  const [q, setQ] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const companiesById = useMemo(() => {
    const map = new Map<number, CompanyResponse>();
    companies.forEach((c) => map.set(c.id, c));
    return map;
  }, [companies]);

  // 역할 옵션 — 로드된 사용자에 실제 존재하는 역할에서만 파생.
  const roleOptions = useMemo(() => {
    const present = new Set(users.map((u) => u.role));
    return [...present].map((r) => ({ value: r, label: ROLE_LABEL[r] }));
  }, [users]);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => users.filter((u) => {
    if (roleFilter && u.role !== roleFilter) return false;
    if (statusFilter === 'enabled' && !u.enabled) return false;
    if (statusFilter === 'pending' && u.enabled) return false;
    if (qLower && !`${u.name} ${u.email}`.toLowerCase().includes(qLower)) return false;
    return true;
  }), [users, roleFilter, statusFilter, qLower]);

  const activeFilterCount = [q, roleFilter, statusFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setRoleFilter(''); setStatusFilter(''); };

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
    <AppShell breadcrumb={[{ label: '사용자 관리' }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <PageHeader
          title="사용자 관리"
          subtitle="회원가입 신청 사용자를 승인하거나, 역할/상태를 변경합니다. 행을 클릭하면 상세 패널이 열립니다."
        />

        <CreateClientUserForm onCreated={load} />

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '이름·이메일 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          {roleOptions.length > 1 && (
            <FilterSelect value={roleFilter} onChange={setRoleFilter} placeholder="역할 전체" options={roleOptions} />
          )}
          <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체"
            options={[{ value: 'enabled', label: '활성' }, { value: 'pending', label: '승인 대기' }]} />
        </FilterBar>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <UserTable users={filtered} companiesById={companiesById} onRowClick={setSelected} onChange={handleChange} />
        )}
      </div>

      <UserDetailPanel
        key={selected?.id ?? 'closed'}
        user={selected}
        company={selected?.company_id ? companiesById.get(selected.company_id) ?? null : null}
        onClose={() => setSelected(null)}
        onChange={handleChange}
      />
    </AppShell>
  );
}
