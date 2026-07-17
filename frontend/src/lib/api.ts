import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStorage } from './tokenStorage';
import type { TokenResponse } from '../types/auth';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
  timeout: 10_000,
});

// OCR/문서인식 계열(paddle 영역추출·4모서리 warp 이미지 반환)은 수 초~십수 초 걸릴 수 있어
// 기본 10초 타임아웃이면 abort 된다. 해당 엔드포인트만 넉넉히(60초) 늘린다.
const OCR_SLOW_ENDPOINTS = /\/(detect-corners|ocr-region-preview|ocr-preview|region-extract|mask-pii)/;

api.interceptors.request.use((config) => {
  const token = tokenStorage.access;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  if (config.url && OCR_SLOW_ENDPOINTS.test(config.url)) {
    config.timeout = 60_000;
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
