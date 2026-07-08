import { useEffect, useState } from 'react';
import { api } from './api';

/** BP 사이드바 "받은 서류 심사" 배지용 — 미읽음(read_at null) 카운트. 60초 polling. */
export function useBpReceivedReviewCount(enabled: boolean): number {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const fetchCount = async () => {
      try {
        const r = await api.get<Array<{ read_at: string | null }>>('/api/document-reviews/received');
        if (cancelled) return;
        setCount(r.data.filter((x) => !x.read_at).length);
      } catch { /* keep prev */ }
    };
    void fetchCount();
    const id = window.setInterval(fetchCount, 60_000);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [enabled]);

  return count;
}
