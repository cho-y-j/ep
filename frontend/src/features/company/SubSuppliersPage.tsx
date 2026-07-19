import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, DataTable, StatusBadge, type Column } from '../../components/ui';
import BusinessNumberInput from '../../components/forms/BusinessNumberInput';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import { COMPANY_TYPE_LABEL, type CompanyResponse, type UserResponse } from '../../types/auth';

type SubType = 'EQUIPMENT' | 'MANPOWER';

/** B4: 자식 공급사별 롤업 요약 (GET /api/companies/children/rollup). */
type ChildRollup = {
  child_company_id: number;
  child_company_name: string;
  readiness_ready: number;
  readiness_pending: number;
  documents_expiring_soon: number;
  pending_users: number;
};

/**
 * 장비공급사(company_admin) 가 하위 공급사(EQUIPMENT/MANPOWER) 를 등록/조회.
 * 부모는 자식 자원을 읽기로 보고, 배차 시 자기 명의로 발송한다.
 */
export default function SubSuppliersPage() {
  const { user } = useAuth();
  const isMaster = !!user?.is_company_admin;
  const [items, setItems] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [rollup, setRollup] = useState<Map<number, ChildRollup>>(new Map());
  const [q, setQ] = useState('');

  useEffect(() => {
    if (!isMaster) return;
    void refresh();
  }, [isMaster]);

  const refresh = async () => {
    setLoading(true);
    setErr(null);
    try {
      const res = await api.get<CompanyResponse[]>('/api/companies/children');
      setItems(res.data ?? []);
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '목록 조회 실패');
    } finally {
      setLoading(false);
    }
    // B4 롤업 — 신규 엔드포인트 미배포(404)면 롤업 생략(배지 컬럼만 빠짐).
    try {
      const r = await api.get<ChildRollup[]>('/api/companies/children/rollup');
      const m = new Map<number, ChildRollup>();
      for (const x of r.data ?? []) m.set(x.child_company_id, x);
      setRollup(m);
    } catch {
      setRollup(new Map());
    }
  };

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(
    () => items.filter((c) => !qLower
      || `${c.name} ${c.business_number} ${COMPANY_TYPE_LABEL[c.type]}`.toLowerCase().includes(qLower)),
    [items, qLower],
  );

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

  const columns: Column<CompanyResponse>[] = [
    { key: 'name', header: '회사명', cell: (c) => <span className="font-medium text-slate-900">{c.name}</span> },
    { key: 'business_number', header: '사업자번호', cell: (c) => <span className="text-slate-600">{c.business_number}</span> },
    {
      key: 'type',
      header: '유형',
      cell: (c) => (
        <StatusBadge tone={c.type === 'EQUIPMENT' ? 'success' : 'warning'}>
          {COMPANY_TYPE_LABEL[c.type]}
        </StatusBadge>
      ),
    },
    {
      key: 'created_at',
      header: '등록일',
      cell: (c) => (
        <span className="text-slate-500 text-xs">
          {new Date(c.created_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}
        </span>
      ),
    },
    ...(rollup.size > 0 ? [{
      key: 'rollup',
      header: '현황',
      cell: (c: CompanyResponse) => {
        const r = rollup.get(c.id);
        if (!r) return <span className="text-slate-300 text-xs">-</span>;
        const total = r.readiness_ready + r.readiness_pending;
        return (
          <div className="flex flex-wrap items-center gap-1">
            <StatusBadge tone="success">투입대기 {r.readiness_ready}/{total}</StatusBadge>
            {r.documents_expiring_soon > 0 && (
              <StatusBadge tone="danger">서류만료임박 {r.documents_expiring_soon}</StatusBadge>
            )}
            {r.pending_users > 0 && (
              <StatusBadge tone="warning">가입대기 {r.pending_users}</StatusBadge>
            )}
          </div>
        );
      },
    } as Column<CompanyResponse>] : []),
  ];

  return (
    <AppShell breadcrumb={[{ label: '하위공급사 관리' }]}>
      <div className="space-y-4">
        <PageHeader
          title="하위공급사 관리"
          subtitle="우리 회사가 관리하는 하위 공급사(장비/인력)를 등록·조회합니다. 하위 공급사의 장비·인원은 우리 목록에 함께 표시되고, 배차 시 우리 명의로 발송됩니다."
          actions={
            <div className="flex items-center gap-2">
              <Link to="/onboarding/sub-suppliers" className="btn-ghost">온보딩 안내</Link>
              <button type="button" onClick={() => setAddOpen(true)} className="btn-primary">
                + 하위공급사 등록
              </button>
            </div>
          }
        />

        {err && <div className="card border-rose-200 bg-rose-50 text-sm text-rose-800">{err}</div>}

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '회사명·사업자번호 검색' }}
          activeFilterCount={q ? 1 : 0}
          onReset={() => setQ('')}
        />

        <DataTable
          columns={columns}
          rows={filtered}
          rowKey={(c) => c.id}
          empty={loading ? '로딩…' : items.length === 0 ? '등록된 하위공급사가 없습니다' : '조건에 맞는 하위공급사가 없습니다'}
        />

        {items.length > 0 && <PendingApprovals childCompanies={items} />}

        {addOpen && (
          <AddSubSupplierModal
            onClose={() => setAddOpen(false)}
            onCreated={() => { setAddOpen(false); void refresh(); }}
          />
        )}
      </div>
    </AppShell>
  );
}

/**
 * V77 자가로그인+부모 승인: 하위 공급사가 스스로 가입하면 enabled=false 로 대기.
 * 부모(장비공급사 master)가 여기서 승인하면 활성화(첫 유저는 자식회사 master 로 승격).
 */
function PendingApprovals({ childCompanies }: { childCompanies: CompanyResponse[] }) {
  const [rows, setRows] = useState<Array<{ user: UserResponse; companyName: string }>>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setErr(null);
    try {
      const perChild = await Promise.all(
        childCompanies.map((c) =>
          api.get<UserResponse[]>(`/api/companies/children/${c.id}/users`)
            .then((res) => (res.data ?? []).filter((u) => !u.enabled).map((u) => ({ user: u, companyName: c.name })))
            .catch(() => [] as Array<{ user: UserResponse; companyName: string }>),
        ),
      );
      setRows(perChild.flat());
    } finally {
      setLoading(false);
    }
  }, [childCompanies]);

  useEffect(() => { void load(); }, [load]);

  const approve = async (id: number) => {
    setBusyId(id);
    setErr(null);
    try {
      await api.post(`/api/companies/children/users/${id}/approve`);
      await load();
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '승인 실패');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
      <div>
        <h3 className="text-sm font-bold text-slate-900">가입 대기 승인</h3>
        <p className="text-xs text-slate-500 mt-0.5">하위 공급사가 스스로 가입하면 여기서 승인해 로그인을 활성화합니다.</p>
      </div>
      {err && <div className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800">{err}</div>}
      {loading ? (
        <p className="text-sm text-slate-400">로딩…</p>
      ) : rows.length === 0 ? (
        <p className="text-sm text-slate-400">가입 대기 중인 인원이 없습니다.</p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {rows.map(({ user, companyName }) => (
            <li key={user.id} className="py-2.5 flex items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <StatusBadge tone="warning">{companyName}</StatusBadge>
                  <span className="font-medium text-slate-900">{user.name}</span>
                </div>
                <div className="text-xs text-slate-500 mt-0.5">{user.email}</div>
              </div>
              <button
                type="button"
                onClick={() => void approve(user.id)}
                disabled={busyId === user.id}
                className="btn-primary shrink-0"
              >
                {busyId === user.id ? '승인 중…' : '승인'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function AddSubSupplierModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState('');
  const [businessNumber, setBusinessNumber] = useState('');
  const [type, setType] = useState<SubType>('EQUIPMENT');
  const [withAdmin, setWithAdmin] = useState(false);
  const [adminEmail, setAdminEmail] = useState('');
  const [adminPassword, setAdminPassword] = useState('');
  const [adminName, setAdminName] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const submit = async () => {
    setErr(null);
    if (!name.trim() || !businessNumber.trim()) { setErr('회사명/사업자번호는 필수입니다'); return; }
    if (withAdmin) {
      if (!adminEmail || !adminPassword || !adminName) { setErr('관리자 계정은 이메일/비밀번호/이름이 모두 필요합니다'); return; }
      if (adminPassword.length < 8) { setErr('비밀번호는 8자 이상이어야 합니다'); return; }
    }
    setBusy(true);
    try {
      const body: Record<string, any> = {
        name: name.trim(),
        business_number: businessNumber.trim(),
        type,
      };
      if (withAdmin) {
        body.admin_email = adminEmail.trim();
        body.admin_password = adminPassword;
        body.admin_name = adminName.trim();
      }
      await api.post('/api/companies/children', body);
      onCreated();
    } catch (e: any) {
      setErr(e?.response?.data?.message || e?.response?.data?.error || '등록 실패');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-5 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900">하위공급사 등록</h3>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600">✕</button>
        </div>

        {err && <div className="rounded-md border border-rose-200 bg-rose-50 p-2 text-xs text-rose-800">{err}</div>}

        <div className="space-y-2">
          <Field label="회사명 *">
            <input type="text" value={name} onChange={(e) => setName(e.target.value)} className="input" />
          </Field>
          <Field label="사업자번호 *">
            <BusinessNumberInput value={businessNumber} onChange={setBusinessNumber} />
          </Field>
          <Field label="유형 *">
            <div className="flex gap-2">
              {(['EQUIPMENT', 'MANPOWER'] as SubType[]).map((t) => (
                <label
                  key={t}
                  className={`flex-1 flex items-center justify-center gap-2 px-3 py-2 rounded-lg border cursor-pointer text-sm ${
                    type === t ? 'border-brand-500 bg-brand-50 text-brand-700 font-semibold' : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <input type="radio" name="subType" checked={type === t} onChange={() => setType(t)} className="sr-only" />
                  {COMPANY_TYPE_LABEL[t]}
                </label>
              ))}
            </div>
          </Field>
        </div>

        <div className="rounded-md border border-slate-200 bg-slate-50 p-2 space-y-2">
          <label className="flex items-center gap-2 text-xs text-slate-700">
            <input type="checkbox" checked={withAdmin} onChange={(e) => setWithAdmin(e.target.checked)} />
            <span>하위공급사 관리자 계정도 함께 생성 (독립 로그인)</span>
          </label>
          {withAdmin && (
            <div className="space-y-2">
              <Field label="관리자 이메일 *">
                <input type="email" value={adminEmail} onChange={(e) => setAdminEmail(e.target.value)} className="input" />
              </Field>
              <Field label="관리자 비밀번호 * (8자 이상)">
                <input type="password" autoComplete="new-password" value={adminPassword}
                       onChange={(e) => setAdminPassword(e.target.value)} className="input font-mono" />
              </Field>
              <Field label="관리자 이름 *">
                <input type="text" value={adminName} onChange={(e) => setAdminName(e.target.value)} className="input" />
              </Field>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="btn-ghost">취소</button>
          <button type="button" onClick={submit} disabled={busy} className="btn-primary">
            {busy ? '등록 중…' : '등록'}
          </button>
        </div>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label className="text-xs text-slate-600 block mb-0.5">{label}</label>
      {children}
    </div>
  );
}
