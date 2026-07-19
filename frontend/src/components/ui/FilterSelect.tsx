type Props = {
  value: string;
  onChange: (v: string) => void;
  /** 빈 값(전체)일 때 보이는 라벨 = 첫 옵션. */
  placeholder: string;
  options: Array<{ value: string; label: string }>;
};

/**
 * 목록 필터용 드롭다운 — 빈 값('')이 "전체"(placeholder). 값은 문자열.
 * 사용법:
 *   <FilterSelect value={site} onChange={setSite} placeholder="현장 전체"
 *     options={sites.map((s) => ({ value: String(s.id), label: s.name }))} />
 */
export default function FilterSelect({ value, onChange, placeholder, options }: Props) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 min-w-[140px]"
    >
      <option value="">{placeholder}</option>
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  );
}
