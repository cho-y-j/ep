import { useState } from 'react';
import { api } from '../../lib/api';
import { toast } from '../../lib/toast';
import type { PersonResponse } from '../../types/person';

/** 공급사가 작업자에게 앱 로그인 계정(아이디/비번)을 발급·재설정. PATCH /api/persons/{id} {username,password}. */
export default function PersonCredentialCard({ person, onUpdated }: {
  person: PersonResponse; onUpdated: (p: PersonResponse) => void;
}) {
  const [username, setUsername] = useState(person.username ?? '');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);

  const save = async () => {
    if (!username.trim()) { toast.error('아이디를 입력하세요'); return; }
    if (!person.username && !password) { toast.error('새 계정은 비밀번호도 입력하세요'); return; }
    setBusy(true);
    try {
      const body: Record<string, unknown> = { username: username.trim() };
      if (password) body.password = password;
      const res = await api.patch<PersonResponse>(`/api/persons/${person.id}`, body);
      onUpdated(res.data);
      setPassword('');
      toast.success('앱 로그인 계정 저장됨');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-6">
      <h3 className="text-base font-bold">앱 로그인 계정</h3>
      <p className="mt-0.5 text-xs text-slate-500">
        작업자가 앱에서 이 아이디/비밀번호로 로그인합니다.
        {person.username ? ' 비밀번호만 입력하면 재설정됩니다.' : ' 아이디·비밀번호를 발급하세요.'}
      </p>
      <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
        <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="아이디" className="input" autoComplete="off" />
        <input value={password} onChange={(e) => setPassword(e.target.value)} type="password"
          placeholder={person.username ? '새 비밀번호 (재설정 시)' : '비밀번호'} className="input" autoComplete="new-password" />
      </div>
      <div className="mt-3 flex justify-end">
        <button onClick={save} disabled={busy} className="btn-primary disabled:opacity-50">{busy ? '저장…' : '계정 저장'}</button>
      </div>
    </div>
  );
}
