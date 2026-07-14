import { useEffect, useRef, useState, type PointerEvent as RPointerEvent } from 'react';

type Point = [number, number];

type Props = {
  /** 표시할 이미지 URL (object URL 등). */
  imageUrl: string;
  /** 자동검출 등으로 프리필할 4모서리 — 원본 이미지 px 좌표 [TL, TR, BR, BL]. 없으면 이미지 네 꼭짓점. */
  initialCorners?: Point[];
  /** 확정 시 원본 이미지 px 좌표 4점 [TL, TR, BR, BL] 반환. */
  onConfirm: (corners: Point[]) => void;
  onCancel: () => void;
};

const HANDLE_R = 9;   // 핸들 표시 반지름(표시 px)
const HIT_R = 22;     // 터치/클릭 히트 반지름(표시 px)

function clamp(v: number, lo: number, hi: number): number {
  return v < lo ? lo : v > hi ? hi : v;
}

/**
 * 4모서리 영역 맞추기 — 배경 크롭 + 원근보정용 4점을 사용자가 드래그로 지정.
 * 외부 라이브러리 없이 SVG + PointerEvent. 좌표 상태는 원본 이미지 px 로 보관해(리사이즈 불변)
 * 표시(scaled) 좌표로 그리고, onConfirm 은 원본 px 로 반환한다.
 * 재사용 가능(업로드 정렬 / 수퍼어드민 영역지정 도구).
 */
export default function DocumentCornerAligner({ imageUrl, initialCorners, onConfirm, onCancel }: Props) {
  const imgRef = useRef<HTMLImageElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const dragRef = useRef<number | null>(null);

  const [nat, setNat] = useState<{ w: number; h: number } | null>(null);   // 원본 px
  const [disp, setDisp] = useState<{ w: number; h: number } | null>(null); // 표시 px
  const [corners, setCorners] = useState<Point[]>([]);                     // 원본 px, TL,TR,BR,BL
  const [dragIdx, setDragIdx] = useState<number | null>(null);

  // 이미지 로드 시 원본/표시 크기 확정 + 코너 초기화.
  function onImgLoad() {
    const img = imgRef.current;
    if (!img) return;
    const nw = img.naturalWidth, nh = img.naturalHeight;
    setNat({ w: nw, h: nh });
    setDisp({ w: img.clientWidth, h: img.clientHeight });
    setCorners(
      initialCorners && initialCorners.length === 4
        ? initialCorners.map(([x, y]) => [clamp(x, 0, nw), clamp(y, 0, nh)] as Point)
        : [[0, 0], [nw, 0], [nw, nh], [0, nh]],
    );
  }

  // 컨테이너 리사이즈 → 표시 크기만 갱신 (코너는 원본 px 라 불변).
  useEffect(() => {
    const img = imgRef.current;
    if (!img) return;
    const ro = new ResizeObserver(() => setDisp({ w: img.clientWidth, h: img.clientHeight }));
    ro.observe(img);
    return () => ro.disconnect();
  }, []);

  const scaleX = nat && disp ? disp.w / nat.w : 1;
  const scaleY = nat && disp ? disp.h / nat.h : 1;
  const toDisp = (p: Point): Point => [p[0] * scaleX, p[1] * scaleY];

  function startDrag(i: number, e: RPointerEvent<SVGCircleElement>) {
    e.preventDefault();
    dragRef.current = i;
    setDragIdx(i);
    try { svgRef.current?.setPointerCapture(e.pointerId); } catch { /* noop */ }
  }

  function onPointerMove(e: RPointerEvent<SVGSVGElement>) {
    const i = dragRef.current;
    if (i == null || !nat) return;
    const rect = svgRef.current?.getBoundingClientRect();
    if (!rect || rect.width === 0 || rect.height === 0) return;
    const nx = clamp(((e.clientX - rect.left) / rect.width) * nat.w, 0, nat.w);
    const ny = clamp(((e.clientY - rect.top) / rect.height) * nat.h, 0, nat.h);
    setCorners((prev) => prev.map((p, idx) => (idx === i ? [nx, ny] as Point : p)));
  }

  function endDrag(e: RPointerEvent<SVGSVGElement>) {
    if (dragRef.current == null) return;
    dragRef.current = null;
    setDragIdx(null);
    try { svgRef.current?.releasePointerCapture(e.pointerId); } catch { /* noop */ }
  }

  const ready = !!(nat && disp && corners.length === 4);
  const dispPts = ready ? corners.map(toDisp) : [];
  const polyPoints = dispPts.map(([x, y]) => `${x},${y}`).join(' ');
  // 선택 사각형 밖을 어둡게 (문서만 남는 크롭 느낌) — 외곽 rect + 내부 4각형 hole (even-odd).
  const holePath = dispPts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x},${y}`).join(' ') + ' Z';

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 min-h-0 overflow-auto flex items-center justify-center bg-slate-900/90 p-3">
        <div className="relative inline-block">
          <img
            ref={imgRef}
            src={imageUrl}
            alt="정렬 대상"
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
              onPointerMove={onPointerMove}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
            >
              <path d={`M0,0 H${disp.w} V${disp.h} H0 Z ${holePath}`} fill="rgba(0,0,0,0.45)" fillRule="evenodd" />
              <polygon points={polyPoints} fill="rgba(37,99,235,0.10)" stroke="#2563eb" strokeWidth={2} />
              {dispPts.map(([x, y], i) => (
                <g key={i}>
                  <circle cx={x} cy={y} r={HIT_R} fill="transparent" style={{ cursor: 'grab' }}
                    onPointerDown={(e) => startDrag(i, e)} />
                  <circle cx={x} cy={y} r={HANDLE_R} pointerEvents="none"
                    fill={dragIdx === i ? '#1d4ed8' : '#ffffff'} stroke="#2563eb" strokeWidth={3} />
                </g>
              ))}
            </svg>
          )}
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 px-4 py-3 border-t border-slate-200 bg-white">
        <span className="text-xs text-slate-500">문서 네 모서리에 점을 맞추세요. 배경은 잘라내고 반듯하게 펴집니다.</span>
        <div className="flex gap-2 shrink-0">
          <button type="button" onClick={onCancel}
            className="text-sm px-3 py-1.5 rounded text-slate-700 hover:bg-slate-100">취소</button>
          <button type="button" disabled={!ready}
            onClick={() => onConfirm(corners.map(([x, y]) => [Math.round(x), Math.round(y)] as Point))}
            className="text-sm px-4 py-1.5 rounded bg-brand-600 text-white hover:bg-brand-700 disabled:opacity-50">
            맞추기 완료
          </button>
        </div>
      </div>
    </div>
  );
}
