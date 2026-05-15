import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  acknowledgePlatformAlert,
  getPlatformAiMetrics,
  getPlatformAlertRules,
  getPlatformAlerts,
  getPlatformDeadLetter,
  getPlatformHealth,
  getPlatformProviderSlos,
  getPlatformProviders,
  getPlatformQueues,
  getPlatformRuntimeSummary,
  getPlatformSchedulers,
  getPlatformWebhooks,
  replayPlatformDeadLetter,
  resolvePlatformAlert,
  suppressPlatformAlert,
  type DeadLetterRow,
  type PlatformAiMetrics,
  type PlatformHealthResponse,
  type PlatformOpsAlert,
  type PlatformProviderMetrics,
  type PlatformProviderSlo,
  type PlatformQueueMetrics,
  type PlatformRuntimeSummary,
  type PlatformSchedulerStatus,
  type PlatformWebhookMetrics,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

function fmtDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

export default function PlatformOpsPage() {
  const { accessToken, tenantId, rolesUpper } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<PlatformHealthResponse | null>(null);
  const [schedulers, setSchedulers] = useState<PlatformSchedulerStatus[]>([]);
  const [queues, setQueues] = useState<PlatformQueueMetrics[]>([]);
  const [providers, setProviders] = useState<PlatformProviderMetrics[]>([]);
  const [providerSlos, setProviderSlos] = useState<PlatformProviderSlo[]>([]);
  const [ai, setAi] = useState<PlatformAiMetrics | null>(null);
  const [webhooks, setWebhooks] = useState<PlatformWebhookMetrics | null>(null);
  const [alerts, setAlerts] = useState<PlatformOpsAlert[]>([]);
  const [runtime, setRuntime] = useState<PlatformRuntimeSummary | null>(null);
  const [deadLetter, setDeadLetter] = useState<DeadLetterRow[]>([]);
  const [severityFilter, setSeverityFilter] = useState<"ALL" | "CRITICAL" | "WARNING">("ALL");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "OPEN" | "ACKNOWLEDGED" | "RESOLVED" | "SUPPRESSED">("ALL");
  const [rulesCount, setRulesCount] = useState(0);

  const canAct = rolesUpper.includes("PLATFORM_ADMIN") || rolesUpper.includes("CLINIC_ADMIN");

  async function loadAll() {
    if (!accessToken || !tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [healthRes, schedulersRes, queuesRes, providersRes, providerSloRes, aiRes, webhookRes, alertsRes, runtimeRes, deadLetterRes, rulesRes] = await Promise.all([
        getPlatformHealth(accessToken, tenantId),
        getPlatformSchedulers(accessToken, tenantId),
        getPlatformQueues(accessToken, tenantId),
        getPlatformProviders(accessToken, tenantId),
        getPlatformProviderSlos(accessToken, tenantId),
        getPlatformAiMetrics(accessToken, tenantId),
        getPlatformWebhooks(accessToken, tenantId),
        getPlatformAlerts(accessToken, tenantId),
        getPlatformRuntimeSummary(accessToken, tenantId),
        getPlatformDeadLetter(accessToken, tenantId),
        getPlatformAlertRules(accessToken, tenantId),
      ]);
      setHealth(healthRes);
      setSchedulers(schedulersRes.schedulers);
      setQueues(queuesRes.queues);
      setProviders(providersRes.providers);
      setProviderSlos(providerSloRes.providers);
      setAi(aiRes);
      setWebhooks(webhookRes);
      setAlerts(alertsRes.alerts);
      setRuntime(runtimeRes);
      setDeadLetter(deadLetterRes.items);
      setRulesCount(rulesRes.rules.length);
    } catch (e) {
      setError((e as Error).message || "Failed to load platform ops");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAll();
  }, [accessToken, tenantId]);

  const filteredAlerts = useMemo(() => alerts.filter((a) => (severityFilter === "ALL" || a.severity === severityFilter) && (statusFilter === "ALL" || a.status === statusFilter)), [alerts, severityFilter, statusFilter]);
  const criticalAlerts = useMemo(() => alerts.filter((a) => a.severity === "CRITICAL" && (a.status === "OPEN" || a.status === "ACKNOWLEDGED")), [alerts]);

  async function onAcknowledge(id: string) {
    if (!accessToken || !tenantId) return;
    await acknowledgePlatformAlert(accessToken, tenantId, id);
    await loadAll();
  }

  async function onResolve(id: string) {
    if (!accessToken || !tenantId) return;
    const notes = window.prompt("Resolution notes") || undefined;
    await resolvePlatformAlert(accessToken, tenantId, id, notes);
    await loadAll();
  }

  async function onSuppress(id: string) {
    if (!accessToken || !tenantId) return;
    await suppressPlatformAlert(accessToken, tenantId, id);
    await loadAll();
  }

  return (
    <div style={{ padding: 16, display: "grid", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h2 style={{ margin: 0 }}>Platform Ops</h2>
          <p style={{ margin: "6px 0 0", color: "#666" }}>Alert center, incident lifecycle, anomaly dashboards, provider SLOs, and DLQ/runtime observability.</p>
        </div>
        <button onClick={() => void loadAll()}>Refresh</button>
      </div>

      {error ? <div style={{ color: "#b00020" }}>{error}</div> : null}
      {loading ? <div>Loading platform observability...</div> : null}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
        <Metric label="Overall Health" value={health?.overallStatus ?? "-"} />
        <Metric label="Active Alerts" value={alerts.filter((a) => a.status === "OPEN" || a.status === "ACKNOWLEDGED").length} />
        <Metric label="Critical Alerts" value={criticalAlerts.length} />
        <Metric label="Alert Rules" value={rulesCount} />
        <Metric label="Queue Backlog" value={queues.reduce((sum, q) => sum + q.pending + q.retrying, 0)} />
        <Metric label="Dead Letters" value={deadLetter.length} />
      </div>

      <section>
        <h3>Active Alerts</h3>
        <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
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
            <tr>{["Severity", "Rule", "Source", "Status", "Occurrences", "First Seen", "Last Seen", "Message", "Actions"].map((c) => <th key={c} style={{ textAlign: "left", padding: 6 }}>{c}</th>)}</tr>
          </thead>
          <tbody>
            {filteredAlerts.length === 0 ? <tr><td style={{ padding: 8 }} colSpan={9}>No alerts</td></tr> : filteredAlerts.map((a) => (
              <tr key={a.id}>
                <td style={{ padding: 6 }}><span style={{ fontWeight: 700, color: a.severity === "CRITICAL" ? "#b00020" : "#9c6f00" }}>{a.severity}</span></td>
                <td style={{ padding: 6 }}>{a.ruleKey || a.alertType}</td>
                <td style={{ padding: 6 }}>{a.source}</td>
                <td style={{ padding: 6 }}>{a.status}</td>
                <td style={{ padding: 6 }}>{a.occurrenceCount}</td>
                <td style={{ padding: 6 }}>{fmtDate(a.firstSeenAt)}</td>
                <td style={{ padding: 6 }}>{fmtDate(a.lastSeenAt)}</td>
                <td style={{ padding: 6 }}>{a.message}</td>
                <td style={{ padding: 6, display: "flex", gap: 6 }}>
                  {canAct && a.status === "OPEN" ? <button onClick={() => void onAcknowledge(a.id)}>Acknowledge</button> : null}
                  {canAct && a.status !== "RESOLVED" ? <button onClick={() => void onResolve(a.id)}>Resolve</button> : null}
                  {canAct && a.status !== "SUPPRESSED" ? <button onClick={() => void onSuppress(a.id)}>Suppress</button> : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section><h3>Provider Health</h3><Table columns={["Provider", "Attempts", "Success%", "Timeout%", "Retry%", "Failures", "Degraded", "SLA Breach"]} rows={providerSlos.map((p) => [p.provider, String(p.attempts), p.successRatePct.toFixed(1), p.timeoutRatePct.toFixed(1), p.retryRatePct.toFixed(1), String(p.failures), String(p.providerDegraded), String(p.deliverySlaBreached)])} /></section>
      <section><h3>Queue Health</h3><Table columns={["Queue", "Size", "Pending", "Retrying", "Failed", "Processing", "Stale", "Throttled", "Suppressed"]} rows={queues.map((q) => [q.queueName, String(q.queueSize), String(q.pending), String(q.retrying), String(q.failed), String(q.processing), String(q.stale), String(q.throttled), String(q.suppressed)])} /></section>
      <section><h3>Scheduler Health</h3><Table columns={["Scheduler", "Enabled", "Last Run", "Failed", "Lock Skip#"]} rows={schedulers.map((s) => [s.schedulerName, String(s.enabled), fmtDate(s.lastRunAt), String(s.failureCount), String(s.lockSkipCount)])} /></section>
      <section><h3>AI Health</h3><SimpleCard title="AI Metrics"><div>Total Calls: {ai?.totalCalls ?? 0}</div><div>Failed Calls: {ai?.failedCalls ?? 0}</div><div>Estimated Cost: {ai?.estimatedCost ?? 0}</div></SimpleCard></section>
      <section><h3>Webhook Health</h3><SimpleCard title="Webhook Metrics"><div>Incoming: {webhooks?.incomingWebhookCount ?? 0}</div><div>Failures: {webhooks?.failedWebhookProcessingCount ?? 0}</div><div>Retries: {webhooks?.retryProcessingCount ?? 0}</div><div>Replay Attempts: {webhooks?.replayAttemptCount ?? 0}</div><div>Unknown Payloads: {webhooks?.unknownProviderPayloadCount ?? 0}</div></SimpleCard></section>
      <section><h3>Resource Health</h3><SimpleCard title="Runtime Summary"><div>Recent Failures: {runtime?.recentFailures ?? 0}</div><div>Retry Storm Signals: {runtime?.retryStormSignals ?? 0}</div><div>Stale Executions: {runtime?.staleExecutions ?? 0}</div></SimpleCard></section>

      <section>
        <h3>DLQ Monitoring</h3>
        <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
          <thead><tr>{["Source", "Execution", "Failure", "Retry#", "Status", "Dead Lettered", "Actions"].map((c) => (<th key={c} style={{ textAlign: "left", padding: 6 }}>{c}</th>))}</tr></thead>
          <tbody>
            {deadLetter.length === 0 ? <tr><td style={{ padding: 8 }} colSpan={7}>No dead-letter records</td></tr> : deadLetter.map((row) => (
              <tr key={row.id}><td style={{ padding: 6 }}>{row.sourceType}</td><td style={{ padding: 6 }}>{row.sourceExecutionId}</td><td style={{ padding: 6 }}>{row.failureReason || "-"}</td><td style={{ padding: 6 }}>{row.retryCount}</td><td style={{ padding: 6 }}>{row.recoveryStatus}</td><td style={{ padding: 6 }}>{fmtDate(row.deadLetteredAt)}</td><td style={{ padding: 6 }}><button disabled={!accessToken || !tenantId || !canAct} onClick={async () => { if (!accessToken || !tenantId) return; try { await replayPlatformDeadLetter(accessToken, tenantId, row.id); await loadAll(); } catch (e) { setError((e as Error).message || "Replay failed"); } }}>Replay</button></td></tr>
            ))}
          </tbody>
        </table>
      </section>

      <section><h3>Critical Alerts</h3><Table columns={["Type", "Source", "Status", "Last Seen"]} rows={criticalAlerts.map((a) => [a.ruleKey || a.alertType, a.source, a.status, fmtDate(a.lastSeenAt)])} /></section>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}><div style={{ color: "#666", fontSize: 12 }}>{label}</div><div style={{ fontSize: 22, fontWeight: 600 }}>{value}</div></div>;
}

function Table({ columns, rows }: { columns: string[]; rows: string[][] }) {
  return <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}><thead><tr>{columns.map((c) => <th key={c} style={{ textAlign: "left", padding: 6 }}>{c}</th>)}</tr></thead><tbody>{rows.length === 0 ? <tr><td style={{ padding: 8 }} colSpan={columns.length}>No records</td></tr> : rows.map((r, idx) => <tr key={idx}>{r.map((v, i) => <td key={i} style={{ padding: 6 }}>{v}</td>)}</tr>)}</tbody></table>;
}

function SimpleCard({ title, children }: { title: string; children: ReactNode }) {
  return <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}><h4 style={{ marginTop: 0 }}>{title}</h4><div style={{ display: "grid", gap: 4 }}>{children}</div></div>;
}
