type Props = {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  /** 바깥 wrapper 폭 제어용 (예: 'flex-1 min-w-[200px]', 'w-56'). */
  className?: string;
};

/**
 * 돋보기 아이콘 내장 검색 입력. FilterBar 내부 + 독립 사용 공용(검색 SVG 1종 중앙화).
 * 사용법: <SearchInput value={q} onChange={setQ} placeholder="이름 검색" />
 */
export default function SearchInput({ value, onChange, placeholder, className }: Props) {
  return (
    <div className={`relative ${className ?? ''}`}>
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder ?? '검색...'}
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
  );
}
