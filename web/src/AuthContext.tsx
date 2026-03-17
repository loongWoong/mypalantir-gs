import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

const AUTH_KEY = 'mypalantir_auth';

interface AuthContextValue {
  isAuthenticated: boolean;
  login: (username: string, password: string) => boolean;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const getEnvCredentials = () => ({
  username: import.meta.env.VITE_LOGIN_USERNAME ?? 'admin',
  password: import.meta.env.VITE_LOGIN_PASSWORD ?? 'admin',
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    try {
      const stored = localStorage.getItem(AUTH_KEY);
      setIsAuthenticated(stored === 'true');
    } catch {
      setIsAuthenticated(false);
    }
  }, []);

  const login = (username: string, password: string): boolean => {
    const { username: envUser, password: envPass } = getEnvCredentials();
    if (username === envUser && password === envPass) {
      localStorage.setItem(AUTH_KEY, 'true');
      setIsAuthenticated(true);
      return true;
    }
    return false;
  };

  const logout = () => {
    localStorage.removeItem(AUTH_KEY);
    setIsAuthenticated(false);
  };

  return (
    <AuthContext.Provider value={{ isAuthenticated, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return ctx;
}
