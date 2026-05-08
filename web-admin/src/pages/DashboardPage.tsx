import * as React from "react";
import { Link as RouterLink } from "react-router-dom";
import { useNavigate } from "react-router-dom";
import { Alert, Box, Button, Card, CardContent, Chip, CircularProgress, Grid, Stack, Typography } from "@mui/material";

import { useAuth } from "../auth/useAuth";
import { getDashboardSummary, getPlatformPlans, getPlatformTenants, getRecentAiRequests, type AiRecentRequestRecord, type DashboardSummary } from "../api/clinicApi";

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
  const navigate = useNavigate();
  const isPlatformAdmin = auth.rolesUpper.includes("PLATFORM_ADMIN");
  const [summary, setSummary] = React.useState<DashboardSummary | null>(null);
  const [platformTenants, setPlatformTenants] = React.useState<number>(0);
  const [platformActiveTenants, setPlatformActiveTenants] = React.useState<number>(0);
  const [platformPlans, setPlatformPlans] = React.useState<number>(0);
  const [recentAiRequests, setRecentAiRequests] = React.useState<AiRecentRequestRecord[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const canBilling = auth.hasPermission("billing.read") || auth.hasPermission("billing.create") || auth.hasPermission("payment.collect");
  const canConsultation = auth.hasPermission("consultation.read") || auth.hasPermission("consultation.create");
  const canVaccination = auth.hasPermission("vaccination.manage") || auth.hasPermission("vaccination.read");
  const canInventory = auth.hasPermission("inventory.manage") || auth.hasPermission("inventory.read");
  const canReports = auth.hasPermission("report.read");
  const canAiCopilot = auth.hasPermission("ai_copilot.read") || auth.hasPermission("ai_copilot.run") || auth.hasPermission("ai_copilot.clinic.read") || auth.hasPermission("ai_copilot.clinic.run");
  const isDoctor = (auth.tenantRole || "").toUpperCase() === "DOCTOR";

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

  React.useEffect(() => {
    let cancelled = false;
    async function loadAiRecent() {
      if (!auth.accessToken || !auth.tenantId || !canAiCopilot) {
        setRecentAiRequests([]);
        return;
      }
      try {
        const rows = await getRecentAiRequests(auth.accessToken, auth.tenantId);
        if (!cancelled) {
          setRecentAiRequests(rows);
        }
      } catch {
        if (!cancelled) {
          setRecentAiRequests([]);
        }
      }
    }
    void loadAiRecent();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, canAiCopilot]);

  React.useEffect(() => {
    let cancelled = false;
    async function loadPlatform() {
      if (!auth.accessToken || !isPlatformAdmin || auth.tenantId) return;
      try {
        const [tenants, plans] = await Promise.all([
          getPlatformTenants(auth.accessToken),
          getPlatformPlans(auth.accessToken),
        ]);
        if (cancelled) return;
        setPlatformTenants(tenants.length);
        setPlatformActiveTenants(tenants.filter((tenant) => tenant.status?.toUpperCase() === "ACTIVE").length);
        setPlatformPlans(plans.length);
      } catch {
        // Keep dashboard usable even when platform API is not available.
      }
    }
    void loadPlatform();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, isPlatformAdmin]);

  const cards: SummaryCard[] = summary
    ? [
        { label: isDoctor ? "My appointments today" : "Today appointments", value: summary.todayAppointments, tone: "primary", link: "/appointments" },
        { label: isDoctor ? "My waiting queue" : "Waiting patients", value: summary.waitingPatients, tone: "warning", link: "/queue" },
        ...(canConsultation ? [
          { label: isDoctor ? "My active consultations" : "In consultation", value: summary.inConsultationCount, tone: "info", link: "/queue" } as SummaryCard,
          { label: isDoctor ? "My completed consultations" : "Completed consultations", value: summary.completedConsultations, tone: "success", link: "/consultations" } as SummaryCard,
        ] : []),
        ...(canBilling ? [
          { label: "Today revenue", value: formatMoney(summary.todayRevenue), tone: "success", link: "/billing" } as SummaryCard,
          { label: "Pending dues", value: formatMoney(summary.pendingDues), tone: "error", link: "/billing" } as SummaryCard,
        ] : []),
        ...(canReports ? [{ label: "Follow-ups due", value: summary.followUpsDue, tone: "warning", link: "/reports" } as SummaryCard] : []),
        ...(canVaccination ? [{ label: "Vaccinations due", value: summary.vaccinationsDue, tone: "warning", link: "/vaccinations" } as SummaryCard] : []),
        ...(canInventory ? [{ label: "Low stock medicines", value: summary.lowStockMedicines, tone: "error", link: "/inventory" } as SummaryCard] : []),
        ...(canAiCopilot ? [{ label: "AI Co-pilot", value: "Enabled", tone: "info", link: "/consultations" } as SummaryCard] : []),
      ]
    : [];

  if (!auth.tenantId && isPlatformAdmin) {
    return (
      <Stack spacing={2}>
        <Alert
          severity="info"
          action={
            auth.activeTenantMemberships.length === 1 ? (
              <Button
                color="inherit"
                size="small"
                onClick={() =>
                  auth.selectTenant({
                    id: auth.activeTenantMemberships[0].tenantId,
                    code: auth.activeTenantMemberships[0].tenantCode || auth.activeTenantMemberships[0].tenantId,
                    name: auth.activeTenantMemberships[0].tenantName || auth.activeTenantMemberships[0].tenantCode || auth.activeTenantMemberships[0].tenantId,
                  })
                }
              >
                Select {auth.activeTenantMemberships[0].tenantCode || "Tenant"}
              </Button>
            ) : (
              <Button color="inherit" size="small" onClick={() => navigate("/platform/tenants")}>
                Open Tenants
              </Button>
            )
          }
        >
          Select a clinic tenant from Platform &gt; Tenants to work inside a clinic.
        </Alert>
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <SummaryTile card={{ label: "Total tenants", value: platformTenants, tone: "primary", link: "/platform/tenants" }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <SummaryTile card={{ label: "Active tenants", value: platformActiveTenants, tone: "success", link: "/platform/tenants" }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <SummaryTile card={{ label: "Plans", value: platformPlans, tone: "info", link: "/platform/plans" }} />
          </Grid>
          <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
            <SummaryTile card={{ label: "Need tenant selection", value: "Yes", tone: "warning", link: "/platform/tenants" }} />
          </Grid>
        </Grid>
      </Stack>
    );
  }

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
            {isDoctor ? "Your assigned queue, appointments, and consultation work." : "Tenant-scoped clinical operations, revenue, and care signals."}
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
        <Stack spacing={2}>
          <Grid container spacing={2}>
            {cards.map((card) => (
              <Grid key={card.label} size={{ xs: 12, sm: 6, lg: 4 }}>
                <SummaryTile card={card} />
              </Grid>
            ))}
          </Grid>
          {canAiCopilot ? (
            <Card>
              <CardContent>
                <Stack spacing={1.5}>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>Recent AI Activity</Typography>
                  {!recentAiRequests.length ? (
                    <Alert severity="info">No recent AI requests are available yet.</Alert>
                  ) : (
                    <Stack spacing={1}>
                      {recentAiRequests.slice(0, 5).map((row) => (
                        <Box key={row.auditId} sx={{ display: "flex", justifyContent: "space-between", gap: 2, flexWrap: "wrap", p: 1, border: "1px solid", borderColor: "divider", borderRadius: 1 }}>
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 800 }}>{row.taskType.replaceAll("_", " ")}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {row.provider || "Provider pending"}{row.model ? ` • ${row.model}` : ""}{row.useCaseCode ? ` • ${row.useCaseCode}` : ""}
                            </Typography>
                          </Box>
                          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                            <Chip size="small" label={row.status} color={row.status === "SUCCESS" ? "success" : row.status === "FALLBACK" ? "warning" : "default"} />
                            {row.confidence != null ? <Chip size="small" label={`Confidence ${(row.confidence * 100).toFixed(0)}%`} /> : null}
                            {row.fallbackUsed ? <Chip size="small" label="Fallback used" /> : null}
                          </Stack>
                        </Box>
                      ))}
                    </Stack>
                  )}
                </Stack>
              </CardContent>
            </Card>
          ) : null}
        </Stack>
      )}
    </Stack>
  );
}
