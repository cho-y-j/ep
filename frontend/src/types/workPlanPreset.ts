/**
 * 작업계획서 프리셋 — 사용자별 1~9 슬롯.
 * payload_json 의 의미는 프론트에서 부여한다 (PresetPayload).
 */
export type PresetResponse = {
  id: number;
  slot: number;
  name: string;
  payload_json: string;
  created_at: string;
  updated_at: string;
};

/**
 * 프론트가 프리셋 슬롯에 담는 JSON 모양.
 * 헤더 필드 + 자원 ID 목록 (장비/인원). 새 plan 작성 시 이 값으로 시드한 뒤 사용자 입력으로 보정.
 */
export type PresetPayload = {
  start_time?: string | null;
  end_time?: string | null;
  work_location?: string | null;
  description?: string | null;
  // 이 슬롯이 어떤 사이트 기준으로 만들어졌는지 (선택 시드용)
  default_site_id?: number | null;
  // 자원 ID 목록 — 새 plan 에 자동 추가하지는 않지만 UI 가 보여주거나 사용자가 추가 시 참고
  equipment_ids?: number[];
  person_ids?: number[];
};
