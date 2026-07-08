import { useEffect, useMemo, useState } from 'react';

interface SpecItem {
  manufacturer: string;
  model: string;
  category: string;
  subCategory?: string;
  fileName: string;
  path: string;
}

/** S-9-D: 장비 제원표 사이드 패널 (build-time JSON 인덱스 기반). */
export function SpecsSidebar() {
  const [specs, setSpecs] = useState<SpecItem[]>([]);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [subCategory, setSubCategory] = useState('');
  const [manufacturer, setManufacturer] = useState('');

  useEffect(() => {
    fetch('/equipment-specs.json')
      .then((r) => (r.ok ? r.json() : []))
      .then((data: SpecItem[]) => setSpecs(Array.isArray(data) ? data : []))
      .catch(() => setSpecs([]));
  }, []);

  const categories = useMemo(
    () => Array.from(new Set(specs.map((s) => s.category))).sort(),
    [specs]
  );
  const subCategories = useMemo(() => {
    if (!category) return [];
    return Array.from(
      new Set(
        specs
          .filter((s) => s.category === category)
          .map((s) => s.subCategory)
          .filter(Boolean) as string[]
      )
    ).sort();
  }, [specs, category]);
  const manufacturers = useMemo(
    () => Array.from(new Set(specs.map((s) => s.manufacturer))).sort(),
    [specs]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return specs.filter((s) => {
      if (category && s.category !== category) return false;
      if (subCategory && s.subCategory !== subCategory) return false;
      if (manufacturer && s.manufacturer !== manufacturer) return false;
      if (q) {
        return (
          s.manufacturer.toLowerCase().includes(q) ||
          s.model.toLowerCase().includes(q) ||
          s.fileName.toLowerCase().includes(q) ||
          s.category.toLowerCase().includes(q) ||
          (s.subCategory ?? '').toLowerCase().includes(q)
        );
      }
      return true;
    });
  }, [specs, query, category, subCategory, manufacturer]);

  return (
    <aside className="hidden lg:block w-72 shrink-0">
      <div className="sticky top-4 card p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700">장비 제원표</span>
          <span className="text-[10px] text-slate-500 tabular-nums">
            {filtered.length}/{specs.length}
          </span>
        </div>
        <input
          type="text"
          placeholder="검색 — 제조사·모델·카테고리"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full text-xs border border-slate-300 rounded px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-300"
        />
        <div className="grid grid-cols-2 gap-1">
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setSubCategory('');
            }}
            className="text-[10px] border border-slate-300 rounded px-1.5 py-1 bg-white truncate"
          >
            <option value="">전체 카테고리</option>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
          <select
            value={manufacturer}
            onChange={(e) => setManufacturer(e.target.value)}
            className="text-[10px] border border-slate-300 rounded px-1.5 py-1 bg-white truncate"
          >
            <option value="">전체 제조사</option>
            {manufacturers.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </div>
        {category && subCategories.length > 0 && (
          <select
            value={subCategory}
            onChange={(e) => setSubCategory(e.target.value)}
            className="w-full text-[10px] border border-slate-300 rounded px-1.5 py-1 bg-white"
          >
            <option value="">{category} 전체</option>
            {subCategories.map((sc) => (
              <option key={sc} value={sc}>
                {sc}
              </option>
            ))}
          </select>
        )}
        {(query || category || subCategory || manufacturer) && (
          <button
            type="button"
            onClick={() => {
              setQuery('');
              setCategory('');
              setSubCategory('');
              setManufacturer('');
            }}
            className="text-[10px] text-slate-500 hover:text-slate-700 underline"
          >
            필터 초기화
          </button>
        )}
        <ul className="border border-slate-200 rounded divide-y divide-slate-100 max-h-[60vh] overflow-y-auto bg-white">
          {filtered.length === 0 ? (
            <li className="px-2 py-3 text-[10px] text-slate-400 italic text-center">
              {specs.length === 0 ? '제원표 인덱스 로딩 중...' : '검색 결과 없음'}
            </li>
          ) : (
            filtered.slice(0, 200).map((s, i) => (
              <li
                key={i}
                className="px-2 py-1.5 hover:bg-blue-50 transition cursor-pointer"
                title={`PDF 열기 — ${s.fileName}`}
                onClick={() => window.open(`/specs/${encodeURI(s.path)}`, '_blank', 'noopener')}
              >
                <div className="text-[11px] font-medium text-slate-800 truncate flex items-center gap-1">
                  <span className="text-blue-500">📄</span>
                  {s.manufacturer} <span className="text-slate-500">{s.model}</span>
                </div>
                <div className="text-[9px] text-slate-400">
                  {s.category}
                  {s.subCategory ? ` · ${s.subCategory}` : ''}
                </div>
              </li>
            ))
          )}
        </ul>
        {filtered.length > 200 && (
          <div className="text-[10px] text-slate-400 italic text-center">
            상위 200개 표시 — 검색으로 좁혀보세요
          </div>
        )}
      </div>
    </aside>
  );
}
