import { useEffect, useState } from 'react';
import { api } from '../../lib/api';

type Onboarding = {
  id: number;
  mode: 'REQUESTED' | 'APPROVED' | 'VERBAL';
  site_name?: string | null;
  approved_at?: string | null;
  verbal_at?: string | null;
};

const MODE_BADGE: Record<'APPROVED' | 'VERBAL', { text: string; cls: string }> = {
  APPROVED: { text: '소급 승인', cls: 'bg-emerald-100 text-emerald-700' },
  VERBAL: { text: '구두승인', cls: 'bg-blue-100 text-blue-700' },
};

/**
 * 자원 상세(장비·인원) 상태 옆 배지 — 이 자원의 확정 온보딩(소급 승인/구두승인)이 있으면 표시.
 * 데이터: GET /api/resource-onboardings/for-resource (접근권한은 자원 스코프 준수 · ADMIN 포함).
 */
export default function OnboardingBadge({ ownerType, ownerId }: { ownerType: 'EQUIPMENT' | 'PERSON'; ownerId: number }) {
  const [items, setItems] = useState<Onboarding[]>([]);

  useEffect(() => {
    let cancelled = false;
    api.get<Onboarding[]>('/api/resource-onboardings/for-resource', { params: { ownerType, ownerId } })
      .then((r) => { if (!cancelled) setItems(r.data ?? []); })
      .catch(() => { if (!cancelled) setItems([]); });
    return () => { cancelled = true; };
  }, [ownerType, ownerId]);

  return (
    <>
      {items.map((o) => {
        const badge = MODE_BADGE[o.mode as 'APPROVED' | 'VERBAL'];
        if (!badge) return null;
        const when = (o.approved_at ?? o.verbal_at ?? '').slice(0, 10);
        const tip = [o.site_name, when && `확정 ${when}`].filter(Boolean).join(' · ');
        return (
          <span
            key={o.id}
            title={tip || undefined}
            className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold ${badge.cls}`}
          >
            {badge.text}
          </span>
        );
      })}
    </>
  );
}
