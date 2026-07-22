const MAX_EDGE = 2000;   // warpImage.ts 와 동일 상한 — 페이지 렌더 메모리/업로드 크기 관리.
const RENDER_SCALE = 2;  // 기본 ~150dpi (텍스트 가독성). 큰 페이지는 MAX_EDGE 로 축소.

/**
 * PDF File → 각 페이지를 canvas 로 렌더한 JPEG blob 배열.
 * pdfjs-dist 는 동적 import (초기 번들 영향 회피). worker 도 ?url 로 별도 청크.
 * 렌더/파싱 실패 시 예외 전파 — 호출부는 원본 PDF 업로드로 폴백한다.
 */
export async function pdfToPageImages(file: File): Promise<Blob[]> {
  const pdfjs = await import('pdfjs-dist');
  const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default;
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl;

  const data = new Uint8Array(await file.arrayBuffer());
  const doc = await pdfjs.getDocument({ data }).promise;
  const out: Blob[] = [];
  try {
    for (let i = 1; i <= doc.numPages; i++) {
      const page = await doc.getPage(i);
      const base = page.getViewport({ scale: 1 });
      const scale = Math.min(RENDER_SCALE, MAX_EDGE / Math.max(base.width, base.height)) || 1;
      const viewport = page.getViewport({ scale });
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(viewport.width);
      canvas.height = Math.round(viewport.height);
      await page.render({ canvas, viewport }).promise;
      const blob = await new Promise<Blob | null>((res) => canvas.toBlob((b) => res(b), 'image/jpeg', 0.92));
      if (blob) out.push(blob);
      page.cleanup();
    }
  } finally {
    await doc.destroy();
  }
  return out;
}
