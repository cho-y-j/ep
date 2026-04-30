import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { fetchMe, login as apiLogin, logout as apiLogout, signup as apiSignup } from '../lib/auth';
import { setAuthExpiredHandler } from '../lib/api';
import { tokenStorage } from '../lib/tokenStorage';
import type { CompanyResponse, MeResponse, Role, UserResponse } from '../types/auth';

type SignupPayload = {
  email: string;
  password: string;
  name: string;
  phone?: string;
  role: Role;
  companyName?: string;
  businessNumber?: string;
};

type AuthContextValue = {
  user: UserResponse | null;
  company: CompanyResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<MeResponse>;
  signup: (payload: SignupPayload) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [company, setCompany] = useState<CompanyResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const applyMe = useCallback((me: MeResponse | null) => {
    setUser(me?.user ?? null);
    setCompany(me?.company ?? null);
  }, []);

  const refresh = useCallback(async () => {
    if (!tokenStorage.access) {
      applyMe(null);
      return;
    }
    try {
      const me = await fetchMe();
      applyMe(me);
    } catch {
      tokenStorage.clear();
      applyMe(null);
    }
  }, [applyMe]);

  useEffect(() => {
    setAuthExpiredHandler(() => applyMe(null));
    refresh().finally(() => setLoading(false));
  }, [applyMe, refresh]);

  const value: AuthContextValue = {
    user,
    company,
    loading,
    async login(email, password) {
      const me = await apiLogin(email, password);
      applyMe(me);
      return me;
    },
    async signup(payload) {
      await apiSignup(payload);
    },
    async logout() {
      await apiLogout();
      applyMe(null);
    },
    refresh,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
