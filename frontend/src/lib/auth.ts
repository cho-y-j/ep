import { api } from './api';
import { tokenStorage } from './tokenStorage';
import type { TokenResponse, MeResponse, Role } from '../types/auth';

export async function login(email: string, password: string): Promise<MeResponse> {
  const tokens = await api
    .post<TokenResponse>('/api/auth/login', { email, password })
    .then((r) => r.data);
  tokenStorage.set(tokens.access_token, tokens.refresh_token);
  return await fetchMe();
}

export async function signup(payload: {
  email: string;
  password: string;
  name: string;
  phone?: string;
  role: Role;
  companyName?: string;
  businessNumber?: string;
}): Promise<void> {
  await api.post('/api/auth/signup', {
    email: payload.email,
    password: payload.password,
    name: payload.name,
    phone: payload.phone,
    role: payload.role,
    company_name: payload.companyName,
    business_number: payload.businessNumber,
  });
}

export async function logout(): Promise<void> {
  const refreshToken = tokenStorage.refresh;
  try {
    if (refreshToken) {
      await api.post('/api/auth/logout', { refresh_token: refreshToken });
    }
  } finally {
    tokenStorage.clear();
  }
}

export async function fetchMe(): Promise<MeResponse> {
  const res = await api.get<MeResponse>('/api/auth/me');
  return res.data;
}
