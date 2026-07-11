import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import AppShell from '../../components/layout/AppShell';
import { PageHeader, StatusBadge } from '../../components/ui';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { CompanyResponse, UserResponse } from '../../types/auth';

type ReadinessItem = { ready: boolean };
type Collection = { status: string };

/**
 * F5: 협력사(하위공급사) 온보딩 위저드 — 부모(장비공급사 관리자) 시점.
 * 신규 API 없이 기존 엔드포인트만 조합해 4단계 진행상황을 실데이터로 안내한다.
 * 핵심: 협력사가 부모가 등록한 사업자번호와 "동일하게" 가입해야 자동 연결된다(AuthService resolveOrCreate).
 */
export default function OnboardingWizardPage() {
  const { user } = useAuth();
  const isMaster = !!user?.is_company_admin;
  const [childCount, setChildCount] = useState(0);
  const [pendingCount, setPendingCount] = useState(0);
  const [collectionCount, setCollectionCount] = useState(0);
  const [readyCount, setReadyCount] = useState(0);
  const [readyTotal, setReadyTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isMaster) { setLoading(false); return; }
    let alive = true;
    void (async () => {
      const kids = await api.get<CompanyResponse[]>('/api/companies/children')
        .then((r) => r.data ?? []).catch(() => [] as CompanyResponse[]);
      const pendings = await Promise.all(kids.map((c) =>
        api.get<UserResponse[]>(`/api/companies/children/${c.id}/users`)
          .then((r) => (r.data ?? []).filter((u) => !u.enabled).length).catch(() => 0),
      ));
      const cols = await api.get<Collection[]>('/api/document-collections')
        .then((r) => r.data ?? []).catch(() => [] as Collection[]);
      const ready = await api.get<ReadinessItem[]>('/api/resources/readiness')
        .then((r) => r.data ?? []).catch(() => [] as ReadinessItem[]);
      if (!alive) return;
      setChildCount(kids.length);
      setPendingCount(pendings.reduce((a, b) => a + b, 0));
      setCollectionCount(cols.length);
      setReadyCount(ready.filter((i) => i.ready).length);
      setReadyTotal(ready.length);
      setLoading(false);
    })();
    return () => { alive = false; };
  }, [isMaster]);

  if (!isMaster) {
    return (
      <AppShell>
        <div className="card border-amber-200 bg-amber-50 text-sm text-amber-900 max-w-xl mx-auto">
          이 페이지는 회사 관리자만 접근할 수 있습니다.
          <div className="mt-2"><Link to="/" className="underline">대시보드로</Link></div>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell breadcrumb={[{ label: '하위공급사 관리', to: '/sub-suppliers' }, { label: '온보딩 안내' }]}>
      <div className="space-y-4 max-w-3xl">
        <PageHeader
          title="협력사 온보딩 안내"
          subtitle="협력사(하위 공급사) 등록부터 자원 투입 준비까지 4단계로 안내합니다. 각 단계는 현재 상태를 실데이터로 표시합니다."
        />

        <div className="card border-brand-200 bg-brand-50/60 text-sm text-slate-700">
          <span className="font-semibold text-brand-800">자동 연결 규칙</span> — 협력사가{' '}
          <span className="font-semibold">부모(우리 회사)가 등록한 사업자번호와 동일하게</span> 가입해야 우리 하위 공급사로 자동 연결됩니다.
          번호가 다르면 별도 회사로 생성되어 연결되지 않습니다.
        </div>

        {loading ? (
          <div className="card text-sm text-slate-400">현황 불러오는 중…</div>
        ) : (
          <ol className="space-y-3">
            <Step
              n={1}
              title="협력사 등록 (또는 사업자번호 안내)"
              status={childCount > 0
                ? <StatusBadge tone="success">{childCount}개 등록됨</StatusBadge>
                : <StatusBadge tone="warning">미등록</StatusBadge>}
              desc="하위공급사 관리에서 협력사 회사(장비/인력)를 사업자번호로 등록합니다. 협력사가 스스로 가입한다면 위의 동일 사업자번호 규칙을 안내하세요."
              cta={<Link to="/sub-suppliers" className="btn-primary">하위공급사 관리로</Link>}
            />
            <Step
              n={2}
              title="가입 대기 승인"
              status={pendingCount > 0
                ? <StatusBadge tone="warning">{pendingCount}명 대기</StatusBadge>
                : <StatusBadge tone="neutral">대기 없음</StatusBadge>}
              desc="협력사가 자가 가입하면 로그인 비활성 상태로 대기합니다. 하위공급사 관리 화면에서 승인하면 로그인이 활성화됩니다."
              cta={<Link to="/sub-suppliers" className="btn-ghost">승인 화면으로</Link>}
            />
            <Step
              n={3}
              title="수집 링크 발송"
              status={collectionCount > 0
                ? <StatusBadge tone="brand">{collectionCount}건</StatusBadge>
                : <StatusBadge tone="neutral">없음</StatusBadge>}
              desc="서류 수집 링크를 만들어 협력사에 보내면, 무로그인으로 필요한 서류를 순서대로 업로드할 수 있습니다."
              cta={<Link to="/document-collections" className="btn-ghost">서류 수집으로</Link>}
            />
            <Step
              n={4}
              title="투입 준비 확인"
              status={readyTotal > 0
                ? <StatusBadge tone="success">{readyCount}/{readyTotal} 준비</StatusBadge>
                : <StatusBadge tone="neutral">자원 없음</StatusBadge>}
              desc="자원별 서류·검사가 완료되면 투입 대기 상태가 됩니다. 파이프라인에서 남은 준비 항목을 확인하세요."
              cta={<Link to="/resource-pipeline" className="btn-ghost">자원 파이프라인으로</Link>}
            />
          </ol>
        )}
      </div>
    </AppShell>
  );
}

function Step({ n, title, desc, status, cta }: {
  n: number; title: string; desc: string; status: ReactNode; cta: ReactNode;
}) {
  return (
    <li className="card flex gap-3">
      <div className="shrink-0 w-7 h-7 rounded-full bg-brand-600 text-white text-sm font-bold flex items-center justify-center">
        {n}
      </div>
      <div className="flex-1 min-w-0 space-y-1.5">
        <div className="flex items-center gap-2 flex-wrap">
          <h3 className="font-bold text-slate-900">{title}</h3>
          {status}
        </div>
        <p className="text-sm text-slate-600">{desc}</p>
        <div className="pt-1">{cta}</div>
      </div>
    </li>
  );
}
