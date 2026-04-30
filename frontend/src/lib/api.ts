import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from './tokenStorage';
import type { TokenResponse } from '../types/auth';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10_000,
});

api.interceptors.request.use((config) => {
  const token = tokenStorage.access;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

let refreshing: Promise<string> | null = null;
let onAuthExpired: (() => void) | null = null;

export function setAuthExpiredHandler(handler: () => void) {
  onAuthExpired = handler;
}

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStorage.refresh;
  if (!refreshToken) throw new Error('no refresh token');
  const res = await axios.post<TokenResponse>(
    `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/auth/refresh`,
    { refresh_token: refreshToken }
  );
  tokenStorage.set(res.data.access_token, res.data.refresh_token);
  return res.data.access_token;
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const isAuthEndpoint = original?.url?.includes('/api/auth/login')
      || original?.url?.includes('/api/auth/refresh')
      || original?.url?.includes('/api/auth/signup');

    if (
      error.response?.status === 401 &&
      original &&
      !original._retried &&
      !isAuthEndpoint &&
      tokenStorage.refresh
    ) {
      original._retried = true;
      try {
        if (!refreshing) {
          refreshing = refreshAccessToken().finally(() => {
            refreshing = null;
          });
        }
        const newToken = await refreshing;
        original.headers.set('Authorization', `Bearer ${newToken}`);
        return api.request(original);
      } catch {
        tokenStorage.clear();
        onAuthExpired?.();
      }
    }

    return Promise.reject(error);
  }
);
