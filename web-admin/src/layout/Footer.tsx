import { Box, Typography } from "@mui/material";

export default function Footer() {
  return (
    <Box sx={{ px: 3, py: 2, borderTop: "1px solid", borderColor: "divider" }}>
      <Typography variant="caption" color="text.secondary">
        Clinic operations, patient safety, and careful review of every draft.
      </Typography>
    </Box>
  );
}
