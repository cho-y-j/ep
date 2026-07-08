import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { useSignaturePad } from '../workPlan/create/components/useSignaturePad';

interface SignatureInfo {
  id: number;
  work_plan_id: number;
  role: 'AUTHOR' | 'SUPERVISOR' | 'CONFIRMER' | 'REVIEWER' | 'APPROVER';
  role_label: string;
  signer_name?: string | null;
  signer_email?: string | null;
  status: 'PENDING' | 'SIGNED' | 'EXPIRED' | 'INVALIDATED';
  token_expires_at?: string | null;
  has_signature: boolean;
}

interface FetchResponse {
  signature: SignatureInfo;
  workPlan: { id: number; title: string };
}

/**
 * 공개 사인 페이지 — 토큰만으로 접근 (비로그인).
 * URL: /sign/:token
 */
export default function SignaturePage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<FetchResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const padEnabled = !!info && !done;
  const { canvasRef, hasInk, handlers, clear, getDataUrl } = useSignaturePad(padEnabled);

  useEffect(() => {
    if (!token) return;
    setLoading(true);
    api
      .get<FetchResponse>(`/api/sign/${token}`)
      .then((res) => {
        setInfo(res.data);
        if (res.data.signature.status === 'SIGNED') setDone(true);
      })
      .catch((e) => {
        setErr(e?.response?.data?.error || e?.message || '사인 정보를 가져올 수 없습니다');
      })
      .finally(() => setLoading(false));
  }, [token]);

  const submit = async () => {
    if (!token) return;
    setSubmitting(true);
    try {
      await api.post(`/api/sign/${token}`, { pngBase64: getDataUrl() });
      setDone(true);
    } catch (e: any) {
      alert('사인 제출 실패: ' + (e?.response?.data?.error || e?.message || e));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="min-h-screen flex items-center justify-center text-slate-500">로딩 중…</div>;
  }
  if (err || !info) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50 p-4">
        <div className="bg-white rounded-xl shadow-md p-6 max-w-md w-full text-center">
          <div className="text-rose-600 text-base font-bold mb-2">사인 페이지를 열 수 없습니다</div>
          <div className="text-sm text-slate-600">{err ?? '유효하지 않은 링크'}</div>
        </div>
      </div>
    );
  }

  const pdfPreviewUrl = `/api/sign/${token}/pdf`;
  const pdfDownloadUrl = `/api/sign/${token}/pdf?disposition=attachment`;

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="bg-white border-b border-slate-200">
        <div className="mx-auto max-w-3xl px-4 py-4">
          <div className="text-xs text-slate-500">SKEP · 전자서명</div>
          <h1 className="text-lg font-bold text-slate-900 mt-0.5">{info.workPlan.title}</h1>
          <div className="mt-1 text-sm text-slate-600">
            <span className="font-semibold">{info.signature.role_label}</span>
            {info.signature.signer_name && <span className="ml-2 text-slate-500">· {info.signature.signer_name}</span>}
          </div>
          <div className="mt-2 flex flex-wrap gap-2">
            <a href={pdfPreviewUrl} target="_blank" rel="noopener noreferrer"
               className="text-xs px-3 py-1.5 rounded-md border border-blue-500 text-blue-700 bg-white hover:bg-blue-50">
              작업계획서 PDF 보기
            </a>
            <a href={pdfDownloadUrl}
               className="text-xs px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50">
              PDF 다운로드
            </a>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 py-6">
        {done ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center">
            <div className="text-emerald-600 text-2xl mb-2">✓</div>
            <div className="text-base font-bold text-slate-900 mb-1">사인이 완료되었습니다</div>
            <div className="text-sm text-slate-500">작업계획서에 사인이 기록되었습니다. 이 창은 닫으셔도 됩니다.</div>
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm p-5 space-y-3">
            <div className="text-sm text-slate-700">
              아래 영역에 마우스 또는 손가락으로 서명하신 후 <strong>사인 제출</strong>을 눌러주세요.
            </div>
            <canvas
              ref={canvasRef}
              width={760}
              height={300}
              className="w-full h-[300px] border border-slate-300 rounded-lg bg-white touch-none"
              {...handlers}
            />
            <div className="flex items-center justify-between">
              <button
                type="button"
                onClick={clear}
                className="text-xs px-3 py-1.5 rounded-md border border-slate-300 text-slate-700 hover:bg-slate-50"
              >
                지우기
              </button>
              <button
                type="button"
                onClick={submit}
                disabled={!hasInk || submitting}
                className="text-sm px-5 py-2 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 disabled:opacity-50"
              >
                {submitting ? '저장 중…' : '사인 제출'}
              </button>
            </div>
            {info.signature.token_expires_at && (
              <div className="text-[11px] text-slate-400">
                링크 만료: {info.signature.token_expires_at.slice(0, 10)}
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
