import { useEffect, useRef, useState } from 'react';

/**
 * S-9-E: BroadcastChannel 기반 폼 ↔ OnlyOffice 새 탭 동기화 훅.
 *
 * 워드 새 탭에서 편집기를 열면 채널을 만들고, 폼 변경 시 reload 메시지를 보낸다.
 * 새 탭은 메시지 수신 시 새 sessionId 로 재로드해 OnlyOffice cache 회피.
 */
export function useEditorSync() {
  const channelRef = useRef<BroadcastChannel | null>(null);
  const channelKeyRef = useRef<string | null>(null);
  const [active, setActive] = useState(false);
  const [syncMsg, setSyncMsg] = useState('');

  useEffect(() => {
    return () => {
      channelRef.current?.close();
    };
  }, []);

  /** 첫 sessionId 로 채널 시작. 동일 채널 키로 이후 sessionId 만 갱신. */
  const start = (sessionId: string) => {
    if (channelRef.current) channelRef.current.close();
    channelKeyRef.current = sessionId;
    channelRef.current = new BroadcastChannel('skep-worksheet-' + sessionId);
    setActive(true);
    setSyncMsg('편집기 새 탭 열림 · 폼 변경 시 자동 반영');
  };

  /** 새 sessionId 로 reload 메시지 송신 — 새 탭이 받아서 OnlyOffice 재로드. */
  const sendReload = (newSessionId: string) => {
    if (!channelRef.current) return;
    channelRef.current.postMessage({ type: 'reload', sessionId: newSessionId });
    setSyncMsg(`반영됨 (${new Date().toLocaleTimeString('ko-KR')})`);
  };

  const fail = (err: string) => setSyncMsg('자동 반영 실패: ' + err);

  const stop = () => {
    channelRef.current?.close();
    channelRef.current = null;
    channelKeyRef.current = null;
    setActive(false);
    setSyncMsg('');
  };

  return { active, syncMsg, start, sendReload, fail, stop, channelKey: channelKeyRef };
}
