import type { ReactNode } from 'react';
import SearchInput from './SearchInput';

type Props = {
  search?: {
    value: string;
    placeholder?: string;
    onChange: (v: string) => void;
    /** 서버 검색 등 비용 큰 필터용 지연(SearchInput debounceMs 전달). */
    debounceMs?: number;
  };
  /** 필터 컨트롤(FilterSelect·select 등). */
  children?: ReactNode;
  /** 정렬 슬롯 — 우측 정렬 영역에 표시. */
  sort?: ReactNode;
  /** 우측 액션 슬롯(다운로드 등). */
  trailing?: ReactNode;
  /** >0 이면 활성 필터 카운트 배지 + 초기화 버튼 노출. */
  activeFilterCount?: number;
  /** 초기화 버튼 클릭. activeFilterCount>0 일 때만 버튼 표시. */
  onReset?: () => void;
};

/**
 * 검색 + 필터들 + (활성 카운트·초기화) + 정렬/우측 액션. 모든 목록 페이지 공통.
 * 사용법:
 *   <FilterBar search={{ value: q, onChange: setQ, placeholder: '검색' }}
 *             activeFilterCount={active} onReset={resetFilters}>
 *     <FilterSelect ... />   // 필터 컨트롤들
 *   </FilterBar>
 * search 만 넘기던 기존 사용법(EquipmentPage)과 하위호환.
 */
export default function FilterBar({ search, children, sort, trailing, activeFilterCount, onReset }: Props) {
  const active = activeFilterCount ?? 0;
  return (
    <div className="flex flex-wrap items-center gap-2 mb-3">
      {search && (
        <SearchInput
          value={search.value}
          onChange={search.onChange}
          placeholder={search.placeholder}
          debounceMs={search.debounceMs}
          className="flex-1 min-w-[200px]"
        />
      )}
      {children}
      {active > 0 && (
        <span className="inline-flex items-center rounded-full bg-brand-600 px-2 py-0.5 text-[11px] font-semibold text-white">
          필터 {active}
        </span>
      )}
      {active > 0 && onReset && (
        <button
          type="button"
          onClick={onReset}
          className="text-xs font-medium text-slate-500 hover:text-slate-800"
        >
          초기화
        </button>
      )}
      {(sort || trailing) && (
        <div className="ml-auto flex items-center gap-2">
          {sort}
          {trailing}
        </div>
      )}
    </div>
  );
}
