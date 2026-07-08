import type { ReactNode } from 'react';

type Props = {
  children: ReactNode;
  className?: string;
};

/** 페이지 내용 일관 폭 — AppShell 안에서 한 번 더 좁히고 싶을 때만 사용. AppShell 기본 1440. */
export default function PageContainer({ children, className }: Props) {
  return <div className={`w-full ${className ?? ''}`}>{children}</div>;
}
