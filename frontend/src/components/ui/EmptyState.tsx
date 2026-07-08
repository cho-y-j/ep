import type { ReactNode } from 'react';

type Props = {
  title?: string;
  text: string;
  action?: ReactNode;
  icon?: ReactNode;
};

/** 빈 상태 — 텍스트 + 선택적 CTA. 다음 액션 항상 제시. */
export default function EmptyState({ title, text, action, icon }: Props) {
  return (
    <div className="card flex flex-col items-center justify-center text-center py-10">
      {icon && <div className="mb-3 text-slate-300">{icon}</div>}
      {title && <div className="text-sm font-medium text-slate-700 mb-1">{title}</div>}
      <p className="text-xs text-slate-500 mb-4 max-w-sm">{text}</p>
      {action}
    </div>
  );
}
