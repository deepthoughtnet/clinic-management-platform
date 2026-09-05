import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  acknowledgePlatformAlert,
  getPlatformAlertRules,
  getPlatformAlerts,
  getPlatformDeadLetter,
  getPlatformHealth,
  getPlatformOperationsOverview,
  getPlatformProviderSlos,
  getPlatformProviders,
  getPlatformQueues,
  getPlatformRuntimeSummary,
  getPlatformSchedulers,
  getPlatformWebhooks,
  testPlatformOperationComponent,
  replayPlatformDeadLetter,
  resolvePlatformAlert,
  suppressPlatformAlert,
  type DeadLetterRow,
  type PlatformOperationsDiagnosticComponent,
  type PlatformOperationsDiagnosticResponse,
  type PlatformHealthResponse,
  type PlatformOperationsComponentHealth,
  type PlatformOperationsOverview,
  type PlatformOpsAlert,
  type PlatformProviderMetrics,
  type PlatformProviderSlo,
  type PlatformQueueMetrics,
  type PlatformRuntimeSummary,
  type PlatformSchedulerStatus,
  type PlatformWebhookMetrics,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";
import { TextEntryDialog } from "../../components/clinical/TextEntryDialog";

type SectionErrorKey =
  | "overview"
  | "health"
  | "alerts"
  | "providers"
  | "providerSlos"
  | "queues"
  | "schedulers"
  | "ai"
  | "webhooks"
  | "runtime"
  | "deadLetter"
  | "rules";

type SectionErrorState = Partial<Record<SectionErrorKey, string | null>>;
type PlatformAlertRuleRow = {
  id: string;
  tenantId: string | null;
  ruleKey: string;
  sourceType: string;
  enabled: boolean;
  severity: "WARNING" | "CRITICAL";
  thresholdType: string;
  thresholdValue: number;
  cooldownMinutes: number;
  autoResolveEnabled: boolean;
};

function fmtDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function fmtShortDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function fmtDurationMs(value: number | null | undefined) {
  if (value == null) return "-";
  if (value < 1000) return `${value} ms`;
  if (value < 60_000) return `${(value / 1000).toFixed(1)} s`;
  return `${Math.round(value / 60_000)} m`;
}

function formatLabel(value: unknown) {
  if (value == null || value === "") return "-";
  return String(value);
}

function statusTone(status: string) {
  switch (status) {
    case "HEALTHY":
      return { border: "#b7e3c0", background: "#f2fbf4", color: "#1a6b2a" };
    case "WARNING":
      return { border: "#f0d38a", background: "#fff9ea", color: "#8a5b00" };
    case "CRITICAL":
      return { border: "#efb4b4", background: "#fff2f2", color: "#9d1c1c" };
    default:
      return { border: "#d6d6d6", background: "#fafafa", color: "#525252" };
  }
}

function statusGlyph(status: string) {
  switch (status) {
    case "HEALTHY":
      return "OK";
    case "WARNING":
      return "!";
    case "CRITICAL":
      return "X";
    default:
      return "?";
  }
}

export default function PlatformOpsPage() {
  const { accessToken, tenantId, rolesUpper } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [overview, setOverview] = useState<PlatformOperationsOverview | null>(null);
  const [overviewError, setOverviewError] = useState<string | null>(null);
  const [sectionErrors, setSectionErrors] = useState<SectionErrorState>({});
  const [health, setHealth] = useState<PlatformHealthResponse | null>(null);
  const [schedulers, setSchedulers] = useState<PlatformSchedulerStatus[]>([]);
  const [queues, setQueues] = useState<PlatformQueueMetrics[]>([]);
  const [providers, setProviders] = useState<PlatformProviderMetrics[]>([]);
  const [providerSlos, setProviderSlos] = useState<PlatformProviderSlo[]>([]);
  const [ai, setAi] = useState<{ totalCalls: number; failedCalls: number; estimatedCost: number } | null>(null);
  const [webhooks, setWebhooks] = useState<PlatformWebhookMetrics | null>(null);
  const [alerts, setAlerts] = useState<PlatformOpsAlert[]>([]);
  const [runtime, setRuntime] = useState<PlatformRuntimeSummary | null>(null);
  const [deadLetter, setDeadLetter] = useState<DeadLetterRow[]>([]);
  const [alertRules, setAlertRules] = useState<PlatformAlertRuleRow[]>([]);
  const [severityFilter, setSeverityFilter] = useState<"ALL" | "CRITICAL" | "WARNING">("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "SUPPRESSED">("ALL");
  const [rulesCount, setRulesCount] = useState(0);
  const [resolveTarget, setResolveTarget] = useState<string | null>(null);
  const [resolveNotes, setResolveNotes] = useState("");
  const [selectedComponent, setSelectedComponent] = useState<string | null>(null);
  const [diagnosticResults, setDiagnosticResults] = useState<Record<string, PlatformOperationsDiagnosticResponse | undefined>>({});
  const [diagnosticInFlight, setDiagnosticInFlight] = useState<string | null>(null);
  const [diagnosticError, setDiagnosticError] = useState<string | null>(null);

  const canAct = rolesUpper.includes("PLATFORM_ADMIN");
  const tenantScopedMode = Boolean(tenantId);
  const tenantScopeMessage = "Tenant-specific operational details are hidden in Platform mode. Select a clinic tenant to view alerts, queues, provider metrics, scheduler details, and DLQ records.";

  async function loadAll() {
    if (!accessToken) return;
    setLoading(true);
    setOverviewError(null);
    setSectionErrors({});
    setOverview(null);
    setHealth(null);
    setSchedulers([]);
    setQueues([]);
    setProviders([]);
    setProviderSlos([]);
    setAi(null);
    setWebhooks(null);
    setAlerts([]);
    setRuntime(null);
    setDeadLetter([]);
    setAlertRules([]);
    setRulesCount(0);
    setDiagnosticError(null);

    const requests: Array<{ key: SectionErrorKey; promise: Promise<unknown> }> = [
      { key: "overview", promise: getPlatformOperationsOverview(accessToken) },
    ];

    if (tenantId) {
      requests.push(
        { key: "health", promise: getPlatformHealth(accessToken, tenantId) },
        { key: "schedulers", promise: getPlatformSchedulers(accessToken, tenantId) },
        { key: "queues", promise: getPlatformQueues(accessToken, tenantId) },
        { key: "providers", promise: getPlatformProviders(accessToken, tenantId) },
        { key: "providerSlos", promise: getPlatformProviderSlos(accessToken, tenantId) },
        { key: "webhooks", promise: getPlatformWebhooks(accessToken, tenantId) },
        { key: "alerts", promise: getPlatformAlerts(accessToken, tenantId) },
        { key: "runtime", promise: getPlatformRuntimeSummary(accessToken, tenantId) },
        { key: "deadLetter", promise: getPlatformDeadLetter(accessToken, tenantId) },
        { key: "rules", promise: getPlatformAlertRules(accessToken, tenantId) },
      );
    }

    const results = await Promise.allSettled(requests.map((request) => request.promise));
    const nextErrors: SectionErrorState = {};

    results.forEach((result, index) => {
      const key = requests[index].key;
      if (result.status === "fulfilled") {
        switch (key) {
          case "overview":
            setOverview(result.value as PlatformOperationsOverview);
            break;
          case "health": {
            const value = result.value as PlatformHealthResponse;
            setHealth(value);
            setAi({
              totalCalls: value.aiMetrics.totalCalls,
              failedCalls: value.aiMetrics.failedCalls,
              estimatedCost: Number(value.aiMetrics.estimatedCost),
            });
            break;
          }
          case "schedulers":
            setSchedulers((result.value as { schedulers: PlatformSchedulerStatus[] }).schedulers);
            break;
          case "queues":
            setQueues((result.value as { queues: PlatformQueueMetrics[] }).queues);
            break;
          case "providers":
            setProviders((result.value as { providers: PlatformProviderMetrics[] }).providers);
            break;
          case "providerSlos":
            setProviderSlos((result.value as { providers: PlatformProviderSlo[] }).providers);
            break;
          case "webhooks":
            setWebhooks(result.value as PlatformWebhookMetrics);
            break;
          case "alerts":
            setAlerts((result.value as { alerts: PlatformOpsAlert[] }).alerts);
            break;
          case "runtime":
            setRuntime(result.value as PlatformRuntimeSummary);
            break;
          case "deadLetter":
            setDeadLetter((result.value as { items: DeadLetterRow[] }).items);
            break;
          case "rules":
            setAlertRules((result.value as { rules: PlatformAlertRuleRow[] }).rules);
            setRulesCount((result.value as { rules: PlatformAlertRuleRow[] }).rules.length);
            break;
        }
      } else {
        const message = (result.reason as Error)?.message || `Failed to load ${key}`;
        nextErrors[key] = message;
        if (key === "overview") {
          setOverviewError(message);
        }
      }
    });

    setSectionErrors(nextErrors);
    setLoading(false);
  }

  useEffect(() => {
    void loadAll();
  }, [accessToken, tenantId]);

  const filteredAlerts = useMemo(
    () => alerts.filter((alert) => (severityFilter === "ALL" || alert.severity === severityFilter) && (statusFilter === "ALL" || alert.status === statusFilter)),
    [alerts, severityFilter, statusFilter],
  );
  const criticalAlerts = useMemo(
    () => alerts.filter((alert) => alert.severity === "CRITICAL" && (alert.status === "OPEN" || alert.status === "ACKNOWLEDGED")),
    [alerts],
  );
  const healthMatrix = overview?.healthMatrix ?? fallbackHealthMatrix();
  const selectedHealth = useMemo(
    () => healthMatrix.find((component) => component.component === selectedComponent) || null,
    [healthMatrix, selectedComponent],
  );
  const selectedDiagnostic = selectedComponent ? diagnosticResults[selectedComponent] ?? null : null;
  const aiSummary = overview?.aiSummary ?? null;
  const overallHealthValue = overview?.summary.overallStatus ?? health?.overallStatus ?? "UNKNOWN";
  const tenantScopedPlaceholderMessage = "Hidden in Platform mode.";
  const manualCooldownRemainingSeconds = selectedDiagnostic ? cooldownRemainingSeconds(selectedDiagnostic.testedAt, 30) : 0;

  async function onAcknowledge(id: string) {
    if (!accessToken || !tenantId) return;
    await acknowledgePlatformAlert(accessToken, tenantId, id);
    await loadAll();
  }

  async function onResolve(id: string) {
    if (!accessToken || !tenantId) return;
    setResolveTarget(id);
    setResolveNotes("");
  }

  async function onSuppress(id: string) {
    if (!accessToken || !tenantId) return;
    await suppressPlatformAlert(accessToken, tenantId, id);
    await loadAll();
  }

  async function submitResolve() {
    if (!accessToken || !tenantId || !resolveTarget) return;
    const notes = resolveNotes.trim() || undefined;
    await resolvePlatformAlert(accessToken, tenantId, resolveTarget, notes);
    setResolveTarget(null);
    setResolveNotes("");
    await loadAll();
  }

  async function testSelectedComponent() {
    if (!accessToken || !selectedComponent || !canAct) return;
    const component = toDiagnosticComponent(selectedComponent);
    if (!component) return;
    setDiagnosticInFlight(selectedComponent);
    setDiagnosticError(null);
    try {
      const result = await testPlatformOperationComponent(accessToken, component);
      setDiagnosticResults((current) => ({ ...current, [selectedComponent]: result }));
      await loadAll();
    } catch (error) {
      setDiagnosticError((error as Error)?.message || "Diagnostic request failed");
    } finally {
      setDiagnosticInFlight(null);
    }
  }

  return (
    <div style={{ padding: 16, display: "grid", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div>
          <h2 style={{ margin: 0 }}>Platform Ops</h2>
          <p style={{ margin: "6px 0 0", color: "#666" }}>Alert center, incident lifecycle, anomaly dashboards, provider SLOs, and DLQ/runtime observability.</p>
        </div>
        <button onClick={() => void loadAll()}>Refresh</button>
      </div>

      {overviewError ? <ErrorBanner message={overviewError} /> : null}
      {loading ? <div>Loading platform observability...</div> : null}

      <section>
        <h3 style={{ marginBottom: 8 }}>Production Health Matrix</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
          {healthMatrix.map((component) => (
            <button
              key={component.component}
              type="button"
              onClick={() => setSelectedComponent(component.component)}
              style={{
                textAlign: "left",
                border: `1px solid ${statusTone(component.status).border}`,
                background: statusTone(component.status).background,
                color: statusTone(component.status).color,
                borderRadius: 10,
                padding: 12,
                cursor: "pointer",
                display: "grid",
                gap: 6,
                minHeight: 124,
                boxShadow: selectedComponent === component.component ? "0 0 0 2px rgba(80, 80, 80, 0.08)" : "none",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8 }}>
                <strong>{component.component}</strong>
                <span aria-hidden="true" style={{ fontWeight: 800, fontSize: 12 }}>{statusGlyph(component.status)}</span>
              </div>
              <div style={{ fontWeight: 700 }}>{component.status}</div>
              <div style={{ fontSize: 13, lineHeight: 1.4 }}>{component.reason}</div>
              <div style={{ fontSize: 12, opacity: 0.9 }}>Checked {fmtShortDate(component.lastCheckedAt)}</div>
            </button>
          ))}
        </div>
        {selectedHealth ? (
          <div style={{ marginTop: 12, border: "1px solid #ddd", borderRadius: 10, padding: 12, background: "#fff" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "baseline", flexWrap: "wrap" }}>
              <strong>{selectedHealth.component}</strong>
              <span style={{ fontWeight: 700 }}>{selectedHealth.status}</span>
            </div>
            <p style={{ margin: "6px 0 10px", color: "#444" }}>{selectedHealth.reason}</p>
            {selectedHealth.component === "Groq" ? (
              <DetailList
                items={[
                  ["Current passive status", selectedHealth.status],
                  ["Reason", selectedHealth.reason],
                  ["Configured", detailValue(selectedHealth.details, "configured")],
                  ["Enabled / fallback enabled", detailValue(selectedHealth.details, "fallbackEnabled") ?? detailValue(selectedHealth.details, "providerReady")],
                  ["Configured model", detailValue(selectedHealth.details, "model")],
                  ["Last observed Groq success", fmtDate(detailValue(selectedHealth.details, "lastSuccessAt") as string | null)],
                  ["Last observed Groq failure", fmtDate(detailValue(selectedHealth.details, "lastFailureAt") as string | null)],
                  ["Recent Groq usage count", detailValue(selectedHealth.details, "recentInvocationCount")],
                  ["Recent failure / error rate", formatPercent(detailValue(selectedHealth.details, "recentErrorRatePct"))],
                  [
                    "Last manual diagnostic",
                    selectedDiagnostic ? `${selectedDiagnostic.status} at ${fmtDate(selectedDiagnostic.testedAt)}` : "Never manually tested",
                  ],
                  ["Last diagnostic latency", fmtDurationMs(selectedDiagnostic?.latencyMs)],
                ]}
              />
            ) : null}
            {selectedHealth.component !== "Groq" ? (
              <DetailList
                items={[
                  ["Current passive status", selectedHealth.status],
                  ["Reason", selectedHealth.reason],
                  ["Last checked", fmtDate(selectedHealth.lastCheckedAt)],
                  ["Last success", fmtDate(selectedHealth.lastSuccessAt)],
                  ["Last failure", fmtDate(selectedHealth.lastFailureAt)],
                  ["Latency", fmtDurationMs(selectedHealth.latencyMs)],
                ]}
              />
            ) : null}
            {selectedDiagnostic ? (
              <div style={{ marginTop: 12, borderTop: "1px solid #eee", paddingTop: 12, display: "grid", gap: 8 }}>
                <strong>Latest Diagnostic</strong>
                <DetailList
                  items={[
                    ["Success", String(selectedDiagnostic.success)],
                    ["Status", selectedDiagnostic.status],
                    ["Message", selectedDiagnostic.message],
                    ["Tested at", fmtDate(selectedDiagnostic.testedAt)],
                    ["Latency", fmtDurationMs(selectedDiagnostic.latencyMs)],
                  ]}
                />
                {selectedDiagnostic.details && Object.keys(selectedDiagnostic.details).length > 0 ? (
                  <DetailList
                    items={Object.entries(selectedDiagnostic.details).map(([key, value]) => [key, formatValue(value)])}
                  />
                ) : null}
              </div>
            ) : null}
            <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
              <button
                type="button"
                disabled={!canAct || !selectedComponent || diagnosticInFlight === selectedComponent || manualCooldownRemainingSeconds > 0}
                onClick={() => void testSelectedComponent()}
              >
                {diagnosticInFlight === selectedComponent
                  ? "Testing..."
                  : selectedHealth.component === "Groq"
                    ? "Test Groq Now"
                    : "Test Now"}
              </button>
              {manualCooldownRemainingSeconds > 0 ? <div style={{ fontSize: 12, color: "#666" }}>Test again in {manualCooldownRemainingSeconds}s</div> : null}
              {diagnosticError ? <ErrorBanner message={diagnosticError} /> : null}
            </div>
            {selectedHealth.details && Object.keys(selectedHealth.details).length > 0 ? (
              <div style={{ marginTop: 12 }}>
                <strong style={{ display: "block", marginBottom: 8 }}>Details</strong>
                <DetailList
                  items={Object.entries(selectedHealth.details).map(([key, value]) => [key, formatValue(value)])}
                />
              </div>
            ) : null}
            <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
              {drilldownActionsForComponent(selectedHealth.component).map((action) => (
                <button key={action.label} type="button" onClick={() => navigate(action.path)}>
                  {action.label}
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      {sectionErrors.health ? <ErrorBanner message={sectionErrors.health} /> : null}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
        <Metric label="Overall Health" value={overallHealthValue} />
        <Metric label="Active Alerts" value={tenantScopedMode ? alerts.filter((a) => a.status === "OPEN" || a.status === "ACKNOWLEDGED").length : "—"} />
        <Metric label="Critical Alerts" value={tenantScopedMode ? criticalAlerts.length : "—"} />
        <Metric label="Alert Rules" value={tenantScopedMode ? rulesCount : "—"} />
        <Metric label="Queue Backlog" value={tenantScopedMode ? queues.reduce((sum, q) => sum + q.pending + q.retrying, 0) : "—"} />
        <Metric label="Dead Letters" value={tenantScopedMode ? deadLetter.length : "—"} />
      </div>

      <section>
        <h3>Release & Runtime</h3>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12 }}>
          <SimpleCard title="Release">
            <DetailList
              items={[
                ["Release tag", overview?.release.releaseTag ?? "Unavailable"],
                ["Git commit", overview?.release.gitCommit ?? "Unavailable"],
                ["Environment", overview?.release.environment ?? "Unavailable"],
                ["Build timestamp", fmtDate(overview?.release.buildTimestamp)],
                ["Deployment timestamp", fmtDate(overview?.release.deploymentTimestamp)],
                ["API version", overview?.release.apiVersion ?? "Unavailable"],
              ]}
            />
          </SimpleCard>
          <SimpleCard title="Runtime">
            <DetailList
              items={[
                ["Application status", overview?.runtime.applicationStatus ?? "Unavailable"],
                ["Application up", overview?.runtime.applicationUp == null ? "Unavailable" : String(overview.runtime.applicationUp)],
                ["API uptime", fmtDurationMs(overview?.runtime.uptimeMs)],
                ["Instance start", fmtDate(overview?.runtime.startTime)],
                ["Last checked", fmtDate(overview?.runtime.lastCheckedAt)],
              ]}
            />
          </SimpleCard>
        </div>
      </section>

      <section>
        <h3>Tenant Operational Detail</h3>
        {!tenantScopedMode ? <InfoBanner message={tenantScopeMessage} /> : null}
      </section>

      <section>
        <h3>Alert Rules</h3>
        {sectionErrors.rules ? <ErrorBanner message={sectionErrors.rules} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <Table
            columns={["Rule", "Source", "Enabled", "Severity", "Threshold", "Cooldown", "Auto Resolve"]}
            rows={alertRules.map((rule) => [
              rule.ruleKey,
              rule.sourceType,
              String(rule.enabled),
              rule.severity,
              `${rule.thresholdType} ${rule.thresholdValue}`,
              `${rule.cooldownMinutes}m`,
              String(rule.autoResolveEnabled),
            ])}
          />
        )}
      </section>

      <section>
        <h3>Active Alerts</h3>
        {sectionErrors.alerts ? <ErrorBanner message={sectionErrors.alerts} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <>
            <div style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
              <select value={severityFilter} onChange={(e) => setSeverityFilter(e.target.value as any)}>
                <option value="ALL">All Severities</option>
                <option value="CRITICAL">Critical</option>
                <option value="WARNING">Warning</option>
              </select>
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as any)}>
                <option value="ALL">All Statuses</option>
                <option value="OPEN">Open</option>
                <option value="ACKNOWLEDGED">Acknowledged</option>
                <option value="RESOLVED">Resolved</option>
                <option value="SUPPRESSED">Suppressed</option>
              </select>
            </div>
            <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
              <thead>
                <tr>{["Severity", "Rule", "Source", "Status", "Occurrences", "First Seen", "Last Seen", "Message", "Actions"].map((column) => <th key={column} style={{ textAlign: "left", padding: 6 }}>{column}</th>)}</tr>
              </thead>
              <tbody>
                {filteredAlerts.length === 0 ? (
                  <tr><td style={{ padding: 8 }} colSpan={9}>No alerts</td></tr>
                ) : filteredAlerts.map((alert) => (
                  <tr key={alert.id}>
                    <td style={{ padding: 6 }}><span style={{ fontWeight: 700, color: alert.severity === "CRITICAL" ? "#b00020" : "#9c6f00" }}>{alert.severity}</span></td>
                    <td style={{ padding: 6 }}>{alert.ruleKey || alert.alertType}</td>
                    <td style={{ padding: 6 }}>{alert.source}</td>
                    <td style={{ padding: 6 }}>{alert.status}</td>
                    <td style={{ padding: 6 }}>{alert.occurrenceCount}</td>
                    <td style={{ padding: 6 }}>{fmtDate(alert.firstSeenAt)}</td>
                    <td style={{ padding: 6 }}>{fmtDate(alert.lastSeenAt)}</td>
                    <td style={{ padding: 6 }}>{alert.message}</td>
                    <td style={{ padding: 6, display: "flex", gap: 6, flexWrap: "wrap" }}>
                      {canAct && alert.status === "OPEN" ? <button onClick={() => void onAcknowledge(alert.id)}>Acknowledge</button> : null}
                      {canAct && alert.status !== "RESOLVED" ? <button onClick={() => void onResolve(alert.id)}>Resolve</button> : null}
                      {canAct && alert.status !== "SUPPRESSED" ? <button onClick={() => void onSuppress(alert.id)}>Suppress</button> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </section>

      <section>
        <h3>Provider Health</h3>
        {sectionErrors.providers || sectionErrors.providerSlos ? <ErrorBanner message={sectionErrors.providers || sectionErrors.providerSlos || "Provider health unavailable"} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <div style={{ display: "grid", gap: 12 }}>
            <SimpleCard title="Provider Metrics">
              <Table
                columns={["Provider", "Status", "Configured", "Enabled", "Success", "Failures", "Timeouts", "Last Failure"]}
                rows={providers.map((provider) => [
                  provider.name,
                  provider.status,
                  String(provider.configured),
                  String(provider.enabled),
                  String(provider.successCount),
                  String(provider.failureCount),
                  String(provider.timeoutCount),
                  provider.lastFailure || "-",
                ])}
              />
            </SimpleCard>
            <SimpleCard title="Provider SLOs">
              <Table
                columns={["Provider", "Attempts", "Success%", "Timeout%", "Retry%", "Failures", "Degraded", "SLA Breach"]}
                rows={providerSlos.map((provider) => [
                  provider.provider,
                  String(provider.attempts),
                  provider.successRatePct.toFixed(1),
                  provider.timeoutRatePct.toFixed(1),
                  provider.retryRatePct.toFixed(1),
                  String(provider.failures),
                  String(provider.providerDegraded),
                  String(provider.deliverySlaBreached),
                ])}
              />
            </SimpleCard>
          </div>
        )}
      </section>

      <section>
        <h3>Queue Health</h3>
        {sectionErrors.queues ? <ErrorBanner message={sectionErrors.queues} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : <Table columns={["Queue", "Size", "Pending", "Retrying", "Failed", "Processing", "Stale", "Throttled", "Suppressed"]} rows={queues.map((queue) => [queue.queueName, String(queue.queueSize), String(queue.pending), String(queue.retrying), String(queue.failed), String(queue.processing), String(queue.stale), String(queue.throttled), String(queue.suppressed)])} />}
      </section>

      <section>
        <h3>Scheduler Health</h3>
        {sectionErrors.schedulers ? <ErrorBanner message={sectionErrors.schedulers} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : <Table columns={["Scheduler", "Enabled", "Last Run", "Failed", "Lock Skip#"]} rows={schedulers.map((scheduler) => [scheduler.schedulerName, String(scheduler.enabled), fmtDate(scheduler.lastRunAt), String(scheduler.failureCount), String(scheduler.lockSkipCount)])} />}
      </section>

      <section>
        <h3>AI Health</h3>
        {sectionErrors.ai ? <ErrorBanner message={sectionErrors.ai} /> : null}
        <SimpleCard title="AI Activity - Last 24 Hours">
          <DetailList
            items={[
              ["Total Calls", aiSummary?.totalCalls ?? ai?.totalCalls ?? 0],
              ["Successful Calls", aiSummary?.successfulCalls ?? 0],
              ["Failed Calls", aiSummary?.failedCalls ?? ai?.failedCalls ?? 0],
              ["Last Activity", fmtDate(aiSummary?.lastActivityAt)],
              ["Calls by Provider", summarizeCounts(aiSummary?.callsByProvider)],
              ["Calls by Status", summarizeCounts(aiSummary?.callsByStatus)],
            ]}
          />
          <div style={{ marginTop: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button type="button" onClick={() => navigate("/platform/ai-ops")}>Open AI Ops</button>
            <button type="button" onClick={() => navigate("/platform/ai-reasoning-console")}>Open AI Reasoning Console</button>
          </div>
        </SimpleCard>
      </section>

      <section>
        <h3>Webhook Health</h3>
        {sectionErrors.webhooks ? <ErrorBanner message={sectionErrors.webhooks} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <SimpleCard title="Webhook Metrics">
            <DetailList
              items={[
                ["Incoming", webhooks?.incomingWebhookCount ?? 0],
                ["Failures", webhooks?.failedWebhookProcessingCount ?? 0],
                ["Retries", webhooks?.retryProcessingCount ?? 0],
                ["Replay Attempts", webhooks?.replayAttemptCount ?? 0],
                ["Unknown Payloads", webhooks?.unknownProviderPayloadCount ?? 0],
              ]}
            />
          </SimpleCard>
        )}
      </section>

      <section>
        <h3>Resource Health</h3>
        {sectionErrors.runtime ? <ErrorBanner message={sectionErrors.runtime} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <SimpleCard title="Runtime Summary">
            <DetailList
              items={[
                ["Recent Failures", runtime?.recentFailures ?? 0],
                ["Retry Storm Signals", runtime?.retryStormSignals ?? 0],
                ["Repeated Provider Failures", runtime?.repeatedProviderFailures ?? 0],
                ["Stale Executions", runtime?.staleExecutions ?? 0],
              ]}
            />
          </SimpleCard>
        )}
      </section>

      <section>
        <h3>DLQ Monitoring</h3>
        {sectionErrors.deadLetter ? <ErrorBanner message={sectionErrors.deadLetter} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : (
          <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
            <thead>
              <tr>{["Source", "Execution", "Failure", "Retry#", "Status", "Dead Lettered", "Actions"].map((column) => <th key={column} style={{ textAlign: "left", padding: 6 }}>{column}</th>)}</tr>
            </thead>
            <tbody>
              {deadLetter.length === 0 ? (
                <tr><td style={{ padding: 8 }} colSpan={7}>No dead-letter records</td></tr>
              ) : deadLetter.map((row) => (
                <tr key={row.id}>
                  <td style={{ padding: 6 }}>{row.sourceType}</td>
                  <td style={{ padding: 6 }}>{row.sourceExecutionId}</td>
                  <td style={{ padding: 6 }}>{row.failureReason || "-"}</td>
                  <td style={{ padding: 6 }}>{row.retryCount}</td>
                  <td style={{ padding: 6 }}>{row.recoveryStatus}</td>
                  <td style={{ padding: 6 }}>{fmtDate(row.deadLetteredAt)}</td>
                  <td style={{ padding: 6 }}>
                    <button
                      disabled={!accessToken || !tenantId || !canAct}
                      onClick={async () => {
                        if (!accessToken || !tenantId) return;
                        try {
                          await replayPlatformDeadLetter(accessToken, tenantId, row.id);
                          await loadAll();
                        } catch (e) {
                          setSectionErrors((current) => ({ ...current, deadLetter: (e as Error).message || "Replay failed" }));
                        }
                      }}
                    >
                      Replay
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h3>Critical Alerts</h3>
        {sectionErrors.alerts ? <ErrorBanner message={sectionErrors.alerts} /> : null}
        {!tenantScopedMode ? <TenantScopedPlaceholder message={tenantScopedPlaceholderMessage} /> : <Table columns={["Type", "Source", "Status", "Last Seen"]} rows={criticalAlerts.map((alert) => [alert.ruleKey || alert.alertType, alert.source, alert.status, fmtDate(alert.lastSeenAt)])} />}
      </section>

      <TextEntryDialog
        open={Boolean(resolveTarget)}
        title="Resolve alert"
        description="Add optional resolution notes before marking the alert resolved."
        label="Resolution notes"
        placeholder="Add resolution notes"
        value={resolveNotes}
        required={false}
        multiline
        confirmLabel="Resolve"
        submittingLabel="Resolving..."
        onCancel={() => setResolveTarget(null)}
        onSubmit={submitResolve}
      />
    </div>
  );
}

function fallbackHealthMatrix(): PlatformOperationsComponentHealth[] {
  return [
    component("API", "UNKNOWN", "Telemetry not available"),
    component("DB", "UNKNOWN", "Telemetry not available"),
    component("Keycloak", "UNKNOWN", "Telemetry not available"),
    component("Redis", "UNKNOWN", "Telemetry not available"),
    component("MinIO", "UNKNOWN", "Telemetry not available"),
    component("Gemini", "UNKNOWN", "Telemetry not available"),
    component("Groq", "UNKNOWN", "Telemetry not available"),
    component("Document AI", "UNKNOWN", "Telemetry not available"),
    component("Scheduler", "UNKNOWN", "Telemetry not available"),
    component("Backups", "UNKNOWN", "Structured backup telemetry not available"),
    component("Release", "UNKNOWN", "Telemetry not available"),
  ];
}

function toDiagnosticComponent(component: string): PlatformOperationsDiagnosticComponent | null {
  switch (component) {
    case "API":
      return "api";
    case "DB":
      return "database";
    case "Redis":
      return "redis";
    case "Keycloak":
      return "keycloak";
    case "MinIO":
      return "minio";
    case "Gemini":
      return "gemini";
    case "Groq":
      return "groq";
    case "Document AI":
      return "document-ai";
    case "Scheduler":
      return "scheduler";
    case "Backups":
      return "backups";
    default:
      return null;
  }
}

function drilldownActionsForComponent(component: string): Array<{ label: string; path: string }> {
  switch (component) {
    case "Gemini":
    case "Groq":
    case "Document AI":
      return [{ label: "Open AI Ops", path: "/platform/ai-ops" }];
    default:
      return [];
  }
}

function cooldownRemainingSeconds(testedAt: string | null | undefined, cooldownSeconds: number) {
  if (!testedAt) return 0;
  const tested = new Date(testedAt).getTime();
  if (Number.isNaN(tested)) return 0;
  const elapsed = Math.floor((Date.now() - tested) / 1000);
  return Math.max(0, cooldownSeconds - elapsed);
}

function detailValue(details: Record<string, unknown> | undefined, key: string) {
  return details && Object.prototype.hasOwnProperty.call(details, key) ? details[key] : null;
}

function formatPercent(value: unknown) {
  if (value == null || value === "") return "-";
  if (typeof value === "number") return `${value}%`;
  const numeric = Number(value);
  return Number.isFinite(numeric) ? `${numeric}%` : formatValue(value);
}

function component(component: string, status: PlatformOperationsComponentHealth["status"], reason: string): PlatformOperationsComponentHealth {
  const now = new Date().toISOString();
  return {
    component,
    status,
    reason,
    lastCheckedAt: now,
    lastSuccessAt: null,
    lastFailureAt: null,
    latencyMs: null,
    details: {},
  };
}

function DetailList({ items }: { items: Array<[string, unknown]> }) {
  return (
    <div style={{ display: "grid", gap: 6 }}>
      {items.map(([label, value]) => (
        <div key={label} style={{ display: "grid", gridTemplateColumns: "180px 1fr", gap: 8 }}>
          <div style={{ color: "#666", fontSize: 12 }}>{label}</div>
          <div style={{ fontSize: 13, fontWeight: 600 }}>{formatValue(value)}</div>
        </div>
      ))}
    </div>
  );
}

function summarizeCounts(value: Record<string, number> | undefined) {
  if (!value) return "-";
  const entries = Object.entries(value)
    .filter(([, count]) => typeof count === "number" && Number.isFinite(count) && count > 0)
    .sort((left, right) => right[1] - left[1])
    .map(([key, count]) => `${key}:${count}`);
  return entries.length === 0 ? "-" : entries.join(", ");
}

function formatValue(value: unknown) {
  if (value == null || value === "") return "-";
  if (typeof value === "boolean") return String(value);
  if (typeof value === "number") return String(value);
  if (typeof value === "object") {
    if (value instanceof Date) return value.toLocaleString();
    return JSON.stringify(value);
  }
  const text = String(value);
  if (text === "Invalid Date") return "-";
  if (/\d{4}-\d{2}-\d{2}T/.test(text)) {
    const parsed = new Date(text);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.toLocaleString();
    }
  }
  return text;
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10, background: "#fff" }}>
      <div style={{ color: "#666", fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 600 }}>{value}</div>
    </div>
  );
}

function Table({ columns, rows }: { columns: string[]; rows: string[][] }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
      <thead>
        <tr>{columns.map((column) => <th key={column} style={{ textAlign: "left", padding: 6 }}>{column}</th>)}</tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr><td style={{ padding: 8 }} colSpan={columns.length}>No records</td></tr>
        ) : rows.map((row, index) => <tr key={index}>{row.map((value, valueIndex) => <td key={valueIndex} style={{ padding: 6 }}>{value}</td>)}</tr>)}
      </tbody>
    </table>
  );
}

function SimpleCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10, background: "#fff" }}>
      <h4 style={{ marginTop: 0 }}>{title}</h4>
      <div style={{ display: "grid", gap: 4 }}>{children}</div>
    </div>
  );
}

function ErrorBanner({ message }: { message: string }) {
  return <div style={{ color: "#9d1c1c", border: "1px solid #efb4b4", background: "#fff2f2", padding: 10, borderRadius: 8 }}>{message}</div>;
}

function InfoBanner({ message }: { message: string }) {
  return <div style={{ color: "#8a5b00", border: "1px solid #f0d38a", background: "#fff9ea", padding: 10, borderRadius: 8 }}>{message}</div>;
}

function TenantScopedPlaceholder({ message }: { message: string }) {
  return <InfoBanner message={message} />;
}
