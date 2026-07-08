// 작업계획서 전용 타입 — skep 전체 Person/Equipment와는 별개 (MVP 내장 모델)
export interface DocumentRef {
  id: string;
  originalName: string;
  storageKey: string;
  mimeType: string;
  size?: number;
  uploadedAt?: string;
  category: string;
  expiresAt?: string;
  verified?: boolean; // skep 검증 상태 — UI 경고용
}

export type Role = "조종원" | "작업지휘자" | "유도원" | "신호수" | "화기감시자" | "점검원" | "소장";

export interface WorksheetPerson {
  id: string;
  name: string;
  birth?: string;
  phone?: string;
  address?: string;
  licenseNo?: string;
  licenseType?: string;
  company?: string;
  roles: Role[];
  photoKey?: string;
  documents: DocumentRef[];
}

export interface WorksheetEquipment {
  id: string;
  equipmentType?: string;
  vehicleNo: string;
  name: string;
  model?: string;
  manufacturer?: string;
  year?: string;
  upperPartYear?: string;
  serialNo?: string;
  capacity?: string;
  insuranceExpiry?: string;
  inspectionExpiry?: string;
  ndtExpiry?: string;
  documents: DocumentRef[];
}
