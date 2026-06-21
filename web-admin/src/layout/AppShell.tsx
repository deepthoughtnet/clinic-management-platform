import * as React from "react";
import { useTheme } from "@mui/material/styles";
import { useLocation } from "react-router-dom";
import {
  Box,
  CssBaseline,
  Drawer,
  GlobalStyles,
  useMediaQuery,
  Chip,
} from "@mui/material";

import { useAuth } from "../auth/useAuth";
import SidebarNav from "./SidebarNav";
import Footer from "./Footer";
import TopBar from "./TopBar";

const DRAWER_OPEN = 284;
const DRAWER_CLOSED = 88;

export default function AppShell({ children }: { children: React.ReactNode }) {
  const theme = useTheme();
  const location = useLocation();
  const auth = useAuth();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const [desktopOpen, setDesktopOpen] = React.useState(true);
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const drawerWidth = desktopOpen ? DRAWER_OPEN : DRAWER_CLOSED;

  React.useEffect(() => {
    // Keep temporary drawers from leaving a stale backdrop behind when the route
    // changes or the tenant context flips while the drawer is open.
    setMobileOpen(false);
  }, [location.pathname, auth.selectedTenant?.id]);

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <CssBaseline />
      <GlobalStyles
        styles={{
          "@media print": {
            ".no-print": { display: "none !important" },
            ".MuiDrawer-root": { display: "none !important" },
            body: { backgroundColor: "#fff !important" },
            "@page": { size: "A4", margin: "12mm" },
          },
        }}
      />
      <Chip
        label="DEMO / UAT"
        color="warning"
        size="small"
        sx={{
          position: "fixed",
          top: { xs: 8, md: 12 },
          right: { xs: 8, md: 12 },
          zIndex: (theme) => theme.zIndex.modal + 1,
          fontWeight: 800,
          letterSpacing: 0.4,
          pointerEvents: "none",
        }}
      />

      {isMobile ? (
        <Drawer className="no-print" open={mobileOpen} onClose={() => setMobileOpen(false)}>
          <SidebarNav open variant="temporary" width={DRAWER_OPEN} onClose={() => setMobileOpen(false)} />
        </Drawer>
      ) : (
        <Box className="no-print" sx={{ width: drawerWidth, flexShrink: 0 }}>
          <SidebarNav open={desktopOpen} variant="permanent" width={drawerWidth} />
        </Box>
      )}

      <Box sx={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
        <Box className="no-print">
          <TopBar
            onToggleSidebar={() => (isMobile ? setMobileOpen(true) : setDesktopOpen((value) => !value))}
            drawerWidth={drawerWidth}
            isMobile={isMobile}
          />
        </Box>

        <Box sx={{ height: 84, flexShrink: 0 }} />
        <Box sx={{ flex: 1, p: { xs: 2, md: 3 } }}>{children}</Box>
        <Box className="no-print">
          <Footer />
        </Box>
      </Box>
    </Box>
  );
}
