import { useState, type ReactNode } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import type { BreadcrumbItem } from './TopBar';

type Props = {
  children: ReactNode;
  breadcrumb?: BreadcrumbItem[];
};

export default function AppShell({ children, breadcrumb }: Props) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="min-h-screen flex bg-slate-50">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} />
      <div className="flex-1 min-w-0 flex flex-col">
        <TopBar breadcrumb={breadcrumb} />
        <main className="flex-1 px-4 md:px-6 py-4 md:py-5">
          <div className="max-w-[1440px] w-full mx-auto">{children}</div>
        </main>
      </div>
    </div>
  );
}
