export type DocxTemplateResponse = {
  id: number;
  target_type: string;
  company_id?: number | null;
  name: string;
  file_size?: number | null;
  created_at: string;
  updated_at: string;
};
