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

// 서버가 동기 PII 마스킹을 수행하는 업로드(공개 수집 업로드·검사 회신 파일)는 대형 이미지 실측 90초+.
// 서버는 성공 저장했는데 FE 타임아웃으로 "업로드 실패" 오표시 → 중복 업로드를 유발하므로 180초로 늘린다.
const PII_UPLOAD_ENDPOINTS = /\/api\/collect\/[^/]+\/documents$|\/api\/resource-checks\/[^/]+\/submit-file$/;

api.interceptors.request.use((config) => {
  const token = tokenStorage.access;
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  if (config.url && PII_UPLOAD_ENDPOINTS.test(config.url)) {
    config.timeout = 180_000;
  } else if (config.url && OCR_SLOW_ENDPOINTS.test(config.url)) {
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
