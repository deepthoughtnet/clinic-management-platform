import * as React from "react";
import { useLocation } from "react-router-dom";
import { Box } from "@mui/material";

import GlobalHelpDrawer from "./GlobalHelpDrawer";
import { resolveHelpPageMeta, resolveHelpRouteByPageKey } from "./helpPageRegistry";
import { subscribeGlobalHelpEvents } from "./helpEvents";

export type HelpContextValue = {
  openHelp: (pageKey?: string) => void;
  closeHelp: () => void;
  isOpen: boolean;
};

export const HelpContext = React.createContext<HelpContextValue | null>(null);

declare global {
  interface Window {
    __arogiaOpenHelp?: (pageKey?: string) => void;
    __arogiaCloseHelp?: () => void;
  }
}

function isDevMode() {
  return import.meta.env?.DEV ?? true;
}

export function useHelp(): HelpContextValue {
  const context = React.useContext(HelpContext);
  if (!context) {
    throw new Error("useHelp must be used within HelpProvider");
  }
  return context;
}

export default function HelpProvider({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const route = React.useMemo(() => resolveHelpPageMeta(`${location.pathname}${location.search}`), [location.pathname, location.search]);
  const [isOpen, setIsOpen] = React.useState(false);
  const [activePageKey, setActivePageKey] = React.useState(route.pageKey);
  const [activePageTitle, setActivePageTitle] = React.useState(route.title);

  const openHelp = React.useCallback((pageKey?: string) => {
    const targetRoute = pageKey ? resolveHelpRouteByPageKey(pageKey) : null;
    const nextPageKey = targetRoute?.pageKey || pageKey || route.pageKey || "UNKNOWN_PAGE";
    const nextPageTitle = targetRoute?.title || route.title || "Help";
    if (isDevMode()) {
      console.log("[help] opening help drawer for pageKey:", nextPageKey, { pathname: targetRoute?.path || location.pathname });
    }
    setActivePageKey(nextPageKey);
    setActivePageTitle(nextPageTitle);
    setIsOpen(true);
  }, [location.pathname, route.pageKey]);

  const closeHelp = React.useCallback(() => {
    if (isDevMode()) {
      console.log("[help] drawer closed");
    }
    setIsOpen(false);
  }, []);

  React.useEffect(() => {
    if (isDevMode()) {
      console.log("[help] drawer state changed", { isOpen, pathname: location.pathname, pageKey: route.pageKey });
    }
  }, [isOpen, location.pathname, route.pageKey]);

  React.useEffect(() => {
    if (!isOpen) {
      setActivePageKey(route.pageKey);
      setActivePageTitle(route.title);
    }
  }, [isOpen, route.pageKey, route.title]);

  React.useEffect(() => {
    if (isDevMode()) {
      console.log("[help] HelpProvider mounted");
    }
    if (isDevMode()) {
      window.__arogiaOpenHelp = openHelp;
      window.__arogiaCloseHelp = closeHelp;
    }
    return subscribeGlobalHelpEvents(
      (detail) => {
        if (isDevMode()) {
          console.log("[help] open event received", detail);
        }
        openHelp(detail.pageKey);
      },
      () => {
        if (isDevMode()) {
          console.log("[help] close event received");
        }
        closeHelp();
      },
    );
  }, [closeHelp, openHelp]);

  React.useEffect(() => {
    if (!isDevMode()) return;
    return () => {
      if (window.__arogiaOpenHelp === openHelp) {
        delete window.__arogiaOpenHelp;
      }
      if (window.__arogiaCloseHelp === closeHelp) {
        delete window.__arogiaCloseHelp;
      }
    };
  }, [closeHelp, openHelp]);

  const contextValue = React.useMemo(
    () => ({ openHelp, closeHelp, isOpen }),
    [closeHelp, isOpen, openHelp],
  );

  return (
    <HelpContext.Provider value={contextValue}>
      {isDevMode() ? (
        <Box
          data-testid="help-provider-mounted"
          aria-hidden="true"
          sx={{
            position: "fixed",
            left: 8,
            bottom: 8,
            zIndex: 9998,
            px: 1,
            py: 0.25,
            borderRadius: 1,
            bgcolor: "warning.light",
            color: "warning.contrastText",
            fontSize: 10,
            fontWeight: 700,
            opacity: 0.85,
            pointerEvents: "none",
          }}
        >
          HelpProvider mounted
        </Box>
      ) : null}
      {children}
      <GlobalHelpDrawer
        open={isOpen}
        pageKey={activePageKey}
        pageTitle={activePageTitle}
        onClose={closeHelp}
      />
    </HelpContext.Provider>
  );
}
