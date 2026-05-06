import { useState, type ReactNode } from 'react';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import type { BreadcrumbItem } from './TopBar';

type Props = {
  children: ReactNode;
  breadcrumb?: BreadcrumbItem[];
  searchPlaceholder?: string;
};

export default function AppShell({ children, breadcrumb, searchPlaceholder }: Props) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="min-h-screen flex bg-slate-50">
      <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} />
      <div className="flex-1 min-w-0 flex flex-col">
        <TopBar breadcrumb={breadcrumb} searchPlaceholder={searchPlaceholder} />
        <main className="flex-1 px-8 py-6">{children}</main>
      </div>
    </div>
  );
}
