import type { OwnerType } from '../../types/document';

/** 폼에서 고른 대상 1건 — 서버 전송 전 화면 상태. */
export type PickedTarget = {
  owner_type: OwnerType;
  owner_id: number;
  label: string;
  /** 장비 선택 때 함께 담긴 조종원이면 그 장비 id — 장비를 해제하면 조종원도 같이 빠진다. */
  via_equipment_id?: number;
  /** 위 장비의 라벨 — 칩/카드에 "12가1234 조종원" 으로 표시. */
  via_equipment_label?: string;
};

/** 대상 식별 키 — 장비 id 와 인원 id 가 겹치므로 유형까지 합쳐야 유일. */
export const targetKey = (t: { owner_type: OwnerType; owner_id: number }) => `${t.owner_type}:${t.owner_id}`;
