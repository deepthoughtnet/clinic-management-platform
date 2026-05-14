import { useEffect, useMemo, useState } from "react";
import {
  activateAiOpsPromptVersion,
  archiveAiOpsPromptVersion,
  createAiOpsPrompt,
  createAiOpsPromptVersion,
  getAiOpsPrompt,
  getAiOpsUsageSummary,
  listAiOpsGuardrails,
  listAiOpsInvocations,
  listAiOpsPrompts,
  listAiOpsTools,
  listAiOpsWorkflowRuns,
  type AiOpsGuardrail,
  type AiOpsInvocationLog,
  type AiOpsPromptDefinition,
  type AiOpsPromptDetail,
  type AiOpsTool,
  type AiOpsUsageSummary,
  type AiOpsWorkflowRun,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

function fmtDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

export default function AiOpsPage() {
  const { accessToken, tenantId } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [usage, setUsage] = useState<AiOpsUsageSummary | null>(null);
  const [prompts, setPrompts] = useState<AiOpsPromptDefinition[]>([]);
  const [selectedPrompt, setSelectedPrompt] = useState<AiOpsPromptDetail | null>(null);
  const [invocations, setInvocations] = useState<AiOpsInvocationLog[]>([]);
  const [tools, setTools] = useState<AiOpsTool[]>([]);
  const [guardrails, setGuardrails] = useState<AiOpsGuardrail[]>([]);
  const [workflowRuns, setWorkflowRuns] = useState<AiOpsWorkflowRun[]>([]);

  async function loadAll() {
    if (!accessToken || !tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [usageRes, promptRes, invocationRes, toolRes, guardrailRes, workflowRes] = await Promise.all([
        getAiOpsUsageSummary(accessToken, tenantId),
        listAiOpsPrompts(accessToken, tenantId),
        listAiOpsInvocations(accessToken, tenantId),
        listAiOpsTools(accessToken, tenantId),
        listAiOpsGuardrails(accessToken, tenantId),
        listAiOpsWorkflowRuns(accessToken, tenantId),
      ]);
      setUsage(usageRes);
      setPrompts(promptRes);
      setInvocations(invocationRes);
      setTools(toolRes);
      setGuardrails(guardrailRes);
      setWorkflowRuns(workflowRes);
      if (promptRes.length > 0) {
        const detail = await getAiOpsPrompt(accessToken, tenantId, promptRes[0].id);
        setSelectedPrompt(detail);
      } else {
        setSelectedPrompt(null);
      }
    } catch (e) {
      setError((e as Error).message || "Failed to load AI Ops data");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadAll();
  }, [accessToken, tenantId]);

  const topInvocations = useMemo(() => invocations.slice(0, 20), [invocations]);

  async function onCreatePrompt() {
    if (!accessToken || !tenantId) return;
    const promptKey = window.prompt("Prompt key");
    if (!promptKey) return;
    const name = window.prompt("Prompt name") || promptKey;
    await createAiOpsPrompt(accessToken, tenantId, { promptKey, name });
    await loadAll();
  }

  async function onCreateVersion(promptId: string) {
    if (!accessToken || !tenantId) return;
    const systemPrompt = window.prompt("System prompt", "You are a careful medical assistant.");
    const userPromptTemplate = window.prompt("User prompt template", "Summarize {{inputVariablesJson}}");
    if (!systemPrompt || !userPromptTemplate) return;
    await createAiOpsPromptVersion(accessToken, tenantId, promptId, { systemPrompt, userPromptTemplate });
    const detail = await getAiOpsPrompt(accessToken, tenantId, promptId);
    setSelectedPrompt(detail);
    await loadAll();
  }

  async function onActivate(promptId: string, versionId: string) {
    if (!accessToken || !tenantId) return;
    await activateAiOpsPromptVersion(accessToken, tenantId, promptId, versionId);
    const detail = await getAiOpsPrompt(accessToken, tenantId, promptId);
    setSelectedPrompt(detail);
    await loadAll();
  }

  async function onArchive(promptId: string, versionId: string) {
    if (!accessToken || !tenantId) return;
    await archiveAiOpsPromptVersion(accessToken, tenantId, promptId, versionId);
    const detail = await getAiOpsPrompt(accessToken, tenantId, promptId);
    setSelectedPrompt(detail);
    await loadAll();
  }

  return (
    <div style={{ padding: 16, display: "grid", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h2 style={{ margin: 0 }}>AI Ops</h2>
          <p style={{ margin: "6px 0 0", color: "#666" }}>Prompt registry, usage, invocation logs, tools, guardrails, and workflow telemetry.</p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button onClick={() => void onCreatePrompt()}>New Prompt</button>
          <button onClick={() => void loadAll()}>Refresh</button>
        </div>
      </div>

      {error ? <div style={{ color: "#b00020" }}>{error}</div> : null}
      {loading ? <div>Loading AI Ops…</div> : null}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
        <Metric label="Total Calls" value={usage?.totalCalls ?? 0} />
        <Metric label="Successful" value={usage?.successfulCalls ?? 0} />
        <Metric label="Failed" value={usage?.failedCalls ?? 0} />
        <Metric label="Input Tokens" value={usage?.inputTokens ?? 0} />
        <Metric label="Output Tokens" value={usage?.outputTokens ?? 0} />
        <Metric label="Est. Cost" value={usage ? usage.estimatedCost.toFixed(4) : "0.0000"} />
      </div>

      <section>
        <h3>Prompt Registry</h3>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 12 }}>
          <div style={{ border: "1px solid #ddd", borderRadius: 8, overflow: "hidden" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={{ textAlign: "left", padding: 8 }}>Key</th><th style={{ textAlign: "left", padding: 8 }}>Active</th></tr></thead>
              <tbody>
                {prompts.map((p) => (
                  <tr key={p.id} style={{ cursor: "pointer" }} onClick={async () => accessToken && tenantId && setSelectedPrompt(await getAiOpsPrompt(accessToken, tenantId, p.id))}>
                    <td style={{ padding: 8 }}>{p.promptKey}</td>
                    <td style={{ padding: 8 }}>{p.activeVersion ?? "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}>
            {selectedPrompt ? (
              <>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <strong>{selectedPrompt.definition.name}</strong>
                  <button onClick={() => void onCreateVersion(selectedPrompt.definition.id)}>Add Version</button>
                </div>
                <div style={{ marginTop: 8, fontSize: 13, color: "#666" }}>{selectedPrompt.definition.description || "No description"}</div>
                <table style={{ width: "100%", marginTop: 10, borderCollapse: "collapse" }}>
                  <thead><tr><th style={{ textAlign: "left", padding: 6 }}>Version</th><th style={{ textAlign: "left", padding: 6 }}>Status</th><th style={{ textAlign: "left", padding: 6 }}>Model</th><th style={{ textAlign: "left", padding: 6 }}>Actions</th></tr></thead>
                  <tbody>
                    {selectedPrompt.versions.map((v) => (
                      <tr key={v.id}>
                        <td style={{ padding: 6 }}>v{v.version}</td>
                        <td style={{ padding: 6 }}>{v.status}</td>
                        <td style={{ padding: 6 }}>{v.modelHint || "-"}</td>
                        <td style={{ padding: 6, display: "flex", gap: 6 }}>
                          {v.status !== "ACTIVE" ? <button onClick={() => void onActivate(selectedPrompt.definition.id, v.id)}>Activate</button> : null}
                          {v.status !== "ARCHIVED" ? <button onClick={() => void onArchive(selectedPrompt.definition.id, v.id)}>Archive</button> : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            ) : <div>Select a prompt</div>}
          </div>
        </div>
      </section>

      <section>
        <h3>Invocation Logs (Recent)</h3>
        <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
          <thead><tr><th style={{ padding: 6, textAlign: "left" }}>Time</th><th style={{ padding: 6, textAlign: "left" }}>Use Case</th><th style={{ padding: 6, textAlign: "left" }}>Provider</th><th style={{ padding: 6, textAlign: "left" }}>Status</th><th style={{ padding: 6, textAlign: "left" }}>Latency</th></tr></thead>
          <tbody>
            {topInvocations.map((row) => (
              <tr key={row.id}>
                <td style={{ padding: 6 }}>{fmtDate(row.createdAt)}</td>
                <td style={{ padding: 6 }}>{row.useCase || row.domain || "-"}</td>
                <td style={{ padding: 6 }}>{row.providerName || "-"}</td>
                <td style={{ padding: 6 }}>{row.status}</td>
                <td style={{ padding: 6 }}>{row.latencyMs ?? "-"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 12 }}>
        <SimpleList title="Tool Registry" items={tools.map((t) => `${t.toolKey} (${t.enabled ? "ENABLED" : "DISABLED"})`)} />
        <SimpleList title="Guardrails" items={guardrails.map((g) => `${g.profileKey} (maxTokens=${g.maxOutputTokens ?? "-"})`)} />
        <SimpleList title="Workflow Runs" items={workflowRuns.map((w) => `${w.workflowKey} - ${w.status} (${fmtDate(w.startedAt)})`)} />
      </section>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string | number }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}>
      <div style={{ color: "#666", fontSize: 12 }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 600 }}>{value}</div>
    </div>
  );
}

function SimpleList({ title, items }: { title: string; items: string[] }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}>
      <h4 style={{ marginTop: 0 }}>{title}</h4>
      {items.length === 0 ? <div style={{ color: "#666" }}>No records</div> : (
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {items.slice(0, 10).map((item, idx) => <li key={idx}>{item}</li>)}
        </ul>
      )}
    </div>
  );
}
