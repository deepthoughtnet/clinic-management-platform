import * as React from "react";
import { AppBar, Box, Button, Chip, TextField, Toolbar, Typography } from "@mui/material";

import { useAuth } from "../auth/useAuth";

export default function TopBar() {
  const auth = useAuth();
  const [tenantInput, setTenantInput] = React.useState(auth.tenantId || "");

  React.useEffect(() => {
    setTenantInput(auth.tenantId || "");
  }, [auth.tenantId]);

  const onApplyTenant = () => {
    localStorage.setItem("clinic_tenant_id", tenantInput.trim());
    window.location.reload();
  };

  return (
    <AppBar position="static" color="primary">
      <Toolbar>
        <Typography variant="h6" sx={{ mr: 2, fontWeight: 800 }}>
          Clinic Management
        </Typography>

        <Box sx={{ flex: 1 }} />

        <Box sx={{ display: "flex", alignItems: "center", gap: 1, mr: 2, flexWrap: "wrap" }}>
          <TextField
            size="small"
            variant="outlined"
            placeholder="Tenant UUID"
            value={tenantInput}
            onChange={(event) => setTenantInput(event.target.value)}
            sx={{ bgcolor: "white", borderRadius: 1, minWidth: 260 }}
          />
          <Button variant="contained" color="secondary" onClick={onApplyTenant}>
            Apply
          </Button>
          <Chip label={auth.tenantName || auth.tenantId || "No tenant selected"} color="secondary" variant="outlined" />
        </Box>

        <Typography variant="body2" sx={{ mr: 2 }}>
          {auth.username}
        </Typography>

        <Button color="inherit" onClick={() => auth.logout()}>
          Logout
        </Button>
      </Toolbar>
    </AppBar>
  );
}
