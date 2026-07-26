import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";

import { useAuth } from "../../auth/useAuth";
import {
  compareCommercialEffectiveEntitlements,
  getCommercialEffectiveEntitlementHistory,
  getCommercialRuntimeDiffSummary,
  listCommercialRuntimeDiffTenants,
  type CommercialEffectiveEntitlementComparison,
  type CommercialEffectiveEntitlementSnapshot,
  type CommercialRuntimeDiffSummary,
  type CommercialRuntimeDiffTenant,
} from "../../api/clinicApi";

function chipTone(value: string | null | undefined) {
  if (!value) return "default" as const;
  if (value === "READY" || value === "MATCH") return "success" as const;
  if (value === "READY_WITH_WARNINGS" || value === "DIFFERENT") return "warning" as const;
  if (value === "BLOCKED" || value === "SNAPSHOT_INVALID" || value === "SNAPSHOT_MISSING") return "error" as const;
  return "default" as const;
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

function summaryCard(label: string, value: number | string | boolean, helperText: string) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }} key={label}>
      <Card variant="outlined" sx={{ height: "100%" }}>
        <CardContent>
          <Stack spacing={0.5}>
            <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
              {label}
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 900 }}>
              {typeof value === "boolean" ? (value ? "Yes" : "No") : value}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {helperText}
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </Grid>
  );
}

export default function CommercialRuntimeDiffPage() {
  const auth = useAuth();
  const [summary, setSummary] = React.useState<CommercialRuntimeDiffSummary | null>(null);
  const [tenants, setTenants] = React.useState<CommercialRuntimeDiffTenant[]>([]);
  const [selectedTenantId, setSelectedTenantId] = React.useState<string>("");
  const [comparison, setComparison] = React.useState<CommercialEffectiveEntitlementComparison | null>(null);
  const [history, setHistory] = React.useState<CommercialEffectiveEntitlementSnapshot[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [statusFilter, setStatusFilter] = React.useState("ALL");

  React.useEffect(() => {
    if (!auth.accessToken) return;
    const accessToken = auth.accessToken;
    let cancelled = false;
    async function load() {
      try {
        const [summaryResult, tenantRows] = await Promise.all([
          getCommercialRuntimeDiffSummary(accessToken),
          listCommercialRuntimeDiffTenants(accessToken),
        ]);
        if (cancelled) return;
        setSummary(summaryResult);
        setTenants(tenantRows);
        setSelectedTenantId((current) => current || tenantRows[0]?.tenantId || "");
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load runtime diff");
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken]);

  React.useEffect(() => {
    if (!auth.accessToken || !selectedTenantId) return;
    const accessToken = auth.accessToken;
    let cancelled = false;
    async function loadTenant() {
      setLoading(true);
      try {
        const [comparisonResult, historyResult] = await Promise.all([
          compareCommercialEffectiveEntitlements(accessToken, selectedTenantId),
          getCommercialEffectiveEntitlementHistory(accessToken, selectedTenantId, { page: 0, size: 8 }),
        ]);
        if (cancelled) return;
        setComparison(comparisonResult);
        setHistory(historyResult.items);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load tenant diff");
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void loadTenant();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, selectedTenantId]);

  if (!auth.rolesUpper.includes("PLATFORM_ADMIN")) {
    return <Alert severity="error">Platform access is restricted to PLATFORM_ADMIN.</Alert>;
  }

  const filteredTenants = tenants.filter((tenant) => statusFilter === "ALL" || tenant.rolloutReadiness === statusFilter);
  const selectedTenant = tenants.find((tenant) => tenant.tenantId === selectedTenantId) || null;

  return (
    <Stack spacing={2.5}>
      <Stack spacing={0.75}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Runtime Diff
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 920 }}>
          Compare legacy runtime entitlements with commercial effective entitlements before rollout. Legacy remains authoritative while commercial runtime is disabled.
        </Typography>
      </Stack>

      {error ? <Alert severity="error">{error}</Alert> : null}

      <Grid container spacing={2}>
        {summary
          ? [
              summaryCard("Tenants with Active Commercial Subscriptions", summary.tenantsWithActiveCommercialSubscriptions, "Active commercial subscriptions observed by the platform"),
              summaryCard("Tenants with Current Valid Snapshots", summary.tenantsWithCurrentValidSnapshots, "Tenants with current and valid effective snapshots"),
              summaryCard("Missing Snapshots", summary.missingSnapshots, "Tenants without a current effective snapshot"),
              summaryCard("Invalid Snapshots", summary.invalidSnapshots, "Tenants whose current snapshot failed validation"),
              summaryCard("Exact Matches", summary.exactMatches, "Legacy and commercial results match"),
              summaryCard("Tenants with Differences", summary.tenantsWithDifferences, "Legacy only, commercial only, or different entitlements"),
              summaryCard("Active Overrides", summary.activeOverrides, "Currently effective override records"),
              summaryCard("Snapshot Generation Failures", summary.snapshotGenerationFailures, "Generation attempts that need attention"),
              summaryCard("Commercial Runtime Enabled", summary.commercialRuntimeEnabled, "Global runtime cutover flag"),
              summaryCard("Shadow Comparison Enabled", summary.shadowComparisonEnabled, "Shadow-mode diagnostics flag"),
              summaryCard("Allowlisted Tenants", summary.allowlistedTenants, "Tenant-specific cutover allowlist size"),
            ]
          : null}
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} alignItems={{ md: "center" }} justifyContent="space-between">
              <Typography variant="h6" sx={{ fontWeight: 900 }}>
                Tenant Readiness
              </Typography>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                <FormControl size="small" sx={{ minWidth: 180 }}>
                  <InputLabel id="runtime-diff-status-filter">Filter</InputLabel>
                  <Select labelId="runtime-diff-status-filter" label="Filter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                    <MenuItem value="ALL">All</MenuItem>
                    <MenuItem value="READY">Ready</MenuItem>
                    <MenuItem value="READY_WITH_WARNINGS">Ready with warnings</MenuItem>
                    <MenuItem value="BLOCKED">Blocked</MenuItem>
                    <MenuItem value="NOT_EVALUATED">Not evaluated</MenuItem>
                  </Select>
                </FormControl>
                <Button variant="outlined" onClick={() => window.location.reload()}>Refresh</Button>
              </Stack>
            </Stack>

            <TableContainer component={Paper} variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Tenant</TableCell>
                    <TableCell>Subscription</TableCell>
                    <TableCell>Published Version</TableCell>
                    <TableCell>Snapshot Status</TableCell>
                    <TableCell>Comparison</TableCell>
                    <TableCell>Overrides</TableCell>
                    <TableCell>Runtime Source</TableCell>
                    <TableCell>Readiness</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredTenants.map((tenant) => (
                    <TableRow key={tenant.tenantId} hover selected={tenant.tenantId === selectedTenantId} onClick={() => setSelectedTenantId(tenant.tenantId)} sx={{ cursor: "pointer" }}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography sx={{ fontWeight: 800 }}>{tenant.tenantName}</Typography>
                          <Typography variant="body2" color="text.secondary">{tenant.tenantCode}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{tenant.currentSubscription}</TableCell>
                      <TableCell>{tenant.publishedVersion}</TableCell>
                      <TableCell><Chip size="small" label={tenant.snapshotStatus} color={chipTone(tenant.snapshotStatus)} variant="outlined" /></TableCell>
                      <TableCell><Chip size="small" label={tenant.comparisonStatus} color={chipTone(tenant.comparisonStatus)} variant="outlined" /></TableCell>
                      <TableCell>{tenant.activeOverrides}</TableCell>
                      <TableCell>{tenant.runtimeSource}</TableCell>
                      <TableCell>
                        <Chip size="small" label={tenant.rolloutReadiness} color={chipTone(tenant.rolloutReadiness)} variant="outlined" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Stack>
        </CardContent>
      </Card>

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>
                  Side-by-Side Comparison
                </Typography>
                {loading ? <Alert severity="info">Loading comparison…</Alert> : null}
                {selectedTenant ? (
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      {selectedTenant.tenantName} · {selectedTenant.tenantCode} · {selectedTenant.rolloutReadiness}
                    </Typography>
                    <Box>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Modules</Typography>
                      <Stack spacing={0.75}>
                        {comparison?.modules.map((item) => (
                          <Chip key={item.code} label={`${item.label} · ${item.category}`} variant="outlined" />
                        )) || null}
                      </Stack>
                    </Box>
                    <Box>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Features</Typography>
                      <Stack spacing={0.75}>
                        {comparison?.features.map((item) => (
                          <Chip key={item.code} label={`${item.label} · ${item.category}`} variant="outlined" />
                        )) || null}
                      </Stack>
                    </Box>
                    <Box>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Limits</Typography>
                      <Stack spacing={0.75}>
                        {comparison?.limits.map((item) => (
                          <Chip key={item.code} label={`${item.label} · ${item.category}`} variant="outlined" />
                        )) || null}
                      </Stack>
                    </Box>
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">Select a tenant to review comparison details.</Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, lg: 6 }}>
          <Card variant="outlined">
            <CardContent>
              <Stack spacing={1.5}>
                <Typography variant="h6" sx={{ fontWeight: 900 }}>
                  Rollout Readiness
                </Typography>
                {selectedTenant ? (
                  <>
                    <Alert severity={chipTone(selectedTenant.rolloutReadiness) === "error" ? "error" : chipTone(selectedTenant.rolloutReadiness) === "warning" ? "warning" : "success"} variant="outlined">
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{selectedTenant.rolloutReadiness}</Typography>
                        <Typography variant="body2">Recommendation: {selectedTenant.recommendation}</Typography>
                      </Stack>
                    </Alert>
                    <Typography variant="body2" color="text.secondary">
                      Generated: {formatDateTime(selectedTenant.generatedAt)} · Snapshot: {selectedTenant.snapshotStatus}
                    </Typography>
                  </>
                ) : (
                  <Typography variant="body2" color="text.secondary">Select a tenant to see rollout guidance.</Typography>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              Snapshot History
            </Typography>
            {history.length ? history.map((snapshot) => (
              <Paper key={snapshot.snapshotId} variant="outlined" sx={{ p: 1.5 }}>
                <Stack direction="row" justifyContent="space-between" spacing={1}>
                  <Box>
                    <Typography sx={{ fontWeight: 800 }}>{snapshot.snapshotStatus}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {snapshot.generationReason} · {formatDateTime(snapshot.generatedAt)}
                    </Typography>
                  </Box>
                  <Chip size="small" label={snapshot.contentHash.slice(0, 12)} variant="outlined" />
                </Stack>
              </Paper>
            )) : (
              <Typography variant="body2" color="text.secondary">No snapshot history available for the selected tenant.</Typography>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  );
}
