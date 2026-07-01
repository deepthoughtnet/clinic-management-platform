import * as React from "react";
import { useNavigate } from "react-router-dom";
import { Box, Button, Stack, Typography } from "@mui/material";


export default function PharmacyReconcileTestPage() {
  const navigate = useNavigate();
  return (
    <Stack spacing={2}>
      <Typography variant="h4" sx={{ fontWeight: 900 }}>
        Reconcile Test
      </Typography>
      <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
        <Button variant="contained" onClick={() => navigate("/inventory")}>
          Inventory
        </Button>
        <Button variant="outlined" onClick={() => navigate("/pharmacy/pos")}>
          POS Sale
        </Button>
      </Box>
    </Stack>
  );
}
