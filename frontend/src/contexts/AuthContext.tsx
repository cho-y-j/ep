import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { fetchMe, login as apiLogin, logout as apiLogout, signup as apiSignup } from '../lib/auth';
import { setAuthExpiredHandler } from '../lib/api';
import { tokenStorage } from '../lib/tokenStorage';
import type { Role, UserResponse } from '../types/auth';

type AuthContextValue = {
  user: UserResponse | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<UserResponse>;
  signup: (payload: { email: string; password: string; name: string; phone?: string; role: Role }) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!tokenStorage.access) {
      setUser(null);
      return;
    }
    try {
      const me = await fetchMe();
      setUser(me);
    } catch {
      tokenStorage.clear();
      setUser(null);
    }
  }, []);

  useEffect(() => {
    setAuthExpiredHandler(() => setUser(null));
    refresh().finally(() => setLoading(false));
  }, [refresh]);

  const value: AuthContextValue = {
    user,
    loading,
    async login(email, password) {
      const me = await apiLogin(email, password);
      setUser(me);
      return me;
    },
    async signup(payload) {
      await apiSignup(payload);
    },
    async logout() {
      await apiLogout();
      setUser(null);
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
