import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { useSupplierIncomingCounts } from '../../lib/useSupplierIncomingCounts';
import TodayTasksRow from './TodayTasksRow';

/** 공급사 대시보드 상단 "오늘 할 일" — 받은 요청 / 만료 임박 서류 / 미서명 일일확인 / 투입 요청(수락된 자원).
 *  받은 요청은 기존 훅(useSupplierIncomingCounts) 재사용, 만료 임박은 대시보드 요약값 전달. */
export default function SupplierTodayTasksWidget({ role, companyId, expiringDocsCount }: {
  role: 'EQUIPMENT_SUPPLIER' | 'MANPOWER_SUPPLIER';
  companyId?: number | null;
  expiringDocsCount: number;
}) {
  const incoming = useSupplierIncomingCounts(companyId);
  const [unsignedLog, setUnsignedLog] = useState<number | null>(null);
  const [candidates, setCandidates] = useState<number | null>(null);

  useEffect(() => {
    let cancelled = false;
    const isEquip = role === 'EQUIPMENT_SUPPLIER';
    Promise.all([
      api.get<Array<{ sign_status: string }>>('/api/daily-work-logs').then((r) => r.data).catch(() => []),
      api.get<any[]>(isEquip ? '/api/bp-dispatched/supplier-equipment' : '/api/bp-dispatched/supplier-persons')
        .then((r) => r.data).catch(() => []),
    ]).then(([logs, cands]) => {
      if (cancelled) return;
      setUnsignedLog(logs.filter((x) => x.sign_status === 'UNSIGNED').length);
      setCandidates(cands.length);
    });
    return () => { cancelled = true; };
  }, [role]);

  const received = incoming.supplements + incoming.checks + incoming.compliance;
  const loading = unsignedLog === null || candidates === null;

  return (
    <TodayTasksRow
      loading={loading}
      tasks={[
        { label: '받은 요청', count: received, to: '/document-management' },
        { label: '만료 임박 서류', count: expiringDocsCount, to: '/document-management' },
        { label: '미서명 일일확인', count: unsignedLog ?? 0, to: '/daily-work-logs' },
        { label: '투입 요청', count: candidates ?? 0, to: '/field-deployments/supplier' },
      ]}
    />
  );
}
