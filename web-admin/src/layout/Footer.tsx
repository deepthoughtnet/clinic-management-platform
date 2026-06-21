import { Box, Typography } from "@mui/material";
import { footerBrandingLine } from "../branding";

export default function Footer() {
  const versionLabel = import.meta.env.VITE_APP_VERSION?.trim() || "v0.0.0";

  return (
    <Box
      sx={{
        px: { xs: 2, md: 3 },
        py: 1.25,
        borderTop: "1px solid",
        borderColor: "divider",
        bgcolor: "background.paper",
      }}
    >
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "auto 1fr auto" },
          alignItems: "center",
          gap: 1,
        }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ minWidth: 64, fontWeight: 700 }}>
          {versionLabel}
        </Typography>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            minWidth: 0,
            textAlign: "center",
            px: { xs: 0, md: 2 },
          }}
        >
          {footerBrandingLine()}
        </Typography>
        <Box sx={{ display: { xs: "none", md: "block" }, minWidth: 64 }} />
      </Box>
    </Box>
  );
}
