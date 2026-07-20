import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { ROLE_LABEL, type Role } from '../../types/auth';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, DataTable, EmptyState } from '../../components/ui';
import type { Column } from '../../components/ui';

type PendingUser = {
  id: number;
  name: string;
  email: string;
  phone?: string | null;
  role: Role;
  company_name?: string | null;
};

export default function SignupApprovalsPage() {
  const [users, setUsers] = useState<PendingUser[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<PendingUser[]>('/api/users/pending');
      setUsers(res.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function act(u: PendingUser, action: 'enable' | 'disable') {
    try {
      await api.patch(`/api/users/${u.id}/${action}`);
      setUsers((prev) => prev.filter((x) => x.id !== u.id));
    } catch (err) {
      if (err instanceof AxiosError) alert(err.response?.data?.message ?? '처리 실패');
    }
  }

  const columns: Column<PendingUser>[] = [
    { key: 'company', header: '회사', cell: (u) => u.company_name ?? <span className="text-slate-400">—</span> },
    { key: 'name', header: '이름', cell: (u) => u.name },
    { key: 'email', header: '이메일', cell: (u) => <span className="text-slate-600">{u.email}</span> },
    { key: 'role', header: '역할', cell: (u) => <span className="text-slate-600">{ROLE_LABEL[u.role]}</span> },
    { key: 'phone', header: '연락처', cell: (u) => u.phone ?? <span className="text-slate-400">—</span> },
    {
      key: 'actions', header: '', width: '160px',
      cell: (u) => (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => void act(u, 'enable')}
            className="rounded-lg bg-emerald-600 px-2 py-1 text-xs text-white hover:bg-emerald-700"
          >
            승인
          </button>
          <button
            type="button"
            onClick={() => void act(u, 'disable')}
            className="rounded-lg border border-rose-300 px-2 py-1 text-xs text-rose-700 hover:bg-rose-50"
          >
            거절
          </button>
        </div>
      ),
    },
  ];

  return (
    <AppShell breadcrumb={[{ label: '가입 승인' }]}>
      <div className="mx-auto max-w-5xl px-6 py-8">
        <PageHeader title="가입 승인" subtitle="회원가입을 신청한 사용자를 승인하거나 거절합니다." />
        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : users.length === 0 ? (
          <EmptyState title="대기 중인 신청이 없습니다" text="새 가입 신청이 들어오면 여기에 표시됩니다." />
        ) : (
          <DataTable columns={columns} rows={users} rowKey={(u) => u.id} />
        )}
      </div>
    </AppShell>
  );
}
