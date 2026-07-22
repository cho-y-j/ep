import type { OwnerType } from './document';

/** 목록용 (GET /api/document-collections) — items 없이 카운트만. */
export type CollectionSummary = {
  id: number;
  token: string;
  title?: string | null;
  owner_summary: string;
  recipient_name?: string | null;
  recipient_phone?: string | null;
  status: string;
  public_url: string;
  target_count: number;
  item_count: number;
  uploaded_count: number;
};

/** 상세/생성 응답의 서류 1건. id = 공개 업로드 단위. */
export type CollectionItem = {
  id: number;
  document_type_id: number;
  document_type_name: string;
  required: boolean;
  sort_order: number;
  uploaded: boolean;
  uploaded_document_id?: number | null;
  file_name?: string | null;
};

/** 상세 응답의 대상 1건(장비/인원) + 그 대상의 서류들. */
export type CollectionTarget = {
  id: number;
  owner_type: OwnerType;
  owner_id: number;
  owner_name?: string | null;
  sort_order: number;
  item_count: number;
  uploaded_count: number;
  required_remaining: number;
  items: CollectionItem[];
};

/** 상세 (GET /api/document-collections/{id}) — 생성 POST 응답도 동일 형태. */
export type CollectionDetail = CollectionSummary & { targets: CollectionTarget[] };

// ── 공개(무로그인) 페이지 — GET /api/collect/{token} ──────────

export type PublicItem = {
  id: number;
  document_type_id: number;
  name: string;
  required: boolean;
  uploaded: boolean;
  file_name?: string | null;
  sample_image_url?: string | null;
  sample_description?: string | null;
};

export type PublicTarget = {
  id: number;
  owner_type: OwnerType;
  owner_label: string;
  /** 등록형(신규 자원) 슬롯 정보 — 갱신형은 planned_type=null·registered=true. */
  planned_type?: string | null;
  planned_type_label?: string | null;
  registered: boolean;
  /** 입력 종류 — EQUIPMENT=VEHICLE_NO, PERSON=NAME. */
  input_kind: string;
  /** 등록된 값(차량번호/이름). 미등록이면 null. */
  input_value?: string | null;
  item_count: number;
  uploaded_count: number;
  required_remaining: number;
  items: PublicItem[];
};

export type PublicCollection = {
  title?: string | null;
  recipient_name?: string | null;
  status: string;
  expired: boolean;
  item_count: number;
  uploaded_count: number;
  required_remaining: number;
  targets: PublicTarget[];
};
