import { useEffect, useState } from 'react';
import { api } from '../../lib/api';
import { useAuth } from '../auth/AuthContext';
import type { CompanyResponse } from '../../types/auth';

/**
 * V77 대행 등록: 장비공급사 회사 관리자가 소유주로 지정할 수 있는 직속 하위공급사(협력사) 목록.
 * SubSuppliersPage 와 동일한 /api/companies/children 를 재사용. 권한/자식 없으면 빈 배열.
 */
export function useSubSuppliers(): CompanyResponse[] {
  const { user } = useAuth();
  const enabled = user?.role === 'EQUIPMENT_SUPPLIER' && !!user?.is_company_admin;
  const [children, setChildren] = useState<CompanyResponse[]>([]);

  useEffect(() => {
    if (!enabled) { setChildren([]); return; }
    let alive = true;
    api.get<CompanyResponse[]>('/api/companies/children')
      .then((res) => { if (alive) setChildren(res.data ?? []); })
      .catch(() => { if (alive) setChildren([]); });
    return () => { alive = false; };
  }, [enabled]);

  return children;
}
