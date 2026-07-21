import { api } from '../../lib/api';
import type { OwnerType } from '../../types/document';
import { targetKey, type PickedTarget } from './target';

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

type SuggestBatchResponse = {
  results: Array<{ owner_type: OwnerType; owner_id: number; required_type_ids: number[]; optional_type_ids: number[] }>;
};

/**
 * 다중 대상판 — 대상 N개를 POST /suggest-batch 1회로 물어본다(50대 선택 시 대상당 호출 방지).
 * 반환은 targetKey → Sel. 응답에 없는 대상은 키가 없어 호출측이 빈 선택으로 다룬다.
 */
export async function fetchSuggestedSelBatch(targets: PickedTarget[]): Promise<Record<string, Sel>> {
  if (targets.length === 0) return {};
  const r = await api.post<SuggestBatchResponse>('/api/document-collections/suggest-batch', {
    targets: targets.map((t) => ({ owner_type: t.owner_type, owner_id: t.owner_id })),
  });
  const out: Record<string, Sel> = {};
  r.data.results.forEach((res) => {
    const sel: Sel = {};
    res.required_type_ids.forEach((id) => { sel[id] = 'required'; });
    res.optional_type_ids.forEach((id) => { sel[id] = 'optional'; });
    out[targetKey({ owner_type: res.owner_type, owner_id: res.owner_id })] = sel;
  });
  return out;
}
