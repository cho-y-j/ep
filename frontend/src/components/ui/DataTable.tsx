import type { ReactNode } from 'react';
import { useTableSort } from './tableSort';

export type Column<T> = {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  width?: string;
  className?: string;
  /** 지정 시 헤더 클릭 정렬 활성(오름→내림→해제). 반환값으로 비교 — null 은 뒤로. */
  sortValue?: (row: T) => string | number | null | undefined;
};

type Props<T> = {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
};

/** 컴팩트 테이블 — 모든 목록 페이지에서 동일 톤. sortValue 있는 컬럼은 헤더 클릭 정렬. */
export default function DataTable<T>({ columns, rows, rowKey, empty, onRowClick }: Props<T>) {
  const sort = useTableSort<string>();
  if (rows.length === 0) {
    return (
      <div className="card flex items-center justify-center py-10 text-sm text-slate-400">
        {empty ?? '데이터가 없습니다'}
      </div>
    );
  }
  const shown = columns.some((c) => c.sortValue)
    ? sort.apply(rows, (row, key) => columns.find((c) => c.key === key)?.sortValue?.(row))
    : rows;
  return (
    <div className="card p-0 overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={`tbl-th ${c.className ?? ''}`} style={c.width ? { width: c.width } : undefined}>
                {c.sortValue ? sort.header(c.key, c.header) : c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {shown.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={onRowClick ? 'hover:bg-slate-50 cursor-pointer' : ''}
            >
              {columns.map((c) => (
                <td key={c.key} className={`tbl-td ${c.className ?? ''}`}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
