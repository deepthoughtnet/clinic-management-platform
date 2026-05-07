import * as React from "react";
import { useTheme } from "@mui/material/styles";
import {
  Box,
  CssBaseline,
  Drawer,
  useMediaQuery,
} from "@mui/material";

import SidebarNav from "./SidebarNav";
import Footer from "./Footer";
import TopBar from "./TopBar";

const DRAWER_OPEN = 284;
const DRAWER_CLOSED = 88;

export default function AppShell({ children }: { children: React.ReactNode }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const [desktopOpen, setDesktopOpen] = React.useState(true);
  const [mobileOpen, setMobileOpen] = React.useState(false);

  const drawerWidth = desktopOpen ? DRAWER_OPEN : DRAWER_CLOSED;

  return (
    <Box sx={{ display: "flex", minHeight: "100vh", bgcolor: "background.default" }}>
      <CssBaseline />

      {isMobile ? (
        <Drawer open={mobileOpen} onClose={() => setMobileOpen(false)}>
          <SidebarNav open variant="temporary" width={DRAWER_OPEN} onClose={() => setMobileOpen(false)} />
        </Drawer>
      ) : (
        <Box sx={{ width: drawerWidth, flexShrink: 0 }}>
          <SidebarNav open={desktopOpen} variant="permanent" width={drawerWidth} />
        </Box>
      )}

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <TopBar
          onToggleSidebar={() => (isMobile ? setMobileOpen(true) : setDesktopOpen((value) => !value))}
        />

        <Box sx={{ p: { xs: 2, md: 3 } }}>{children}</Box>
        <Footer />
      </Box>
    </Box>
  );
}
