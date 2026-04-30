import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import PersonTable from './PersonTable';
import PersonRoleFilter from './PersonRoleFilter';
import PersonCreateForm from './PersonCreateForm';
import type { PersonResponse, PersonRole } from '../../types/person';
import { rolesAllowedFor } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';

export default function PersonPage() {
  const { user, company } = useAuth();
  const navigate = useNavigate();
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterRole, setFilterRole] = useState<PersonRole | ''>('');
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

  const selfSupplierType: CompanyType | undefined = !isAdmin && company
    ? company.type
    : undefined;

  const pageTitle = (() => {
    if (isAdmin) return '인원 관리';
    if (selfSupplierType === 'EQUIPMENT') return '조종원 관리';
    if (selfSupplierType === 'MANPOWER') return '작업자 관리';
    return '인원 관리';
  })();

  const filterRoles: PersonRole[] = isAdmin
    ? (['OPERATOR', 'WORK_DIRECTOR', 'GUIDE', 'FIRE_WATCH', 'SIGNALER', 'INSPECTOR', 'SITE_MANAGER'])
    : (selfSupplierType ? rolesAllowedFor(selfSupplierType) : []);

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

  function handleCreated(p: PersonResponse) {
    setCreating(false);
    navigate(`/persons/${p.id}`);
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">{pageTitle}</h1>
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

        {filterRoles.length > 1 && (
          <div className="mb-4">
            <PersonRoleFilter value={filterRole} onChange={setFilterRole} options={filterRoles} />
          </div>
        )}

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <PersonTable
            persons={persons}
            companiesById={companiesById}
            showSupplierColumn={isAdmin}
            onRowClick={(p) => navigate(`/persons/${p.id}`)}
          />
        )}
      </div>
    </main>
  );
}
