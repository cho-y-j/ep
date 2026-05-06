import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import EquipmentTable from './EquipmentTable';
import EquipmentCreateForm from './EquipmentCreateForm';
import EquipmentStatCards from './EquipmentStatCards';
import EquipmentBottomWidgets from './EquipmentBottomWidgets';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

export default function EquipmentPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCategory, setFilterCategory] = useState<EquipmentCategory | ''>('');
  const [searchInput, setSearchInput] = useState('');
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

  const filtered = equipment.filter((e) => {
    if (!searchInput) return true;
    const q = searchInput.toLowerCase();
    return (
      (e.vehicle_no ?? '').toLowerCase().includes(q) ||
      (e.code ?? '').toLowerCase().includes(q) ||
      (e.model ?? '').toLowerCase().includes(q) ||
      (e.manufacturer ?? '').toLowerCase().includes(q)
    );
  });

  return (
    <AppShell
      breadcrumb={[{ label: '장비 관리', to: '/equipment' }, { label: '장비 목록' }]}
      searchPlaceholder="장비명, 장비코드로 검색"
    >
      <div className="space-y-6">
        {/* 헤더 */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">장비 목록</h1>
            <p className="text-sm text-slate-500 mt-1">현장에 등록된 모든 장비 정보를 확인하고 관리할 수 있습니다.</p>
          </div>
          {canEdit && !creating && (
            <button onClick={() => setCreating(true)} className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700">
              <span>+</span> 장비 등록
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

        {/* 검색 + 필터 행 */}
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[240px]">
              <input
                type="text"
                placeholder="장비명, 장비코드로 검색"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="w-full pl-9 pr-3 py-2 rounded-lg border border-slate-200 bg-slate-50 focus:bg-white focus:border-brand-300 outline-none text-sm"
              />
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">⌕</span>
            </div>
            <FilterSelect
              value={filterCategory}
              onChange={(v) => setFilterCategory(v as EquipmentCategory | '')}
              placeholder="장비 종류 전체"
              options={EQUIPMENT_CATEGORIES.map((c) => ({ value: c, label: EQUIPMENT_CATEGORY_LABEL[c] }))}
            />
            <FilterSelect value="" onChange={() => {}} placeholder="상태 전체" options={[
              { value: 'RUNNING', label: '가동 중' },
              { value: 'NEED_INSPECT', label: '점검 필요' },
              { value: 'BROKEN', label: '고장' },
              { value: 'IDLE', label: '미사용' },
            ]} />
            <FilterSelect value="" onChange={() => {}} placeholder="현장 전체" options={[]} />
            <FilterSelect value="" onChange={() => {}} placeholder="가동률 전체" options={[
              { value: 'high', label: '70% 이상' },
              { value: 'mid', label: '40-70%' },
              { value: 'low', label: '40% 미만' },
            ]} />
            <button type="button" className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-700 hover:bg-slate-50">
              <span>⛁</span> 더보기
            </button>
          </div>
        </div>

        {/* 통계 카드 */}
        <EquipmentStatCards equipment={equipment} />

        {/* 테이블 */}
        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <EquipmentTable
            equipment={filtered}
            companiesById={companiesById}
            showSupplierColumn={isAdmin}
            onRowClick={(e) => navigate(`/equipment/${e.id}`)}
          />
        )}

        {/* 페이지네이션 (간단 버전) */}
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">전체 {filtered.length}대</span>
          <div className="flex items-center gap-1">
            <button type="button" className="px-2 py-1 rounded text-slate-400 hover:bg-slate-100">‹</button>
            <button type="button" className="min-w-[28px] px-2 py-1 rounded bg-brand-600 text-white">1</button>
            <button type="button" className="px-2 py-1 rounded text-slate-400 hover:bg-slate-100">›</button>
          </div>
          <select className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm">
            <option>10개씩 보기</option>
            <option>20개씩 보기</option>
            <option>50개씩 보기</option>
          </select>
        </div>

        {/* 하단 위젯 */}
        <EquipmentBottomWidgets equipment={equipment} />
      </div>
    </AppShell>
  );
}

function FilterSelect({
  value, onChange, placeholder, options,
}: {
  value: string;
  onChange: (v: string) => void;
  placeholder: string;
  options: Array<{ value: string; label: string }>;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 min-w-[140px]"
    >
      <option value="">{placeholder}</option>
      {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}
