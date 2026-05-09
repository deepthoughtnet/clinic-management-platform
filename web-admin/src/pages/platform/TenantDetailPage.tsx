import * as React from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  createPlatformTenantAdminUser,
  getPlatformPlans,
  getPlatformTenant,
  type PlatformPlan,
  type PlatformTenantDetail,
  updatePlatformTenantModules,
  updatePlatformTenantPlan,
  updatePlatformTenantStatus,
} from "../../api/clinicApi";

const MODULE_CODES = ["APPOINTMENTS", "CONSULTATION", "PRESCRIPTION", "BILLING", "VACCINATION", "INVENTORY", "AI_COPILOT"] as const;

function DetailCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card variant="outlined" sx={{ height: "100%" }}>
      <CardContent>
        <Stack spacing={1.25}>
          <Typography variant="subtitle2" color="text.secondary">{title}</Typography>
          {children}
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function TenantDetailPage() {
  const auth = useAuth();
  const { tenantId = "" } = useParams();
  const navigate = useNavigate();
  const [tenant, setTenant] = React.useState<PlatformTenantDetail | null>(null);
  const [plans, setPlans] = React.useState<PlatformPlan[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [message, setMessage] = React.useState<string | null>(null);
  const [adminEmail, setAdminEmail] = React.useState("");
  const [adminFirst, setAdminFirst] = React.useState("");
  const [adminLast, setAdminLast] = React.useState("");

  const load = React.useCallback(async () => {
    if (!auth.accessToken || !tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [detail, planList] = await Promise.all([
        getPlatformTenant(auth.accessToken, tenantId),
        getPlatformPlans(auth.accessToken),
      ]);
      setTenant(detail);
      setPlans(planList);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load tenant");
    } finally {
      setLoading(false);
    }
  }, [auth.accessToken, tenantId]);

  React.useEffect(() => { void load(); }, [load]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  if (loading) {
    return <Box sx={{ display: "grid", placeItems: "center", minHeight: 240 }}><CircularProgress /></Box>;
  }

  if (error || !tenant) {
    return <Alert severity="error">{error || "Tenant not found"}</Alert>;
  }

  const active = (tenant.tenant.status || "").toUpperCase() === "ACTIVE";

  return (
    <Stack spacing={2.5}>
      <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Button size="small" onClick={() => navigate("/platform/tenants")}>Back to tenants</Button>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>{tenant.tenant.name}</Typography>
          <Typography color="text.secondary" sx={{ fontFamily: "monospace" }}>{tenant.tenant.code}</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant={auth.selectedTenant?.id === tenant.tenant.id ? "contained" : "outlined"}
            onClick={() => {
              auth.selectTenant({ id: tenant.tenant.id, code: tenant.tenant.code, name: tenant.tenant.name });
              navigate("/");
            }}
          >
            {auth.selectedTenant?.id === tenant.tenant.id ? "Open Tenant" : "Select Tenant"}
          </Button>
          <Button onClick={async () => {
            if (!auth.accessToken) return;
            await updatePlatformTenantStatus(auth.accessToken, tenant.tenant.id, !active);
            setMessage(`Tenant ${!active ? "activated" : "deactivated"}.`);
            await load();
          }}>{active ? "Deactivate" : "Activate"}</Button>
        </Stack>
      </Box>

      {message ? <Alert severity="success" onClose={() => setMessage(null)}>{message}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 4 }}>
          <DetailCard title="Overview">
            <Chip size="small" color={active ? "success" : "warning"} label={tenant.tenant.status} />
            <Typography variant="body2">Plan: <strong>{tenant.tenant.planId || "-"}</strong></Typography>
            <Typography variant="body2">Users: <strong>{tenant.userCount}</strong></Typography>
            <Typography variant="body2">Admins: <strong>{tenant.adminCount}</strong></Typography>
          </DetailCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <DetailCard title="Clinic Profile">
            <Typography variant="body2">{tenant.clinicProfile?.displayName || "-"}</Typography>
            <Typography variant="body2" color="text.secondary">{tenant.clinicProfile?.city || "-"}, {tenant.clinicProfile?.country || "-"}</Typography>
            <Typography variant="body2">{tenant.clinicProfile?.email || "No clinic email"}</Typography>
            <Typography variant="body2">{tenant.clinicProfile?.phone || "No phone"}</Typography>
          </DetailCard>
        </Grid>
        <Grid size={{ xs: 12, md: 4 }}>
          <DetailCard title="Subscription">
            <Typography variant="body2">Status: <strong>{tenant.latestSubscription?.status || "-"}</strong></Typography>
            <Typography variant="body2">Start: <strong>{tenant.latestSubscription?.startDate || "-"}</strong></Typography>
            <Typography variant="body2">Trial: <strong>{tenant.latestSubscription?.trial ? "Yes" : "No"}</strong></Typography>
          </DetailCard>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <DetailCard title="Plan & Entitlements">
            <TextField
              size="small"
              label="Plan"
              select
              value={tenant.tenant.planId || ""}
              onChange={async (event) => {
                if (!auth.accessToken) return;
                await updatePlatformTenantPlan(auth.accessToken, tenant.tenant.id, event.target.value);
                setMessage("Plan updated.");
                await load();
              }}
              sx={{ maxWidth: 280 }}
            >
              {plans.map((plan) => <MenuItem key={plan.id} value={plan.id}>{plan.id} - {plan.name}</MenuItem>)}
            </TextField>
          </DetailCard>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <DetailCard title="Tenant Modules">
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              {MODULE_CODES.map((moduleCode) => (
                <Stack key={moduleCode} direction="row" spacing={0.5} alignItems="center">
                  <Switch
                    size="small"
                    checked={Boolean(tenant.modules?.[moduleCode])}
                    onChange={async (event) => {
                      if (!auth.accessToken) return;
                      await updatePlatformTenantModules(auth.accessToken, tenant.tenant.id, {
                        ...tenant.modules,
                        [moduleCode]: event.target.checked,
                      });
                      setMessage("Module settings updated.");
                      await load();
                    }}
                  />
                  <Typography variant="body2">{moduleCode}</Typography>
                </Stack>
              ))}
            </Stack>
          </DetailCard>
        </Grid>

        <Grid size={{ xs: 12 }}>
          <DetailCard title="Create Clinic Admin">
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.25}>
              <TextField label="Admin Email" size="small" value={adminEmail} onChange={(e) => setAdminEmail(e.target.value)} fullWidth />
              <TextField label="First Name" size="small" value={adminFirst} onChange={(e) => setAdminFirst(e.target.value)} fullWidth />
              <TextField label="Last Name" size="small" value={adminLast} onChange={(e) => setAdminLast(e.target.value)} fullWidth />
              <Button
                variant="contained"
                onClick={async () => {
                  if (!auth.accessToken || !adminEmail) return;
                  try {
                    await createPlatformTenantAdminUser(auth.accessToken, tenant.tenant.id, {
                      email: adminEmail,
                      firstName: adminFirst || null,
                      lastName: adminLast || null,
                    });
                    setMessage("Clinic admin user provisioned.");
                    setAdminEmail("");
                    setAdminFirst("");
                    setAdminLast("");
                    await load();
                  } catch (err) {
                    setError(err instanceof Error ? err.message : "Failed to create admin user");
                  }
                }}
              >
                Create Admin
              </Button>
            </Stack>
          </DetailCard>
        </Grid>
      </Grid>
    </Stack>
  );
}
