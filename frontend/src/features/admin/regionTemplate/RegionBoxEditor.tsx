import { useEffect, useRef, useState, type PointerEvent as RPointerEvent } from 'react';
import type { Box } from './regionTemplateTypes';

type Props = {
  /** warp된(또는 평면) 캔버스 이미지 URL — natural=aspect. 박스는 이 위에서만 그린다(좌표계 불변식). */
  imageUrl: string;
  boxes: Box[];
  selectedIdx: number | null;
  onChange: (boxes: Box[]) => void;
  onSelect: (idx: number | null) => void;
};

type Handle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';
type Rect = [number, number, number, number]; // x,y,w,h 0..1

type Drag =
  | { mode: 'draw'; ax: number; ay: number }
  | { mode: 'move'; idx: number; px: number; py: number; orig: Rect }
  | { mode: 'resize'; idx: number; handle: Handle; orig: Rect };

const HANDLES: Handle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
const HANDLE_R = 5;   // 표시 반지름
const HIT_R = 11;     // 히트 반지름
const MIN = 0.01;     // 최소 박스 크기(정규화)

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

function resizeRect(orig: Rect, h: Handle, nx: number, ny: number): Rect {
  let left = orig[0], top = orig[1];
  const right0 = orig[0] + orig[2], bottom0 = orig[1] + orig[3];
  let right = right0, bottom = bottom0;
  if (h.includes('n')) top = clamp(ny, 0, bottom0 - MIN);
  if (h.includes('s')) bottom = clamp(ny, top + MIN, 1);
  if (h.includes('w')) left = clamp(nx, 0, right0 - MIN);
  if (h.includes('e')) right = clamp(nx, left + MIN, 1);
  return [left, top, right - left, bottom - top];
}

function moveRect(orig: Rect, px: number, py: number, nx: number, ny: number): Rect {
  const w = orig[2], ht = orig[3];
  return [clamp(orig[0] + (nx - px), 0, 1 - w), clamp(orig[1] + (ny - py), 0, 1 - ht), w, ht];
}

/**
 * warp된 캔버스 위 필드 박스 편집기 — 무라이브러리 SVG. 좌표는 0..1 정규화로 저장(해상도 불변).
 * 빈 캔버스 드래그=새 박스, 박스 본체 드래그=이동, 8핸들=리사이즈, 클릭=선택, Delete=삭제.
 * PointerEvent + setPointerCapture 패턴은 DocumentCornerAligner 관례 재사용.
 */
export default function RegionBoxEditor({ imageUrl, boxes, selectedIdx, onChange, onSelect }: Props) {
  const imgRef = useRef<HTMLImageElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const dragRef = useRef<Drag | null>(null);
  const draftRef = useRef<Rect | null>(null);

  const [disp, setDisp] = useState<{ w: number; h: number } | null>(null);
  const [draft, setDraft] = useState<Rect | null>(null);

  function onImgLoad() {
    const img = imgRef.current;
    if (img) setDisp({ w: img.clientWidth, h: img.clientHeight });
  }
  useEffect(() => {
    const img = imgRef.current;
    if (!img) return;
    const ro = new ResizeObserver(() => setDisp({ w: img.clientWidth, h: img.clientHeight }));
    ro.observe(img);
    return () => ro.disconnect();
  }, []);

  function pointerNorm(e: RPointerEvent<Element>): [number, number] | null {
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0 || rect.height === 0) return null;
    return [clamp((e.clientX - rect.left) / rect.width, 0, 1), clamp((e.clientY - rect.top) / rect.height, 0, 1)];
  }

  function startBackground(e: RPointerEvent<SVGSVGElement>) {
    const p = pointerNorm(e);
    if (!p) return;
    dragRef.current = { mode: 'draw', ax: p[0], ay: p[1] };
    draftRef.current = [p[0], p[1], 0, 0];
    setDraft(draftRef.current);
    onSelect(null);
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function startMove(i: number, e: RPointerEvent<SVGRectElement>) {
    e.stopPropagation();
    const p = pointerNorm(e);
    if (!p) return;
    onSelect(i);
    dragRef.current = { mode: 'move', idx: i, px: p[0], py: p[1], orig: boxes[i].box };
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function startResize(i: number, h: Handle, e: RPointerEvent<SVGCircleElement>) {
    e.stopPropagation();
    onSelect(i);
    dragRef.current = { mode: 'resize', idx: i, handle: h, orig: boxes[i].box };
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function onPointerMove(e: RPointerEvent<SVGSVGElement>) {
    const d = dragRef.current;
    if (!d) return;
    const p = pointerNorm(e);
    if (!p) return;
    const [nx, ny] = p;
    if (d.mode === 'draw') {
      const r: Rect = [Math.min(d.ax, nx), Math.min(d.ay, ny), Math.abs(nx - d.ax), Math.abs(ny - d.ay)];
      draftRef.current = r;
      setDraft(r);
    } else if (d.mode === 'move') {
      const nb = moveRect(d.orig, d.px, d.py, nx, ny);
      onChange(boxes.map((b, i) => (i === d.idx ? { ...b, box: nb } : b)));
    } else {
      const nb = resizeRect(d.orig, d.handle, nx, ny);
      onChange(boxes.map((b, i) => (i === d.idx ? { ...b, box: nb } : b)));
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
        onChange([...boxes, { key: '', box: r, parser: 'text' }]);
        onSelect(boxes.length);
      }
    }
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.key === 'Delete' || e.key === 'Backspace') && selectedIdx != null) {
      e.preventDefault();
      onChange(boxes.filter((_, i) => i !== selectedIdx));
      onSelect(null);
    }
  }

  const px = (v: number) => v * (disp?.w ?? 0);
  const py = (v: number) => v * (disp?.h ?? 0);

  return (
    <div tabIndex={0} onKeyDown={onKeyDown}
      className="flex-1 min-h-0 overflow-auto flex items-center justify-center bg-slate-900/90 p-3 outline-none">
      <div className="relative inline-block">
        <img
          ref={imgRef}
          src={imageUrl}
          alt="영역지정 캔버스"
          draggable={false}
          onLoad={onImgLoad}
          className="block max-w-full max-h-[70vh] w-auto h-auto select-none"
        />
        {disp && (
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
                <g key={i}>
                  <rect
                    x={px(b.box[0])} y={py(b.box[1])} width={px(b.box[2])} height={py(b.box[3])}
                    fill={sel ? 'rgba(37,99,235,0.14)' : 'rgba(37,99,235,0.06)'}
                    stroke={sel ? '#1d4ed8' : '#60a5fa'} strokeWidth={sel ? 2 : 1.5}
                    style={{ cursor: 'move' }}
                    onPointerDown={(e) => startMove(i, e)}
                  />
                  {py(b.box[1]) > 12 && (
                    <text x={px(b.box[0]) + 2} y={py(b.box[1]) - 3}
                      fontSize={11} fill={sel ? '#1d4ed8' : '#3b82f6'} pointerEvents="none">
                      {b.key || '(키 미지정)'}
                    </text>
                  )}
                </g>
              );
            })}
            {selectedIdx != null && boxes[selectedIdx] && HANDLES.map((h) => {
              const [hx, hy] = handlePos(boxes[selectedIdx].box, h);
              return (
                <g key={h}>
                  <circle cx={px(hx)} cy={py(hy)} r={HIT_R} fill="transparent"
                    style={{ cursor: CURSOR[h] }}
                    onPointerDown={(e) => startResize(selectedIdx, h, e)} />
                  <circle cx={px(hx)} cy={py(hy)} r={HANDLE_R} pointerEvents="none"
                    fill="#ffffff" stroke="#1d4ed8" strokeWidth={2} />
                </g>
              );
            })}
            {draft && (
              <rect x={px(draft[0])} y={py(draft[1])} width={px(draft[2])} height={py(draft[3])}
                fill="rgba(37,99,235,0.10)" stroke="#1d4ed8" strokeWidth={1.5} strokeDasharray="4 3"
                pointerEvents="none" />
            )}
          </svg>
        )}
      </div>
    </div>
  );
}
