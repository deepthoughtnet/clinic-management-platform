import { createContext } from "react";

export type AuthContextValue = {
  initialized: boolean;
  authenticated: boolean;
  username: string;
  rolesUpper: string[];
  permissions: string[];
  tenantId: string | null;
  tenantName: string | null;
  accessToken: string | null;
  hasPermission: (permission: string) => boolean;
  login: (prompt?: boolean) => Promise<void>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
