import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import type { CompanyResponse } from '../../types/auth';
import AppHeader from '../../components/AppHeader';
import CompanyDetailPanel from './CompanyDetailPanel';
import CompanyTable from './CompanyTable';
import CompanyCreateForm from './CompanyCreateForm';

export default function AdminCompaniesPage() {
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<CompanyResponse | null>(null);
  const [creating, setCreating] = useState(false);

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

  function handleCreated() {
    setCreating(false);
    void load();
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
          {!creating && (
            <button onClick={() => setCreating(true)} className="btn-primary">회사 등록</button>
          )}
        </div>

        {creating && (
          <CompanyCreateForm
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <CompanyTable companies={companies} onRowClick={setSelected} />
        )}
      </div>

      <CompanyDetailPanel
        key={selected?.id ?? 'closed'}
        company={selected}
        onClose={() => setSelected(null)}
        onChange={handleChange}
      />
    </main>
  );
}
