import { useEffect, useMemo, useState } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppHeader from '../../components/AppHeader';
import EquipmentDetailPanel from './EquipmentDetailPanel';
import EquipmentTable from './EquipmentTable';
import EquipmentCategoryFilter from './EquipmentCategoryFilter';
import EquipmentCreateForm from './EquipmentCreateForm';
import type { EquipmentCategory, EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

export default function EquipmentPage() {
  const { user } = useAuth();
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCategory, setFilterCategory] = useState<EquipmentCategory | ''>('');
  const [selected, setSelected] = useState<EquipmentResponse | null>(null);
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

  function handleCreated() {
    setCreating(false);
    void load();
  }

  function handleChange(updated: EquipmentResponse) {
    setEquipment((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
    setSelected(updated);
  }

  function handleDelete(id: number) {
    setEquipment((prev) => prev.filter((e) => e.id !== id));
    setSelected(null);
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <AppHeader />
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
            onRowClick={setSelected}
          />
        )}
      </div>

      <EquipmentDetailPanel
        key={selected?.id ?? 'closed'}
        equipment={selected}
        supplier={selected ? companiesById.get(selected.supplier_id) ?? null : null}
        onClose={() => setSelected(null)}
        onChange={handleChange}
        onDelete={handleDelete}
        canEdit={Boolean(canEdit && (isAdmin || selected?.supplier_id === user?.company_id))}
      />
    </main>
  );
}
