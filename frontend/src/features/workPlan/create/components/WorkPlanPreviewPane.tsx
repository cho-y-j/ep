import { useState } from 'react';
import { SpecsSidebar } from './SpecsSidebar';

interface Props {
  sessionId: string | null;
  loading: boolean;
  error: string | null;
  onRefresh: () => void;
  lastUpdatedAt: Date | null;
}

export default function WorkPlanPreviewPane({ sessionId, loading, error, onRefresh, lastUpdatedAt }: Props) {
  const [specsOpen, setSpecsOpen] = useState(false);

  return (
    <>
      <aside className="sticky top-4 self-start h-[calc(100vh-32px)] flex flex-col rounded-xl border border-slate-200 bg-white overflow-hidden w-full">
        <div className="flex items-center justify-between gap-2 px-3 py-2 border-b border-slate-200 bg-slate-50 shrink-0">
          <div className="text-xs text-slate-600">
            <span className="font-semibold">미리보기 · 편집</span>
            {loading && <span className="ml-2 text-slate-400">동기화 중…</span>}
            {!loading && lastUpdatedAt && (
              <span className="ml-2 text-slate-400">갱신 {lastUpdatedAt.toLocaleTimeString('ko-KR').slice(0, 8)}</span>
            )}
          </div>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setSpecsOpen(true)}
              className="text-xs px-2.5 py-1 rounded-md border border-slate-300 text-slate-700 bg-white hover:bg-slate-100"
              title="장비 제원표 따로 보기"
            >
              제원표
            </button>
            <button
              type="button"
              onClick={onRefresh}
              disabled={loading}
              className="text-xs px-2.5 py-1 rounded-md border border-slate-300 text-slate-700 bg-white hover:bg-slate-100 disabled:opacity-50"
              title="지금 다시 동기화"
            >
              새로고침
            </button>
          </div>
        </div>

        {error && (
          <p className="m-3 text-xs text-rose-600 bg-rose-50 border border-rose-200 rounded p-2">{error}</p>
        )}

        <div className="flex-1 bg-slate-100">
          {sessionId ? (
            <iframe
              title="OnlyOffice 인플레이스 편집기"
              src={`/worksheet/edit/${sessionId}?ch=${sessionId}`}
              className="w-full h-full border-0 bg-white"
            />
          ) : (
            <div className="h-full flex items-center justify-center text-sm text-slate-400 px-6 text-center">
              {loading ? '편집기 초기화 중…' : '폼을 입력하면 편집기가 자동 열립니다.'}
            </div>
          )}
        </div>
      </aside>

      {specsOpen && (
        <div className="fixed inset-0 z-40 bg-black/30" onClick={() => setSpecsOpen(false)}>
          <aside
            className="fixed right-0 top-0 h-screen w-[420px] max-w-[90vw] bg-white shadow-2xl z-50 flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
              <h3 className="text-sm font-bold text-slate-900">장비 제원표</h3>
              <button
                type="button"
                onClick={() => setSpecsOpen(false)}
                className="text-xs px-2 py-1 rounded-md border border-slate-300 text-slate-700 bg-white hover:bg-slate-100"
              >
                닫기
              </button>
            </div>
            <div className="flex-1 overflow-y-auto p-3">
              <SpecsSidebar />
            </div>
          </aside>
        </div>
      )}
    </>
  );
}
