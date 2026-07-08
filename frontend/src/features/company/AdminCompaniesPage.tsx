import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import type { CompanyResponse, CompanyType } from '../../types/auth';
import AppShell from '../../components/layout/AppShell';
import CompanyDetailPanel from './CompanyDetailPanel';
import CompanyTable from './CompanyTable';
import CompanyCreateForm from './CompanyCreateForm';

interface Props {
  /** undefined = 전체. ['BP'] = BP 만. ['EQUIPMENT','MANPOWER'] = 공급사. */
  filterTypes?: CompanyType[];
  title?: string;
  breadcrumbLabel?: string;
  description?: string;
}

export default function AdminCompaniesPage({
  filterTypes,
  title = '회사 관리',
  breadcrumbLabel,
  description,
}: Props) {
  const navigate = useNavigate();
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<CompanyResponse | null>(null);
  const [creating, setCreating] = useState(false);

  /** 행 클릭 시 회사 상세 풀페이지로 — 탭(정보/장비/인원/서류). */
  function handleRowClick(c: CompanyResponse) {
    navigate(`/admin/companies/${c.id}`);
  }

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

  const visible = useMemo(() => {
    if (!filterTypes || filterTypes.length === 0) return companies;
    return companies.filter((c) => filterTypes.includes(c.type));
  }, [companies, filterTypes]);

  return (
    <AppShell breadcrumb={[{ label: breadcrumbLabel ?? title }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between gap-3 mb-2">
          <h1 className="text-2xl font-bold">{title}</h1>
          {!creating && (
            <button onClick={() => setCreating(true)} className="btn-primary">회사 등록</button>
          )}
        </div>
        {description && <p className="text-sm text-slate-500 mb-6">{description}</p>}

        {creating && (
          <CompanyCreateForm
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <CompanyTable companies={visible} onRowClick={handleRowClick} onEdit={setSelected} />
        )}
      </div>

      <CompanyDetailPanel
        key={selected?.id ?? 'closed'}
        company={selected}
        onClose={() => setSelected(null)}
        onChange={handleChange}
      />
    </AppShell>
  );
}
