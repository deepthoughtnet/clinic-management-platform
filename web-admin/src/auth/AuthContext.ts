import { createContext } from "react";

export type SelectedTenant = {
  id: string;
  code: string;
  name: string;
};

export type AuthContextValue = {
  initialized: boolean;
  authenticated: boolean;
  username: string;
  rolesUpper: string[];
  permissions: string[];
  selectedTenant: SelectedTenant | null;
  tenantId: string | null;
  tenantName: string | null;
  appUserId: string | null;
  tenantRole: string | null;
  activeTenantMemberships: Array<{
    tenantId: string;
    tenantCode?: string | null;
    tenantName?: string | null;
    role?: string | null;
    status?: string | null;
    modules?: {
      carePilot?: boolean | null;
      aiCopilot?: boolean | null;
    } | null;
  }>;
  tenantModules: {
    carePilot?: boolean | null;
    aiCopilot?: boolean | null;
  } | null;
  accessToken: string | null;
  initError: string | null;
  selectTenant: (tenant: SelectedTenant | null) => void;
  retryInit: () => void;
  clearSession: () => void;
  hasPermission: (permission: string) => boolean;
  login: (prompt?: boolean) => Promise<void>;
  logout: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);
