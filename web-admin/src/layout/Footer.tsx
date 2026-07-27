import { Box, Typography } from "@mui/material";
import { footerBrandingLine } from "../branding";

export default function Footer() {
  const rawVersion = import.meta.env.VITE_APP_VERSION?.trim() || "";
  const normalizedVersion = rawVersion && rawVersion !== "0.0.0" && rawVersion !== "v0.0.0"
    ? (rawVersion.startsWith("v") ? rawVersion : `v${rawVersion}`)
    : "";

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
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          gap: 1,
          flexWrap: "wrap",
        }}
      >
        {normalizedVersion ? (
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            {normalizedVersion}
          </Typography>
        ) : null}
        <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, textAlign: "center" }}>
          {footerBrandingLine()}
        </Typography>
      </Box>
    </Box>
  );
}
