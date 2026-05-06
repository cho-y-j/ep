import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import Pagination from '../../components/Pagination';
import ConfirmDialog from '../../components/ConfirmDialog';
import PersonTable from './PersonTable';
import PersonRoleFilter from './PersonRoleFilter';
import PersonCreateForm from './PersonCreateForm';
import type { PersonResponse, PersonRole } from '../../types/person';
import { rolesAllowedFor } from '../../types/person';
import type { CompanyResponse, CompanyType } from '../../types/auth';

type Page<T> = {
  content: T[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
};

export default function PersonPage() {
  const { user, company } = useAuth();
  const navigate = useNavigate();
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [filterRole, setFilterRole] = useState<PersonRole | ''>('');
  const [searchInput, setSearchInput] = useState('');
  const [searchQ, setSearchQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [creating, setCreating] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [confirmBulkDelete, setConfirmBulkDelete] = useState(false);
  const [bulkBusy, setBulkBusy] = useState(false);

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

  const selfSupplierType: CompanyType | undefined = !isAdmin && company ? company.type : undefined;

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
      const params: Record<string, string> = { page: String(page), size: String(size) };
      if (filterRole) params.role = filterRole;
      if (searchQ) params.q = searchQ;

      const [pRes, cRes] = await Promise.all([
        api.get<Page<PersonResponse>>('/api/persons', { params }),
        isAdmin
          ? api.get<CompanyResponse[]>('/api/companies')
          : Promise.resolve({ data: [] as CompanyResponse[] }),
      ]);
      setPersons(pRes.data.content);
      setTotalElements(pRes.data.total_elements);
      setTotalPages(pRes.data.total_pages);
      setCompanies(cRes.data);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterRole, searchQ, page, size]);

  function applySearch(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setSearchQ(searchInput.trim());
  }

  function handleCreated(p: PersonResponse) {
    setCreating(false);
    navigate(`/persons/${p.id}`);
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function toggleSelectAll() {
    setSelectedIds((prev) => {
      if (prev.size === persons.length && persons.length > 0) return new Set();
      return new Set(persons.map((p) => p.id));
    });
  }

  async function bulkDelete() {
    setBulkBusy(true);
    try {
      await api.post('/api/persons/bulk-delete', { ids: Array.from(selectedIds) });
      setSelectedIds(new Set());
      setConfirmBulkDelete(false);
      await load();
    } catch (err) {
      if (err instanceof AxiosError) {
        alert(err.response?.data?.message ?? '삭제 실패');
      }
    } finally {
      setBulkBusy(false);
    }
  }

  const allSelected = persons.length > 0 && selectedIds.size === persons.length;

  return (
    <AppShell>
      <div className="max-w-6xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-2">
          <h1 className="text-2xl font-bold">{pageTitle}</h1>
          {canEdit && !creating && (
            <button onClick={() => setCreating(true)} className="btn-primary">
              + 인원 추가
            </button>
          )}
        </div>
        <p className="text-sm text-slate-500 mb-6">
          현장에 등록된 모든 인원 정보를 확인하고 관리할 수 있습니다.
        </p>

        {creating && canEdit && (
          <PersonCreateForm
            suppliers={isAdmin ? supplierCompanies : undefined}
            selfSupplierType={selfSupplierType}
            requireSupplierId={isAdmin}
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        <div className="card mb-4">
          <form onSubmit={applySearch} className="flex flex-wrap items-center gap-3">
            <div className="flex-1 min-w-[240px] relative">
              <input
                type="text"
                placeholder="이름, 연락처로 검색"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="input pl-9"
              />
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">⌕</span>
            </div>
            {filterRoles.length > 1 && (
              <PersonRoleFilter
                value={filterRole}
                onChange={(v) => { setPage(0); setFilterRole(v); }}
                options={filterRoles}
              />
            )}
            <button type="submit" className="text-sm px-3 py-2 rounded-lg bg-slate-800 text-white hover:bg-slate-900">
              검색
            </button>
            {searchQ && (
              <button
                type="button"
                onClick={() => { setSearchInput(''); setSearchQ(''); setPage(0); }}
                className="text-sm text-slate-500 hover:text-slate-900"
              >
                초기화
              </button>
            )}
          </form>
        </div>

        {canEdit && selectedIds.size > 0 && (
          <div className="card mb-4 flex items-center justify-between bg-amber-50 border-amber-200">
            <span className="text-sm text-amber-800 font-medium">
              {selectedIds.size}건 선택됨
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setSelectedIds(new Set())}
                className="text-sm text-slate-600 hover:text-slate-900 px-3 py-1.5"
              >
                선택 해제
              </button>
              <button
                type="button"
                onClick={() => setConfirmBulkDelete(true)}
                className="text-sm px-3 py-1.5 rounded bg-red-600 text-white hover:bg-red-700"
              >
                일괄 삭제
              </button>
            </div>
          </div>
        )}

        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <>
            <PersonTable
              persons={persons}
              companiesById={companiesById}
              showSupplierColumn={isAdmin}
              onRowClick={(p) => navigate(`/persons/${p.id}`)}
              selectedIds={canEdit ? selectedIds : undefined}
              onToggleSelect={canEdit ? toggleSelect : undefined}
              onToggleSelectAll={canEdit ? toggleSelectAll : undefined}
              allSelected={allSelected}
            />
            <Pagination
              page={page}
              totalPages={totalPages}
              totalElements={totalElements}
              size={size}
              onPageChange={setPage}
              onSizeChange={(s) => { setSize(s); setPage(0); }}
            />
          </>
        )}
      </div>

      <ConfirmDialog
        open={confirmBulkDelete}
        title="인원 일괄 삭제"
        message={`선택된 ${selectedIds.size}건을 삭제합니다.\n복구할 수 없습니다.`}
        confirmLabel="삭제"
        variant="danger"
        busy={bulkBusy}
        onConfirm={bulkDelete}
        onCancel={() => setConfirmBulkDelete(false)}
      />
    </AppShell>
  );
}
