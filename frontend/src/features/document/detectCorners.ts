import { api } from '../../lib/api';

/**
 * 폰/스캔 이미지의 문서 4모서리 자동 검출 (백엔드 → paddle /detect-corners 프록시).
 * token 지정 시 공개(무로그인) 수집 링크용 /api/collect/{token}/detect-corners, 아니면 인증 화면용 /api/documents/detect-corners.
 * 성공 시 원본 이미지 px 좌표 [TL, TR, BR, BL] 4점, 실패/미가동 시 undefined → 호출측이 이미지 꼭짓점으로 폴백.
 */
export async function detectDocumentCorners(file: File, token?: string): Promise<[number, number][] | undefined> {
  try {
    const fd = new FormData();
    fd.append('file', file);
    const url = token ? `/api/collect/${token}/detect-corners` : '/api/documents/detect-corners';
    const res = await api.post<{ detected: boolean; corners?: [number, number][]; image_size?: [number, number] }>(
      url, fd,
      { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    const c = res.data.corners;
    if (Array.isArray(c) && c.length === 4) return c;
  } catch {
    /* paddle 미가동 등 — 이미지 꼭짓점 폴백 */
  }
  return undefined;
}
