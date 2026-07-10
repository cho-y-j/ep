import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, FilterBar } from '../../components/ui';
import EquipmentTable from './EquipmentTable';
import EquipmentCreateForm from './EquipmentCreateForm';
import EquipmentStatCards from './EquipmentStatCards';
import EquipmentBottomWidgets from './EquipmentBottomWidgets';
import { EQUIPMENT_CATEGORIES, EQUIPMENT_CATEGORY_LABEL, type EquipmentCategory, type EquipmentResponse } from '../../types/equipment';
import type { CompanyResponse } from '../../types/auth';

export default function EquipmentPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [search] = useSearchParams();
  // ADMIN 컨텍스트 필터: 회사 사이드 패널 "이 공급사의 장비" 링크에서 진입한 경우.
  const supplierFilterId = search.get('supplierId') ? Number(search.get('supplierId')) : null;
  // BP nav 분리: scope=own (BP 직속) / scope=external (외부 공급사)
  const scope = (search.get('scope') ?? '') as '' | 'own' | 'external';
  const [equipment, setEquipment] = useState<EquipmentResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCategory, setFilterCategory] = useState<EquipmentCategory | ''>('');
  const [searchInput, setSearchInput] = useState('');
  const [creating, setCreating] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  // #5: BP 도 자기 회사 직속 장비 등록 가능 (BP 보유 차량 등).
  const canEdit = isAdmin || user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'BP';

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

  const isBp = user?.role === 'BP';
  const myCompanyId = user?.company_id ?? null;
  // BP nav scope 적용 + URL 탭으로 공급사 1개 선택 (?focusSupplierId=...)
  const focusSupplierId = search.get('focusSupplierId') ? Number(search.get('focusSupplierId')) : null;

  const scopedAll = equipment.filter((e) => {
    if (supplierFilterId != null && e.supplier_id !== supplierFilterId) return false;
    if (isBp && scope === 'own' && myCompanyId != null && e.supplier_id !== myCompanyId) return false;
    if (isBp && scope === 'external' && myCompanyId != null && e.supplier_id === myCompanyId) return false;
    return true;
  });

  // 외부 보기일 때 공급사 탭 후보
  const externalSuppliers = useMemo(() => {
    if (!isBp || scope !== 'external') return [];
    const map = new Map<number, string>();
    for (const e of equipment) {
      if (myCompanyId != null && e.supplier_id === myCompanyId) continue;
      if (!map.has(e.supplier_id)) map.set(e.supplier_id, e.supplier_name ?? `공급사 #${e.supplier_id}`);
    }
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [equipment, isBp, scope, myCompanyId]);

  const filtered = scopedAll.filter((e) => {
    if (focusSupplierId != null && e.supplier_id !== focusSupplierId) return false;
    if (!searchInput) return true;
    const q = searchInput.toLowerCase();
    return (
      (e.vehicle_no ?? '').toLowerCase().includes(q) ||
      (e.code ?? '').toLowerCase().includes(q) ||
      (e.model ?? '').toLowerCase().includes(q) ||
      (e.manufacturer ?? '').toLowerCase().includes(q)
    );
  });
  const supplierFilterName = supplierFilterId != null
    ? companiesById.get(supplierFilterId)?.name : null;

  const headerTitle = isBp
    ? (scope === 'external' ? '공급사 장비' : scope === 'own' ? '내 장비' : '배치 장비')
    : '장비 목록';
  const headerSub = isBp
    ? (scope === 'external'
        ? '내 현장에 ACTIVE 참여 중인 공급사 장비입니다.'
        : scope === 'own'
          ? '우리 회사 직속 장비입니다.'
          : '내 현장에 배치된 장비를 확인합니다.')
    : '내 회사 장비 정보를 확인하고 관리합니다.';

  return (
    <AppShell breadcrumb={[{ label: '장비 관리', to: '/equipment' }, { label: '장비 목록' }]}>
      <div className="space-y-4">
        {supplierFilterId != null && (
          <div className="flex items-center justify-between px-3 py-2 rounded-md bg-indigo-50 border border-indigo-200 text-xs">
            <span className="text-indigo-800">
              <strong>{supplierFilterName ?? `회사 #${supplierFilterId}`}</strong> 의 장비만 보는 중
            </span>
            <div className="flex items-center gap-3">
              <Link to={`/persons?supplierId=${supplierFilterId}`}
                    className="text-indigo-700 hover:text-indigo-900 underline">
                이 회사의 인원 보기 →
              </Link>
              <Link to="/equipment" className="text-slate-600 hover:text-slate-900 underline">필터 해제</Link>
            </div>
          </div>
        )}

        <PageHeader
          title={headerTitle}
          subtitle={headerSub}
          actions={canEdit && !creating ? (
            <button onClick={() => setCreating(true)} className="btn-primary">
              + 장비 등록
            </button>
          ) : null}
        />

        {creating && canEdit && (
          <EquipmentCreateForm
            equipmentSuppliers={isAdmin ? equipmentSuppliers : undefined}
            requireSupplierId={isAdmin}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        {isBp && scope === 'external' && externalSuppliers.length > 0 && (
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => navigate('/equipment?scope=external')}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
                focusSupplierId == null
                  ? 'bg-brand-600 text-white border-brand-600'
                  : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
              }`}
            >
              전체 ({scopedAll.length})
            </button>
            {externalSuppliers.map((s) => {
              const count = scopedAll.filter((e) => e.supplier_id === s.id).length;
              const active = focusSupplierId === s.id;
              return (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => navigate(`/equipment?scope=external&focusSupplierId=${s.id}`)}
                  className={`px-3 py-1.5 rounded-full text-xs font-semibold border ${
                    active
                      ? 'bg-amber-500 text-white border-amber-500'
                      : 'bg-white text-amber-700 border-amber-200 hover:bg-amber-50'
                  }`}
                >
                  {s.name} ({count})
                </button>
              );
            })}
          </div>
        )}

        <FilterBar
          search={{
            value: searchInput,
            placeholder: '장비명, 차량번호, 모델로 검색',
            onChange: setSearchInput,
          }}
        >
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value as EquipmentCategory | '')}
            className="input max-w-[160px]"
          >
            <option value="">장비 종류 전체</option>
            {EQUIPMENT_CATEGORIES.map((c) => (
              <option key={c} value={c}>{EQUIPMENT_CATEGORY_LABEL[c]}</option>
            ))}
          </select>
        </FilterBar>

        <EquipmentStatCards equipment={equipment} />

        {loading ? (
          <div className="card text-center text-sm text-slate-400 py-10">불러오는 중...</div>
        ) : (
          <EquipmentTable
            equipment={filtered}
            companiesById={companiesById}
            showSupplierColumn={isAdmin || (isBp && scope === 'external')}
            onRowClick={(e) => navigate(`/equipment/${e.id}`)}
            selfCompanyId={myCompanyId}
          />
        )}

        <div className="text-xs text-slate-500">전체 {filtered.length}대</div>

        <EquipmentBottomWidgets equipment={equipment} />
      </div>
    </AppShell>
  );
}
