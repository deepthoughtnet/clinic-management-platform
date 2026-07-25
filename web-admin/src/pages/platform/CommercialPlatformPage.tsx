import * as React from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, Grid, Paper, Stack, Typography } from "@mui/material";
import OpenInNewRoundedIcon from "@mui/icons-material/OpenInNewRounded";

import { useAuth } from "../../auth/useAuth";
import { getCommercialPlatformOverview } from "../../api/clinicApi";

function metricTone(label: string) {
  if (label.includes("Published") || label.includes("Active")) return "success" as const;
  if (label.includes("Draft")) return "warning" as const;
  return "primary" as const;
}

export default function CommercialPlatformPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [overview, setOverview] = React.useState<Awaited<ReturnType<typeof getCommercialPlatformOverview>> | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken) return;
      setLoading(true);
      setError(null);
      try {
        const result = await getCommercialPlatformOverview(auth.accessToken);
        if (!cancelled) {
          setOverview(result);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load commercial overview");
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

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Commercial Platform
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 840 }}>
          This workspace manages commercial packaging and the product catalog. It does not change tenant runtime access.
        </Typography>
      </Stack>

      <Alert severity="info" variant="outlined">
        Catalog only. Tenant access still comes from the existing legacy plan and module entitlement path.
      </Alert>

      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <Typography variant="body2" color="text.secondary">Loading commercial overview…</Typography> : null}

      <Grid container spacing={2}>
        {(overview?.kpis || []).map((card) => (
          <Grid key={card.key} size={{ xs: 12, sm: 6, md: 3 }}>
            <Card variant="outlined" sx={{ height: "100%" }}>
              <CardContent>
                <Stack spacing={0.75}>
                  <Chip size="small" label={card.label} color={metricTone(card.label)} variant="outlined" sx={{ alignSelf: "flex-start" }} />
                  <Typography variant="h4" sx={{ fontWeight: 900 }}>
                    {card.value}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {card.helperText}
                  </Typography>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              Commercial Lifecycle
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
              {(overview?.lifecycle || []).map((stage) => (
                <Chip
                  key={stage.key}
                  label={stage.comingSoon ? `${stage.label} - Coming soon` : stage.label}
                  color={stage.available ? "success" : "default"}
                  variant={stage.available ? "filled" : "outlined"}
                />
              ))}
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        {(overview?.actions || []).map((action) => (
          <Grid key={action.key} size={{ xs: 12, md: 6 }}>
            <Paper variant="outlined" sx={{ p: 2.25, height: "100%" }}>
              <Stack spacing={1.25}>
                <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                  <Typography variant="h6" sx={{ fontWeight: 900 }}>
                    {action.label}
                  </Typography>
                  <OpenInNewRoundedIcon fontSize="small" />
                </Stack>
                <Typography variant="body2" color="text.secondary">
                  {action.path}
                </Typography>
                <Box>
                  <Button variant={action.primary ? "contained" : "outlined"} onClick={() => navigate(action.path)}>
                    Open
                  </Button>
                </Box>
              </Stack>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
