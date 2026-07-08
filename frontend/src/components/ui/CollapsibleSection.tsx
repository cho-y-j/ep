import { useState, type ReactNode } from 'react';

type Props = {
  id?: string;
  title: string;
  summary?: ReactNode;
  status?: ReactNode;
  defaultOpen?: boolean;
  /** true면 접혀있어도 children을 mount 유지 (CSS hidden). 사인 패널처럼 ref/state 잃으면 안 되는 컴포넌트용. */
  keepMounted?: boolean;
  children: ReactNode;
};

/**
 * 작업계획서 등 긴 폼에서 사용. 펼쳐지면 children 노출, 접히면 summary 만 한 줄.
 * 핵심: 사용자가 처음 진입 시 메인 영역이 너무 빽빽하지 않도록 — 기본 입력만 펼쳐두고 나머지는 접기.
 */
export default function CollapsibleSection({ id, title, summary, status, defaultOpen = false, keepMounted = false, children }: Props) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section id={id} className="card p-0 overflow-hidden">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between gap-3 px-4 py-3 hover:bg-slate-50 transition-colors"
      >
        <div className="flex items-center gap-2 min-w-0">
          <span className={`shrink-0 inline-flex items-center justify-center w-4 h-4 text-slate-400 transition-transform ${open ? 'rotate-90' : ''}`}>
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </span>
          <span className="text-sm font-semibold text-slate-900 truncate">{title}</span>
          {status && <span className="shrink-0">{status}</span>}
        </div>
        {!open && summary && <div className="shrink-0 text-xs text-slate-500 truncate">{summary}</div>}
      </button>
      {keepMounted ? (
        <div className={`border-t border-slate-100 ${open ? '' : 'hidden'}`}>{children}</div>
      ) : (
        open && <div className="border-t border-slate-100">{children}</div>
      )}
    </section>
  );
}
