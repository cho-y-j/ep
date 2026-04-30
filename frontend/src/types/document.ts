export type OwnerType = 'PERSON' | 'EQUIPMENT';

export type DocumentTypeResponse = {
  id: number;
  name: string;
  applies_to: OwnerType;
  has_expiry: boolean;
  requires_verification: boolean;
  sort_order: number;
  active: boolean;
};

export type DocumentResponse = {
  id: number;
  document_type_id: number;
  document_type_name: string;
  document_type_has_expiry: boolean;
  owner_type: OwnerType;
  owner_id: number;
  file_name: string;
  file_size: number;
  content_type: string;
  expiry_date?: string | null;
  verified: boolean;
  created_at: string;
};

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** 만료까지 며칠 남았는지. expired면 음수, 만료일 없으면 null */
export function daysUntilExpiry(expiryDate?: string | null): number | null {
  if (!expiryDate) return null;
  const exp = new Date(expiryDate);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffMs = exp.getTime() - today.getTime();
  return Math.floor(diffMs / (1000 * 60 * 60 * 24));
}
