import { NavLink, useLocation } from "react-router-dom";
import { Box, Divider, Drawer, List, ListItemButton, ListItemText, Typography } from "@mui/material";

import { NAV, type NavItem } from "./nav";

export type SidebarNavProps = {
  open: boolean;
  variant: "permanent" | "temporary";
  width: number;
  onClose?: () => void;
};

function SectionHeader({ label }: { label: string }) {
  return (
    <Box sx={{ px: 2, pt: 2, pb: 1 }}>
      <Typography variant="overline" sx={{ opacity: 0.7 }}>
        {label}
      </Typography>
    </Box>
  );
}

function isActivePath(current: string, path: string): boolean {
  if (path === "/") return current === "/";
  return current === path || current.startsWith(`${path}/`);
}

export default function SidebarNav({ open, variant, width, onClose }: SidebarNavProps) {
  const location = useLocation();

  return (
    <Drawer
      open={open}
      onClose={onClose}
      variant={variant}
      sx={{
        width,
        flexShrink: 0,
        "& .MuiDrawer-paper": {
          width,
          boxSizing: "border-box",
        },
      }}
    >
      <Box sx={{ px: 2, py: 1.75 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
          Clinic Management
        </Typography>
        <Typography variant="caption" sx={{ opacity: 0.7 }}>
          Care operations console
        </Typography>
      </Box>

      <Divider />

      <List sx={{ py: 0 }}>
        {NAV.map((item: NavItem) => {
          if (item.section) return <SectionHeader key={item.key} label={item.section} />;
          if (!item.path) return null;

          const active = isActivePath(location.pathname, item.path);

          return (
            <ListItemButton
              key={item.key}
              component={NavLink}
              to={item.path}
              onClick={variant === "temporary" ? onClose : undefined}
              sx={{
                mx: 1,
                my: 0.25,
                borderRadius: 1.5,
                bgcolor: active ? "action.selected" : undefined,
              }}
            >
              <Box sx={{ width: 28, height: 28, borderRadius: 1, bgcolor: "action.hover", mr: 1.25, flexShrink: 0 }} />
              <ListItemText primary={item.label} primaryTypographyProps={{ variant: "body2" }} />
            </ListItemButton>
          );
        })}
      </List>

      <Box sx={{ flex: 1 }} />

      <Divider />

      <Box sx={{ px: 2, py: 1.5 }}>
        <Typography variant="caption" sx={{ opacity: 0.7 }}>
          v0.1 • Clinic Admin
        </Typography>
      </Box>
    </Drawer>
  );
}
