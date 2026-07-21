import { api } from '../../lib/api';
import type { OwnerType } from '../../types/document';

/** 수집요청 폼의 서류별 선택 상태 — 키가 없으면 '제외'. */
export type Sel = Record<number, 'none' | 'required' | 'optional'>;

type SuggestResponse = { required_type_ids: number[]; optional_type_ids: number[] };

/**
 * 대상 자원의 유형(장비종류/인력역할)에 설정된 필수·선택 서류를 폼 선택상태로 변환.
 * 유형 설정에 없는 서류는 키가 없어 '제외'로 남는다. 실패 시 빈 선택(전부 제외).
 */
export async function fetchSuggestedSel(ownerType: OwnerType, ownerId: number): Promise<Sel> {
  try {
    const r = await api.get<SuggestResponse>('/api/document-collections/suggest', {
      params: { ownerType, ownerId },
    });
    const sel: Sel = {};
    r.data.required_type_ids.forEach((id) => { sel[id] = 'required'; });
    r.data.optional_type_ids.forEach((id) => { sel[id] = 'optional'; });
    return sel;
  } catch {
    return {};
  }
}
