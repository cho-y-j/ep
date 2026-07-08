// OnlyOffice Document Server iframe 편집 페이지
// skep v1 의 WorkPlanEditor 페이지를 v2 로 이식 + React 트리 격리 패턴 유지.
//
// 동작:
//   1) 라우터 param sessionId 로 localStorage 에서 config 꺼냄 (WorkPlanCreatePage 가 미리 저장)
//   2) OnlyOffice DocsAPI.DocEditor 로 init — fullscreen iframe
//   3) BroadcastChannel('skep-worksheet-' + ch) 로 부모 페이지의 reload 메시지 수신 → 새 sessionId 로 재로드
//   4) 부모 페이지가 "작업계획서로 돌아가기" goback 클릭 시 window.close()
import { memo, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

// DocsAPI 는 WorkPlanEditPage 에서 declare 된 타입 재사용. 여기선 별도 declare 안 함.

const ONLYOFFICE_API_SCRIPT = '/onlyoffice/web-apps/apps/api/documents/api.js';

interface MountProps {
  configStr: string;
  onError: (msg: string) => void;
}

/** OnlyOffice container 를 React 트리 밖에 mount — reconciliation 충돌 회피. */
const EditorMount = memo(function EditorMount({ configStr, onError }: MountProps) {
  useEffect(() => {
    const host = document.createElement('div');
    host.id = 'onlyoffice-host';
    Object.assign(host.style, {
      position: 'fixed',
      top: '0',
      left: '0',
      width: '100vw',
      height: '100vh',
      zIndex: '40',
    });
    const inner = document.createElement('div');
    inner.id = 'onlyoffice-container';
    inner.style.width = '100%';
    inner.style.height = '100%';
    host.appendChild(inner);
    document.body.appendChild(host);

    const config = JSON.parse(configStr);
    config.events = config.events || {};
    config.events.onRequestClose = () => window.close();
    config.events.onRequestSaveAs = () => window.close();
    config.events.onError = (e: any) => {
      const d = e?.data;
      onError(typeof d === 'string' ? d : (d?.errorDescription || d?.errorCode || 'OnlyOffice 오류'));
    };

    const ensureScript = () => new Promise<void>((resolve, reject) => {
      if (window.DocsAPI) return resolve();
      const existing = document.querySelector(`script[src="${ONLYOFFICE_API_SCRIPT}"]`) as HTMLScriptElement | null;
      if (existing) {
        existing.addEventListener('load', () => resolve(), { once: true });
        existing.addEventListener('error', () => reject(new Error('OnlyOffice script load fail')), { once: true });
        return;
      }
      const s = document.createElement('script');
      s.src = ONLYOFFICE_API_SCRIPT;
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('OnlyOffice script load fail'));
      document.head.appendChild(s);
    });

    let editor: any = null;
    ensureScript().then(() => {
      if (!window.DocsAPI) {
        onError('OnlyOffice API 미로드');
        return;
      }
      editor = window.DocsAPI.DocEditor('onlyoffice-container', config);
      (window as any).docEditor = editor;
    }).catch((e: any) => onError(e?.message || 'OnlyOffice 로드 실패'));

    return () => {
      try { (editor as any)?.destroyEditor?.(); } catch { /* noop */ }
      (window as any).docEditor = undefined;
      host.remove();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [configStr]);

  return null;
});

export default function WorksheetEditorPage() {
  const { sessionId = '' } = useParams<{ sessionId: string }>();
  const [search] = useSearchParams();
  const navigate = useNavigate();
  const channelKey = search.get('ch') ?? sessionId;
  const [currentSid, setCurrentSid] = useState(sessionId);
  const [err, setErr] = useState<string | null>(null);
  const channelRef = useRef<BroadcastChannel | null>(null);

  // config string — 첫 mount 시 1회 + currentSid 변경 시 추가 1회만 읽음.
  // 읽은 직후 localStorage 에서 제거해 무한 적재 방지하면서도 리렌더 시 빈 값 안 나오게 useState 캐싱.
  const readConfig = (sid: string): string => {
    try {
      const k = `worksheet-editor-${sid}-config`;
      // 새로고침 / OnlyOffice onError 후 재시도에서도 사용 가능하도록 삭제하지 않고 보존.
      // 같은 sessionId 는 backend 에서 1회용이라 누적 우려 적음.
      return localStorage.getItem(k) || '';
    } catch {
      return '';
    }
  };
  const [configStr, setConfigStr] = useState(() => readConfig(currentSid));

  useEffect(() => {
    if (currentSid === sessionId) return;
    const v = readConfig(currentSid);
    if (v) setConfigStr(v);
  }, [currentSid, sessionId]);

  // 부모 페이지의 reload 메시지 수신
  useEffect(() => {
    if (!channelKey) return;
    const ch = new BroadcastChannel('skep-worksheet-' + channelKey);
    channelRef.current = ch;
    ch.onmessage = (ev) => {
      const msg = ev.data;
      if (msg?.type === 'reload' && msg?.sessionId) {
        setCurrentSid(msg.sessionId);
      }
    };
    return () => { ch.close(); };
  }, [channelKey]);

  if (!sessionId) {
    return <div className="p-8 text-center text-rose-600">세션 ID 가 없습니다.</div>;
  }
  if (!configStr) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center gap-3 p-8 text-center bg-rose-50">
        <h1 className="text-xl font-bold text-rose-700">⚠ OnlyOffice 편집기 설정 없음</h1>
        <p className="text-sm text-slate-700">
          이 세션 ID 의 편집기 config 가 localStorage 에 없습니다. <br/>
          작업계획서 생성 페이지의 "미리보기 ↗" / "편집 ↗" 버튼으로 새로 열어주세요.
        </p>
        <code className="text-xs text-slate-500">sessionId: {sessionId}</code>
        <button onClick={() => navigate('/work-plans/new')} className="text-sm px-3 py-1.5 rounded-lg bg-slate-900 text-white hover:bg-slate-700 mt-4">
          작업계획서 생성으로 이동
        </button>
      </div>
    );
  }

  return (
    <>
      <EditorMount configStr={configStr} onError={setErr} />
      {err && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-rose-50 border border-rose-300 text-rose-700 px-4 py-2 rounded-lg text-sm shadow">
          {err}
        </div>
      )}
    </>
  );
}
