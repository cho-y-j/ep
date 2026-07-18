import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import TodayTasksRow from './TodayTasksRow';

/** BP 대시보드 상단 "오늘 할 일" — 받은 심사 / 서명 대기 / 소급 승인 / 미서명 작업확인 / 받은 투입 요청.
 *  각 카드 클릭 시 해당 화면으로 이동. 카운트는 기존 목록 엔드포인트 재사용. */
export default function BpPendingQueueWidget() {
  const [counts, setCounts] = useState<Record<string, number> | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<Array<{ read_at: string | null }>>('/api/document-reviews/received').then((r) => r.data).catch(() => []),
      api.get<{ count: number }>('/api/dashboards/bp/pending-signatures').then((r) => r.data.count).catch(() => 0),
      api.get<Array<{ mode: string }>>('/api/resource-onboardings/bp').then((r) => r.data).catch(() => []),
      api.get<Array<{ sign_status: string }>>('/api/daily-work-logs').then((r) => r.data).catch(() => []),
      api.get<Array<{ status: string }>>('/api/field-deployments/bp').then((r) => r.data).catch(() => []),
    ]).then(([reviews, signCount, onboardings, logs, deploys]) => {
      if (cancelled) return;
      setCounts({
        review: reviews.filter((x) => !x.read_at).length,
        sign: signCount,
        onboarding: onboardings.filter((x) => x.mode === 'REQUESTED').length,
        unsignedLog: logs.filter((x) => x.sign_status === 'UNSIGNED').length,
        deploy: deploys.filter((x) => x.status === 'REQUESTED').length,
      });
    });
    return () => { cancelled = true; };
  }, []);

  return (
    <TodayTasksRow
      loading={counts === null}
      tasks={counts === null ? [] : [
        { label: '받은 서류 심사', count: counts.review, to: '/document-reviews/received' },
        { label: '서명 대기', count: counts.sign, to: '/work-plans' },
        { label: '소급 승인 대기', count: counts.onboarding, to: '/resource-onboardings/bp' },
        { label: '미서명 작업확인', count: counts.unsignedLog, to: '/daily-work-logs/bp' },
        { label: '받은 투입 요청', count: counts.deploy, to: '/field-deployments/bp' },
      ]}
    />
  );
}
