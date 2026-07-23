import { useEffect, useRef, useState } from 'react';

type Props = {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  /** 바깥 wrapper 폭 제어용 (예: 'flex-1 min-w-[200px]', 'w-56'). */
  className?: string;
  /** 지정 시 타이핑을 로컬 유지하고 onChange 를 지연 호출 — 서버 검색 등 비용 큰 필터용(200~300 권장). */
  debounceMs?: number;
};

/**
 * 돋보기 아이콘 내장 검색 입력. FilterBar 내부 + 독립 사용 공용(검색 SVG 1종 중앙화).
 * 사용법: <SearchInput value={q} onChange={setQ} placeholder="이름 검색" />
 */
export default function SearchInput({ value, onChange, placeholder, className, debounceMs }: Props) {
  const [local, setLocal] = useState(value);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;
  // 외부에서 값이 바뀌면(필터 초기화 등) 로컬 입력도 동기화.
  useEffect(() => { setLocal(value); }, [value]);
  useEffect(() => {
    if (!debounceMs || local === value) return;
    const t = setTimeout(() => onChangeRef.current(local), debounceMs);
    return () => clearTimeout(t);
  }, [local, value, debounceMs]);
  return (
    <div className={`relative ${className ?? ''}`}>
      <input
        type="search"
        value={debounceMs ? local : value}
        onChange={(e) => (debounceMs ? setLocal(e.target.value) : onChange(e.target.value))}
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
