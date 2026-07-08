import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, DataTable, StatusBadge, type Column } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';

// 백엔드 Jackson SNAKE_CASE — JSON 키는 snake_case.
interface CompanyUser {
  id: number;
  email: string;
  name: string;
  phone?: string | null;
  role: string;
  company_id?: number | null;
  is_company_admin: boolean;
  enabled: boolean;
  show_in_quote?: boolean;
  quote_display_order?: number | null;
  created_at?: string | null;
}

interface CompanyProfile {
  id: number;
  name: string;
  business_number?: string | null;
  type?: string | null;
  business_address?: string | null;
  business_category?: string | null;
  business_subcategory?: string | null;
  ceo_name?: string | null;
  phone?: string | null;
  fax?: string | null;
}

/** 회사 master(= isCompanyAdmin) 가 자기 회사 하위 직원을 관리. */
export default function CompanyUsersPage() {
  const { user } = useAuth();
  const isMaster = !!user?.is_company_admin;
  const [items, setItems] = useState<CompanyUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CompanyUser | null>(null);

  useEffect(() => {
    if (!isMaster) return;
    void refresh();
  }, [isMaster]);

  const refresh = async () => {
    setLoading(true);
    setErr(null);
    try {
      const res = await api.get<CompanyUser[]>('/api/companies/me/users');
      setItems(res.data ?? []);
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '목록 조회 실패');
    } finally {
      setLoading(false);
    }
  };

  const onApprove = async (id: number) => {
    if (!confirm('이 가입 신청을 승인하시겠습니까?')) return;
    try {
      await api.post(`/api/companies/me/users/${id}/approve`);
      await refresh();
    } catch (e: any) {
      alert('승인 실패: ' + (e?.response?.data?.message || e?.message));
    }
  };

  const onDisable = async (id: number) => {
    if (!confirm('이 직원 계정을 비활성화하시겠습니까? (로그아웃 처리됨)')) return;
    try {
      await api.post(`/api/companies/me/users/${id}/disable`);
      await refresh();
    } catch (e: any) {
      alert('비활성화 실패: ' + (e?.response?.data?.message || e?.message));
    }
  };

  if (!isMaster) {
    return (
      <AppShell>
        <div className="card border-amber-200 bg-amber-50 text-sm text-amber-900 max-w-xl mx-auto">
          이 페이지는 회사 관리자만 접근할 수 있습니다.
          <div className="mt-2"><Link to="/" className="underline">대시보드로</Link></div>
        </div>
      </AppShell>
    );
  }

  const canEdit = (u: CompanyUser) => u.id === user?.id || !u.is_company_admin;
  const canDisable = (u: CompanyUser) => u.enabled && !u.is_company_admin && u.id !== user?.id;

  const columns: Column<CompanyUser>[] = [
    {
      key: 'name',
      header: '이름',
      cell: (u) => (
        <div className="flex items-center gap-2">
          <span className="font-medium text-slate-900">{u.name}</span>
          {u.is_company_admin && <StatusBadge tone="purple">관리자</StatusBadge>}
        </div>
      ),
    },
    { key: 'email', header: '이메일', cell: (u) => <span className="text-slate-600">{u.email}</span> },
    { key: 'phone', header: '전화', cell: (u) => <span className="text-slate-600">{u.phone || '-'}</span> },
    {
      key: 'status',
      header: '상태',
      cell: (u) => u.enabled ? <StatusBadge tone="success">활성</StatusBadge> : <StatusBadge tone="warning">대기</StatusBadge>,
    },
    {
      key: 'quote',
      header: '견적서 노출',
      cell: (u) => u.show_in_quote
        ? <StatusBadge tone="brand">노출 {u.quote_display_order != null ? `· ${u.quote_display_order}순위` : ''}</StatusBadge>
        : <span className="text-slate-400 text-xs">-</span>,
    },
    {
      key: 'createdAt',
      header: '가입일',
      cell: (u) => <span className="text-slate-500 text-xs">{u.created_at ? new Date(u.created_at).toLocaleDateString() : '-'}</span>,
    },
    {
      key: 'actions',
      header: <span className="text-right block">동작</span>,
      className: 'text-right',
      cell: (u) => (
        <div className="inline-flex gap-1.5">
          {!u.enabled && (
            <button type="button" onClick={() => onApprove(u.id)}
              className="text-[11px] px-2 py-1 rounded border border-emerald-300 text-emerald-700 hover:bg-emerald-50">
              승인
            </button>
          )}
          {canEdit(u) && (
            <button type="button" onClick={() => setEditTarget(u)}
              className="text-[11px] px-2 py-1 rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
              편집
            </button>
          )}
          {canDisable(u) && (
            <button type="button" onClick={() => onDisable(u.id)}
              className="text-[11px] px-2 py-1 rounded border border-rose-300 text-rose-700 hover:bg-rose-50">
              비활성화
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <AppShell>
      <div className="space-y-4">
        <PageHeader
          title="회사 · 직원 관리"
          subtitle="회사 프로필(견적서 양식용) · 직원 등록/승인/노출 설정"
          actions={
            <button type="button" onClick={() => setAddOpen(true)} className="btn-primary">
              + 직원 추가
            </button>
          }
        />

        {err && <div className="card border-rose-200 bg-rose-50 text-sm text-rose-800">{err}</div>}

        {user?.company_id && <CompanyProfileCard companyId={user.company_id} />}

        <DataTable
          columns={columns}
          rows={items}
          rowKey={(u) => u.id}
          empty={loading ? '로딩…' : '직원이 없습니다'}
        />

        {addOpen && (
          <AddUserModal onClose={() => setAddOpen(false)} onCreated={() => { setAddOpen(false); void refresh(); }} />
        )}
        {editTarget && (
          <EditUserModal
            target={editTarget}
            onClose={() => setEditTarget(null)}
            onSaved={() => { setEditTarget(null); void refresh(); }}
          />
        )}
      </div>
    </AppShell>
  );
}

/** 견적서 양식의 "공급자" 박스에 자동 채울 회사 프로필 카드 (편집 가능). */
function CompanyProfileCard({ companyId }: { companyId: number }) {
  const [data, setData] = useState<CompanyProfile | null>(null);
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<Partial<CompanyProfile>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    void load();
  }, [companyId]);

  const load = async () => {
    try {
      const res = await api.get<CompanyProfile>(`/api/companies/${companyId}`);
      setData(res.data);
      setForm(res.data);
    } catch (e: any) {
      setErr(e?.response?.data?.message || '회사 정보 조회 실패');
    }
  };

  const save = async () => {
    setBusy(true); setErr(null);
    try {
      await api.patch(`/api/companies/${companyId}/profile`, {
        business_address: form.business_address || null,
        business_category: form.business_category || null,
        business_subcategory: form.business_subcategory || null,
        ceo_name: form.ceo_name || null,
        phone: form.phone || null,
        fax: form.fax || null,
      });
      setEditing(false);
      await load();
    } catch (e: any) {
      setErr(e?.response?.data?.message || '저장 실패');
    } finally {
      setBusy(false);
    }
  };

  if (!data) return null;

  const FIELDS: Array<{ key: keyof CompanyProfile; label: string; placeholder: string }> = [
    { key: 'ceo_name', label: '대표자명', placeholder: '예: 박정규' },
    { key: 'business_address', label: '사업장주소', placeholder: '예: 경기도 용인시 처인구 …' },
    { key: 'business_category', label: '업태', placeholder: '예: 건설' },
    { key: 'business_subcategory', label: '종목', placeholder: '예: 건설기계도급및대여' },
    { key: 'phone', label: '대표 전화', placeholder: '031-282-7600' },
    { key: 'fax', label: '팩스', placeholder: '031-284-0900' },
  ];

  return (
    <div className="card space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-bold text-slate-900">회사 정보 (견적서 양식 자동 채움)</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            {data.name} · {data.business_number ?? '사업자번호 미입력'}
          </p>
        </div>
        {!editing ? (
          <button type="button" onClick={() => { setEditing(true); setForm(data); }}
                  className="text-xs px-3 py-1.5 rounded border border-slate-300 text-slate-700 hover:bg-slate-50">
            편집
          </button>
        ) : (
          <div className="flex gap-2">
            <button type="button" onClick={() => { setEditing(false); setForm(data); }}
                    className="text-xs px-3 py-1.5 rounded text-slate-600 hover:bg-slate-100">
              취소
            </button>
            <button type="button" onClick={save} disabled={busy}
                    className="text-xs px-3 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
              {busy ? '저장 중…' : '저장'}
            </button>
          </div>
        )}
      </div>
      {err && <ErrBox text={err} />}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {FIELDS.map((f) => (
          <div key={f.key as string}>
            <label className="text-xs text-slate-600 block mb-0.5">{f.label}</label>
            {editing ? (
              <input
                type="text"
                value={(form[f.key] as string) ?? ''}
                onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                placeholder={f.placeholder}
                className="input"
              />
            ) : (
              <div className="text-sm text-slate-800">
                {(data[f.key] as string) || <span className="text-slate-400">미입력</span>}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function AddUserModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    setErr(null);
    if (!email || !password || !name) { setErr('이메일/비밀번호/이름은 필수입니다'); return; }
    if (password.length < 8) { setErr('비밀번호는 8자 이상이어야 합니다'); return; }
    setBusy(true);
    try {
      await api.post('/api/companies/me/users', { email, password, name, phone });
      onCreated();
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '등록 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title="직원 추가" onClose={onClose}>
      <p className="text-xs text-slate-500">role 과 회사는 자동으로 본인 회사 기준이 적용됩니다. 즉시 활성 상태로 생성됩니다.</p>
      {err && <ErrBox text={err} />}
      <div className="space-y-2">
        <Field label="이메일 *"><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} className="input" /></Field>
        <Field label="비밀번호 * (8자 이상)"><input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} className="input font-mono" /></Field>
        <Field label="이름 *"><input type="text" value={name} onChange={(e) => setName(e.target.value)} className="input" /></Field>
        <Field label="전화"><input type="text" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="010-0000-0000" className="input" /></Field>
      </div>
      <ModalActions onClose={onClose} onSubmit={submit} busy={busy} submitLabel="등록" busyLabel="생성 중…" />
    </Modal>
  );
}

function EditUserModal({ target, onClose, onSaved }: { target: CompanyUser; onClose: () => void; onSaved: () => void }) {
  const [name, setName] = useState(target.name);
  const [phone, setPhone] = useState(target.phone ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [showInQuote, setShowInQuote] = useState(!!target.show_in_quote);
  const [quoteOrder, setQuoteOrder] = useState(
    target.quote_display_order != null ? String(target.quote_display_order) : '',
  );
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    setErr(null);
    if (!name.trim()) { setErr('이름은 필수입니다'); return; }
    if (newPassword && newPassword.length < 8) { setErr('비밀번호는 8자 이상이어야 합니다'); return; }
    setBusy(true);
    try {
      const body: Record<string, any> = {
        name,
        phone,
        show_in_quote: showInQuote,
        quote_display_order: showInQuote && quoteOrder ? Number(quoteOrder) : null,
      };
      if (newPassword) body.new_password = newPassword;
      await api.patch(`/api/companies/me/users/${target.id}`, body);
      onSaved();
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '저장 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title={`${target.name} 정보 수정`} onClose={onClose}>
      <p className="text-xs text-slate-500">
        이메일/역할/회사는 변경 불가. 비밀번호는 비워두면 그대로 유지됩니다.
      </p>
      {err && <ErrBox text={err} />}
      <div className="space-y-2">
        <Field label="이메일 (변경 불가)">
          <input type="email" value={target.email} disabled className="input bg-slate-50 text-slate-400" />
        </Field>
        <Field label="이름 *">
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} className="input" />
        </Field>
        <Field label="전화">
          <input type="text" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="010-0000-0000" className="input" />
        </Field>

        {/* 견적서 양식 우측 담당자 노출 토글 */}
        <div className="rounded-md border border-slate-200 bg-slate-50 p-2 space-y-2">
          <label className="flex items-center gap-2 text-xs text-slate-700">
            <input type="checkbox" checked={showInQuote} onChange={(e) => setShowInQuote(e.target.checked)} />
            <span>견적서 양식 우측 "담당자" 목록에 노출 (최대 4명)</span>
          </label>
          {showInQuote && (
            <Field label="노출 순서 (1~4 권장, 비우면 가장 마지막)">
              <input type="number" min={1} max={99} value={quoteOrder}
                     onChange={(e) => setQuoteOrder(e.target.value)}
                     placeholder="1, 2, 3, 4..." className="input" />
            </Field>
          )}
        </div>

        <Field label="새 비밀번호 (변경 시에만 입력, 8자 이상)">
          <input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="비워두면 변경 안 됨"
            autoComplete="new-password"
            className="input font-mono"
          />
        </Field>
        {newPassword && (
          <div className="rounded-md border border-amber-200 bg-amber-50 p-2 text-[11px] text-amber-800">
            저장 시 직원의 기존 로그인 세션이 무효화됩니다. 새 비밀번호를 직접 전달하세요.
          </div>
        )}
      </div>
      <ModalActions onClose={onClose} onSubmit={submit} busy={busy} submitLabel="저장" busyLabel="저장 중…" />
    </Modal>
  );
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-5 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900">{title}</h3>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

function ModalActions({ onClose, onSubmit, busy, submitLabel, busyLabel }: {
  onClose: () => void;
  onSubmit: () => void;
  busy: boolean;
  submitLabel: string;
  busyLabel: string;
}) {
  return (
    <div className="flex items-center justify-end gap-2 pt-2">
      <button type="button" onClick={onClose} className="btn-ghost">취소</button>
      <button type="button" onClick={onSubmit} disabled={busy} className="btn-primary">
        {busy ? busyLabel : submitLabel}
      </button>
    </div>
  );
}

function ErrBox({ text }: { text: string }) {
  return <div className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800">{text}</div>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-slate-600 block mb-0.5">{label}</label>
      {children}
    </div>
  );
}
