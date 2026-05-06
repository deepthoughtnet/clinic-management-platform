import * as React from "react";
import { Link as RouterLink } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, Typography } from "@mui/material";

import { useAuth } from "../auth/useAuth";
import { getDashboardSummary, type DashboardSummary } from "../api/clinicApi";

type SummaryCard = {
  label: string;
  value: number | string;
  tone: "primary" | "info" | "success" | "warning" | "error";
  link?: string;
};

function formatMoney(value: number | string | null | undefined) {
  const amount = typeof value === "string" ? Number(value) : value ?? 0;
  return amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function SummaryTile({ card }: { card: SummaryCard }) {
  return (
    <Card
      sx={{
        height: "100%",
        border: "1px solid",
        borderColor: "divider",
        background: card.tone === "primary" ? "linear-gradient(180deg, rgba(25,118,210,0.10), rgba(25,118,210,0.02))" : undefined,
      }}
    >
      <CardContent>
        <Stack spacing={1}>
          <Typography variant="overline" sx={{ opacity: 0.75 }}>
            {card.label}
          </Typography>
          <Typography variant="h4" sx={{ fontWeight: 900 }}>
            {card.value}
          </Typography>
          {card.link ? (
            <Button size="small" component={RouterLink} to={card.link} sx={{ alignSelf: "flex-start" }}>
              Open
            </Button>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const auth = useAuth();
  const [summary, setSummary] = React.useState<DashboardSummary | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    async function load() {
      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const value = await getDashboardSummary(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setSummary(value);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load dashboard summary");
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
  }, [auth.accessToken, auth.tenantId]);

  const cards: SummaryCard[] = summary
    ? [
        { label: "Today appointments", value: summary.todayAppointments, tone: "primary", link: "/appointments" },
        { label: "Waiting patients", value: summary.waitingPatients, tone: "warning", link: "/queue" },
        { label: "In consultation", value: summary.inConsultationCount, tone: "info", link: "/queue" },
        { label: "Completed consultations", value: summary.completedConsultations, tone: "success", link: "/consultations" },
        { label: "Today revenue", value: formatMoney(summary.todayRevenue), tone: "success", link: "/billing" },
        { label: "Pending dues", value: formatMoney(summary.pendingDues), tone: "error", link: "/billing" },
        { label: "Follow-ups due", value: summary.followUpsDue, tone: "warning", link: "/reports" },
        { label: "Vaccinations due", value: summary.vaccinationsDue, tone: "warning", link: "/vaccinations" },
        { label: "Low stock medicines", value: summary.lowStockMedicines, tone: "error", link: "/inventory" },
      ]
    : [];

  if (!auth.tenantId) {
    return <Alert severity="warning">No tenant is selected for this session.</Alert>;
  }

  return (
    <Stack spacing={3}>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}>
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 900, mb: 1 }}>
            Dashboard
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Tenant-scoped clinical operations, revenue, and care signals.
          </Typography>
        </Box>
        <Chip label={auth.tenantName || "Clinic tenant"} variant="outlined" />
      </Box>

      {error ? <Alert severity="error">{error}</Alert> : null}

      {loading ? (
        <Box sx={{ display: "grid", placeItems: "center", minHeight: 220 }}>
          <CircularProgress />
        </Box>
      ) : (
        <Grid container spacing={2}>
          {cards.map((card) => (
            <Grid key={card.label} size={{ xs: 12, sm: 6, lg: 4 }}>
              <SummaryTile card={card} />
            </Grid>
          ))}
        </Grid>
      )}
    </Stack>
  );
}
