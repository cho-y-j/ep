import type { ReactNode } from 'react';

export type Column<T> = {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  width?: string;
  className?: string;
};

type Props<T> = {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  empty?: ReactNode;
  onRowClick?: (row: T) => void;
};

/** 컴팩트 테이블 — 모든 목록 페이지에서 동일 톤. */
export default function DataTable<T>({ columns, rows, rowKey, empty, onRowClick }: Props<T>) {
  if (rows.length === 0) {
    return (
      <div className="card flex items-center justify-center py-10 text-sm text-slate-400">
        {empty ?? '데이터가 없습니다'}
      </div>
    );
  }
  return (
    <div className="card p-0 overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={`tbl-th ${c.className ?? ''}`} style={c.width ? { width: c.width } : undefined}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
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
