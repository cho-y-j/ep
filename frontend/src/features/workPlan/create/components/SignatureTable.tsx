import type { PersonResponse } from '../../../../types/person';

interface SignatureTableProps {
  values: Record<string, any>;
  onChange: (key: string, val: any) => void;
  persons: PersonResponse[];
}

/** p1_signatures 전용 — 5행 (작성/담당/확인/검토/승인) × {이름(인력 dropdown + 직접입력) | 직위 | 일자}. */
const SIGNATURE_ROWS = [
  { idx: 0, label: '작성자', defaultPosition: 'Biz.P 현장소장' },
  { idx: 1, label: '담당자', defaultPosition: 'SKEP 관리감독자' },
  { idx: 2, label: '확인자', defaultPosition: 'SKEP HYPER' },
  { idx: 3, label: '검토자', defaultPosition: 'SKEP 안전관리자' },
  { idx: 4, label: '승인자', defaultPosition: 'SKEP 현장총괄' },
];

export function SignatureTable({ values, onChange, persons }: SignatureTableProps) {
  return (
    <div className="space-y-1.5">
      <div className="grid grid-cols-[60px_1fr_1fr_110px] gap-1.5 text-[10px] font-semibold text-slate-500 px-1">
        <div>구분</div>
        <div>이름 (인력 선택)</div>
        <div>직위</div>
        <div>일자</div>
      </div>
      {SIGNATURE_ROWS.map((r) => {
        const nameKey = `sig${r.idx}_name`;
        const posKey = `sig${r.idx}_position`;
        const dateKey = `sig${r.idx}_date`;
        const currentName = values[nameKey] || '';
        const selectedPerson = persons.find((p) => p.name === currentName);
        return (
          <div key={r.idx} className="grid grid-cols-[60px_1fr_1fr_110px] gap-1.5 items-center">
            <div className="text-[11px] font-medium text-slate-700 bg-slate-100 px-1.5 py-1.5 rounded text-center">
              {r.label}
            </div>
            <div className="relative">
              <select
                value={selectedPerson?.id ?? '__manual__'}
                onChange={(e) => {
                  const v = e.target.value;
                  if (v === '__manual__') return;
                  if (v === '__clear__') {
                    onChange(nameKey, '');
                    return;
                  }
                  const p = persons.find((x) => x.id === Number(v));
                  if (p) onChange(nameKey, p.name);
                }}
                className="w-full border border-slate-300 rounded px-1.5 py-1 text-xs bg-white"
              >
                <option value="__manual__">{currentName || '-- 인력 선택 or 직접입력 --'}</option>
                <option value="__clear__">(지우기)</option>
                {persons.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                    {p.job_title ? ` · ${p.job_title}` : ''}
                  </option>
                ))}
              </select>
              <input
                data-field-key={nameKey}
                type="text"
                value={currentName}
                onChange={(e) => onChange(nameKey, e.target.value)}
                placeholder="직접 입력"
                className="w-full border border-slate-300 rounded px-1.5 py-1 text-xs mt-0.5"
              />
            </div>
            <input
              data-field-key={posKey}
              type="text"
              value={values[posKey] ?? r.defaultPosition}
              onChange={(e) => onChange(posKey, e.target.value)}
              className="w-full border border-slate-300 rounded px-1.5 py-1 text-xs"
            />
            <input
              data-field-key={dateKey}
              type="date"
              value={values[dateKey] || ''}
              onChange={(e) => onChange(dateKey, e.target.value)}
              className="w-full border border-slate-300 rounded px-1.5 py-1 text-xs"
            />
          </div>
        );
      })}
    </div>
  );
}
