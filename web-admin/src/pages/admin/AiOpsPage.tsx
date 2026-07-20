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
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Button,
  TextField,
  Stack,
} from "@mui/material";

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
  const [createPromptOpen, setCreatePromptOpen] = useState(false);
  const [createPromptKey, setCreatePromptKey] = useState("");
  const [createPromptName, setCreatePromptName] = useState("");
  const [createPromptError, setCreatePromptError] = useState<string | null>(null);
  const [createVersionOpen, setCreateVersionOpen] = useState(false);
  const [createVersionTarget, setCreateVersionTarget] = useState<string | null>(null);
  const [systemPrompt, setSystemPrompt] = useState("You are a careful medical assistant.");
  const [userPromptTemplate, setUserPromptTemplate] = useState("Summarize {{inputVariablesJson}}");
  const [createVersionError, setCreateVersionError] = useState<string | null>(null);

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
    setCreatePromptOpen(true);
    setCreatePromptKey("");
    setCreatePromptName("");
    setCreatePromptError(null);
  }

  async function onCreateVersion(promptId: string) {
    setCreateVersionTarget(promptId);
    setCreateVersionOpen(true);
    setSystemPrompt("You are a careful medical assistant.");
    setUserPromptTemplate("Summarize {{inputVariablesJson}}");
    setCreateVersionError(null);
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

  async function submitCreatePrompt() {
    if (!accessToken || !tenantId) return;
    const promptKey = createPromptKey.trim();
    const name = createPromptName.trim() || promptKey;
    if (!promptKey) {
      setCreatePromptError("Prompt key is required.");
      return;
    }
    try {
      await createAiOpsPrompt(accessToken, tenantId, { promptKey, name });
      setCreatePromptOpen(false);
      await loadAll();
    } catch (e) {
      setCreatePromptError(e instanceof Error ? e.message : "Failed to create prompt");
    }
  }

  async function submitCreateVersion() {
    if (!accessToken || !tenantId || !createVersionTarget) return;
    if (!systemPrompt.trim() || !userPromptTemplate.trim()) {
      setCreateVersionError("System prompt and user prompt template are required.");
      return;
    }
    try {
      await createAiOpsPromptVersion(accessToken, tenantId, createVersionTarget, {
        systemPrompt: systemPrompt.trim(),
        userPromptTemplate: userPromptTemplate.trim(),
      });
      const detail = await getAiOpsPrompt(accessToken, tenantId, createVersionTarget);
      setSelectedPrompt(detail);
      setCreateVersionOpen(false);
      setCreateVersionTarget(null);
      await loadAll();
    } catch (e) {
      setCreateVersionError(e instanceof Error ? e.message : "Failed to create prompt version");
    }
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

      <Dialog open={createPromptOpen} onClose={() => setCreatePromptOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Create prompt</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              autoFocus
              label="Prompt key"
              value={createPromptKey}
              onChange={(e) => setCreatePromptKey(e.target.value)}
              error={Boolean(createPromptError) && !createPromptKey.trim()}
              helperText={createPromptError || "Unique identifier used by the prompt registry."}
            />
            <TextField
              label="Prompt name"
              value={createPromptName}
              onChange={(e) => setCreatePromptName(e.target.value)}
              helperText="Optional display name. Defaults to the prompt key."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreatePromptOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitCreatePrompt()} disabled={!createPromptKey.trim()}>Create</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={createVersionOpen} onClose={() => setCreateVersionOpen(false)} fullWidth maxWidth="md">
        <DialogTitle>Create prompt version</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              autoFocus
              label="System prompt"
              multiline
              minRows={5}
              value={systemPrompt}
              onChange={(e) => setSystemPrompt(e.target.value)}
              error={Boolean(createVersionError) && !systemPrompt.trim()}
              helperText={createVersionError || "Required system prompt sent to the model."}
            />
            <TextField
              label="User prompt template"
              multiline
              minRows={4}
              value={userPromptTemplate}
              onChange={(e) => setUserPromptTemplate(e.target.value)}
              helperText="Required template with input variables."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateVersionOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void submitCreateVersion()} disabled={!systemPrompt.trim() || !userPromptTemplate.trim()}>Create Version</Button>
        </DialogActions>
      </Dialog>
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
