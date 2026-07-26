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
import { useSearchParams } from "react-router-dom";

import { useAuth } from "../../auth/useAuth";
import {
  compareCommercialEffectiveEntitlements,
  getCommercialEffectiveEntitlementHistory,
  getCommercialRuntimeDiffSummary,
  getPlatformTenants,
  listCommercialRuntimeDiffTenants,
  type CommercialEffectiveEntitlementComparison,
  type CommercialEffectiveEntitlementSnapshot,
  type CommercialRuntimeDiffSummary,
  type CommercialRuntimeDiffTenant,
  type PlatformTenant,
} from "../../api/clinicApi";
import CommercialTenantSearchSelector from "../../shared/components/commercial/CommercialTenantSearchSelector";

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

function comparisonLabel(category: string) {
  switch (category) {
    case "MATCH":
      return "Match";
    case "LEGACY_ONLY":
      return "Legacy Only";
    case "COMMERCIAL_ONLY":
      return "Commercial Only";
    case "DIFFERENT":
      return "Different";
    case "LIMIT_DIFFERENCE":
      return "Different";
    case "SNAPSHOT_MISSING":
      return "Snapshot Missing";
    case "SNAPSHOT_INVALID":
      return "Snapshot Invalid";
    default:
      return category;
  }
}

function comparisonStatusTone(category: string) {
  if (category === "MATCH") return "success" as const;
  if (category === "DIFFERENT") return "warning" as const;
  if (category === "LEGACY_ONLY" || category === "COMMERCIAL_ONLY" || category === "LIMIT_DIFFERENCE") return "info" as const;
  return "default" as const;
}

export default function CommercialRuntimeDiffPage() {
  const auth = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [summary, setSummary] = React.useState<CommercialRuntimeDiffSummary | null>(null);
  const [tenants, setTenants] = React.useState<CommercialRuntimeDiffTenant[]>([]);
  const [tenantDirectory, setTenantDirectory] = React.useState<PlatformTenant[]>([]);
  const [comparison, setComparison] = React.useState<CommercialEffectiveEntitlementComparison | null>(null);
  const [history, setHistory] = React.useState<CommercialEffectiveEntitlementSnapshot[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [statusFilter, setStatusFilter] = React.useState("ALL");
  const selectedTenantParam = searchParams.get("tenant") || "";
  const selectedTenantId = selectedTenantParam;

  React.useEffect(() => {
    if (!auth.accessToken) return;
    const accessToken = auth.accessToken;
    let cancelled = false;
    async function load() {
      try {
        const [summaryResult, tenantRows, tenantRowsDirectory] = await Promise.all([
          getCommercialRuntimeDiffSummary(accessToken),
          listCommercialRuntimeDiffTenants(accessToken),
          getPlatformTenants(accessToken),
        ]);
        if (cancelled) return;
        setSummary(summaryResult);
        setTenants(tenantRows);
        setTenantDirectory(tenantRowsDirectory);
        if (!selectedTenantParam && tenantRows[0]?.tenantId) {
          setSearchParams({ tenant: tenantRows[0].tenantId }, { replace: true });
        }
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
  }, [auth.accessToken, selectedTenantParam, setSearchParams]);

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
  const selectedTenantDirectory = tenantDirectory.find((tenant) => tenant.id === selectedTenantId) || null;
  const tenantOptions = tenantDirectory.map((tenant) => {
    const runtimeTenant = tenants.find((row) => row.tenantId === tenant.id) || null;
    return {
      ...tenant,
      subscriptionName: runtimeTenant?.subscriptionName || runtimeTenant?.currentSubscription || null,
      subscriptionStatus: runtimeTenant?.subscriptionStatus || null,
      planTemplateName: runtimeTenant?.planTemplateName || null,
      publishedVersionLabel: runtimeTenant?.publishedVersion || null,
    };
  });
  const comparisonRows = React.useMemo(() => {
    const rows = [
      ...(comparison?.modules || []).map((item) => ({ section: "Modules", ...item })),
      ...(comparison?.features || []).map((item) => ({ section: "Features", ...item })),
      ...(comparison?.limits || []).map((item) => ({ section: "Limits", ...item })),
    ];
    return rows.sort((left, right) => `${left.section}:${left.label}`.localeCompare(`${right.section}:${right.label}`));
  }, [comparison]);
  const comparisonMetrics = React.useMemo(() => {
    const moduleRows = comparison?.modules || [];
    const featureRows = comparison?.features || [];
    const limitRows = comparison?.limits || [];
    return {
      legacyModules: moduleRows.filter((item) => item.category === "LEGACY_ONLY" || item.category === "MATCH" || item.category === "DIFFERENT").length,
      commercialModules: moduleRows.filter((item) => item.category === "COMMERCIAL_ONLY" || item.category === "MATCH" || item.category === "DIFFERENT").length,
      matchingModules: moduleRows.filter((item) => item.category === "MATCH").length,
      legacyOnlyModules: moduleRows.filter((item) => item.category === "LEGACY_ONLY").length,
      commercialOnlyModules: moduleRows.filter((item) => item.category === "COMMERCIAL_ONLY").length,
      moduleCoverage: moduleRows.length ? Math.round((moduleRows.filter((item) => item.category === "MATCH").length / moduleRows.length) * 100) : 0,
      legacyFeatures: featureRows.filter((item) => item.category === "LEGACY_ONLY" || item.category === "MATCH" || item.category === "DIFFERENT").length,
      commercialFeatures: featureRows.filter((item) => item.category === "COMMERCIAL_ONLY" || item.category === "MATCH" || item.category === "DIFFERENT").length,
      matchingFeatures: featureRows.filter((item) => item.category === "MATCH").length,
      featureDifferences: featureRows.filter((item) => item.category !== "MATCH").length,
      limitDifferences: limitRows.filter((item) => item.category !== "MATCH").length,
      criticalDifferences: [...moduleRows, ...featureRows, ...limitRows].filter((item) => item.category === "DIFFERENT" || item.category === "LIMIT_DIFFERENCE").length,
    };
  }, [comparison]);

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

      {comparison ? (
        <Grid container spacing={2}>
          {[
            summaryCard("Legacy Modules", comparisonMetrics.legacyModules, "Modules visible in the legacy runtime projection"),
            summaryCard("Commercial Modules", comparisonMetrics.commercialModules, "Modules visible in the commercial snapshot"),
            summaryCard("Matching Modules", comparisonMetrics.matchingModules, "Modules that match across both models"),
            summaryCard("Legacy-Only Modules", comparisonMetrics.legacyOnlyModules, "Modules granted only by legacy runtime"),
            summaryCard("Commercial-Only Modules", comparisonMetrics.commercialOnlyModules, "Modules granted only by commercial snapshot"),
            summaryCard("Module Coverage %", `${comparisonMetrics.moduleCoverage}%`, "Share of module rows that match"),
            summaryCard("Legacy Features", comparisonMetrics.legacyFeatures, "Features visible in the legacy runtime projection"),
            summaryCard("Commercial Features", comparisonMetrics.commercialFeatures, "Features visible in the commercial snapshot"),
            summaryCard("Matching Features", comparisonMetrics.matchingFeatures, "Features that match across both models"),
            summaryCard("Feature Differences", comparisonMetrics.featureDifferences, "Features that differ between models"),
            summaryCard("Limit Differences", comparisonMetrics.limitDifferences, "Configured limit differences"),
            summaryCard("Critical Differences", comparisonMetrics.criticalDifferences, "Differences that need review before rollout"),
          ]}
        </Grid>
      ) : null}

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={1.5}>
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              Commercial tenant under review
            </Typography>
            <Typography variant="body2" color="text.secondary">
              This workspace uses a URL-driven commercial tenant context. The header tenant does not control the runtime diff view.
            </Typography>
            <CommercialTenantSearchSelector
              tenants={tenantOptions}
              value={selectedTenantId}
              onChange={(tenantId) => setSearchParams(tenantId ? { tenant: tenantId } : {}, { replace: true })}
              loading={!tenantDirectory.length && !error}
            />
            {selectedTenantDirectory ? (
              <Typography variant="body2" color="text.secondary">
                Selected commercial tenant: {selectedTenantDirectory.name} ({selectedTenantDirectory.code})
              </Typography>
            ) : null}
          </Stack>
        </CardContent>
      </Card>

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
                    <TableRow key={tenant.tenantId} hover selected={tenant.tenantId === selectedTenantId} onClick={() => setSearchParams({ tenant: tenant.tenantId }, { replace: true })} sx={{ cursor: "pointer" }}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography sx={{ fontWeight: 800 }}>{tenant.tenantName}</Typography>
                          <Typography variant="body2" color="text.secondary">{tenant.tenantCode}</Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography sx={{ fontWeight: 700 }}>{tenant.subscriptionName || tenant.currentSubscription || "None"}</Typography>
                          <Typography variant="body2" color="text.secondary">{tenant.subscriptionStatus || "—"}</Typography>
                        </Stack>
                      </TableCell>
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
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Entitlement</TableCell>
                            <TableCell>Legacy</TableCell>
                            <TableCell>Commercial</TableCell>
                            <TableCell>Result</TableCell>
                            <TableCell>Commercial Source</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {comparisonRows.map((item) => (
                            <TableRow key={`${item.section}-${item.code}`}>
                              <TableCell>
                                <Stack spacing={0.25}>
                                  <Typography sx={{ fontWeight: 800 }}>{item.label}</Typography>
                                  <Typography variant="body2" color="text.secondary">{item.code} · {item.section}</Typography>
                                </Stack>
                              </TableCell>
                              <TableCell>{item.legacyValue || "—"}</TableCell>
                              <TableCell>{item.commercialValue || "—"}</TableCell>
                              <TableCell><Chip size="small" label={comparisonLabel(item.category)} color={comparisonStatusTone(item.category)} variant="outlined" /></TableCell>
                              <TableCell>{item.detail || "—"}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
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
                    <Alert severity={selectedTenant.readinessStatus === "BLOCKED" ? "error" : selectedTenant.readinessStatus === "READY_WITH_WARNINGS" ? "warning" : "success"} variant="outlined">
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>{selectedTenant.readinessStatus || selectedTenant.rolloutReadiness}</Typography>
                        <Typography variant="body2">Recommendation: {selectedTenant.recommendation}</Typography>
                      </Stack>
                    </Alert>
                    <Typography variant="body2" color="text.secondary">
                      Generated: {formatDateTime(selectedTenant.generatedAt)} · Snapshot: {selectedTenant.snapshotStatus}
                    </Typography>
                    <Stack spacing={0.75}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Checklist</Typography>
                      <Typography variant="body2" color="text.secondary">Subscription: {selectedTenant.subscriptionStatus || "NONE"}</Typography>
                      <Typography variant="body2" color="text.secondary">Published Version: {selectedTenant.publishedVersion}</Typography>
                      <Typography variant="body2" color="text.secondary">Snapshot: {selectedTenant.snapshotStatus} / {selectedTenant.validationState || "—"}</Typography>
                      <Typography variant="body2" color="text.secondary">Comparison: {selectedTenant.comparisonStatus}</Typography>
                      <Typography variant="body2" color="text.secondary">Overrides: {selectedTenant.activeOverrides}</Typography>
                      <Typography variant="body2" color="text.secondary">Runtime Source: {selectedTenant.runtimeSource}</Typography>
                      {selectedTenant.readinessBlockers.length ? (
                        <Typography variant="body2" color="text.secondary">
                          Blockers: {selectedTenant.readinessBlockers.join(" · ")}
                        </Typography>
                      ) : null}
                      {selectedTenant.readinessWarnings.length ? (
                        <Typography variant="body2" color="text.secondary">
                          Warnings: {selectedTenant.readinessWarnings.join(" · ")}
                        </Typography>
                      ) : null}
                      <Typography variant="body2" color="text.secondary">Next action: {selectedTenant.targetRoute}</Typography>
                    </Stack>
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
