import { api } from './api';

export type Consultation = {
  id: number;
  company_name: string;
  contact_name: string;
  phone: string;
  email: string | null;
  message: string;
  handled: boolean;
  created_at: string;
};

export async function createConsultation(payload: {
  companyName: string;
  contactName: string;
  phone: string;
  email?: string;
  message: string;
}): Promise<void> {
  await api.post('/api/consultations', {
    company_name: payload.companyName,
    contact_name: payload.contactName,
    phone: payload.phone,
    email: payload.email || undefined,
    message: payload.message,
  });
}

export async function listConsultations(): Promise<Consultation[]> {
  const res = await api.get<Consultation[]>('/api/consultations');
  return res.data;
}

export async function handleConsultation(id: number): Promise<void> {
  await api.patch(`/api/consultations/${id}/handle`);
}
