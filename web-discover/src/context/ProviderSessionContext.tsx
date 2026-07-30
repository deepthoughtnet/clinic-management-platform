import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { useLocation } from "react-router-dom";
import {
  ProviderAuthError,
  loadProviderWorkspace,
  logoutProviderSession,
  type ProviderWorkspaceResponse,
} from "../api/providerAuth";

type ProviderSessionStatus = "idle" | "loading" | "authenticated" | "anonymous" | "error";

type ProviderSessionContextValue = {
  workspace: ProviderWorkspaceResponse | null;
  status: ProviderSessionStatus;
  error: string | null;
  refreshSession: (force?: boolean) => Promise<ProviderWorkspaceResponse | null>;
  logout: () => Promise<void>;
};

const ProviderSessionContext = createContext<ProviderSessionContextValue | null>(null);

function isProviderRoute(pathname: string) {
  return pathname === "/provider" || pathname.startsWith("/provider/");
}

export function ProviderSessionProvider({ children }: { children: ReactNode }) {
  const location = useLocation();
  const [workspace, setWorkspace] = useState<ProviderWorkspaceResponse | null>(null);
  const [status, setStatus] = useState<ProviderSessionStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const requestSequenceRef = useRef(0);

  async function refreshSession(force = false) {
    if (!force && status === "loading") {
      return workspace;
    }
    const requestSequence = ++requestSequenceRef.current;
    setStatus("loading");
    setError(null);
    try {
      const nextWorkspace = await loadProviderWorkspace();
      if (requestSequence === requestSequenceRef.current) {
        setWorkspace(nextWorkspace);
        setStatus("authenticated");
        setError(null);
      }
      return nextWorkspace;
    } catch (ex) {
      if (requestSequence !== requestSequenceRef.current) {
        return null;
      }
      if (ex instanceof ProviderAuthError && ex.status === 401) {
        setWorkspace(null);
        setStatus("anonymous");
        setError(null);
        return null;
      }
      setWorkspace(null);
      setStatus("error");
      setError(ex instanceof Error ? ex.message : "Could not restore your provider session.");
      return null;
    }
  }

  async function logout() {
    await logoutProviderSession();
    requestSequenceRef.current += 1;
    setWorkspace(null);
    setStatus("anonymous");
    setError(null);
  }

  useEffect(() => {
    if (!isProviderRoute(location.pathname)) {
      return;
    }
    if (status !== "idle") {
      return;
    }
    void refreshSession();
  }, [location.pathname, status]);

  return (
    <ProviderSessionContext.Provider value={{ workspace, status, error, refreshSession, logout }}>
      {children}
    </ProviderSessionContext.Provider>
  );
}

export function useProviderSession() {
  const context = useContext(ProviderSessionContext);
  if (!context) {
    throw new Error("ProviderSessionContext is not available.");
  }
  return context;
}
