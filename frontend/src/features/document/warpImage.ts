type Point = [number, number];

const MAX_EDGE = 2000; // 폰 사진(1200만 화소)을 그대로 warp 하면 느리고 업로드도 커진다 — 긴 변 기준 축소.

/** 8원 1차 연립방정식 (가우스 소거, 부분 피벗). 해가 없으면 null. */
function solve(A: number[][], b: number[]): number[] | null {
  const n = b.length;
  for (let i = 0; i < n; i++) {
    let p = i;
    for (let r = i + 1; r < n; r++) if (Math.abs(A[r][i]) > Math.abs(A[p][i])) p = r;
    if (Math.abs(A[p][i]) < 1e-9) return null;
    [A[i], A[p]] = [A[p], A[i]];
    [b[i], b[p]] = [b[p], b[i]];
    for (let r = 0; r < n; r++) {
      if (r === i) continue;
      const f = A[r][i] / A[i][i];
      if (f === 0) continue;
      for (let c = i; c < n; c++) A[r][c] -= f * A[i][c];
      b[r] -= f * b[i];
    }
  }
  return b.map((v, i) => v / A[i][i]);
}

/** dst 4점 → src 4점 사영변환 계수 8개. 역방향 샘플링용(결과 픽셀 → 원본 픽셀). */
function homography(dst: Point[], src: Point[]): number[] | null {
  const A: number[][] = [];
  const b: number[] = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = dst[i];
    const [u, v] = src[i];
    A.push([x, y, 1, 0, 0, 0, -x * u, -y * u]); b.push(u);
    A.push([0, 0, 0, x, y, 1, -x * v, -y * v]); b.push(v);
  }
  return solve(A, b);
}

/**
 * 4모서리(원본 이미지 px, [TL,TR,BR,BL])로 원근보정 + 크롭한 새 이미지 File 을 만든다.
 * 서류 수집 공개 링크(무로그인)는 서버 warp(/ocr-region-preview)를 못 쓰므로 브라우저에서 처리한다.
 * 실패(캔버스 미지원·특이 행렬 등)하면 원본 File 을 그대로 돌려준다.
 */
export async function warpImageByCorners(file: File, corners: Point[]): Promise<File> {
  if (corners.length !== 4) return file;
  const bmp = await createImageBitmap(file);
  const dist = (a: Point, b: Point) => Math.hypot(a[0] - b[0], a[1] - b[1]);
  const [tl, tr, br, bl] = corners;
  const rawW = Math.max(dist(tl, tr), dist(bl, br));
  const rawH = Math.max(dist(tl, bl), dist(tr, br));
  const scale = Math.min(1, MAX_EDGE / Math.max(rawW, rawH));
  const w = Math.round(rawW * scale);
  const h = Math.round(rawH * scale);
  const m = w > 1 && h > 1 ? homography([[0, 0], [w, 0], [w, h], [0, h]], corners) : null;

  const sc = document.createElement('canvas');
  sc.width = bmp.width;
  sc.height = bmp.height;
  const sctx = sc.getContext('2d');
  if (!m || !sctx) { bmp.close(); return file; }
  sctx.drawImage(bmp, 0, 0);
  bmp.close();

  const src = sctx.getImageData(0, 0, sc.width, sc.height);
  const out = new ImageData(w, h);
  const [a, b, c, d, e, f, g, i] = m;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const den = g * x + i * y + 1;
      const sx = Math.round((a * x + b * y + c) / den);
      const sy = Math.round((d * x + e * y + f) / den);
      const di = (y * w + x) * 4;
      out.data[di + 3] = 255;
      if (sx < 0 || sy < 0 || sx >= src.width || sy >= src.height) continue;
      const si = (sy * src.width + sx) * 4;
      out.data[di] = src.data[si];
      out.data[di + 1] = src.data[si + 1];
      out.data[di + 2] = src.data[si + 2];
    }
  }

  const dc = document.createElement('canvas');
  dc.width = w;
  dc.height = h;
  const dctx = dc.getContext('2d');
  if (!dctx) return file;
  dctx.putImageData(out, 0, 0);
  const name = file.name.replace(/(\.[a-z0-9]+)?$/i, '') + '-aligned.jpg';
  return new Promise((resolve) =>
    dc.toBlob((blob) => resolve(blob ? new File([blob], name, { type: 'image/jpeg' }) : file), 'image/jpeg', 0.92),
  );
}
