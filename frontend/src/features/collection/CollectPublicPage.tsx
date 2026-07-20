import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';

type PublicItem = {
  document_type_id: number;
  name: string;
  required: boolean;
  uploaded: boolean;
  file_name?: string | null;
  sample_image_url?: string | null;
};
type PublicResponse = {
  title?: string | null;
  owner_label: string;
  recipient_name?: string | null;
  status: string;
  expired: boolean;
  items: PublicItem[];
};

export default function CollectPublicPage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<PublicResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploadingId, setUploadingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sampleUrl, setSampleUrl] = useState<string | null>(null);
  const inputs = useRef<Record<number, HTMLInputElement | null>>({});

  async function load() {
    if (!token) return;
    setLoading(true);
    try {
      const r = await api.get<PublicResponse>(`/api/collect/${token}`);
      setInfo(r.data); setError(null);
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '링크를 열 수 없습니다') : '링크를 열 수 없습니다');
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [token]);

  async function upload(typeId: number, file: File) {
    if (!token) return;
    setUploadingId(typeId); setError(null);
    try {
      const fd = new FormData();
      fd.append('documentTypeId', String(typeId));
      fd.append('file', file);
      await api.post(`/api/collect/${token}/documents`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      await load();
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '업로드 실패') : '업로드 실패');
    } finally { setUploadingId(null); }
  }

  async function submit() {
    if (!token) return;
    setSubmitting(true); setError(null);
    try {
      await api.post(`/api/collect/${token}/submit`);
      await load();
      alert('제출되었습니다. 감사합니다.');
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '제출 실패') : '제출 실패');
    } finally { setSubmitting(false); }
  }

  if (loading) return <Centered><p className="text-sm text-slate-400">불러오는 중…</p></Centered>;
  if (error && !info) return <Centered><p className="text-sm text-red-600">{error}</p></Centered>;
  if (!info) return null;

  const requiredItems = info.items.filter((i) => i.required);
  const optionalItems = info.items.filter((i) => !i.required);
  const allRequiredUploaded = requiredItems.every((i) => i.uploaded);

  return (
    <div className="min-h-screen bg-slate-50 py-8">
      <div className="mx-auto max-w-xl px-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h1 className="text-lg font-bold text-slate-900">서류 제출</h1>
          <p className="mt-1 text-sm text-slate-500">
            {info.recipient_name ? `${info.recipient_name} 님, ` : ''}<strong className="text-slate-700">{info.owner_label}</strong> 관련 서류를 첨부해 주세요.
          </p>
          {info.title && <p className="mt-1 text-sm text-slate-600">{info.title}</p>}
          {info.expired && <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-700">만료된 링크입니다.</p>}
          {info.status === 'CANCELLED' && <p className="mt-3 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-500">취소된 요청입니다.</p>}
          {error && <p className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

          <div className="mt-5 space-y-5">
            {requiredItems.length > 0 && (
              <Section label="필수 서류" items={requiredItems} disabled={info.expired || info.status === 'CANCELLED'}
                uploadingId={uploadingId} inputs={inputs} onUpload={upload} onShowSample={setSampleUrl} accent="rose" />
            )}
            {optionalItems.length > 0 && (
              <Section label="선택 서류" items={optionalItems} disabled={info.expired || info.status === 'CANCELLED'}
                uploadingId={uploadingId} inputs={inputs} onUpload={upload} onShowSample={setSampleUrl} accent="amber" />
            )}
          </div>

          <button onClick={submit} disabled={submitting || info.expired || info.status === 'CANCELLED'}
            className="mt-6 w-full rounded-lg bg-brand-600 px-4 py-3 text-sm font-bold text-white hover:bg-brand-700 disabled:opacity-50">
            {submitting ? '제출 중…' : allRequiredUploaded ? '제출 완료' : '제출 (필수 서류를 모두 올려주세요)'}
          </button>
          <p className="mt-2 text-center text-xs text-slate-400">PDF / 사진(JPG·PNG) 파일을 올릴 수 있습니다.</p>
        </div>
      </div>

      {sampleUrl && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setSampleUrl(null)}>
          <div className="max-h-full w-full max-w-lg overflow-auto rounded-xl bg-white p-3 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-semibold text-slate-700">제출 예시</span>
              <button type="button" onClick={() => setSampleUrl(null)} className="rounded p-1 text-slate-400 hover:bg-slate-100" aria-label="닫기">✕</button>
            </div>
            <img src={sampleUrl} alt="제출 예시" className="mx-auto max-h-[70vh] w-auto rounded border border-slate-100" />
            <p className="mt-2 text-center text-xs text-slate-500">개인정보가 가려진 예시입니다. 이런 형식으로 촬영·스캔해서 올려주세요.</p>
          </div>
        </div>
      )}
    </div>
  );
}

function Section({ label, items, disabled, uploadingId, inputs, onUpload, onShowSample, accent }: {
  label: string; items: PublicItem[]; disabled: boolean; uploadingId: number | null;
  inputs: React.MutableRefObject<Record<number, HTMLInputElement | null>>;
  onUpload: (typeId: number, file: File) => void; onShowSample: (url: string) => void; accent: 'rose' | 'amber';
}) {
  return (
    <div>
      <div className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-400">{label}</div>
      <div className="space-y-2">
        {items.map((it) => (
          <div key={it.document_type_id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className={`rounded px-1.5 py-0.5 text-[11px] font-semibold ${accent === 'rose' ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'}`}>
                  {accent === 'rose' ? '필수' : '선택'}
                </span>
                <span className="truncate text-sm font-medium text-slate-800">{it.name}</span>
                {it.sample_image_url && (
                  <button type="button" onClick={() => onShowSample(it.sample_image_url!)}
                    className="shrink-0 rounded border border-brand-200 bg-brand-50 px-1.5 py-0.5 text-[11px] font-semibold text-brand-700 hover:bg-brand-100">
                    샘플 보기
                  </button>
                )}
              </div>
              {it.uploaded && <div className="mt-0.5 truncate text-xs text-emerald-600">✓ 업로드됨{it.file_name ? ` · ${it.file_name}` : ''}</div>}
            </div>
            <div className="shrink-0">
              <input ref={(el) => { inputs.current[it.document_type_id] = el; }} type="file"
                accept="application/pdf,image/*" className="hidden"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) onUpload(it.document_type_id, f); e.target.value = ''; }} />
              <button type="button" disabled={disabled || uploadingId === it.document_type_id}
                onClick={() => inputs.current[it.document_type_id]?.click()}
                className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
                {uploadingId === it.document_type_id ? '올리는 중…' : it.uploaded ? '다시 올리기' : '파일 첨부'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="flex min-h-screen items-center justify-center bg-slate-50">{children}</div>;
}
