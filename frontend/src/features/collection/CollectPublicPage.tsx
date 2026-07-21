import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { PublicCollection, PublicItem, PublicTarget } from '../../types/collection';
import DocumentCornerAligner from '../document/DocumentCornerAligner';
import { detectDocumentCorners } from '../document/detectCorners';
import { warpImageByCorners } from '../document/warpImage';

type Pending = { itemId: number; file: File; url: string };

const uploadedOf = (t: PublicTarget) => t.items.filter((i) => i.uploaded).length;
const requiredLeftOf = (t: PublicTarget) => t.items.filter((i) => i.required && !i.uploaded).length;

export default function CollectPublicPage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<PublicCollection | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploadingId, setUploadingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [sample, setSample] = useState<{ url: string | null; desc: string | null } | null>(null);
  const [openTargetId, setOpenTargetId] = useState<number | null>(null);
  /** 이미지 업로드 전 4모서리 보정 대기 상태 (건너뛰기 가능). */
  const [pending, setPending] = useState<Pending | null>(null);
  const [autoCorners, setAutoCorners] = useState<[number, number][] | undefined>(undefined);
  const inputs = useRef<Record<number, HTMLInputElement | null>>({});

  useEffect(() => {
    if (!token) return;
    let alive = true;
    setLoading(true);
    api.get<PublicCollection>(`/api/collect/${token}`)
      .then((r) => {
        if (!alive) return;
        setInfo(r.data);
        // 기본 접힘 + 첫 미완료(필수 남은) 대상만 자동 펼침 — 위에서부터 순서대로 올리게 유도.
        setOpenTargetId(r.data.targets.find((t) => requiredLeftOf(t) > 0)?.id ?? null);
        setError(null);
      })
      .catch((err) => {
        if (alive) setError(err instanceof AxiosError ? (err.response?.data?.message ?? '링크를 열 수 없습니다') : '링크를 열 수 없습니다');
      })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [token]);

  /** 업로드 성공 → 전체 재조회 없이 그 item 만 갱신(대상 50건 화면 성능). 대상이 끝나면 다음 대상을 편다. */
  function markUploaded(itemId: number, fileName: string) {
    setInfo((prev) => {
      if (!prev) return prev;
      const targets = prev.targets.map((t) => ({
        ...t,
        items: t.items.map((it) => (it.id === itemId ? { ...it, uploaded: true, file_name: fileName } : it)),
      }));
      const done = targets.find((t) => t.items.some((it) => it.id === itemId));
      if (done && requiredLeftOf(done) === 0) {
        setOpenTargetId(targets.find((t) => requiredLeftOf(t) > 0)?.id ?? null);
      }
      return { ...prev, targets };
    });
  }

  function pickFile(itemId: number, file: File) {
    if (!file.type.startsWith('image/')) { void upload(itemId, file); return; }
    // 이미지면 먼저 4모서리 보정 — 자동검출은 백그라운드(실패해도 이미지 꼭짓점으로 시작).
    setAutoCorners(undefined);
    setPending({ itemId, file, url: URL.createObjectURL(file) });
    // 공개(무로그인) 화면 — 토큰 기반 detect-corners 로 자동검출(인증 엔드포인트는 403).
    void detectDocumentCorners(file, token).then((c) => { if (c) setAutoCorners(c); });
  }

  function closePending() {
    setPending((p) => { if (p) URL.revokeObjectURL(p.url); return null; });
    setAutoCorners(undefined);
  }

  async function upload(itemId: number, file: File) {
    if (!token) return;
    setUploadingId(itemId); setError(null);
    try {
      const fd = new FormData();
      fd.append('itemId', String(itemId));
      fd.append('file', file);
      await api.post(`/api/collect/${token}/documents`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      markUploaded(itemId, file.name);
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '업로드 실패') : '업로드 실패');
    } finally { setUploadingId(null); }
  }

  async function uploadAligned(corners: [number, number][]) {
    if (!pending) return;
    const { itemId, file } = pending;
    closePending();
    setUploadingId(itemId);
    const aligned = await warpImageByCorners(file, corners).catch(() => file);
    await upload(itemId, aligned);
  }

  async function submit() {
    if (!token) return;
    setSubmitting(true); setError(null);
    try {
      await api.post(`/api/collect/${token}/submit`);
      alert('제출되었습니다. 감사합니다.');
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '제출 실패') : '제출 실패');
    } finally { setSubmitting(false); }
  }

  if (loading) return <Centered><p className="text-sm text-slate-400">불러오는 중…</p></Centered>;
  if (error && !info) return <Centered><p className="text-sm text-red-600">{error}</p></Centered>;
  if (!info) return null;

  const total = info.targets.reduce((n, t) => n + t.items.length, 0);
  const uploaded = info.targets.reduce((n, t) => n + uploadedOf(t), 0);
  const requiredLeft = info.targets.reduce((n, t) => n + requiredLeftOf(t), 0);
  const disabled = info.expired || info.status === 'CANCELLED';

  return (
    <div className="min-h-screen bg-slate-50 pb-10">
      {/* 상단 고정 진행바 — 지금 몇 건 남았는지 항상 보이게. */}
      <div className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto max-w-xl px-4 py-3">
          <div className="flex items-center justify-between text-sm">
            <span className="font-bold text-slate-900">서류 제출</span>
            <span className="text-slate-600">
              전체 <strong className="text-slate-900">{uploaded}/{total}</strong>
              <span className="mx-1.5 text-slate-300">·</span>
              필수 남음 <strong className={requiredLeft > 0 ? 'text-rose-600' : 'text-emerald-600'}>{requiredLeft}</strong>
            </span>
          </div>
          <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
            <div className="h-full rounded-full bg-brand-600 transition-all"
              style={{ width: `${total === 0 ? 0 : Math.round((uploaded / total) * 100)}%` }} />
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-xl px-4 py-5">
        <p className="text-sm text-slate-500">
          {info.recipient_name ? `${info.recipient_name} 님, ` : ''}아래 대상별로 서류를 첨부해 주세요.
        </p>
        {info.title && <p className="mt-1 text-sm font-semibold text-slate-700">{info.title}</p>}
        {info.expired && <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-700">만료된 링크입니다.</p>}
        {info.status === 'CANCELLED' && <p className="mt-3 rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-500">취소된 요청입니다.</p>}
        {error && <p className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">{error}</p>}

        {/* 대상별 아코디언 — 서버가 준 순서 그대로. */}
        <div className="mt-4 space-y-2">
          {info.targets.map((t) => {
            const up = uploadedOf(t);
            const left = requiredLeftOf(t);
            const open = openTargetId === t.id;
            return (
              <div key={t.id} className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
                <button type="button" onClick={() => setOpenTargetId(open ? null : t.id)}
                  className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left">
                  <span className="flex min-w-0 items-center gap-2">
                    <span className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] font-semibold ${
                      t.owner_type === 'EQUIPMENT' ? 'bg-brand-50 text-brand-700' : 'bg-slate-100 text-slate-600'}`}>
                      {t.owner_type === 'EQUIPMENT' ? '장비' : '인력'}
                    </span>
                    <span className="truncate text-sm font-bold text-slate-900">{t.owner_label}</span>
                  </span>
                  <span className="shrink-0 text-xs">
                    {left === 0
                      ? <span className="font-semibold text-emerald-600">완료 {up}/{t.items.length}</span>
                      : <span className="text-slate-500">{up}/{t.items.length} · 필수 <strong className="text-rose-600">{left}</strong></span>}
                    <span className="ml-2 text-slate-400">{open ? '▲' : '▼'}</span>
                  </span>
                </button>
                {open && (
                  <div className="space-y-2 border-t border-slate-100 p-3">
                    {t.items.map((it) => (
                      <ItemRow key={it.id} item={it} disabled={disabled} uploading={uploadingId === it.id}
                        inputRef={(el) => { inputs.current[it.id] = el; }}
                        onPick={(f) => pickFile(it.id, f)}
                        onClickUpload={() => inputs.current[it.id]?.click()}
                        onShowSample={setSample} />
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>

        <button onClick={submit} disabled={submitting || disabled}
          className="mt-6 w-full rounded-lg bg-brand-600 px-4 py-3 text-sm font-bold text-white hover:bg-brand-700 disabled:opacity-50">
          {submitting ? '제출 중…' : requiredLeft === 0 ? '제출 완료' : `제출 (필수 ${requiredLeft}건 남음)`}
        </button>
        <p className="mt-2 text-center text-xs text-slate-400">PDF / 사진(JPG·PNG) 파일을 올릴 수 있습니다.</p>
      </div>

      {/* 업로드 전 4모서리 보정 — 건너뛰면 원본 그대로 올라간다. */}
      {pending && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/70">
          <div className="flex items-center justify-between gap-3 bg-white px-4 py-3">
            <span className="text-sm font-bold text-slate-800">서류 영역 맞추기</span>
            <button type="button" onClick={() => { const p = pending; closePending(); void upload(p.itemId, p.file); }}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">
              건너뛰고 바로 올리기
            </button>
          </div>
          <DocumentCornerAligner
            key={autoCorners ? 'auto' : 'manual'}
            imageUrl={pending.url}
            initialCorners={autoCorners}
            onConfirm={(c) => void uploadAligned(c as [number, number][])}
            onCancel={closePending}
          />
        </div>
      )}

      {sample && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" onClick={() => setSample(null)}>
          <div className="max-h-full w-full max-w-lg overflow-auto rounded-xl bg-white p-3 shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="mb-2 flex items-center justify-between">
              <span className="text-sm font-semibold text-slate-700">제출 예시</span>
              <button type="button" onClick={() => setSample(null)} className="rounded p-1 text-slate-400 hover:bg-slate-100" aria-label="닫기">✕</button>
            </div>
            {sample.desc && (
              <p className="mb-2 whitespace-pre-wrap rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-700">{sample.desc}</p>
            )}
            {sample.url && (
              <>
                <img src={sample.url} alt="제출 예시" className="mx-auto max-h-[70vh] w-auto rounded border border-slate-100" />
                <p className="mt-2 text-center text-xs text-slate-500">개인정보가 가려진 예시입니다. 이런 형식으로 촬영·스캔해서 올려주세요.</p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ItemRow({ item, disabled, uploading, inputRef, onPick, onClickUpload, onShowSample }: {
  item: PublicItem;
  disabled: boolean;
  uploading: boolean;
  inputRef: (el: HTMLInputElement | null) => void;
  onPick: (file: File) => void;
  onClickUpload: () => void;
  onShowSample: (s: { url: string | null; desc: string | null }) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 p-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] font-semibold ${item.required ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'}`}>
            {item.required ? '필수' : '선택'}
          </span>
          <span className="truncate text-sm font-medium text-slate-800">{item.name}</span>
          {(item.sample_image_url || item.sample_description) && (
            <button type="button" onClick={() => onShowSample({ url: item.sample_image_url ?? null, desc: item.sample_description ?? null })}
              className="shrink-0 rounded border border-brand-200 bg-brand-50 px-1.5 py-0.5 text-[11px] font-semibold text-brand-700 hover:bg-brand-100">
              샘플 보기
            </button>
          )}
        </div>
        {item.uploaded && <div className="mt-0.5 truncate text-xs text-emerald-600">✓ 업로드됨{item.file_name ? ` · ${item.file_name}` : ''}</div>}
      </div>
      <div className="shrink-0">
        <input ref={inputRef} type="file" accept="application/pdf,image/*" className="hidden"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) onPick(f); e.target.value = ''; }} />
        <button type="button" disabled={disabled || uploading} onClick={onClickUpload}
          className={`rounded-md px-3 py-1.5 text-xs font-semibold disabled:opacity-50 ${
            item.uploaded ? 'border border-slate-300 text-slate-700 hover:bg-slate-50' : 'bg-brand-600 text-white hover:bg-brand-700'}`}>
          {uploading ? '올리는 중…' : item.uploaded ? '다시 올리기' : '파일 올리기'}
        </button>
      </div>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="flex min-h-screen items-center justify-center bg-slate-50">{children}</div>;
}
