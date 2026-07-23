import { useState, type ReactNode } from 'react';

export type SortDir = 'asc' | 'desc';

/** 범용 비교 — null/undefined 뒤로, 숫자는 수치, 나머지는 한국어 로케일 문자열(ISO 날짜 포함). */
export function compareValues(x: unknown, y: unknown): number {
  if (x == null && y == null) return 0;
  if (x == null) return 1;
  if (y == null) return -1;
  if (typeof x === 'number' && typeof y === 'number') return x - y;
  return String(x).localeCompare(String(y), 'ko');
}

/**
 * 컬럼 헤더 클릭 정렬 공용 훅 — 오름 → 내림 → 해제 3단 토글.
 * (해제 시 호출측의 기본 순서 유지 — apply 가 rows 를 그대로 반환.)
 * 사용법:
 *   const sort = useTableSort<'name' | 'date'>();
 *   const rows = sort.apply(items, (r, k) => (k === 'name' ? r.name : r.created_at));
 *   <th>{sort.header('name', '이름')}</th>
 */
export function useTableSort<K extends string>() {
  const [sortKey, setSortKey] = useState<K | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  const toggleSort = (key: K) => {
    if (sortKey !== key) { setSortKey(key); setSortDir('asc'); return; }
    if (sortDir === 'asc') { setSortDir('desc'); return; }
    setSortKey(null);
  };

  function apply<T>(rows: T[], valueOf: (row: T, key: K) => unknown): T[] {
    if (!sortKey) return rows;
    const arr = rows.slice();
    arr.sort((a, b) => {
      const r = compareValues(valueOf(a, sortKey), valueOf(b, sortKey));
      return sortDir === 'asc' ? r : -r;
    });
    return arr;
  }

  const header = (k: K, label: ReactNode): ReactNode => {
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

  return { sortKey, sortDir, toggleSort, apply, header };
}
