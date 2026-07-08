import { useEffect, useState } from 'react';
import { api } from './api';
import { useAuth } from '../features/auth/AuthContext';

/**
 * Sidebar + TopBar 가 둘 다 /api/notifications/unread-count 를 polling 하던 중복을 제거하기 위한 hook.
 * module-level cache + listener pattern. 첫 구독자가 polling 시작, 마지막 구독자 unmount 시 중지.
 */
let cachedValue = 0;
const listeners = new Set<(n: number) => void>();
let intervalId: number | null = null;

function notify(value: number) {
  cachedValue = value;
  listeners.forEach((cb) => cb(value));
}

function fetchUnread() {
  api.get<{ unread: number }>('/api/notifications/unread-count')
    .then((res) => {
      if (res.data.unread !== cachedValue) notify(res.data.unread);
    })
    .catch(() => { /* ignore */ });
}

export function useUnreadCount(): number {
  const { user } = useAuth();
  const [count, setCount] = useState(cachedValue);

  useEffect(() => {
    if (!user) return;
    listeners.add(setCount);
    setCount(cachedValue);
    if (listeners.size === 1) {
      fetchUnread();
      intervalId = window.setInterval(fetchUnread, 60_000);
    }
    return () => {
      listeners.delete(setCount);
      if (listeners.size === 0 && intervalId != null) {
        window.clearInterval(intervalId);
        intervalId = null;
      }
    };
  }, [user]);

  return count;
}
