import { useEffect, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AxiosError } from 'axios';
import { api } from '../../lib/api';
import type { PublicCollection, PublicItem, PublicTarget } from '../../types/collection';
import DocumentCornerAligner from '../document/DocumentCornerAligner';
import DocumentMaskEditor from '../document/DocumentMaskEditor';
import { detectDocumentCorners } from '../document/detectCorners';
import { warpImageByCorners } from '../document/warpImage';
import { pdfToPageImages } from '../document/pdfToPages';

type Pending = { itemId: number; file: File; url: string };
/** 모서리 보정본 위에 가릴 곳(검정 박스)을 덮는 대기 상태 — 건너뛰면 보정본 그대로 올라간다. */
type Masking = { itemId: number; file: File; url: string };
/** PDF 를 페이지 이미지로 렌더해 페이지별로 가리는 대기 상태 — 다 가리면 이미지들을 올려 서버가 1 PDF 로 재병합. */
type PdfMasking = { itemId: number; file: File; pages: { url: string; file: File }[]; index: number; results: File[]; maskedAny: boolean };
/** 항목별로 '담아둔' 장(사진/PDF) — 한 장씩 누적한 뒤 [업로드]로 한 번에 병합 전송(서버가 N개→1 PDF). */
type Staged = { url: string; file: File };

const uploadedOf = (t: PublicTarget) => t.items.filter((i) => i.uploaded).length;
const requiredLeftOf = (t: PublicTarget) => t.items.filter((i) => i.required && !i.uploaded).length;

export default function CollectPublicPage() {
  const { token } = useParams<{ token: string }>();
  const [info, setInfo] = useState<PublicCollection | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploadingId, setUploadingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // 등록형(신규 자원) — 미등록 슬롯의 차량번호/이름 입력값과 진행 상태.
  const [registerValue, setRegisterValue] = useState<Record<number, string>>({});
  const [registeringId, setRegisteringId] = useState<number | null>(null);
  const [sample, setSample] = useState<{ url: string | null; desc: string | null; pdf?: boolean } | null>(null);
  const [openTargetId, setOpenTargetId] = useState<number | null>(null);
  /** 이미지 업로드 전 4모서리 보정 대기 상태 (건너뛰기 가능). */
  const [pending, setPending] = useState<Pending | null>(null);
  const [autoCorners, setAutoCorners] = useState<[number, number][] | undefined>(undefined);
  /** 보정 완료 후 가리기(마스킹) 대기 상태. */
  const [masking, setMasking] = useState<Masking | null>(null);
  /** PDF 페이지별 가리기 대기 상태. */
  const [pdfMasking, setPdfMasking] = useState<PdfMasking | null>(null);
  /** 항목별 스테이징 — 처리(보정/마스킹/PDF)까지 끝난 장들을 담아두고, [업로드]로 한 번에 전송. */
  const [staged, setStaged] = useState<Record<number, Staged[]>>({});
  /** 항목별 만료일 입력(선택) — 비워도 업로드 가능(관리자가 나중에 채움). */
  const [expiry, setExpiry] = useState<Record<number, string>>({});

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

  /** 촬영 / 단일 이미지 — 4모서리 보정 후 스테이징에 담는다. PDF 1개는 페이지 가리기 후 담는다. */
  function pickFile(itemId: number, file: File) {
    if (file.type === 'application/pdf') { void startPdfMasking(itemId, file); return; }
    if (!file.type.startsWith('image/')) { stage(itemId, [file]); return; }
    // 이미지면 먼저 4모서리 보정 — 자동검출은 백그라운드(실패해도 이미지 꼭짓점으로 시작).
    setAutoCorners(undefined);
    setPending({ itemId, file, url: URL.createObjectURL(file) });
    // 공개(무로그인) 화면 — 토큰 기반 detect-corners 로 자동검출(인증 엔드포인트는 403).
    void detectDocumentCorners(file, token).then((c) => { if (c) setAutoCorners(c); });
  }

  /** 앨범/파일 다중 선택 — 1장이면 정렬 단계를 거치고, 2장 이상(Ctrl 다중)이면 그대로 스테이징에 합류. */
  function pickFiles(itemId: number, files: File[]) {
    if (files.length === 0) return;
    if (files.length === 1) { pickFile(itemId, files[0]); return; }
    stage(itemId, files);
  }

  function closePending() {
    setPending((p) => { if (p) URL.revokeObjectURL(p.url); return null; });
    setAutoCorners(undefined);
  }

  /** 파일 1개면 그대로, 2개 이상이면 올린 순서대로 서버가 1개 PDF로 병합해 저장. 성공하면 true. */
  async function upload(itemId: number, files: File[]): Promise<boolean> {
    if (!token || files.length === 0) return false;
    setUploadingId(itemId); setError(null);
    try {
      const fd = new FormData();
      fd.append('itemId', String(itemId));
      const ed = expiry[itemId];
      if (ed) fd.append('expiryDate', ed); // 선택 — 비우면 미전송(null 저장)
      for (const f of files) fd.append('file', f);
      await api.post(`/api/collect/${token}/documents`, fd, { headers: { 'Content-Type': 'multipart/form-data' } });
      markUploaded(itemId, files.length === 1 ? files[0].name : `${files[0].name} 외 ${files.length - 1}건`);
      return true;
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '업로드 실패') : '업로드 실패');
      return false;
    } finally { setUploadingId(null); }
  }

  /** 처리 끝난 장을 스테이징에 push(누적). 썸네일용 objectURL 을 만들어 둔다. */
  function stage(itemId: number, files: File[]) {
    if (files.length === 0) return;
    const add = files.map((f) => ({ url: URL.createObjectURL(f), file: f }));
    setStaged((s) => ({ ...s, [itemId]: [...(s[itemId] ?? []), ...add] }));
  }
  /** 담아둔 장 1개 삭제. */
  function removeStaged(itemId: number, url: string) {
    URL.revokeObjectURL(url);
    setStaged((s) => ({ ...s, [itemId]: (s[itemId] ?? []).filter((x) => x.url !== url) }));
  }
  /** 항목 스테이징 비우기(업로드 성공 후). objectURL 회수. */
  function clearStaged(itemId: number) {
    setStaged((s) => {
      (s[itemId] ?? []).forEach((x) => URL.revokeObjectURL(x.url));
      const next = { ...s }; delete next[itemId]; return next;
    });
  }
  /** [업로드] — 담아둔 장 전체를 한 번에 병합 전송. 성공하면 스테이징·만료일 입력을 비운다. */
  async function uploadStaged(itemId: number) {
    const cur = staged[itemId] ?? [];
    if (cur.length === 0) return;
    const ok = await upload(itemId, cur.map((x) => x.file));
    if (ok) {
      clearStaged(itemId);
      setExpiry((s) => { const next = { ...s }; delete next[itemId]; return next; });
    }
  }

  /** 4모서리 확정 → 원근보정(warp) 후, 바로 올리지 않고 가리기(마스킹) 단계를 연다. */
  async function uploadAligned(corners: [number, number][]) {
    if (!pending) return;
    const { itemId, file } = pending;
    closePending();
    const aligned = await warpImageByCorners(file, corners).catch(() => file);
    setMasking({ itemId, file: aligned, url: URL.createObjectURL(aligned) });
  }

  function closeMasking() {
    setMasking((m) => { if (m) URL.revokeObjectURL(m.url); return null; });
  }

  /** 가리기 "적용" — 검정 박스가 구워진 File 을 스테이징에 담는다(자동 마스킹 위에 수동 박스 중첩). */
  function uploadMasked(maskedFile: File) {
    if (!masking) return;
    const { itemId } = masking;
    closeMasking();
    stage(itemId, [maskedFile]);
  }

  /** 가리기 "건너뛰기" — 보정본 그대로 담는다. */
  function skipMasking() {
    if (!masking) return;
    const { itemId, file } = masking;
    closeMasking();
    stage(itemId, [file]);
  }

  /** PDF → 페이지 이미지로 렌더 후 페이지별 가리기 시작. 렌더 실패/0페이지면 원본 PDF 를 그대로 담는다. */
  async function startPdfMasking(itemId: number, file: File) {
    setUploadingId(itemId); setError(null);
    let blobs: Blob[];
    try { blobs = await pdfToPageImages(file); }
    catch { setUploadingId(null); stage(itemId, [file]); return; }
    setUploadingId(null);
    if (blobs.length === 0) { stage(itemId, [file]); return; }
    const base = file.name.replace(/\.[^.]+$/, '') || '문서';
    const pages = blobs.map((b, i) => ({
      url: URL.createObjectURL(b),
      file: new File([b], `${base}-p${i + 1}.jpg`, { type: 'image/jpeg' }),
    }));
    setPdfMasking({ itemId, file, pages, index: 0, results: [], maskedAny: false });
  }

  /** 현재 페이지 결과(가린 이미지 또는 원본 페이지)를 쌓고 다음 페이지로. 마지막이면 업로드. */
  function advancePdf(pageFile: File, wasMasked: boolean) {
    if (!pdfMasking) return;
    const results = [...pdfMasking.results, pageFile];
    const maskedAny = pdfMasking.maskedAny || wasMasked;
    const next = pdfMasking.index + 1;
    if (next >= pdfMasking.pages.length) {
      const { itemId, file, pages } = pdfMasking;
      pages.forEach((p) => URL.revokeObjectURL(p.url));
      setPdfMasking(null);
      // 아무 페이지도 안 가렸으면 원본 PDF(텍스트 레이어 보존) 그대로, 하나라도 가렸으면 페이지 이미지들을 스테이징에 담는다.
      stage(itemId, maskedAny ? results : [file]);
    } else {
      setPdfMasking({ ...pdfMasking, index: next, results, maskedAny });
    }
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

  /** 등록형 미등록 슬롯 — 차량번호/이름 입력 → 자원 신규 등록. 성공 시 재조회로 서류 업로드가 열린다. */
  async function register(target: PublicTarget) {
    if (!token) return;
    const value = (registerValue[target.id] ?? '').trim();
    if (!value) { setError(target.input_kind === 'VEHICLE_NO' ? '차량번호를 입력하세요' : '이름을 입력하세요'); return; }
    setRegisteringId(target.id); setError(null);
    try {
      await api.post(`/api/collect/${token}/targets/${target.id}/register`, { value });
      const r = await api.get<PublicCollection>(`/api/collect/${token}`);
      setInfo(r.data);
      setOpenTargetId(target.id); // 등록한 카드를 열어 둔 채 서류 업로드로 이어지게.
    } catch (err) {
      setError(err instanceof AxiosError ? (err.response?.data?.message ?? '등록 실패') : '등록 실패');
    } finally { setRegisteringId(null); }
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
                    {!t.registered
                      ? <span className="font-semibold text-amber-600">등록 필요</span>
                      : left === 0
                        ? <span className="font-semibold text-emerald-600">완료 {up}/{t.items.length}</span>
                        : <span className="text-slate-500">{up}/{t.items.length} · 필수 <strong className="text-rose-600">{left}</strong></span>}
                    <span className="ml-2 text-slate-400">{open ? '▲' : '▼'}</span>
                  </span>
                </button>
                {open && (
                  <div className="space-y-2 border-t border-slate-100 p-3">
                    {!t.registered ? (
                      <RegisterRow target={t} value={registerValue[t.id] ?? ''} disabled={disabled}
                        registering={registeringId === t.id}
                        onChange={(v) => setRegisterValue((s) => ({ ...s, [t.id]: v }))}
                        onSubmit={() => register(t)} />
                    ) : (
                      t.items.map((it) => (
                        <ItemRow key={it.id} item={it} disabled={disabled} uploading={uploadingId === it.id}
                          staged={staged[it.id] ?? []}
                          expiry={expiry[it.id] ?? ''}
                          onExpiryChange={(v) => setExpiry((s) => ({ ...s, [it.id]: v }))}
                          onPick={(f) => pickFile(it.id, f)}
                          onPickFiles={(fs) => pickFiles(it.id, fs)}
                          onRemoveStaged={(url) => removeStaged(it.id, url)}
                          onUpload={() => uploadStaged(it.id)}
                          onShowSample={setSample} />
                      ))
                    )}
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
        <p className="mt-2 text-center text-xs text-slate-400">촬영·사진·PDF를 한 장씩 담은 뒤 [업로드]를 누르면 1개로 합쳐 제출됩니다.</p>
      </div>

      {/* 업로드 전 4모서리 보정 — 건너뛰면 원본 그대로 스테이징에 담긴다. */}
      {pending && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/70">
          <div className="flex items-center justify-between gap-3 bg-white px-4 py-3">
            <span className="text-sm font-bold text-slate-800">서류 영역 맞추기</span>
            <button type="button" onClick={() => { const p = pending; closePending(); stage(p.itemId, [p.file]); }}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50">
              보정 건너뛰고 담기
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

      {/* 보정 후 가리기 — 가릴 곳을 검정 박스로 덮어 적용하면 그 픽셀이 실제로 칠해져 업로드된다. 건너뛰면 보정본 그대로. */}
      {masking && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/70">
          <div className="flex items-center justify-between gap-3 bg-white px-4 py-3">
            <span className="text-sm font-bold text-slate-800">가릴 곳 덮기 (선택)</span>
          </div>
          <DocumentMaskEditor
            imageUrl={masking.url}
            onConfirm={(f) => void uploadMasked(f)}
            onCancel={skipMasking}
          />
        </div>
      )}

      {/* PDF 페이지별 가리기 — 각 페이지 이미지 위에 가릴 곳을 덮는다. 마지막 페이지까지 마치면 업로드(서버가 1 PDF 로 병합). */}
      {pdfMasking && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/70">
          <div className="flex items-center justify-between gap-3 bg-white px-4 py-3">
            <span className="text-sm font-bold text-slate-800">
              가릴 곳 덮기 · 페이지 {pdfMasking.index + 1}/{pdfMasking.pages.length}
            </span>
          </div>
          <DocumentMaskEditor
            key={pdfMasking.index}
            imageUrl={pdfMasking.pages[pdfMasking.index].url}
            onConfirm={(f) => advancePdf(f, true)}
            onCancel={() => advancePdf(pdfMasking.pages[pdfMasking.index].file, false)}
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
              sample.pdf ? (
                <>
                  <iframe src={sample.url} title="제출 예시" className="mx-auto h-[70vh] w-full rounded border border-slate-100" />
                  <p className="mt-2 text-center text-xs text-slate-500">개인정보가 가려진 예시입니다. 이런 형식으로 제출해 주세요.</p>
                </>
              ) : (
                <>
                  <img src={sample.url} alt="제출 예시" className="mx-auto max-h-[70vh] w-auto rounded border border-slate-100" />
                  <p className="mt-2 text-center text-xs text-slate-500">개인정보가 가려진 예시입니다. 이런 형식으로 촬영·스캔해서 올려주세요.</p>
                </>
              )
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function ItemRow({ item, disabled, uploading, staged, expiry, onExpiryChange, onPick, onPickFiles, onRemoveStaged, onUpload, onShowSample }: {
  item: PublicItem;
  disabled: boolean;
  uploading: boolean;
  staged: Staged[];                      // 담아둔 장(썸네일)
  expiry: string;                        // 만료일 입력값(선택, yyyy-MM-dd)
  onExpiryChange: (v: string) => void;   // 만료일 입력 변경
  onPick: (file: File) => void;          // 촬영/단일 이미지 → 정렬 후 스테이징
  onPickFiles: (files: File[]) => void;  // 앨범·파일 다중 → 스테이징
  onRemoveStaged: (url: string) => void; // 담아둔 장 1개 삭제
  onUpload: () => void;                  // 담아둔 장 전체를 한 번에 병합 전송
  onShowSample: (s: { url: string | null; desc: string | null; pdf?: boolean }) => void;
}) {
  // 촬영과 앨범/파일을 별도 input 으로 분리 — capture 는 촬영 input 에만 둬 앨범·파일·복수 선택을 막지 않는다.
  const cameraRef = useRef<HTMLInputElement | null>(null);
  const filesRef = useRef<HTMLInputElement | null>(null);
  const count = staged.length;
  return (
    <div className="rounded-lg border border-slate-200 p-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <span className={`shrink-0 rounded px-1.5 py-0.5 text-[11px] font-semibold ${item.required ? 'bg-rose-100 text-rose-700' : 'bg-amber-100 text-amber-700'}`}>
            {item.required ? '필수' : '선택'}
          </span>
          <span className="truncate text-sm font-medium text-slate-800">{item.name}</span>
          {(item.sample_image_url || item.sample_description) && (
            <button type="button" onClick={() => onShowSample({ url: item.sample_image_url ?? null, desc: item.sample_description ?? null, pdf: item.sample_pdf })}
              className="shrink-0 rounded border border-brand-200 bg-brand-50 px-1.5 py-0.5 text-[11px] font-semibold text-brand-700 hover:bg-brand-100">
              샘플 보기
            </button>
          )}
        </div>
        {item.uploaded && <div className="mt-0.5 truncate text-xs text-emerald-600">✓ 업로드됨{item.file_name ? ` · ${item.file_name}` : ''}</div>}
      </div>

      {/* 담은 장 목록 — 한 장씩 쌓이고, 각 썸네일에서 개별 삭제. */}
      {count > 0 && (
        <div className="mt-2 flex flex-wrap gap-2">
          {staged.map((s, i) => (
            <div key={s.url} className="relative h-16 w-16 overflow-hidden rounded-md border border-slate-200 bg-slate-50">
              {s.file.type.startsWith('image/')
                ? <img src={s.url} alt={`${i + 1}장`} className="h-full w-full object-cover" />
                : <span className="flex h-full w-full items-center justify-center text-[10px] font-semibold text-slate-500">PDF</span>}
              <span className="absolute left-0 top-0 rounded-br bg-black/50 px-1 text-[10px] font-bold text-white">{i + 1}</span>
              <button type="button" onClick={() => onRemoveStaged(s.url)} aria-label={`${i + 1}장 삭제`}
                className="absolute right-0 top-0 rounded-bl bg-black/60 px-1 text-[11px] leading-4 text-white hover:bg-black/80">✕</button>
            </div>
          ))}
        </div>
      )}

      {/* 만료일 입력(선택) — 만료일 있는 서류만. 비워도 업로드된다(담당자가 나중에 정리). */}
      {count > 0 && item.has_expiry && (
        <label className="mt-2 flex items-center gap-2">
          <span className="shrink-0 text-xs text-slate-500">만료일 <span className="text-slate-400">(모르면 비워두세요)</span></span>
          <input type="date" value={expiry} onChange={(e) => onExpiryChange(e.target.value)}
            disabled={disabled || uploading}
            className="rounded-md border border-slate-300 px-2 py-1 text-xs disabled:opacity-50" />
        </label>
      )}

      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        {/* 촬영 — 후면 카메라. 반복 촬영으로 한 장씩 누적. */}
        <input ref={cameraRef} type="file" accept="image/*" capture="environment" className="hidden"
          onChange={(e) => { const f = e.target.files?.[0]; if (f) onPick(f); e.target.value = ''; }} />
        {/* 앨범·파일 — 여러 장/PDF 가능(capture 없음). Ctrl 다중선택도 그대로 누적. */}
        <input ref={filesRef} type="file" accept="image/*,application/pdf" multiple className="hidden"
          onChange={(e) => { const fs = Array.from(e.target.files ?? []); if (fs.length) onPickFiles(fs); e.target.value = ''; }} />
        <button type="button" disabled={disabled || uploading} onClick={() => cameraRef.current?.click()}
          className="rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
          📷 촬영 추가
        </button>
        <button type="button" disabled={disabled || uploading} onClick={() => filesRef.current?.click()}
          className="rounded-md border border-slate-300 px-2.5 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">
          ＋ 사진·파일
        </button>
        {count > 0 && (
          <button type="button" disabled={disabled || uploading} onClick={onUpload}
            className="ml-auto rounded-md bg-brand-600 px-3 py-1.5 text-xs font-bold text-white hover:bg-brand-700 disabled:opacity-50">
            {uploading ? '올리는 중…' : `업로드 (${count}장)`}
          </button>
        )}
      </div>
      {count > 0 && (
        <p className="mt-1.5 text-[11px] text-slate-400">사진을 계속 추가한 뒤 <b className="text-slate-500">업로드</b>를 누르면 {count}장이 1개로 합쳐 제출됩니다.</p>
      )}
    </div>
  );
}

/** 등록형 미등록 슬롯 — 차량번호/이름 입력 + [등록]. 등록되면 이 자리에 서류 업로드 목록이 뜬다. */
function RegisterRow({ target, value, disabled, registering, onChange, onSubmit }: {
  target: PublicTarget;
  value: string;
  disabled: boolean;
  registering: boolean;
  onChange: (v: string) => void;
  onSubmit: () => void;
}) {
  const isVehicle = target.input_kind === 'VEHICLE_NO';
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3">
      <p className="mb-2 text-xs text-slate-600">
        <strong className="text-slate-800">{target.planned_type_label ?? (isVehicle ? '장비' : '인력')}</strong>
        {' '}— {isVehicle ? '차량번호' : '이름'}을 입력하면 등록되고 서류 업로드가 열립니다.
      </p>
      <div className="flex items-center gap-2">
        <input value={value} onChange={(e) => onChange(e.target.value)}
          placeholder={isVehicle ? '예: 12가3456' : '예: 홍길동'}
          disabled={disabled || registering}
          onKeyDown={(e) => { if (e.key === 'Enter') onSubmit(); }}
          className="min-w-0 flex-1 rounded-md border border-slate-300 px-3 py-2 text-sm disabled:opacity-50" />
        <button type="button" onClick={onSubmit} disabled={disabled || registering || !value.trim()}
          className="shrink-0 rounded-md bg-brand-600 px-4 py-2 text-sm font-bold text-white hover:bg-brand-700 disabled:opacity-50">
          {registering ? '등록 중…' : '등록'}
        </button>
      </div>
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="flex min-h-screen items-center justify-center bg-slate-50">{children}</div>;
}
