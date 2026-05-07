import * as React from "react";
import { Alert, Card, CardContent, Chip, CircularProgress, Grid, Stack, Typography } from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import { getPlatformPlans, getPlatformTenants, type PlatformPlan } from "../../api/clinicApi";

export default function PlansModulesPage() {
  const auth = useAuth();
  const [plans, setPlans] = React.useState<PlatformPlan[]>([]);
  const [activeTenantCount, setActiveTenantCount] = React.useState(0);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken) return;
      setLoading(true);
      setError(null);
      try {
        const [planRows, tenants] = await Promise.all([
          getPlatformPlans(auth.accessToken),
          getPlatformTenants(auth.accessToken),
        ]);
        if (cancelled) return;
        setPlans(planRows);
        setActiveTenantCount(tenants.filter((tenant) => (tenant.status || "").toUpperCase() === "ACTIVE").length);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load plans");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  if (loading) {
    return <Stack alignItems="center" sx={{ py: 6 }}><CircularProgress /></Stack>;
  }

  return (
    <Stack spacing={2.5}>
      <Typography variant="h4" sx={{ fontWeight: 900 }}>Plans / Modules</Typography>
      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined"><CardContent><Typography variant="overline">Active Tenants</Typography><Typography variant="h4" sx={{ fontWeight: 900 }}>{activeTenantCount}</Typography></CardContent></Card>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Card variant="outlined"><CardContent><Typography variant="overline">Plans</Typography><Typography variant="h4" sx={{ fontWeight: 900 }}>{plans.length}</Typography></CardContent></Card>
        </Grid>
      </Grid>

      <Grid container spacing={2}>
        {plans.map((plan) => (
          <Grid key={plan.id} size={{ xs: 12, md: 6 }}>
            <Card variant="outlined" sx={{ height: "100%" }}>
              <CardContent>
                <Stack spacing={1.2}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip size="small" label={plan.id} color="primary" />
                    <Typography variant="h6" sx={{ fontWeight: 800 }}>{plan.name}</Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Feature payload is available from backend and can be expanded into fine-grained entitlements.
                  </Typography>
                  <Typography variant="caption" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}>
                    {plan.features || "{}"}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
