import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { AxiosError } from 'axios';
import { api } from '../lib/api';
import { useAuth } from '../contexts/AuthContext';
import AppHeader from '../components/AppHeader';
import EquipmentDetailPanel from '../components/EquipmentDetailPanel';
import EquipmentFields, { EMPTY_EQUIPMENT_FIELDS, type EquipmentFieldValues } from '../components/forms/EquipmentFields';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory, type EquipmentResponse } from '../types/equipment';
import type { CompanyResponse } from '../types/auth';

export default function EquipmentPage() {
  const { user } = useAuth();
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCategory, setFilterCategory] = useState<EquipmentCategory | ''>('');
  const [selected, setSelected] = useState<EquipmentResponse | null>(null);

  const [creating, setCreating] = useState(false);
  const [createValues, setCreateValues] = useState<EquipmentFieldValues>(EMPTY_EQUIPMENT_FIELDS);
  const [creatingBusy, setCreatingBusy] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

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

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setCreatingBusy(true);
    setCreateError(null);
    try {
      const body: Record<string, unknown> = {
        category: createValues.category,
        vehicle_no: createValues.vehicleNo || null,
        model: createValues.model || null,
        manufacturer: createValues.manufacturer || null,
        year: createValues.year ? Number(createValues.year) : null,
      };
      if (isAdmin) {
        if (!createValues.supplierId) {
          setCreateError('장비공급사를 선택하세요');
          setCreatingBusy(false);
          return;
        }
        body.supplier_id = createValues.supplierId;
      }
      await api.post('/api/equipment', body);
      setCreateValues(EMPTY_EQUIPMENT_FIELDS);
      setCreating(false);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        setCreateError(err.response?.data?.message ?? '등록 실패');
      } else {
        setCreateError('등록 실패');
      }
    } finally {
      setCreatingBusy(false);
    }
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
          {canEdit && (
            <button onClick={() => setCreating((v) => !v)} className="btn-primary">
              {creating ? '취소' : '장비 등록'}
            </button>
          )}
        </div>

        {creating && canEdit && (
          <form onSubmit={onCreate} className="card mb-6 space-y-4">
            <h2 className="text-base font-bold">새 장비 등록</h2>
            <EquipmentFields
              values={createValues}
              onChange={setCreateValues}
              equipmentSuppliers={isAdmin ? equipmentSuppliers : undefined}
              required
            />
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

        <div className="mb-4 flex items-center gap-2">
          <span className="text-sm text-slate-500">분류</span>
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value as EquipmentCategory | '')}
            className="input bg-white max-w-xs"
          >
            <option value="">전체</option>
            {EQUIPMENT_CATEGORIES.map((c) => (
              <option key={c} value={c}>{EQUIPMENT_CATEGORY_LABEL[c]}</option>
            ))}
          </select>
        </div>

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <div className="card overflow-hidden p-0">
            <table className="w-full text-sm">
              <thead className="bg-slate-50 border-b border-slate-200">
                <tr className="text-left text-slate-500">
                  <th className="px-4 py-3 font-medium">차량번호</th>
                  <th className="px-4 py-3 font-medium">분류</th>
                  <th className="px-4 py-3 font-medium">제조사 / 모델</th>
                  <th className="px-4 py-3 font-medium">년도</th>
                  {isAdmin && <th className="px-4 py-3 font-medium">공급사</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {equipment.map((e) => {
                  const supplier = companiesById.get(e.supplier_id);
                  return (
                    <tr key={e.id} onClick={() => setSelected(e)} className="cursor-pointer hover:bg-slate-50">
                      <td className="px-4 py-3 font-medium">{e.vehicle_no || <span className="text-slate-400">—</span>}</td>
                      <td className="px-4 py-3 text-slate-600">{EQUIPMENT_CATEGORY_LABEL[e.category]}</td>
                      <td className="px-4 py-3 text-slate-600">
                        {[e.manufacturer, e.model].filter(Boolean).join(' / ') || <span className="text-slate-400">—</span>}
                      </td>
                      <td className="px-4 py-3 text-slate-600">{e.year ?? <span className="text-slate-400">—</span>}</td>
                      {isAdmin && (
                        <td className="px-4 py-3 text-slate-500">
                          {supplier?.name ?? `id=${e.supplier_id}`}
                        </td>
                      )}
                    </tr>
                  );
                })}
                {equipment.length === 0 && (
                  <tr>
                    <td colSpan={isAdmin ? 5 : 4} className="px-4 py-8 text-center text-slate-400">
                      장비 없음
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <EquipmentDetailPanel
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
