import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { UserResponse } from '../../types/auth';

/** ADMIN 이 원청(CLIENT) 관제 계정을 생성 — 원청 지정 필수, 회사 소속 없음. */
export default function CreateClientUserForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [orgs, setOrgs] = useState<{ id: number; name: string; code: string }[]>([]);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [clientOrgId, setClientOrgId] = useState<number | ''>('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    api.get<{ id: number; name: string; code: string }[]>('/api/client-orgs')
      .then((r) => setOrgs(r.data))
      .catch(() => setOrgs([]));
  }, [open]);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setOk(null);
    if (clientOrgId === '') { setError('원청을 선택하세요'); return; }
    setBusy(true);
    try {
      const res = await api.post<UserResponse>('/api/users', {
        email: email.trim(),
        password,
        name: name.trim(),
        phone: phone.trim() || undefined,
        role: 'CLIENT',
        client_org_id: clientOrgId,
      });
      setOk(`원청 계정 생성 완료: ${res.data.email}`);
      setEmail(''); setPassword(''); setName(''); setPhone(''); setClientOrgId('');
      onCreated();
    } catch (err) {
      setError(err instanceof AxiosError ? err.response?.data?.message ?? '생성 실패' : '생성 실패');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card mb-4">
      <button type="button" onClick={() => setOpen((v) => !v)} className="flex w-full items-center justify-between text-left">
        <span className="text-sm font-bold text-slate-900">원청(관제) 계정 생성</span>
        <span className="text-xs text-slate-400">{open ? '닫기' : '열기'}</span>
      </button>
      {open && (
        <form onSubmit={submit} className="mt-4 space-y-3">
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="이메일" required><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required className="input" /></Field>
            <Field label="비밀번호 (8자 이상)" required><input type="text" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} className="input" /></Field>
            <Field label="이름" required><input value={name} onChange={(e) => setName(e.target.value)} required className="input" /></Field>
            <Field label="연락처"><input value={phone} onChange={(e) => setPhone(e.target.value)} className="input" /></Field>
            <Field label="원청" required>
              <select value={clientOrgId} onChange={(e) => setClientOrgId(e.target.value === '' ? '' : Number(e.target.value))} required className="input bg-white">
                <option value="">선택</option>
                {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select>
            </Field>
          </div>
          {error && <p className="text-sm text-rose-600">{error}</p>}
          {ok && <p className="text-sm text-emerald-600">{ok}</p>}
          <button type="submit" disabled={busy} className="btn-primary disabled:opacity-50">
            {busy ? '생성 중...' : '원청 계정 생성'}
          </button>
        </form>
      )}
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block text-sm">
      <span className="text-slate-600">{label}{required && <span className="text-rose-500"> *</span>}</span>
      <div className="mt-1">{children}</div>
    </label>
  );
}
