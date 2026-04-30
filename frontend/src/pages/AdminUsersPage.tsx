import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { ROLE_LABEL, type UserResponse } from '../types/auth';

export default function AdminUsersPage() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<UserResponse[]>('/api/users');
      setUsers(res.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  async function toggle(u: UserResponse) {
    setBusyId(u.id);
    try {
      const path = u.enabled ? `/api/users/${u.id}/disable` : `/api/users/${u.id}/enable`;
      await api.patch(path);
      await load();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <main className="min-h-screen bg-slate-50">
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
                  <th className="px-4 py-3 font-medium">상태</th>
                  <th className="px-4 py-3"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className="px-4 py-3">{u.email}</td>
                    <td className="px-4 py-3">{u.name}</td>
                    <td className="px-4 py-3 text-slate-600">{ROLE_LABEL[u.role]}</td>
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
                    <td className="px-4 py-3 text-right">
                      {u.role !== 'ADMIN' && (
                        <button
                          onClick={() => toggle(u)}
                          disabled={busyId === u.id}
                          className="text-brand-600 hover:underline text-sm disabled:opacity-50"
                        >
                          {u.enabled ? '비활성화' : '승인'}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
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
    </main>
  );
}
