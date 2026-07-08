import { useState } from 'react';
import { api } from '../../../../lib/api';

interface PdfMailDialogProps {
  open: boolean;
  buildBlob: () => Promise<{ blob: Blob; baseName: string }>;
  onClose: () => void;
}

/** S-9-C: PDF 메일 발송 다이얼로그 — DOCX → 서버 PDF 변환 + 발송. */
export function PdfMailDialog({ open, buildBlob, onClose }: PdfMailDialogProps) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [sending, setSending] = useState(false);
  const [msg, setMsg] = useState('');

  if (!open) return null;

  const send = async () => {
    if (!to.trim()) {
      setMsg('받는 사람 이메일을 입력하세요');
      return;
    }
    setSending(true);
    setMsg('');
    try {
      const { blob, baseName } = await buildBlob();
      const fd = new FormData();
      fd.append('file', blob, `${baseName}.docx`);
      fd.append('name', baseName);
      if (from.trim()) fd.append('from', from.trim());
      fd.append('to', to.trim());
      if (subject.trim()) fd.append('subject', subject.trim());
      if (body.trim()) fd.append('body', body);
      const res = await api.post('/api/worksheet/send-pdf', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const payload = (res as any)?.data ?? res;
      if (payload?.ok === false) {
        setMsg(payload.message || '발송 실패');
      } else {
        setMsg(`✓ ${payload?.to} 로 발송 완료`);
        setTimeout(() => {
          onClose();
          setMsg('');
        }, 1500);
      }
    } catch (err: any) {
      setMsg('발송 실패: ' + (err?.response?.data?.message || err?.message || err));
    } finally {
      setSending(false);
    }
  };

  return (
    <div
      className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
      onClick={() => !sending && onClose()}
    >
      <div
        className="bg-white rounded-xl p-6 max-w-lg w-full space-y-3 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-bold">작업계획서 PDF 이메일 발송</h3>
          <button
            onClick={() => !sending && onClose()}
            className="text-slate-400 hover:text-slate-600"
            disabled={sending}
            type="button"
          >
            ✕
          </button>
        </div>
        <div className="text-xs text-slate-500">
          현재 작성된 작업계획서가 PDF 로 변환되어 첨부파일로 발송됩니다.
        </div>
        <div>
          <label className="text-xs font-medium text-slate-600 block mb-1">
            답장 받을 이메일{' '}
            <span className="text-slate-400 font-normal">(선택, 수신자가 회신할 주소)</span>
          </label>
          <input
            type="email"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            placeholder="예: your@company.com (선택)"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            disabled={sending}
          />
        </div>
        <div>
          <label className="text-xs font-medium text-slate-600 block mb-1">
            받는 사람 <span className="text-rose-500">*</span> (여러 명은 쉼표로)
          </label>
          <input
            type="text"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            placeholder="예: manager@site.com, safety@site.com"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            disabled={sending}
          />
        </div>
        <div>
          <label className="text-xs font-medium text-slate-600 block mb-1">제목 (비워두면 자동)</label>
          <input
            type="text"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
            placeholder="[SKEP] 작업계획서"
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            disabled={sending}
          />
        </div>
        <div>
          <label className="text-xs font-medium text-slate-600 block mb-1">내용</label>
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            placeholder="본문에 함께 보낼 메시지를 작성하세요."
            rows={5}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            disabled={sending}
          />
        </div>
        {msg && (
          <div
            className={`text-sm px-3 py-2 rounded ${
              msg.startsWith('✓') ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'
            }`}
          >
            {msg}
          </div>
        )}
        <div className="flex justify-end gap-2 pt-2">
          <button
            onClick={onClose}
            disabled={sending}
            className="btn-ghost"
            type="button"
          >
            취소
          </button>
          <button
            onClick={send}
            disabled={sending || !to.trim()}
            className="px-4 py-2 rounded-md bg-emerald-600 text-white text-sm font-medium hover:bg-emerald-700 disabled:opacity-50"
            type="button"
          >
            {sending ? '발송 중... (PDF 변환 ~10초)' : '발송'}
          </button>
        </div>
      </div>
    </div>
  );
}
