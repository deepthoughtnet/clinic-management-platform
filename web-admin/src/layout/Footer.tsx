import { Box, Typography } from "@mui/material";
import { footerBrandingLine } from "../branding";

export default function Footer() {
  return (
    <Box sx={{ px: 3, py: 2, borderTop: "1px solid", borderColor: "divider" }}>
      <Typography variant="caption" color="text.secondary">
        {footerBrandingLine()}
      </Typography>
    </Box>
  );
}
