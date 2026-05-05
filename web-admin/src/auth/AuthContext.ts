import { createContext } from "react";

export type AuthContextValue = {
  initialized: boolean;
  authenticated: boolean;
  username: string;
  rolesUpper: string[];
  tenantId: string | null;
  tenantName: string | null;
  accessToken: string | null;
  login: (prompt?: boolean) => Promise<void>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
