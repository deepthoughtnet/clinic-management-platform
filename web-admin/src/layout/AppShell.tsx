import * as React from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { alpha, useTheme } from "@mui/material/styles";
import {
  AppBar,
  Box,
  Chip,
  CssBaseline,
  Drawer,
  IconButton,
  Toolbar,
  Typography,
  useMediaQuery,
} from "@mui/material";

import MenuIcon from "@mui/icons-material/Menu";
import LocalHospitalRoundedIcon from "@mui/icons-material/LocalHospitalRounded";
import SidebarNav from "./SidebarNav";
import Footer from "./Footer";
import TopBar from "./TopBar";

const DRAWER_OPEN = 284;
const DRAWER_CLOSED = 88;

export default function AppShell({ children }: { children: React.ReactNode }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const location = useLocation();
  const navigate = useNavigate();
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
        <AppBar
          position="sticky"
          elevation={0}
          sx={{
            bgcolor: alpha(theme.palette.primary.main, 0.02),
            backdropFilter: "blur(12px)",
            borderBottom: "1px solid",
            borderColor: "divider",
          }}
        >
          <Toolbar sx={{ gap: 1 }}>
            {isMobile ? (
              <IconButton color="inherit" onClick={() => setMobileOpen(true)}>
                <MenuIcon />
              </IconButton>
            ) : (
              <IconButton color="inherit" onClick={() => setDesktopOpen((value) => !value)}>
                <MenuIcon />
              </IconButton>
            )}
            <LocalHospitalRoundedIcon fontSize="small" />
            <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
              Clinic Management
            </Typography>
            <Box sx={{ flex: 1 }} />
            <Chip label={location.pathname === "/" ? "Dashboard" : location.pathname.slice(1)} variant="outlined" />
          </Toolbar>
        </AppBar>

        <TopBar />

        <Box sx={{ p: { xs: 2, md: 3 } }}>{children}</Box>
        <Footer />
      </Box>
    </Box>
  );
}
