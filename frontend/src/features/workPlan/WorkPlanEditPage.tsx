import { useEffect, useRef, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { api } from '../../lib/api';

declare global {
  interface Window {
    DocsAPI?: {
      DocEditor: (containerId: string, config: Record<string, unknown>) => unknown;
    };
  }
}

/**
 * OnlyOffice 인플레이스 편집 페이지.
 *
 * 1. 백엔드 status 확인 → enabled false 면 안내만 표시
 * 2. config 가져오기 → DocsAPI.DocEditor 로 렌더
 * 3. JS SDK 는 OnlyOffice Document Server 도메인의 web-apps/apps/api/documents/api.js
 */
export default function WorkPlanEditPage() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const templateId = searchParams.get('templateId');
  const containerRef = useRef<HTMLDivElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<{ enabled: boolean; server_url?: string } | null>(null);

  useEffect(() => {
    api.get<{ enabled: boolean; server_url?: string }>('/api/onlyoffice/status')
      .then((res) => setStatus(res.data))
      .catch(() => setStatus({ enabled: false }));
  }, []);

  useEffect(() => {
    if (!status?.enabled || !id) return;
    const serverUrl = status.server_url;
    if (!serverUrl) {
      setError('OnlyOffice 서버 URL 이 설정되지 않았습니다');
      return;
    }
    let editor: unknown = null;
    let scriptEl: HTMLScriptElement | null = null;

    (async () => {
      try {
        const cfgRes = await api.get<Record<string, unknown>>(`/api/onlyoffice/work-plan/${id}/config`, {
          params: templateId ? { templateId } : {},
        });
        // SDK 로드
        await loadScript(`${serverUrl.replace(/\/$/, '')}/web-apps/apps/api/documents/api.js`);
        if (!window.DocsAPI) {
          setError('OnlyOffice SDK 로드 실패');
          return;
        }
        editor = window.DocsAPI.DocEditor('onlyoffice-container', {
          ...cfgRes.data,
          width: '100%',
          height: '100%',
          events: {
            onError: (e: unknown) => { if (import.meta.env.DEV) console.error('[OO onError]', e); },
            onWarning: (e: unknown) => { if (import.meta.env.DEV) console.warn('[OO onWarning]', e); },
          },
        });
      } catch (err) {
        const ax = err as { response?: { data?: { message?: string } } };
        setError(ax?.response?.data?.message ?? '편집 세션 시작 실패');
      }
    })();

    return () => {
      if (editor && typeof (editor as { destroyEditor?: () => void }).destroyEditor === 'function') {
        (editor as { destroyEditor: () => void }).destroyEditor();
      }
      if (scriptEl) document.body.removeChild(scriptEl);
    };
  }, [id, templateId, status]);

  if (status === null) {
    return <div className="p-8 text-slate-400">상태 확인 중...</div>;
  }
  if (!status.enabled) {
    return (
      <div className="mx-auto max-w-2xl p-8">
        <h1 className="text-xl font-bold text-slate-900">OnlyOffice 편집 비활성화</h1>
        <p className="mt-3 text-sm text-slate-600">
          서버 환경변수 <code className="rounded bg-slate-100 px-1">ONLYOFFICE_ENABLED=true</code> 와
          <code className="rounded bg-slate-100 px-1">ONLYOFFICE_URL</code>,
          <code className="rounded bg-slate-100 px-1">PUBLIC_BACKEND_URL</code> 이 설정되어야 합니다.
        </p>
        <p className="mt-2 text-sm text-slate-500">
          비활성 상태에서는 작업계획서 상세 → "DOCX 출력" 버튼으로 한번에 다운로드 가능합니다.
        </p>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 flex flex-col">
      <div className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-2">
        <span className="text-sm font-semibold text-slate-700">작업계획서 #{id} OnlyOffice 편집</span>
        <button type="button" onClick={() => window.close()} className="text-sm text-slate-500 hover:text-slate-900">닫기</button>
      </div>
      {error && <div className="border-b border-rose-200 bg-rose-50 px-4 py-2 text-sm text-rose-700">{error}</div>}
      <div ref={containerRef} className="flex-1">
        <div id="onlyoffice-container" className="h-full w-full" />
      </div>
    </div>
  );
}

function loadScript(src: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${src}"]`) as HTMLScriptElement | null;
    if (existing) { resolve(); return; }
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('script load failed: ' + src));
    document.body.appendChild(script);
  });
}
