import { useMemo, useState } from 'react';
import { PERSON_ROLE_LABEL, PERSON_STATUS_LABEL, type PersonResponse, type PersonRole, type PersonStatus } from '../../types/person';
import type { CompanyType } from '../../types/auth';
import Avatar from '../../components/Avatar';

const SUPPLIER_TYPE_CHIP: Record<CompanyType, string> = {
  BP: 'bg-blue-100 text-blue-800 border-blue-200',
  EQUIPMENT: 'bg-emerald-100 text-emerald-800 border-emerald-200',
  MANPOWER: 'bg-amber-100 text-amber-800 border-amber-200',
  SAFETY_INSPECTION: 'bg-indigo-100 text-indigo-800 border-indigo-200',
};
const SUPPLIER_TYPE_LABEL: Record<CompanyType, string> = {
  BP: 'BP',
  EQUIPMENT: '장비',
  MANPOWER: '인력',
  SAFETY_INSPECTION: '점검',
};

type Props = {
  persons: PersonResponse[];
  onRowClick: (p: PersonResponse) => void;
  selectedIds?: Set<number>;
  onToggleSelect?: (id: number) => void;
  onToggleSelectAll?: () => void;
  allSelected?: boolean;
  /** 직종 셀의 ✏ 클릭 시 호출. 미전달 시 편집 버튼 미노출. */
  onEditRoles?: (p: PersonResponse) => void;
  /** 현재 사용자 회사 id — 자기 직속 vs 외부 공급사 chip 구분용. */
  selfCompanyId?: number | null;
};

const STATUS_CLS: Record<PersonStatus, string> = {
  WORKING: 'bg-emerald-100 text-emerald-700',
  VACATION: 'bg-amber-100 text-amber-700',
  RETIRED: 'bg-rose-100 text-rose-700',
};

type SortKey = 'name' | 'roles' | 'supplier' | 'phone' | 'registered' | 'status' | 'docs';
type SortDir = 'asc' | 'desc';

const STATUS_ORDER: Record<PersonStatus, number> = { WORKING: 0, VACATION: 1, RETIRED: 2 };

function compareBy(a: PersonResponse, b: PersonResponse, key: SortKey): number {
  const cmp = (x: string | number | null | undefined, y: string | number | null | undefined): number => {
    if (x == null && y == null) return 0;
    if (x == null) return 1;
    if (y == null) return -1;
    if (typeof x === 'number' && typeof y === 'number') return x - y;
    return String(x).localeCompare(String(y), 'ko');
  };
  switch (key) {
    case 'name': return cmp(a.name, b.name);
    case 'roles': {
      const ax = a.roles?.[0] ? PERSON_ROLE_LABEL[a.roles[0] as PersonRole] : null;
      const bx = b.roles?.[0] ? PERSON_ROLE_LABEL[b.roles[0] as PersonRole] : null;
      return cmp(ax, bx);
    }
    case 'supplier': return cmp(a.supplier_name, b.supplier_name);
    case 'phone': return cmp(a.phone, b.phone);
    case 'registered': return cmp(a.created_at, b.created_at);
    case 'status': return cmp(STATUS_ORDER[a.status] ?? 99, STATUS_ORDER[b.status] ?? 99);
    case 'docs': return cmp(a.document_count ?? 0, b.document_count ?? 0);
  }
}

export default function PersonTable({
  persons, onRowClick,
  selectedIds, onToggleSelect, onToggleSelectAll, allSelected,
  onEditRoles, selfCompanyId,
}: Props) {
  const selectable = !!onToggleSelect;
  const colCount = (selectable ? 1 : 0) + 8;

  const [sortKey, setSortKey] = useState<SortKey | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const sortedPersons = useMemo(() => {
    if (!sortKey) return persons;
    const arr = persons.slice();
    arr.sort((a, b) => {
      const r = compareBy(a, b, sortKey);
      return sortDir === 'asc' ? r : -r;
    });
    return arr;
  }, [persons, sortKey, sortDir]);

  const toggleSort = (key: SortKey) => {
    if (sortKey !== key) { setSortKey(key); setSortDir('asc'); return; }
    if (sortDir === 'asc') { setSortDir('desc'); return; }
    setSortKey(null);
  };

  const SortHeader = ({ k, label }: { k: SortKey; label: string }) => {
    const active = sortKey === k;
    const arrow = !active ? '↕' : sortDir === 'asc' ? '↑' : '↓';
    return (
      <button type="button" onClick={() => toggleSort(k)}
        className={`inline-flex items-center gap-1 font-medium ${active ? 'text-slate-900' : 'text-slate-500 hover:text-slate-700'}`}>
        <span>{label}</span>
        <span className={`text-[10px] ${active ? 'text-brand-600' : 'text-slate-300'}`}>{arrow}</span>
      </button>
    );
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="border-b border-slate-200">
          <tr className="text-left text-slate-500 text-xs">
            {selectable && (
              <th className="px-4 py-3 w-10">
                <input
                  type="checkbox"
                  checked={allSelected ?? false}
                  onChange={onToggleSelectAll}
                  className="rounded border-slate-300"
                />
              </th>
            )}
            <th className="px-4 py-3"><SortHeader k="name" label="이름" /></th>
            <th className="px-4 py-3"><SortHeader k="roles" label="직종" /></th>
            <th className="px-4 py-3"><SortHeader k="supplier" label="소속" /></th>
            <th className="px-4 py-3"><SortHeader k="phone" label="연락처" /></th>
            <th className="px-4 py-3"><SortHeader k="registered" label="등록일" /></th>
            <th className="px-4 py-3"><SortHeader k="status" label="상태" /></th>
            <th className="px-4 py-3"><SortHeader k="docs" label="첨부 서류" /></th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {sortedPersons.map((p) => {
            const checked = selectedIds?.has(p.id) ?? false;
            const registered = p.created_at?.slice(0, 10).replace(/-/g, '. ') ?? '-';
            const statusCls = STATUS_CLS[p.status] ?? 'bg-slate-100 text-slate-600';
            return (
              <tr
                key={p.id}
                onClick={() => onRowClick(p)}
                className="cursor-pointer hover:bg-slate-50"
              >
                {selectable && (
                  <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => onToggleSelect?.(p.id)}
                      className="rounded border-slate-300"
                    />
                  </td>
                )}
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <Avatar
                      fetchUrl={p.has_photo ? `/api/persons/${p.id}/photo` : undefined}
                      fallbackText={p.name}
                      alt={p.name}
                      size={40}
                    />
                    <div className="min-w-0">
                      <div className="font-semibold text-slate-900 truncate">{p.name}</div>
                      <div className="text-xs text-slate-500 mt-0.5 font-mono">{p.employee_no ?? '-'}</div>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3" onClick={(e) => e.stopPropagation()}>
                  <div className="flex items-center gap-1.5 flex-wrap">
                    {p.roles && p.roles.length > 0 ? (
                      p.roles.map((r) => (
                        <span key={r} className="inline-flex px-1.5 py-0.5 rounded-full bg-brand-50 text-brand-700 text-[11px] font-semibold">
                          {PERSON_ROLE_LABEL[r as PersonRole] ?? r}
                        </span>
                      ))
                    ) : (
                      <span className="text-xs text-slate-400">미지정</span>
                    )}
                    {onEditRoles && (
                      <button type="button" onClick={() => onEditRoles(p)}
                        className="text-[11px] px-1.5 py-0.5 rounded border border-slate-300 text-slate-600 hover:bg-slate-50">
                        ✎
                      </button>
                    )}
                  </div>
                  {p.job_title && <div className="text-[11px] text-slate-400 mt-0.5">{p.job_title}</div>}
                </td>
                <td className="px-4 py-3">
                  {p.supplier_name ? (
                    <div className="flex items-center gap-1.5 mb-0.5">
                      {p.supplier_type && (
                        <span className={`inline-flex px-1.5 py-0.5 rounded text-[10px] font-bold border ${SUPPLIER_TYPE_CHIP[p.supplier_type]}`}>
                          {SUPPLIER_TYPE_LABEL[p.supplier_type]}
                        </span>
                      )}
                      <span className={`text-[12px] font-semibold ${selfCompanyId != null && p.supplier_id === selfCompanyId ? 'text-emerald-700' : 'text-slate-800'}`}>
                        {p.supplier_name}
                      </span>
                    </div>
                  ) : null}
                  <div className="text-slate-700">{p.team ?? '-'}</div>
                  {p.current_site_id ? (
                    <div className="text-xs text-blue-700 mt-0.5 truncate">@ {p.current_site_name ?? `현장 #${p.current_site_id}`}</div>
                  ) : null}
                </td>
                <td className="px-4 py-3 text-slate-700">{p.phone ?? '-'}</td>
                <td className="px-4 py-3 text-slate-700">{registered}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-semibold ${statusCls}`}>
                    {PERSON_STATUS_LABEL[p.status]}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-700">{p.document_count ?? 0}</td>
              </tr>
            );
          })}
          {persons.length === 0 && (
            <tr>
              <td colSpan={colCount} className="px-4 py-12 text-center text-slate-400">
                인원 없음
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
