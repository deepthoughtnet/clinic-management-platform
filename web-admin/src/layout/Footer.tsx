import { Box, Typography } from "@mui/material";
import { branding } from "../branding";

export default function Footer() {
  return (
    <Box sx={{ px: 3, py: 2, borderTop: "1px solid", borderColor: "divider" }}>
      <Typography variant="caption" color="text.secondary">
        {branding.productName} - {branding.tagline} - Powered by {branding.aiPlatformName}
      </Typography>
    </Box>
  );
}
