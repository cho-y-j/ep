import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import PersonTable from './PersonTable';
import PersonRoleFilter from './PersonRoleFilter';
import PersonCreateForm from './PersonCreateForm';
import PersonDetailPanel from './PersonDetailPanel';
import type { PersonResponse, PersonRole } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';

export default function PersonPage() {
  const { user, company } = useAuth();
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterRole, setFilterRole] = useState<PersonRole | ''>('');
  const [selected, setSelected] = useState<PersonResponse | null>(null);
  const [creating, setCreating] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  const canEdit = isAdmin || user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER';

  const supplierCompanies = useMemo(
    () => companies.filter((c) => c.type === 'EQUIPMENT' || c.type === 'MANPOWER'),
    [companies]
  );
  const companiesById = useMemo(() => {
    const map = new Map<number, CompanyResponse>();
    companies.forEach((c) => map.set(c.id, c));
    return map;
  }, [companies]);

  // 셀프 등록 시 본인 회사 type
  const selfSupplierType: CompanyType | undefined = !isAdmin && company
    ? company.type
    : undefined;

  async function load() {
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (filterRole) params.role = filterRole;
      const [pRes, cRes] = await Promise.all([
        api.get<PersonResponse[]>('/api/persons', { params }),
        isAdmin
          ? api.get<CompanyResponse[]>('/api/companies')
          : Promise.resolve({ data: [] as CompanyResponse[] }),
      ]);
      setPersons(pRes.data);
      setCompanies(cRes.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterRole]);

  function handleCreated() {
    setCreating(false);
    void load();
  }

  function handleChange(updated: PersonResponse) {
    setPersons((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
    setSelected(updated);
  }

  function handleDelete(id: number) {
    setPersons((prev) => prev.filter((p) => p.id !== id));
    setSelected(null);
  }

  // 셀렉트한 인원의 supplier type (수정 시 역할 필터링)
  const selectedSupplierType: CompanyType | undefined = selected
    ? (companiesById.get(selected.supplier_id)?.type ?? selfSupplierType)
    : undefined;

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">인원 관리</h1>
          {canEdit && !creating && (
            <button onClick={() => setCreating(true)} className="btn-primary">
              인원 등록
            </button>
          )}
        </div>

        {creating && canEdit && (
          <PersonCreateForm
            suppliers={isAdmin ? supplierCompanies : undefined}
            selfSupplierType={selfSupplierType}
            requireSupplierId={isAdmin}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        <div className="mb-4">
          <PersonRoleFilter value={filterRole} onChange={setFilterRole} />
        </div>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <PersonTable
            persons={persons}
            companiesById={companiesById}
            showSupplierColumn={isAdmin}
            onRowClick={setSelected}
          />
        )}
      </div>

      <PersonDetailPanel
        key={selected?.id ?? 'closed'}
        person={selected}
        supplier={selected ? companiesById.get(selected.supplier_id) ?? null : null}
        supplierType={selectedSupplierType}
        onClose={() => setSelected(null)}
        onChange={handleChange}
        onDelete={handleDelete}
        canEdit={Boolean(canEdit && (isAdmin || selected?.supplier_id === user?.company_id))}
      />
    </main>
  );
}
