import { api } from '../../lib/api';

/**
 * 폰/스캔 이미지의 문서 4모서리 자동 검출 (백엔드 /api/documents/detect-corners → paddle /detect-corners 프록시).
 * 성공 시 원본 이미지 px 좌표 [TL, TR, BR, BL] 4점, 실패/미가동 시 undefined → 호출측이 이미지 꼭짓점으로 폴백.
 */
export async function detectDocumentCorners(file: File): Promise<[number, number][] | undefined> {
  try {
    const fd = new FormData();
    fd.append('file', file);
    const res = await api.post<{ detected: boolean; corners?: [number, number][]; image_size?: [number, number] }>(
      '/api/documents/detect-corners', fd,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    const c = res.data.corners;
    if (Array.isArray(c) && c.length === 4) return c;
  } catch {
    /* paddle 미가동 등 — 이미지 꼭짓점 폴백 */
  }
  return undefined;
}
