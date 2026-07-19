import { useEffect, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import Sidebar from './Sidebar';
import TopBar from './TopBar';
import type { BreadcrumbItem } from './TopBar';

type Props = {
  children: ReactNode;
  breadcrumb?: BreadcrumbItem[];
};

export default function AppShell({ children, breadcrumb }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const { pathname } = useLocation();

  // 라우트 이동 시 모바일 드로어 닫기 (링크 클릭 후 콘텐츠를 가리지 않도록).
  useEffect(() => { setMobileOpen(false); }, [pathname]);

  return (
    <div className="min-h-screen flex bg-slate-50">
      <Sidebar
        collapsed={collapsed}
        onToggle={() => setCollapsed((v) => !v)}
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
      />
      <div className="flex-1 min-w-0 flex flex-col">
        <TopBar breadcrumb={breadcrumb} onMenuClick={() => setMobileOpen(true)} />
        <main className="flex-1 px-4 md:px-6 py-4 md:py-5">
          <div className="max-w-[1440px] w-full mx-auto">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
