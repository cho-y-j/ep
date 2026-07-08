export type BundleResponse = {
  id: number;
  quotation_request_id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  sent_at: string;
  include_email: boolean;
  email_sent_at?: string | null;
  notes?: string | null;
};

export type SendBundlePayload = {
  includeEmail: boolean;
  notes?: string | null;
  /** includeEmail=true 일 때 수신자. 비우면 서버가 BP admin 자동. */
  emails?: string[];
};
