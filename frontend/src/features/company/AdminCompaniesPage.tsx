import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { COMPANY_TYPE_LABEL, type CompanyResponse, type CompanyType } from '../../types/auth';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar, FilterSelect } from '../../components/ui';
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
  // 클라이언트 필터 — 로드된 목록을 좁힘.
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

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

  // 유형 옵션 — (filterTypes 적용 후) 실제 존재하는 유형에서만 파생.
  const typeOptions = useMemo(() => {
    const present = new Set(visible.map((c) => c.type));
    return [...present].map((t) => ({ value: t, label: COMPANY_TYPE_LABEL[t] }));
  }, [visible]);

  const qLower = q.trim().toLowerCase();
  const filtered = useMemo(() => visible.filter((c) => {
    if (typeFilter && c.type !== typeFilter) return false;
    if (qLower && !`${c.name} ${c.business_number}`.toLowerCase().includes(qLower)) return false;
    return true;
  }), [visible, typeFilter, qLower]);

  const activeFilterCount = [q, typeFilter].filter(Boolean).length;
  const resetFilters = () => { setQ(''); setTypeFilter(''); };

  return (
    <AppShell breadcrumb={[{ label: breadcrumbLabel ?? title }]}>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <PageHeader
          title={title}
          subtitle={description}
          actions={!creating ? (
            <button onClick={() => setCreating(true)} className="btn-primary">회사 등록</button>
          ) : undefined}
        />

        {creating && (
          <CompanyCreateForm
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        <FilterBar
          search={{ value: q, onChange: setQ, placeholder: '회사명·사업자번호 검색' }}
          activeFilterCount={activeFilterCount}
          onReset={resetFilters}
        >
          {typeOptions.length > 1 && (
            <FilterSelect value={typeFilter} onChange={setTypeFilter} placeholder="유형 전체" options={typeOptions} />
          )}
        </FilterBar>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <CompanyTable companies={filtered} onRowClick={handleRowClick} onEdit={setSelected} />
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
