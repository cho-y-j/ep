import { useEffect, useRef, useState, type PointerEvent as RPointerEvent } from 'react';
import { maskImageByBoxes } from './maskImage';

type Rect = [number, number, number, number]; // x, y, w, h (원본 이미지 px)
type Handle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';

type Props = {
  /** 표시 + burn 대상 이미지 URL (object URL 등). "적용" 시 이 URL 을 다시 읽어 검정 박스를 픽셀에 굽는다. */
  imageUrl: string;
  /** "적용" — 검정 박스를 실제로 칠한 File 반환. */
  onConfirm: (maskedFile: File) => void;
  /** "건너뛰기/취소" — 가리지 않고 그대로 진행. */
  onCancel: () => void;
};

type Drag =
  | { mode: 'draw'; ax: number; ay: number }
  | { mode: 'move'; idx: number; px: number; py: number; orig: Rect }
  | { mode: 'resize'; idx: number; handle: Handle; orig: Rect };

const HANDLES: Handle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
const HANDLE_R = 6;   // 표시 반지름
const HIT_R = 14;     // 터치/클릭 히트 반지름
const MIN = 8;        // 최소 박스 크기(원본 px)

const CURSOR: Record<Handle, string> = {
  nw: 'nwse-resize', se: 'nwse-resize', ne: 'nesw-resize', sw: 'nesw-resize',
  n: 'ns-resize', s: 'ns-resize', e: 'ew-resize', w: 'ew-resize',
};

function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v;
}

function handlePos(r: Rect, h: Handle): [number, number] {
  const [x, y, w, ht] = r;
  const cx = h.includes('w') ? x : h.includes('e') ? x + w : x + w / 2;
  const cy = h.includes('n') ? y : h.includes('s') ? y + ht : y + ht / 2;
  return [cx, cy];
}

function resizeRect(orig: Rect, h: Handle, nx: number, ny: number, W: number, H: number): Rect {
  let left = orig[0], top = orig[1];
  const right0 = orig[0] + orig[2], bottom0 = orig[1] + orig[3];
  let right = right0, bottom = bottom0;
  if (h.includes('n')) top = clamp(ny, 0, bottom0 - MIN);
  if (h.includes('s')) bottom = clamp(ny, top + MIN, H);
  if (h.includes('w')) left = clamp(nx, 0, right0 - MIN);
  if (h.includes('e')) right = clamp(nx, left + MIN, W);
  return [left, top, right - left, bottom - top];
}

function moveRect(orig: Rect, px: number, py: number, nx: number, ny: number, W: number, H: number): Rect {
  const w = orig[2], ht = orig[3];
  return [clamp(orig[0] + (nx - px), 0, W - w), clamp(orig[1] + (ny - py), 0, H - ht), w, ht];
}

/**
 * 서류 이미지 위 수동 마스킹 — 가릴 곳을 드래그로 검정 박스 여러 개 덮고 "적용" 하면 그 픽셀을 실제로 칠한다.
 * 무라이브러리 SVG + PointerEvent. 좌표는 원본 px 로 저장(DocumentCornerAligner·warpImage 와 일관).
 * 빈영역 드래그=새 박스, 본체 드래그=이동, 8핸들=리사이즈, 선택 후 Delete/삭제버튼=삭제.
 * (드래그 수학은 RegionBoxEditor, 레이아웃/좌표계는 DocumentCornerAligner 관례 재사용.)
 */
export default function DocumentMaskEditor({ imageUrl, onConfirm, onCancel }: Props) {
  const imgRef = useRef<HTMLImageElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const dragRef = useRef<Drag | null>(null);
  const draftRef = useRef<Rect | null>(null);

  const [nat, setNat] = useState<{ w: number; h: number } | null>(null);   // 원본 px
  const [disp, setDisp] = useState<{ w: number; h: number } | null>(null); // 표시 px
  const [boxes, setBoxes] = useState<Rect[]>([]);                          // 원본 px
  const [selectedIdx, setSelectedIdx] = useState<number | null>(null);
  const [draft, setDraft] = useState<Rect | null>(null);
  const [busy, setBusy] = useState(false);

  function onImgLoad() {
    const img = imgRef.current;
    if (!img) return;
    setNat({ w: img.naturalWidth, h: img.naturalHeight });
    setDisp({ w: img.clientWidth, h: img.clientHeight });
  }
  useEffect(() => {
    const img = imgRef.current;
    if (!img) return;
    const ro = new ResizeObserver(() => setDisp({ w: img.clientWidth, h: img.clientHeight }));
    ro.observe(img);
    return () => ro.disconnect();
  }, []);

  function pointerPx(e: RPointerEvent<Element>): [number, number] | null {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0 || rect.height === 0 || !nat) return null;
    return [
      clamp(((e.clientX - rect.left) / rect.width) * nat.w, 0, nat.w),
      clamp(((e.clientY - rect.top) / rect.height) * nat.h, 0, nat.h),
    ];
  }

  function startBackground(e: RPointerEvent<SVGSVGElement>) {
    const p = pointerPx(e);
    if (!p) return;
    dragRef.current = { mode: 'draw', ax: p[0], ay: p[1] };
    draftRef.current = [p[0], p[1], 0, 0];
    setDraft(draftRef.current);
    setSelectedIdx(null);
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function startMove(i: number, e: RPointerEvent<SVGRectElement>) {
    e.stopPropagation();
    const p = pointerPx(e);
    if (!p) return;
    setSelectedIdx(i);
    dragRef.current = { mode: 'move', idx: i, px: p[0], py: p[1], orig: boxes[i] };
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function startResize(i: number, h: Handle, e: RPointerEvent<SVGCircleElement>) {
    e.stopPropagation();
    setSelectedIdx(i);
    dragRef.current = { mode: 'resize', idx: i, handle: h, orig: boxes[i] };
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function onPointerMove(e: RPointerEvent<SVGSVGElement>) {
    const d = dragRef.current;
    if (!d || !nat) return;
    const p = pointerPx(e);
    if (!p) return;
    const [nx, ny] = p;
    if (d.mode === 'draw') {
      const r: Rect = [Math.min(d.ax, nx), Math.min(d.ay, ny), Math.abs(nx - d.ax), Math.abs(ny - d.ay)];
      draftRef.current = r;
      setDraft(r);
    } else if (d.mode === 'move') {
      const nb = moveRect(d.orig, d.px, d.py, nx, ny, nat.w, nat.h);
      setBoxes((prev) => prev.map((b, i) => (i === d.idx ? nb : b)));
    } else {
      const nb = resizeRect(d.orig, d.handle, nx, ny, nat.w, nat.h);
      setBoxes((prev) => prev.map((b, i) => (i === d.idx ? nb : b)));
    }
  }

  function endDrag(e: RPointerEvent<SVGSVGElement>) {
    const d = dragRef.current;
    if (!d) return;
    dragRef.current = null;
    try { svgRef.current?.releasePointerCapture(e.pointerId); } catch { /* noop */ }
    if (d.mode === 'draw') {
      const r = draftRef.current;
      draftRef.current = null;
      setDraft(null);
      if (r && r[2] >= MIN && r[3] >= MIN) {
        setBoxes([...boxes, r]);
        setSelectedIdx(boxes.length);
      }
    }
  }

  function deleteSelected() {
    if (selectedIdx == null) return;
    setBoxes((prev) => prev.filter((_, i) => i !== selectedIdx));
    setSelectedIdx(null);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIdx != null) {
      e.preventDefault();
      deleteSelected();
    }
  }

  async function apply() {
    if (busy) return;
    if (boxes.length === 0) { onCancel(); return; } // 가릴 것 없음 → 그대로 진행
    setBusy(true);
    try {
      const blob = await fetch(imageUrl).then((r) => r.blob());
      const file = new File([blob], 'document.jpg', { type: blob.type || 'image/jpeg' });
      onConfirm(await maskImageByBoxes(file, boxes));
    } catch {
      // 굽기 실패(가리기 미적용) 시 가려지지 않은 채 올리지 않도록 편집을 유지 — 사용자가 재시도/건너뛰기 선택.
      setBusy(false);
    }
  }

  const scaleX = nat && disp ? disp.w / nat.w : 1;
  const scaleY = nat && disp ? disp.h / nat.h : 1;
  const dx = (v: number) => v * scaleX;
  const dy = (v: number) => v * scaleY;
  const ready = !!(nat && disp);

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div tabIndex={0} onKeyDown={onKeyDown}
        className="flex-1 min-h-0 overflow-auto flex items-center justify-center bg-slate-900/90 p-3 outline-none">
        <div className="relative inline-block">
          <img
            ref={imgRef}
            src={imageUrl}
            alt="가리기 대상"
            draggable={false}
            onLoad={onImgLoad}
            className="block max-w-full max-h-[60vh] w-auto h-auto select-none"
          />
          {ready && disp && (
            <svg
              ref={svgRef}
              width={disp.w}
              height={disp.h}
              className="absolute inset-0 touch-none"
              style={{ cursor: 'crosshair' }}
              onPointerDown={startBackground}
              onPointerMove={onPointerMove}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
            >
              {boxes.map((b, i) => {
                const sel = i === selectedIdx;
                return (
                  <rect key={i}
                    x={dx(b[0])} y={dy(b[1])} width={dx(b[2])} height={dy(b[3])}
                    fill="rgba(0,0,0,0.8)"
                    stroke={sel ? '#f43f5e' : 'rgba(255,255,255,0.6)'} strokeWidth={sel ? 2 : 1}
                    style={{ cursor: 'move' }}
                    onPointerDown={(e) => startMove(i, e)}
                  />
                );
              })}
              {selectedIdx != null && boxes[selectedIdx] && HANDLES.map((h) => {
                const [hx, hy] = handlePos(boxes[selectedIdx], h);
                return (
                  <g key={h}>
                    <circle cx={dx(hx)} cy={dy(hy)} r={HIT_R} fill="transparent"
                      style={{ cursor: CURSOR[h] }}
                      onPointerDown={(e) => startResize(selectedIdx, h, e)} />
                    <circle cx={dx(hx)} cy={dy(hy)} r={HANDLE_R} pointerEvents="none"
                      fill="#ffffff" stroke="#f43f5e" strokeWidth={2} />
                  </g>
                );
              })}
              {draft && (
                <rect x={dx(draft[0])} y={dy(draft[1])} width={dx(draft[2])} height={dy(draft[3])}
                  fill="rgba(0,0,0,0.55)" stroke="#f43f5e" strokeWidth={1.5} strokeDasharray="4 3"
                  pointerEvents="none" />
              )}
            </svg>
          )}
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 px-4 py-3 border-t border-slate-200 bg-white">
        <span className="text-xs text-slate-500">
          가릴 곳을 드래그해 검정 박스로 덮으세요. 박스를 눌러 옮기거나 크기 조절, 선택 후 삭제할 수 있어요.
        </span>
        <div className="flex gap-2 shrink-0">
          {selectedIdx != null && (
            <button type="button" onClick={deleteSelected}
              className="text-sm px-3 py-1.5 rounded text-rose-600 hover:bg-rose-50">삭제</button>
          )}
          <button type="button" onClick={onCancel} disabled={busy}
            className="text-sm px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100 disabled:opacity-50">건너뛰기</button>
          <button type="button" onClick={() => void apply()} disabled={busy}
            className="text-sm px-4 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
            {busy ? '적용 중…' : '적용'}
          </button>
        </div>
      </div>
    </div>
  );
}
