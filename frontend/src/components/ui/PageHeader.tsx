import type { ReactNode } from 'react';

type Props = {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
};

/** 페이지 상단 헤더 — 제목 + 부제 + 우상단 액션 슬롯. 일관된 밀도. */
export default function PageHeader({ title, subtitle, actions }: Props) {
  return (
    <div className="page-header">
      <div className="min-w-0">
        <h1 className="h1-page truncate">{title}</h1>
        {subtitle && <p className="subtitle truncate">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  );
}
