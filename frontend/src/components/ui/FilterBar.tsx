import type { ReactNode } from 'react';

type Props = {
  search?: {
    value: string;
    placeholder?: string;
    onChange: (v: string) => void;
  };
  children?: ReactNode;
  trailing?: ReactNode;
};

/** 검색 + 필터들 + 우측 액션 슬롯. 모든 목록 페이지 공통. */
export default function FilterBar({ search, children, trailing }: Props) {
  return (
    <div className="flex flex-wrap items-center gap-2 mb-3">
      {search && (
        <div className="relative flex-1 min-w-[200px]">
          <input
            type="search"
            value={search.value}
            onChange={(e) => search.onChange(e.target.value)}
            placeholder={search.placeholder ?? '검색...'}
            className="input pl-8"
          />
          <svg
            className="absolute left-2.5 top-2.5 w-4 h-4 text-slate-400"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.3-4.3" />
          </svg>
        </div>
      )}
      {children}
      {trailing && <div className="ml-auto flex items-center gap-2">{trailing}</div>}
    </div>
  );
}
