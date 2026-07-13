import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import AppShell from '../../components/layout/AppShell';
import ConfirmDialog from '../../components/ConfirmDialog';
import PersonTable from './PersonTable';
import PersonCreateForm from './PersonCreateForm';
import {
  PERSON_STATUS_LABEL, PERSON_ROLE_LABEL,
  rolesAllowedFor, type PersonResponse, type PersonRole, type PersonStatus,
} from '../../types/person';
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
  const [search] = useSearchParams();
  // ADMIN 컨텍스트 필터: 회사 사이드 패널 "이 공급사의 인원" 링크에서 진입.
  const supplierFilterId = search.get('supplierId') ? Number(search.get('supplierId')) : null;
  // BP 사이드바: "내 직속 인원" / "공급사 인원" 분리.
  const scope = search.get('scope'); // 'own' | 'external' | null
  const [persons, setPersons] = useState<PersonResponse[]>([]);
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);
  const [loading, setLoading] = useState(true);

  const [filterTeam, setFilterTeam] = useState('');
  const [filterRole, setFilterRole] = useState<PersonRole | ''>('');
  const [filterStatus, setFilterStatus] = useState<PersonStatus | ''>('');
  const [searchInput, setSearchInput] = useState('');
  const [searchQ, setSearchQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [creating, setCreating] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [confirmBulkDelete, setConfirmBulkDelete] = useState(false);
  const [bulkBusy, setBulkBusy] = useState(false);
  /** 직종 빠른 수정 모달 — 인원 행의 ✎ 클릭 시 열림. */
  const [editingRoles, setEditingRoles] = useState<PersonResponse | null>(null);
  const [editingRolesValue, setEditingRolesValue] = useState<Set<PersonRole>>(new Set());
  const [savingRoles, setSavingRoles] = useState(false);

  const isAdmin = user?.role === 'ADMIN';
  // #5: BP 도 자기 회사 직속 운전수/지휘자 등록 가능.
  const canEdit = isAdmin || user?.role === 'EQUIPMENT_SUPPLIER' || user?.role === 'MANPOWER_SUPPLIER' || user?.role === 'BP';
  const selfSupplierType: CompanyType | undefined = !isAdmin && company ? company.type : undefined;

  const supplierCompanies = useMemo(
    () => companies.filter((c) => c.type === 'EQUIPMENT' || c.type === 'MANPOWER'),
    [companies]
  );

  const pageTitle = (() => {
    if (isAdmin) return '인원 목록';
    if (selfSupplierType === 'EQUIPMENT') return '조종원 목록';
    if (selfSupplierType === 'MANPOWER') return '작업자 목록';
    if (selfSupplierType === 'BP') {
      if (scope === 'own') return '배치 인원';
      if (scope === 'external') return '공급사 인원';
    }
    return '인원 목록';
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
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterRole, searchQ, page, size]);

  const filteredPersons = useMemo(() => {
    return persons.filter((p) => {
      if (supplierFilterId != null && p.supplier_id !== supplierFilterId) return false;
      if (filterTeam && p.team !== filterTeam) return false;
      if (filterStatus && p.status !== filterStatus) return false;
      // BP 사이드바 scope: own=내 직속, external=공급사 인원
      if (scope === 'own' && user?.company_id != null && p.supplier_id !== user.company_id) return false;
      if (scope === 'external' && user?.company_id != null && p.supplier_id === user.company_id) return false;
      return true;
    });
  }, [persons, filterTeam, filterStatus, supplierFilterId, scope, user?.company_id]);
  const supplierFilterName = supplierFilterId != null
    ? companies.find((c) => c.id === supplierFilterId)?.name : null;

  const teams = useMemo(() => {
    const set = new Set<string>();
    persons.forEach((p) => { if (p.team) set.add(p.team); });
    return Array.from(set);
  }, [persons]);

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
      if (prev.size === filteredPersons.length && filteredPersons.length > 0) return new Set();
      return new Set(filteredPersons.map((p) => p.id));
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

  const allSelected = filteredPersons.length > 0 && selectedIds.size === filteredPersons.length;

  // 페이지네이션 페이지 번호 (1-based, 시안: 1 2 3 4 ... 8)
  const renderPageNumbers = () => {
    const pages: (number | '...')[] = [];
    const total = totalPages;
    if (total <= 6) {
      for (let i = 0; i < total; i++) pages.push(i);
    } else {
      pages.push(0, 1, 2, 3);
      if (page < total - 2) pages.push('...');
      pages.push(total - 1);
    }
    return pages;
  };

  return (
    <AppShell
      breadcrumb={[{ label: '인원 관리', to: '/persons' }, { label: pageTitle }]}
     
    >
      <div className="space-y-6">
        {supplierFilterId != null && (
          <div className="flex items-center justify-between px-4 py-2 rounded-lg bg-indigo-50 border border-indigo-200 text-sm">
            <span className="text-indigo-800">
              <strong>{supplierFilterName ?? `회사 #${supplierFilterId}`}</strong> 의 인원만 보는 중
            </span>
            <div className="flex items-center gap-3">
              <Link to={`/equipment?supplierId=${supplierFilterId}`}
                    className="text-xs text-indigo-700 hover:text-indigo-900 underline">
                이 회사의 장비 보기 →
              </Link>
              <Link to="/persons" className="text-xs text-slate-600 hover:text-slate-900 underline">필터 해제</Link>
            </div>
          </div>
        )}

        {/* 헤더 */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{pageTitle}</h1>
            <p className="text-sm text-slate-500 mt-1">현장에 등록된 모든 인원 정보를 확인하고 관리할 수 있습니다.</p>
          </div>
          {canEdit && !creating && (
            <button onClick={() => setCreating(true)} className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700">
              <span>+</span> 인원 추가
            </button>
          )}
        </div>

        {creating && canEdit && (
          <PersonCreateForm
            suppliers={isAdmin ? supplierCompanies : undefined}
            selfSupplierType={selfSupplierType}
            requireSupplierId={isAdmin}
            showDocumentStep
            onCreated={handleCreated}
            onCancel={() => setCreating(false)}
          />
        )}

        {/* 검색 + 필터 행 */}
        <div className="rounded-xl border border-slate-200 bg-white p-4">
          <div className="flex flex-wrap items-center gap-3">
            <form
              onSubmit={(e) => { e.preventDefault(); setPage(0); setSearchQ(searchInput.trim()); }}
              className="relative flex-1 min-w-[240px]"
            >
              <input
                type="text"
                placeholder="이름, 연락처, 자격증으로 검색"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="w-full pl-9 pr-3 py-2 rounded-lg border border-slate-200 bg-slate-50 focus:bg-white focus:border-brand-300 outline-none text-sm"
              />
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">⌕</span>
            </form>
            <FilterSelect
              value={filterTeam}
              onChange={setFilterTeam}
              placeholder="소속 전체"
              options={teams.map((t) => ({ value: t, label: t }))}
            />
            <FilterSelect
              value={filterRole}
              onChange={(v) => { setPage(0); setFilterRole(v as PersonRole | ''); }}
              placeholder="직종 전체"
              options={filterRoles.map((r) => ({ value: r, label: PERSON_ROLE_LABEL[r] }))}
            />
            <FilterSelect
              value={filterStatus}
              onChange={(v) => setFilterStatus(v as PersonStatus | '')}
              placeholder="상태 전체"
              options={[
                { value: 'WORKING', label: PERSON_STATUS_LABEL.WORKING },
                { value: 'VACATION', label: PERSON_STATUS_LABEL.VACATION },
                { value: 'RETIRED', label: PERSON_STATUS_LABEL.RETIRED },
              ]}
            />
          </div>
        </div>

        {/* 일괄 작업 */}
        {canEdit && selectedIds.size > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-3 flex items-center justify-between">
            <span className="text-sm text-amber-800 font-medium">{selectedIds.size}건 선택됨</span>
            <div className="flex gap-2">
              <button type="button" onClick={() => setSelectedIds(new Set())} className="text-sm text-slate-600 hover:text-slate-900 px-3 py-1.5">선택 해제</button>
              <button type="button" onClick={() => setConfirmBulkDelete(true)} className="text-sm px-3 py-1.5 rounded bg-rose-600 text-white hover:bg-rose-700">일괄 삭제</button>
            </div>
          </div>
        )}

        {/* 테이블 */}
        {loading ? (
          <p className="text-slate-400">불러오는 중...</p>
        ) : (
          <PersonTable
            persons={filteredPersons}
            onRowClick={(p) => navigate(`/persons/${p.id}`)}
            selectedIds={canEdit ? selectedIds : undefined}
            onToggleSelect={canEdit ? toggleSelect : undefined}
            onToggleSelectAll={canEdit ? toggleSelectAll : undefined}
            allSelected={allSelected}
            onEditRoles={canEdit ? (p) => {
              setEditingRoles(p);
              setEditingRolesValue(new Set(p.roles ?? []));
            } : undefined}
            selfCompanyId={user?.company_id ?? null}
          />
        )}

        {/* 페이지네이션 */}
        <div className="flex items-center justify-between text-sm">
          <span className="text-slate-500">전체 {totalElements}건</span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-2 py-1 rounded text-slate-400 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
            >
              ‹
            </button>
            {renderPageNumbers().map((p, i) =>
              p === '...' ? (
                <span key={`d${i}`} className="px-2 py-1 text-slate-400">…</span>
              ) : (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPage(p)}
                  className={`min-w-[28px] px-2 py-1 rounded ${
                    p === page ? 'bg-brand-600 text-white' : 'text-slate-700 hover:bg-slate-100'
                  }`}
                >
                  {p + 1}
                </button>
              )
            )}
            <button
              type="button"
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="px-2 py-1 rounded text-slate-400 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
            >
              ›
            </button>
          </div>
          <select
            value={size}
            onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
            className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm"
          >
            <option value={10}>10개씩 보기</option>
            <option value={20}>20개씩 보기</option>
            <option value={50}>50개씩 보기</option>
          </select>
        </div>
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

      {editingRoles && (
        <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4" onClick={() => setEditingRoles(null)}>
          <div className="bg-white rounded-xl shadow-xl max-w-md w-full p-5 space-y-4" onClick={(e) => e.stopPropagation()}>
            <div>
              <h3 className="text-base font-bold text-slate-900">{editingRoles.name} 의 직종 수정</h3>
              <p className="text-xs text-slate-500 mt-1">여러 개 선택 가능</p>
            </div>
            <div className="grid grid-cols-2 gap-2">
              {filterRoles.map((r) => {
                const checked = editingRolesValue.has(r);
                return (
                  <label key={r} className={`flex items-center gap-2 px-3 py-2 rounded-lg border cursor-pointer ${checked ? 'border-brand-500 bg-brand-50' : 'border-slate-200 hover:bg-slate-50'}`}>
                    <input type="checkbox" checked={checked}
                      onChange={() => {
                        setEditingRolesValue((prev) => {
                          const n = new Set(prev);
                          if (n.has(r)) n.delete(r); else n.add(r);
                          return n;
                        });
                      }}
                      className="rounded border-slate-300"
                    />
                    <span className="text-sm">{PERSON_ROLE_LABEL[r]}</span>
                  </label>
                );
              })}
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button type="button" onClick={() => setEditingRoles(null)}
                className="px-3 py-1.5 rounded text-sm text-slate-700 hover:bg-slate-100">
                취소
              </button>
              <button type="button" disabled={savingRoles}
                onClick={async () => {
                  if (!editingRoles) return;
                  setSavingRoles(true);
                  try {
                    await api.patch(`/api/persons/${editingRoles.id}`, { roles: Array.from(editingRolesValue) });
                    setEditingRoles(null);
                    void load();
                  } catch (e) {
                    if (e instanceof AxiosError) alert(e.response?.data?.message ?? '저장 실패');
                  } finally {
                    setSavingRoles(false);
                  }
                }}
                className="px-3 py-1.5 rounded bg-brand-600 text-white text-sm font-semibold hover:bg-brand-700 disabled:opacity-50">
                {savingRoles ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}
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
