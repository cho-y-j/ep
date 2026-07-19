import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';

interface ClientOrg {
  id: number;
  name: string;
  code: string;
  note: string | null;
  active: boolean;
}

interface Draft { name: string; code: string; note: string; }
const EMPTY: Draft = { name: '', code: '', note: '' };

export default function ClientOrgsPage() {
  const [items, setItems] = useState<ClientOrg[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [editing, setEditing] = useState<ClientOrg | null>(null);
  const [error, setError] = useState<string | null>(null);
  // 클라이언트 필터 — 로드된 목록을 좁힘.
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<ClientOrg[]>('/api/client-orgs/all');
      setItems(res.data);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void load(); }, []);

  async function save() {
    setError(null);
    try {
      if (editing) {
        await api.patch(`/api/client-orgs/${editing.id}`, draft);
      } else {
        await api.post('/api/client-orgs', draft);
      }
      setCreating(false);
      setEditing(null);
      setDraft(EMPTY);
      void load();
    } catch (e: any) {
      setError(e?.response?.data?.message || '저장 실패');
    }
  }

  async function deactivate(c: ClientOrg) {
    if (!confirm(`'${c.name}' 비활성화하시겠습니까? (이력은 보존)`)) return;
    await api.post(`/api/client-orgs/${c.id}/deactivate`);
    void load();
  }
  async function activate(c: ClientOrg) {
    await api.post(`/api/client-orgs/${c.id}/activate`);
    void load();
  }
  async function hardDelete(c: ClientOrg) {
    if (!confirm(`'${c.name}' 을(를) 완전 삭제합니다. 참조하는 자원 이력이 있으면 거절됩니다. 진행하시겠습니까?`)) return;
    try {
      await api.delete(`/api/client-orgs/${c.id}`);
      void load();
    } catch (e: any) {
      alert(e?.response?.data?.message || '삭제 실패');
    }
  }

  function startEdit(c: ClientOrg) {
    setEditing(c);
    setDraft({ name: c.name, code: c.code, note: c.note ?? '' });
    setCreating(true);
  }
  function cancel() {
    setCreating(false);
    setEditing(null);
    setDraft(EMPTY);
    setError(null);
  }

  const qLower = q.trim().toLowerCase();
  const filtered = items.filter((c) => {
    if (statusFilter === 'active' && !c.active) return false;
    if (statusFilter === 'inactive' && c.active) return false;
    if (qLower && !`${c.name} ${c.code}`.toLowerCase().includes(qLower)) return false;
    return true;
  });
  const activeFilterCount = [q, statusFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setStatusFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: '원청기관 관리' }]}>
      <div className="max-w-4xl mx-auto px-6 py-8">
        <PageHeader
          title="원청기관 관리"
          subtitle="삼성 / SK / 현대 등 발주처(원청) 명단. 자원(장비/인원) 의 이력 라벨에 사용됩니다."
          actions={!creating ? (
            <button onClick={() => setCreating(true)} className="btn-primary">+ 새 원청기관</button>
          ) : undefined}
        />

        {creating && (
          <div className="mb-6 p-4 border border-slate-200 rounded-lg bg-slate-50">
            <h2 className="font-semibold mb-3">{editing ? '원청기관 수정' : '새 원청기관'}</h2>
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="text-xs text-slate-600">이름 *</span>
                <input className="input" value={draft.name}
                       onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
              </label>
              <label className="block">
                <span className="text-xs text-slate-600">코드 * (영문/숫자)</span>
                <input className="input" value={draft.code}
                       onChange={(e) => setDraft({ ...draft, code: e.target.value })} />
              </label>
              <label className="block col-span-2">
                <span className="text-xs text-slate-600">비고</span>
                <input className="input" value={draft.note}
                       onChange={(e) => setDraft({ ...draft, note: e.target.value })} />
              </label>
            </div>
            {error && <div className="text-sm text-rose-600 mt-2">{error}</div>}
            <div className="flex gap-2 justify-end mt-3">
              <button onClick={cancel} className="btn-ghost">취소</button>
              <button onClick={save} className="btn-primary"
                      disabled={!draft.name.trim() || !draft.code.trim()}>저장</button>
            </div>
          </div>
        )}

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '이름·코드 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          <FilterSelect value={statusFilter} onChange={setStatusFilter} placeholder="상태 전체"
            options={[{ value: 'active', label: '활성' }, { value: 'inactive', label: '비활성' }]} />
        </FilterBar>

        {loading ? <div className="text-sm text-slate-500">로딩 중…</div> : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500 border-b border-slate-200">
                <th className="py-2">이름</th>
                <th>코드</th>
                <th>비고</th>
                <th>상태</th>
                <th className="text-right">관리</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((c) => (
                <tr key={c.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="py-2 font-medium">{c.name}</td>
                  <td><code className="text-xs">{c.code}</code></td>
                  <td className="text-slate-500">{c.note ?? '-'}</td>
                  <td>
                    {c.active
                      ? <span className="text-xs px-2 py-0.5 rounded bg-emerald-100 text-emerald-700">활성</span>
                      : <span className="text-xs px-2 py-0.5 rounded bg-slate-100 text-slate-500">비활성</span>}
                  </td>
                  <td className="text-right space-x-3">
                    <button onClick={() => startEdit(c)} className="text-xs text-slate-600 hover:text-slate-900">수정</button>
                    {c.active
                      ? <button onClick={() => deactivate(c)} className="text-xs text-amber-600 hover:text-amber-700">비활성화</button>
                      : <button onClick={() => activate(c)} className="text-xs text-emerald-600 hover:text-emerald-700">활성화</button>}
                    <button onClick={() => hardDelete(c)} className="text-xs text-rose-600 hover:text-rose-700">삭제</button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr><td colSpan={5} className="py-6 text-center text-slate-400">
                  {items.length === 0 ? '등록된 원청기관 없음' : '조건에 맞는 원청기관 없음'}
                </td></tr>
              )}
            </tbody>
          </table>
        )}
      </div>
    </AppShell>
  );
}
