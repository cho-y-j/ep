type Props = {
  page: number;          // 현재 0-based
  totalPages: number;
  totalElements: number;
  size: number;
  onPageChange: (page: number) => void;
  onSizeChange?: (size: number) => void;
};

const SIZES = [10, 20, 50, 100];

export default function Pagination({ page, totalPages, totalElements, size, onPageChange, onSizeChange }: Props) {
  if (totalPages <= 1 && !onSizeChange) {
    return (
      <div className="flex items-center justify-between text-sm text-slate-500 mt-4">
        <span>전체 {totalElements}건</span>
      </div>
    );
  }

  const pages: number[] = [];
  const maxButtons = 7;
  let start = Math.max(0, page - 3);
  let end = Math.min(totalPages, start + maxButtons);
  start = Math.max(0, end - maxButtons);
  for (let i = start; i < end; i++) pages.push(i);

  return (
    <div className="flex items-center justify-between mt-4 text-sm">
      <span className="text-slate-500">전체 {totalElements}건</span>
      <div className="flex items-center gap-1">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          className="px-2 py-1 rounded text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
        >
          ‹
        </button>
        {pages.map((p) => (
          <button
            key={p}
            type="button"
            onClick={() => onPageChange(p)}
            className={`min-w-[28px] px-2 py-1 rounded ${
              p === page ? 'bg-brand-600 text-white' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {p + 1}
          </button>
        ))}
        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1}
          className="px-2 py-1 rounded text-slate-600 hover:bg-slate-100 disabled:opacity-30 disabled:hover:bg-transparent"
        >
          ›
        </button>
      </div>
      {onSizeChange ? (
        <select
          value={size}
          onChange={(e) => onSizeChange(Number(e.target.value))}
          className="input bg-white max-w-[120px] py-1"
        >
          {SIZES.map((s) => (
            <option key={s} value={s}>{s}개씩 보기</option>
          ))}
        </select>
      ) : <span />}
    </div>
  );
}
