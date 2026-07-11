import { useEffect, useState } from 'react';
import { api } from './api';

type Counts = { supplements: number; checks: number; compliance: number; collections: number };

/** 공급사 사이드바 "받은" 항목 옆 배지용 — OPEN/REQUESTED/SUBMITTED 카운트. 60초 polling. */
export function useSupplierIncomingCounts(companyId: number | null | undefined): Counts {
  const [counts, setCounts] = useState<Counts>({ supplements: 0, checks: 0, compliance: 0, collections: 0 });

  useEffect(() => {
    if (!companyId) return;
    let cancelled = false;
    const fetchAll = async () => {
      try {
        const [s, c, o, dc] = await Promise.all([
          api.get<any[]>('/api/document-supplements').then((r) => r.data).catch(() => []),
          api.get<any[]>('/api/resource-checks/supplier-list').then((r) => r.data).catch(() => []),
          api.get<any[]>('/api/compliance-orders', { params: { scope: 'supplier' } }).then((r) => r.data).catch(() => []),
          api.get<any[]>('/api/document-collections').then((r) => r.data).catch(() => []),
        ]);
        if (cancelled) return;
        setCounts({
          supplements: s.filter((x) => x.target_supplier_company_id === companyId && x.status === 'OPEN').length,
          checks: c.filter((x) => x.status === 'REQUESTED').length,
          compliance: o.filter((x) => x.status === 'REQUESTED' || x.status === 'OVERDUE').length,
          collections: dc.filter((x) => x.status === 'SUBMITTED').length,
        });
      } catch { /* keep prev */ }
    };
    void fetchAll();
    const id = window.setInterval(fetchAll, 60_000);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [companyId]);

  return counts;
}
