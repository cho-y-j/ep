import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import EquipmentTable from './EquipmentTable';
import EquipmentCategoryFilter from './EquipmentCategoryFilter';
import EquipmentCreateForm from './EquipmentCreateForm';
import type { EquipmentCategory, EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

export default function EquipmentPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCategory, setFilterCategory] = useState<EquipmentCategory | ''>('');
  const [creating, setCreating] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  const canEdit = isAdmin || user?.role === 'EQUIPMENT_SUPPLIER';

  const equipmentSuppliers = useMemo(
    () => companies.filter((c) => c.type === 'EQUIPMENT'),
    [companies]
  );
  const companiesById = useMemo(() => {
    const map = new Map<number, CompanyResponse>();
    companies.forEach((c) => map.set(c.id, c));
    return map;
  }, [companies]);

  async function load() {
    setLoading(true);
    try {
      const params: Record<string, string> = {};
      if (filterCategory) params.category = filterCategory;
      const [eqRes, coRes] = await Promise.all([
        api.get<EquipmentResponse[]>('/api/equipment', { params }),
        isAdmin
          ? api.get<CompanyResponse[]>('/api/companies', { params: { type: 'EQUIPMENT' } })
          : Promise.resolve({ data: [] as CompanyResponse[] }),
      ]);
      setEquipment(eqRes.data);
      setCompanies(coRes.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterCategory]);

  function handleCreated(e: EquipmentResponse) {
    setCreating(false);
    navigate(`/equipment/${e.id}`);
  }

  return (
    <AppShell>
      <div className="max-w-5xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <h1 className="text-2xl font-bold">장비 관리</h1>
          {canEdit && !creating && (
            <button onClick={() => setCreating(true)} className="btn-primary">
              장비 등록
            </button>
          )}
        </div>

        {creating && canEdit && (
          <EquipmentCreateForm
            equipmentSuppliers={isAdmin ? equipmentSuppliers : undefined}
            requireSupplierId={isAdmin}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        <div className="mb-4">
          <EquipmentCategoryFilter value={filterCategory} onChange={setFilterCategory} />
        </div>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <EquipmentTable
            equipment={equipment}
            companiesById={companiesById}
            showSupplierColumn={isAdmin}
            onRowClick={(e) => navigate(`/equipment/${e.id}`)}
          />
        )}
      </div>
    </AppShell>
  );
}
