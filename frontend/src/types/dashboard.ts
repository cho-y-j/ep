import type { OwnerType } from './document';

export type DashboardCounts = {
  persons?: number | null;
  equipment?: number | null;
  companies?: number | null;
  users_pending?: number | null;
  documents_expiring30d?: number | null;
  documents_unverified?: number | null;
};

export type ExpiringDocumentItem = {
  id: number;
  document_type_id: number;
  document_type_name: string;
  owner_type: OwnerType;
  owner_id: number;
  owner_name: string;
  expiry_date: string;
  days_left: number;
};

export type DashboardSummary = {
  counts: DashboardCounts;
  expiring_documents: ExpiringDocumentItem[];
};
