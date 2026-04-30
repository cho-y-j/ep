import { useEffect, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../lib/api';
import { COMPANY_TYPE_LABEL, type CompanyResponse, type CompanyType } from '../types/auth';
import { formatBusinessNumber } from '../lib/format';
import CompanyDetailPanel from '../components/CompanyDetailPanel';
import AppHeader from '../components/AppHeader';

const TYPES: CompanyType[] = ['BP', 'EQUIPMENT', 'MANPOWER'];

export default function AdminCompaniesPage() {
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<CompanyResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [createForm, setCreateForm] = useState({ name: '', businessNumber: '', type: 'BP' as CompanyType });
  const [creatingBusy, setCreatingBusy] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    try {
      const res = await api.get<CompanyResponse[]>('/api/companies');
      setCompanies(res.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setCreatingBusy(true);
    setCreateError(null);
    try {
      await api.post('/api/companies', {
        name: createForm.name,
        business_number: createForm.businessNumber,
        type: createForm.type,
      });
      setCreateForm({ name: '', businessNumber: '', type: 'BP' });
      setCreating(false);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        setCreateError(err.response?.data?.message ?? '생성 실패');
      } else {
        setCreateError('생성 실패');
      }
    } finally {
      setCreatingBusy(false);
    }
  }

  function handleChange(updated: CompanyResponse) {
    setCompanies((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
    setSelected(updated);
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">회사 관리</h1>
          <button onClick={() => setCreating((v) => !v)} className="btn-primary">
            {creating ? '취소' : '회사 등록'}
          </button>
        </div>

        {creating && (
          <form onSubmit={onCreate} className="card mb-6 space-y-4">
            <h2 className="text-base font-bold">새 회사 등록</h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <label className="block">
                <span className="text-sm font-medium text-slate-700">회사명</span>
                <input value={createForm.name} onChange={(e) => setCreateForm((f) => ({ ...f, name: e.target.value }))} required className="input mt-1" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">사업자번호</span>
                <input
                  value={createForm.businessNumber}
                  onChange={(e) => setCreateForm((f) => ({ ...f, businessNumber: formatBusinessNumber(e.target.value) }))}
                  required
                  placeholder="123-45-67890"
                  inputMode="numeric"
                  maxLength={12}
                  className="input mt-1"
                />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-slate-700">유형</span>
                <select value={createForm.type} onChange={(e) => setCreateForm((f) => ({ ...f, type: e.target.value as CompanyType }))} className="input mt-1 bg-white">
                  {TYPES.map((t) => <option key={t} value={t}>{COMPANY_TYPE_LABEL[t]}</option>)}
                </select>
              </label>
            </div>
            {createError && (
              <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{createError}</p>
            )}
            <div className="flex justify-end">
              <button type="submit" disabled={creatingBusy} className="btn-primary disabled:opacity-50">
                {creatingBusy ? '등록 중...' : '등록'}
              </button>
            </div>
          </form>
        )}

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <div className="card overflow-hidden p-0">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr className="text-left text-slate-500">
                  <th className="px-4 py-3 font-medium">회사명</th>
                  <th className="px-4 py-3 font-medium">사업자번호</th>
                  <th className="px-4 py-3 font-medium">유형</th>
                  <th className="px-4 py-3 font-medium">등록일</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {companies.map((c) => (
                  <tr key={c.id} onClick={() => setSelected(c)} className="cursor-pointer hover:bg-slate-50">
                    <td className="px-4 py-3 font-medium">{c.name}</td>
                    <td className="px-4 py-3">{c.business_number}</td>
                    <td className="px-4 py-3 text-slate-600">{COMPANY_TYPE_LABEL[c.type]}</td>
                    <td className="px-4 py-3 text-slate-500">{new Date(c.created_at).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}</td>
                  </tr>
                ))}
                {companies.length === 0 && (
                  <tr><td colSpan={4} className="px-4 py-8 text-center text-slate-400">회사 없음</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <CompanyDetailPanel
        company={selected}
        onClose={() => setSelected(null)}
        onChange={handleChange}
      />
    </main>
  );
}
