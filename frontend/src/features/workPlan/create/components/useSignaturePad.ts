import { useEffect, useRef, useState } from 'react';

/**
 * HTML5 캔버스 사인 패드 공통 훅 — SignaturePadDialog + SignaturePage 양쪽에서 재사용.
 * 마우스/터치 둘 다 지원. PNG dataURL 반환.
 *
 * @param enabled 캔버스 초기화 트리거 — true로 바뀔 때마다 흰 배경으로 reset.
 */
export function useSignaturePad(enabled: boolean) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const drawingRef = useRef(false);
  const lastPosRef = useRef<{ x: number; y: number } | null>(null);
  const [hasInk, setHasInk] = useState(false);

  useEffect(() => {
    if (!enabled) return;
    const c = canvasRef.current;
    if (!c) return;
    const ctx = c.getContext('2d');
    if (!ctx) return;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, c.width, c.height);
    ctx.strokeStyle = '#0f172a';
    ctx.lineWidth = 2.5;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    setHasInk(false);
  }, [enabled]);

  const getPos = (e: React.MouseEvent | React.TouchEvent) => {
    const c = canvasRef.current!;
    const rect = c.getBoundingClientRect();
    const sx = c.width / rect.width;
    const sy = c.height / rect.height;
    let cx = 0, cy = 0;
    if ('touches' in e) {
      const t = e.touches[0] ?? e.changedTouches[0];
      cx = t.clientX; cy = t.clientY;
    } else {
      cx = e.clientX; cy = e.clientY;
    }
    return { x: (cx - rect.left) * sx, y: (cy - rect.top) * sy };
  };

  const handlers = {
    onMouseDown: (e: React.MouseEvent) => {
      e.preventDefault();
      drawingRef.current = true;
      lastPosRef.current = getPos(e);
    },
    onMouseMove: (e: React.MouseEvent) => {
      if (!drawingRef.current) return;
      e.preventDefault();
      const c = canvasRef.current!;
      const ctx = c.getContext('2d')!;
      const pos = getPos(e);
      const last = lastPosRef.current!;
      ctx.beginPath();
      ctx.moveTo(last.x, last.y);
      ctx.lineTo(pos.x, pos.y);
      ctx.stroke();
      lastPosRef.current = pos;
      setHasInk(true);
    },
    onMouseUp: () => { drawingRef.current = false; lastPosRef.current = null; },
    onMouseLeave: () => { drawingRef.current = false; lastPosRef.current = null; },
    onTouchStart: (e: React.TouchEvent) => {
      e.preventDefault();
      drawingRef.current = true;
      lastPosRef.current = getPos(e);
    },
    onTouchMove: (e: React.TouchEvent) => {
      if (!drawingRef.current) return;
      e.preventDefault();
      const c = canvasRef.current!;
      const ctx = c.getContext('2d')!;
      const pos = getPos(e);
      const last = lastPosRef.current!;
      ctx.beginPath();
      ctx.moveTo(last.x, last.y);
      ctx.lineTo(pos.x, pos.y);
      ctx.stroke();
      lastPosRef.current = pos;
      setHasInk(true);
    },
    onTouchEnd: () => { drawingRef.current = false; lastPosRef.current = null; },
  };

  const clear = () => {
    const c = canvasRef.current;
    if (!c) return;
    const ctx = c.getContext('2d')!;
    ctx.fillStyle = '#ffffff';
    ctx.fillRect(0, 0, c.width, c.height);
    setHasInk(false);
  };

  const getDataUrl = () => canvasRef.current?.toDataURL('image/png') ?? '';

  return { canvasRef, hasInk, handlers, clear, getDataUrl };
}
