export type DispatchedEquipmentResponse = {
  id: number;
  quotation_request_id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  equipment_id: number;
  equipment_label: string;
  equipment_category?: string | null;
  daily_price?: number | null;
  ot_daily_price?: number | null;
  monthly_price?: number | null;
  ot_monthly_price?: number | null;
  notes?: string | null;
  sent_at: string;
};

export type DispatchRequestPayload = {
  items: Array<{
    equipment_id: number;
    daily_price?: number | null;
    ot_daily_price?: number | null;
    monthly_price?: number | null;
    ot_monthly_price?: number | null;
    notes?: string | null;
  }>;
  notes?: string | null;
};

export type DispatchedPersonResponse = {
  id: number;
  quotation_request_id: number;
  supplier_company_id: number;
  supplier_company_name?: string | null;
  person_id: number;
  person_label: string;
  job_title?: string | null;
  daily_price?: number | null;
  monthly_price?: number | null;
  notes?: string | null;
  sent_at: string;
};

export type DispatchPersonPayload = {
  items: Array<{
    person_id: number;
    daily_price?: number | null;
    monthly_price?: number | null;
    notes?: string | null;
  }>;
  notes?: string | null;
};
