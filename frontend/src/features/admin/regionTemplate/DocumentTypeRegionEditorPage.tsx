import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import AppShell from '../../../components/layout/AppShell';
import { api } from '../../../lib/api';
import { toast } from '../../../lib/toast';
import { parseRequiredFields, type DocumentTypeResponse } from '../../../types/document';
import DocumentCornerAligner from '../../document/DocumentCornerAligner';
import { detectDocumentCorners } from '../../document/detectCorners';
import RegionBoxEditor from './RegionBoxEditor';
import FieldInspector from './FieldInspector';
import {
  type Aspect, type Box, type RegionTemplate,
  DEFAULT_ASPECT, STANDARD_KEYS,
} from './regionTemplateTypes';

type Point = [number, number];
type Step = 'upload' | 'align' | 'edit';

type ExtractResp = {
  ok?: boolean;
  aligned?: boolean;
  fields?: Record<string, string>;
  regions?: { key: string; text: string; score: number }[];
  warped_image_base64?: string;
};

/** 저장된 ocr_region_template(JSON) → aspect + boxes. 형식 불량이면 null. */
function parseTemplate(s?: string | null): { aspect: Aspect; boxes: Box[] } | null {
  if (!s || !s.trim()) return null;
  try {
    const t = JSON.parse(s) as RegionTemplate;
    if (!t.aspect || !Array.isArray(t.fields)) return null;
    return {
      aspect: { w: t.aspect.w, h: t.aspect.h },
      boxes: t.fields.map((f) => ({ key: f.key, box: f.box, parser: f.parser })),
    };
  } catch {
    return null;
  }
}

/** 4모서리에서 문서의 실제 종횡비를 추정 → warp aspect 로. A4 강제 대신이라 신분증·자격증 등 비-A4 왜곡 방지.
 *  긴 변을 2048로 스케일해 비율 보존(코너 순서 무관: 합=TL/BR, y-x=TR/BL 로 정렬). */
function aspectFromCorners(c: Point[] | null): Aspect | null {
  if (!c || c.length !== 4) return null;
  const bySum = [...c].sort((a, b) => (a[0] + a[1]) - (b[0] + b[1]));
  const byDiff = [...c].sort((a, b) => (a[1] - a[0]) - (b[1] - b[0]));
  const tl = bySum[0], br = bySum[3], tr = byDiff[0], bl = byDiff[3];
  const dist = (p: Point, q: Point) => Math.hypot(p[0] - q[0], p[1] - q[1]);
  const w = (dist(tl, tr) + dist(bl, br)) / 2;
  const h = (dist(tl, bl) + dist(tr, br)) / 2;
  if (!(w > 0) || !(h > 0)) return null;
  const longer = 2048;
  return w >= h
    ? { w: longer, h: Math.max(1, Math.round((longer * h) / w)) }
    : { w: Math.max(1, Math.round((longer * w) / h)), h: longer };
}

/**
 * 수퍼어드민 영역지정(템플릿) 도구 — 오케스트레이터.
 * 업로드 → (사진이면 4모서리 정렬+warp / 평면이면 스킵) → warped 캔버스 위 박스 편집 → 디바운스 미리보기 → 저장(PATCH).
 * 좌표계 불변식: 박스는 반드시 warp된(또는 원본=aspect) 캔버스 위에서만.
 */
export default function DocumentTypeRegionEditorPage() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [docType, setDocType] = useState<DocumentTypeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [sampleLoading, setSampleLoading] = useState(false);   // 등록된 샘플을 배경으로 불러오는 중
  const [step, setStep] = useState<Step>('upload');

  const [aspect, setAspect] = useState<Aspect>(DEFAULT_ASPECT);
  const [file, setFile] = useState<File | null>(null);
  const [sampleUrl, setSampleUrl] = useState<string | null>(null);   // 원본 object URL (정렬용)
  const [canvasUrl, setCanvasUrl] = useState<string | null>(null);   // warped data URL 또는 원본(평면)
  const [corners, setCorners] = useState<Point[] | null>(null);      // 확정 코너(warp) — 미리보기 재현용, 평면이면 null
  const [initialCorners, setInitialCorners] = useState<Point[] | undefined>(undefined);

  const [boxes, setBoxes] = useState<Box[]>([]);
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);

  const [alignBusy, setAlignBusy] = useState(false);
  const [warpBusy, setWarpBusy] = useState(false);
  const [preview, setPreview] = useState<{ fields: Record<string, string>; regions: ExtractResp['regions'] } | null>(null);
  const [previewBusy, setPreviewBusy] = useState(false);
  const [saving, setSaving] = useState(false);

  // doc-type 로드(리스트 → id find) + 기존 템플릿 프리필(aspect·boxes).
  useEffect(() => {
    let alive = true;
    api.get<DocumentTypeResponse[]>('/api/admin/document-types')
      .then((r) => {
        if (!alive) return;
        const dt = r.data.find((t) => t.id === Number(id)) ?? null;
        setDocType(dt);
        const parsed = dt ? parseTemplate(dt.ocr_region_template) : null;
        if (parsed) { setAspect(parsed.aspect); setBoxes(parsed.boxes); }
        // V116 샘플 이미지가 있으면 그 위에서 바로 영역 편집(재업로드 불필요). 좌표계는 그대로 —
        // 새 템플릿일 때만 aspect 를 샘플 natural 로 잡고(skipAlign 과 동일), 기존 템플릿은 저장 aspect 유지.
        // 로드 실패(키만 있고 파일 유실 등)면 조용히 업로드 폼으로 폴백.
        const sampleKey = dt?.sample_image_key;
        if (dt && sampleKey) {
          setSampleLoading(true);
          const url = `/api/document-types/${dt.id}/sample?v=${encodeURIComponent(sampleKey)}`;
          const img = new Image();
          img.onload = () => {
            if (!alive) return;
            if (!parsed) setAspect({ w: img.naturalWidth, h: img.naturalHeight });
            setCanvasUrl(url);
            setStep('edit');
            setSampleLoading(false);
          };
          img.onerror = () => { if (alive) setSampleLoading(false); };
          img.src = url;
        }
      })
      .catch(() => { if (alive) toast.error('서류 종류를 불러올 수 없습니다'); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [id]);

  // 원본 object URL 생성/해제.
  useEffect(() => {
    if (!file) { setSampleUrl(null); return; }
    const url = URL.createObjectURL(file);
    setSampleUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const keyOptions = useMemo(
    () => Array.from(new Set([...STANDARD_KEYS, ...parseRequiredFields(docType?.required_fields)])),
    [docType],
  );
  const boxesKey = JSON.stringify(boxes);

  // 디바운스(700ms) 미리보기 — 현재 박스로 region-extract(returnWarped=false), in-flight 취소.
  useEffect(() => {
    if (step !== 'edit' || !file) return;
    if (boxes.length === 0) { setPreview(null); return; }
    const ctrl = new AbortController();
    const timer = setTimeout(async () => {
      setPreviewBusy(true);
      try {
        const fd = new FormData();
        fd.append('file', file);
        fd.append('template', JSON.stringify({ version: 1, aspect, fields: boxes }));
        if (corners) fd.append('corners', JSON.stringify(corners));
        fd.append('returnWarped', 'false');
        const res = await api.post<ExtractResp>('/api/admin/document-types/region-extract', fd, {
          headers: { 'Content-Type': 'multipart/form-data' }, signal: ctrl.signal,
        });
        if (ctrl.signal.aborted) return;
        setPreview({ fields: res.data.fields ?? {}, regions: res.data.regions ?? [] });
      } catch {
        /* 취소/실패 — 무시(미리보기는 best-effort) */
      } finally {
        if (!ctrl.signal.aborted) setPreviewBusy(false);
      }
    }, 700);
    return () => { clearTimeout(timer); ctrl.abort(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, file, corners, aspect.w, aspect.h, boxesKey]);

  function onFilePicked(f: File) {
    setFile(f);
    setCanvasUrl(null);
    setCorners(null);
    setSelectedIdx(null);
    setPreview(null);
    setStep('upload');
  }

  /** 평면(스캔/캡처) — 정렬 스킵, 캔버스=원본, aspect=원본 natural. */
  function skipAlign() {
    if (!sampleUrl) return;
    const img = new Image();
    img.onload = () => {
      setAspect({ w: img.naturalWidth, h: img.naturalHeight });
      setCanvasUrl(sampleUrl);
      setCorners(null);
      setStep('edit');
    };
    img.src = sampleUrl;
  }

  /** 사진 — 4모서리 정렬 단계 진입(자동검출 프리필). */
  async function startAlign() {
    if (!file) return;
    setInitialCorners(undefined);
    setAlignBusy(true);
    setStep('align');
    const c = await detectDocumentCorners(file);
    setInitialCorners(c);
    setAlignBusy(false);
  }

  /** 정렬 확정 → 서버 warp(returnWarped=true) 로 반듯한 캔버스 획득. */
  async function doWarp(c: Point[]) {
    if (!file) return;
    setWarpBusy(true);
    // 코너에서 문서 실제 종횡비를 계산해 warp aspect 로 사용 → A4 강제 왜곡 방지(신분증·자격증 등 비-A4).
    // 사용자가 필요 시 aspect 입력/A4 프리셋으로 덮어쓸 수 있음.
    const derived = aspectFromCorners(c) ?? aspect;
    setAspect(derived);
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('template', JSON.stringify({ version: 1, aspect: derived, fields: [] }));
      fd.append('corners', JSON.stringify(c));
      fd.append('returnWarped', 'true');
      const res = await api.post<ExtractResp>('/api/admin/document-types/region-extract', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      if (res.data.warped_image_base64) {
        setCanvasUrl(`data:image/png;base64,${res.data.warped_image_base64}`);
        setCorners(c);
        setStep('edit');
      } else {
        toast.error('정렬 이미지를 가져오지 못했습니다 (paddle 미가동?)');
      }
    } catch {
      toast.error('정렬 처리 실패');
    } finally {
      setWarpBusy(false);
    }
  }

  async function save() {
    if (!id) return;
    setSaving(true);
    try {
      const tmpl = JSON.stringify({ version: 1, aspect, fields: boxes });
      await api.patch(`/api/admin/document-types/${id}`, { ocr_region_template: tmpl });
      setDocType((prev) => (prev ? { ...prev, ocr_region_template: tmpl } : prev));
      toast.success('영역 템플릿을 저장했습니다');
    } catch (e: any) {
      toast.error(e?.response?.data?.message ?? '저장 실패');
    } finally {
      setSaving(false);
    }
  }

  const selectedBox = selectedIdx != null ? boxes[selectedIdx] ?? null : null;
  const selectedPreview = useMemo(() => {
    if (!selectedBox || !preview) return null;
    const region = preview.regions?.find((r) => r.key === selectedBox.key);
    return {
      value: preview.fields[selectedBox.key] ?? '',
      rawText: region?.text ?? '',
      score: region?.score ?? 0,
    };
  }, [selectedBox, preview]);

  return (
    <AppShell breadcrumb={[{ label: '서류종류 관리', to: '/admin/document-types' }, { label: '영역 편집' }]}>
      <div className="max-w-6xl mx-auto px-6 py-6">
        <div className="flex items-center gap-3 mb-4">
          <button type="button" onClick={() => navigate('/admin/document-types')}
            className="text-sm px-2.5 py-1 rounded text-slate-600 hover:bg-slate-100">← 목록</button>
          <h1 className="text-xl font-bold">
            영역지정 — {loading ? '…' : docType?.name ?? `서류 #${id}`}
          </h1>
        </div>

        {loading || sampleLoading ? (
          <div className="card p-8 text-center text-slate-400">불러오는 중…</div>
        ) : !docType ? (
          <div className="card p-8 text-center text-slate-400">해당 서류 종류를 찾을 수 없습니다.</div>
        ) : step === 'upload' ? (
          <div className="card p-6 space-y-5 max-w-2xl">
            {!docType.sample_image_key && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
                <p className="text-sm font-semibold text-amber-800">
                  샘플 이미지를 먼저 등록하면 그 위에서 영역을 지정할 수 있습니다.
                </p>
                <p className="mt-1 text-xs text-amber-700">
                  서류종류 관리에서 이 서류의 샘플 이미지를 등록하면, 다음부터 그 이미지를 배경으로 바로 영역을 그릴 수 있어요.
                  <button type="button" onClick={() => navigate('/admin/document-types')}
                    className="ml-1 font-semibold underline hover:text-amber-900">서류종류 관리로 이동</button>
                </p>
              </div>
            )}
            <div>
              <div className="text-sm font-semibold text-slate-700 mb-1">정렬 비율(aspect)</div>
              <div className="flex items-end gap-2">
                <label className="block">
                  <span className="text-xs text-slate-500">가로(w)</span>
                  <input type="number" min={1} value={aspect.w}
                    onChange={(e) => setAspect((a) => ({ ...a, w: Math.max(1, Number(e.target.value) || 1) }))}
                    className="input mt-1 w-28" />
                </label>
                <span className="pb-2 text-slate-400">×</span>
                <label className="block">
                  <span className="text-xs text-slate-500">세로(h)</span>
                  <input type="number" min={1} value={aspect.h}
                    onChange={(e) => setAspect((a) => ({ ...a, h: Math.max(1, Number(e.target.value) || 1) }))}
                    className="input mt-1 w-28" />
                </label>
                <button type="button" onClick={() => setAspect(DEFAULT_ASPECT)}
                  className="pb-2 text-xs text-brand-600 hover:underline">A4(1653×2339)</button>
              </div>
              <p className="text-[11px] text-slate-400 mt-1">
                사진 정렬(warp) 시 이 비율로 폅니다. 평면 스캔은 원본 크기를 사용합니다.
              </p>
            </div>

            <div>
              <div className="text-sm font-semibold text-slate-700 mb-1">샘플 이미지</div>
              <label className="block px-4 py-6 rounded-lg border-2 border-dashed border-slate-300 text-center cursor-pointer hover:bg-slate-50">
                <div className="text-sm text-slate-700">클릭하여 이미지 선택</div>
                <div className="text-xs text-slate-400 mt-1">JPG / PNG / WEBP</div>
                <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
                  onChange={(e) => { const f = e.target.files?.[0]; if (f) onFilePicked(f); }} />
              </label>
            </div>

            {file && sampleUrl && (
              <div className="flex gap-4 items-start border-t border-slate-100 pt-4">
                <img src={sampleUrl} alt="샘플" className="w-32 h-32 object-contain rounded border border-slate-200 bg-slate-50" />
                <div className="space-y-2">
                  <div className="text-xs text-slate-500">이 샘플을 어떻게 다룰까요?</div>
                  <button type="button" onClick={() => void startAlign()}
                    className="block w-full text-left text-sm px-3 py-2 rounded-lg bg-brand-600 text-white hover:bg-brand-700">
                    폰 사진 — 4모서리 정렬 후 편집
                  </button>
                  <button type="button" onClick={skipAlign}
                    className="block w-full text-left text-sm px-3 py-2 rounded-lg border border-slate-300 text-slate-700 hover:bg-slate-50">
                    스캔/캡처 — 정렬 없이 편집
                  </button>
                  {boxes.length > 0 && (
                    <p className="text-[11px] text-slate-400">기존 템플릿 {boxes.length}개 박스를 편집합니다.</p>
                  )}
                </div>
              </div>
            )}
          </div>
        ) : step === 'align' ? (
          <div className="card overflow-hidden flex flex-col h-[74vh]">
            {alignBusy || warpBusy || !sampleUrl ? (
              <div className="flex-1 flex flex-col items-center justify-center gap-4">
                <div className="relative w-14 h-14">
                  <div className="absolute inset-0 rounded-full border-4 border-slate-200" />
                  <div className="absolute inset-0 rounded-full border-4 border-brand-600 border-t-transparent animate-spin" />
                </div>
                <div className="text-sm font-semibold text-slate-900">
                  {warpBusy ? '정렬(warp) 처리 중…' : '문서 모서리 자동 검출 중…'}
                </div>
              </div>
            ) : (
              <DocumentCornerAligner
                imageUrl={sampleUrl}
                initialCorners={initialCorners}
                onConfirm={(c) => void doWarp(c)}
                onCancel={() => setStep('upload')}
              />
            )}
          </div>
        ) : (
          <div className="card p-0 overflow-hidden">
            <div className="flex items-center justify-between px-4 py-2.5 border-b border-slate-200 bg-slate-50">
              <div className="text-xs text-slate-500">
                aspect {aspect.w}×{aspect.h} · 박스 {boxes.length}개
                {previewBusy && <span className="ml-2 text-brand-600">미리보기 갱신 중…</span>}
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={() => setStep('upload')}
                  className="text-sm px-3 py-1.5 rounded border border-slate-300 text-slate-600 hover:bg-slate-50">
                  다른 샘플
                </button>
                <button type="button" onClick={() => void save()} disabled={saving}
                  className="text-sm px-4 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
                  {saving ? '저장 중…' : '저장'}
                </button>
              </div>
            </div>
            <div className="flex min-h-0" style={{ height: '72vh' }}>
              <div className="flex-1 min-w-0 flex flex-col">
                {canvasUrl && (
                  <RegionBoxEditor
                    imageUrl={canvasUrl}
                    boxes={boxes}
                    selectedIdx={selectedIdx}
                    onChange={setBoxes}
                    onSelect={setSelectedIdx}
                  />
                )}
              </div>
              <div className="w-80 shrink-0 border-l border-slate-200 overflow-auto">
                <div className="px-4 py-2 border-b border-slate-200 bg-white text-xs font-semibold text-slate-700">
                  필드 {selectedIdx != null ? `#${selectedIdx + 1}` : ''}
                </div>
                <FieldInspector
                  box={selectedBox}
                  keyOptions={keyOptions}
                  preview={selectedPreview}
                  onChange={(patch) => setBoxes((prev) => prev.map((b, i) => (i === selectedIdx ? { ...b, ...patch } : b)))}
                  onDelete={() => {
                    if (selectedIdx == null) return;
                    setBoxes((prev) => prev.filter((_, i) => i !== selectedIdx));
                    setSelectedIdx(null);
                  }}
                />
                <div className="px-4 pb-4 text-[11px] text-slate-400">
                  빈 곳을 드래그해 박스를 만들고, 본체 드래그로 이동, 모서리·변 핸들로 크기 조절, Delete 로 삭제합니다.
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
