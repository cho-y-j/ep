import { createRoot, type Root } from 'react-dom/client';
import { useEffect, useState } from 'react';

type ToastKind = 'success' | 'error' | 'info';
type ToastItem = { id: number; kind: ToastKind; message: string };

let nextId = 1;
const listeners = new Set<(items: ToastItem[]) => void>();
let items: ToastItem[] = [];

function emit() {
  if (listeners.size === 0) return;
  const snapshot = [...items];
  listeners.forEach((cb) => cb(snapshot));
}

function add(kind: ToastKind, message: string, ttl = 4000) {
  const id = nextId++;
  items = [...items, { id, kind, message }];
  emit();
  setTimeout(() => {
    items = items.filter((t) => t.id !== id);
    emit();
  }, ttl);
}

export const toast = {
  success: (m: string) => add('success', m, 3000),
  error:   (m: string) => add('error', m, 5000),
  info:    (m: string) => add('info', m, 3500),
};

const KIND_CLS: Record<ToastKind, string> = {
  success: 'bg-emerald-50 border-emerald-200 text-emerald-900',
  error:   'bg-rose-50 border-rose-200 text-rose-900',
  info:    'bg-slate-50 border-slate-200 text-slate-900',
};

function ToastContainer() {
  const [list, setList] = useState<ToastItem[]>(items);
  useEffect(() => {
    listeners.add(setList);
    return () => { listeners.delete(setList); };
  }, []);
  if (list.length === 0) return null;
  return (
    <div className="fixed top-4 right-4 z-[60] flex flex-col gap-2 max-w-sm">
      {list.map((t) => (
        <div
          key={t.id}
          className={`${KIND_CLS[t.kind]} border rounded-lg shadow-md px-3 py-2 text-sm whitespace-pre-wrap animate-in fade-in slide-in-from-right-2`}
        >
          {t.message}
        </div>
      ))}
    </div>
  );
}

let toastRoot: Root | null = null;
/** main.tsx 에서 1회 호출. window.alert 도 토스트로 redirect. */
export function mountToastSystem() {
  if (toastRoot) return;
  let host = document.getElementById('toast-root');
  if (!host) {
    host = document.createElement('div');
    host.id = 'toast-root';
    document.body.appendChild(host);
  }
  toastRoot = createRoot(host);
  toastRoot.render(<ToastContainer />);
  // 기존 78곳 alert() 호출을 코드 변경 없이 토스트로 redirect.
  // 메시지에 "실패" / "오류" 같은 키워드 있으면 error, 아니면 info.
  // TODO: alert() 호출처를 toast.error/info 로 점진적 명시 치환 후 shim 제거.
  // 한국어 키워드 위주로 좁힘 — "fail" 같은 영어가 정상 문구에 섞여도 오분류 안 되게.
  const ALERT_ERR_RE = /실패|오류|에러|거부|차단|denied/i;
  window.alert = (message?: any) => {
    const s = String(message ?? '');
    if (ALERT_ERR_RE.test(s)) toast.error(s);
    else toast.info(s);
  };
}
