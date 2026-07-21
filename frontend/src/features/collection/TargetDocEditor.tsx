import { useMemo, useState } from 'react';
import type { DocumentTypeResponse } from '../../types/document';
import type { Sel } from './suggest';
import { targetKey, type PickedTarget } from './target';

type Props = {
  targets: PickedTarget[];
  /** targetKey → 서류별 선택 상태(현재값). */
  sel: Record<string, Sel>;
  /** targetKey → 자동 제안값(수정됨 배지 판정 기준). */
  suggested: Record<string, Sel>;
  onChange: (key: string, next: Sel) => void;
  typesByOwner: { EQUIPMENT: DocumentTypeResponse[]; PERSON: DocumentTypeResponse[] };
};

const MODES = ['none', 'required', 'optional'] as const;
const MODE_LABEL = { none: '제외', required: '필수', optional: '선택' } as const;

const countOf = (s: Sel, mode: 'required' | 'optional') =>
  Object.values(s).filter((v) => v === mode).length;

/** 두 선택상태가 같은지 — 자동 제안과 달라졌으면 '수정됨' 배지. */
function sameSel(a: Sel, b: Sel): boolean {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)].map(Number));
  for (const k of keys) if ((a[k] ?? 'none') !== (b[k] ?? 'none')) return false;
  return true;
}

/**
 * 대상별 수집 서류 편집 — 대상마다 접힌 카드, 펼치면 서류별 제외/필수/선택 3토글.
 * 자동 제안(POST /suggest-batch)과 달라진 대상은 '수정됨' 으로 표시하고, 서류 0건 대상은 경고한다.
 */
export default function TargetDocEditor({ targets, sel, suggested, onChange, typesByOwner }: Props) {
  const [open, setOpen] = useState<Record<string, boolean>>({});

  const emptyKeys = useMemo(
    () => targets.map(targetKey).filter((k) => countOf(sel[k] ?? {}, 'required') + countOf(sel[k] ?? {}, 'optional') === 0),
    [targets, sel],
  );

  return (
    <div className="space-y-2">
      {emptyKeys.length > 0 && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-semibold text-amber-700">
          수집할 서류가 0건인 대상이 {emptyKeys.length}건 있습니다. 카드를 펼쳐 서류를 고르거나 대상에서 빼주세요.
        </p>
      )}
      {targets.map((t) => {
        const key = targetKey(t);
        const cur = sel[key] ?? {};
        const req = countOf(cur, 'required');
        const opt = countOf(cur, 'optional');
        const empty = req + opt === 0;
        const edited = suggested[key] !== undefined && !sameSel(cur, suggested[key]);
        const types = typesByOwner[t.owner_type === 'EQUIPMENT' ? 'EQUIPMENT' : 'PERSON'];
        return (
          <div key={key} className={`rounded-lg border ${empty ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'}`}>
            <button type="button" onClick={() => setOpen((s) => ({ ...s, [key]: !s[key] }))}
              className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left">
              <span className="flex min-w-0 items-center gap-2">
                <span className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                  t.owner_type === 'EQUIPMENT' ? 'bg-brand-50 text-brand-700' : 'bg-slate-100 text-slate-600'}`}>
                  {t.owner_type === 'EQUIPMENT' ? '장비' : '인력'}
                </span>
                <span className="truncate text-sm font-semibold text-slate-800">{t.label}</span>
                {t.via_equipment_label && <span className="shrink-0 text-xs text-slate-400">{t.via_equipment_label} 조종원</span>}
                {edited && <span className="shrink-0 rounded bg-blue-100 px-1.5 py-0.5 text-[11px] font-semibold text-blue-700">수정됨</span>}
              </span>
              <span className="shrink-0 text-xs text-slate-500">
                {empty ? <span className="font-semibold text-amber-700">서류 없음</span> : `필수 ${req} · 선택 ${opt}`}
                <span className="ml-2 text-slate-400">{open[key] ? '▲' : '▼'}</span>
              </span>
            </button>
            {open[key] && (
              <div className="divide-y divide-slate-100 border-t border-slate-200">
                {types.length === 0 ? <p className="p-3 text-xs text-slate-400">서류 종류가 없습니다.</p> :
                  types.map((ty) => (
                    <div key={ty.id} className="flex items-center justify-between px-3 py-2">
                      <span className="text-sm text-slate-700">{ty.name}</span>
                      <div className="inline-flex overflow-hidden rounded-md border border-slate-300 text-xs">
                        {MODES.map((v) => (
                          <button key={v} type="button" onClick={() => onChange(key, { ...cur, [ty.id]: v })}
                            className={`px-2.5 py-1 font-semibold border-r border-slate-200 last:border-r-0 ${
                              (cur[ty.id] ?? 'none') === v
                                ? v === 'required' ? 'bg-rose-600 text-white' : v === 'optional' ? 'bg-amber-500 text-white' : 'bg-slate-500 text-white'
                                : 'bg-white text-slate-600 hover:bg-slate-50'}`}>
                            {MODE_LABEL[v]}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
