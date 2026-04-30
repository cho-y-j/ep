import { api } from './api';
import { tokenStorage } from './tokenStorage';
import type { TokenResponse, UserResponse, Role } from '../types/auth';

export async function login(email: string, password: string): Promise<UserResponse> {
  const tokens = await api
    .post<TokenResponse>('/api/auth/login', { email, password })
    .then((r) => r.data);
  tokenStorage.set(tokens.access_token, tokens.refresh_token);
  const me = await fetchMe();
  return me;
}

export async function signup(payload: {
  email: string;
  password: string;
  name: string;
  phone?: string;
  role: Role;
}): Promise<void> {
  await api.post('/api/auth/signup', payload);
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

export async function fetchMe(): Promise<UserResponse> {
  const res = await api.get<UserResponse>('/api/auth/me');
  return res.data;
}
