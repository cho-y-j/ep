import { useState } from 'react';
import { useSignaturePad } from './useSignaturePad';

interface SignaturePadDialogProps {
  open: boolean;
  title: string;
  signerName?: string;
  onClose: () => void;
  onConfirm: (pngBase64: string) => Promise<void> | void;
}

/** HTML5 캔버스 사인 다이얼로그 — useSignaturePad 훅 wrap. */
export function SignaturePadDialog({ open, title, signerName, onClose, onConfirm }: SignaturePadDialogProps) {
  const { canvasRef, hasInk, handlers, clear, getDataUrl } = useSignaturePad(open);
  const [submitting, setSubmitting] = useState(false);

  const confirm = async () => {
    setSubmitting(true);
    try {
      await onConfirm(getDataUrl());
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl">
        <div className="px-5 py-3 border-b border-slate-200 flex items-center justify-between">
          <div>
            <h3 className="text-base font-bold text-slate-900">{title}</h3>
            {signerName && <div className="text-xs text-slate-500 mt-0.5">{signerName}</div>}
          </div>
          <button type="button" onClick={onClose} className="text-slate-500 hover:text-slate-800 text-xl leading-none">×</button>
        </div>
        <div className="p-4">
          <div className="text-xs text-slate-500 mb-2">아래 영역에 마우스 또는 손가락으로 서명해주세요.</div>
          <canvas
            ref={canvasRef}
            width={760}
            height={280}
            className="w-full h-[280px] border border-slate-300 rounded-lg bg-white touch-none"
            {...handlers}
          />
        </div>
        <div className="px-5 py-3 border-t border-slate-200 flex items-center justify-between">
          <button
            type="button"
            onClick={clear}
            className="text-xs px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50"
          >
            지우기
          </button>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="text-sm px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50"
            >
              취소
            </button>
            <button
              type="button"
              onClick={confirm}
              disabled={!hasInk || submitting}
              className="text-sm px-4 py-1.5 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? '저장 중…' : '사인 저장'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
