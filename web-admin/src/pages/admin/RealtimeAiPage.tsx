import { useEffect, useMemo, useState } from "react";
import {
  completeRealtimeVoiceSession,
  createRealtimeVoiceSession,
  getRealtimeVoiceSessionEvents,
  getRealtimeVoiceSessionTranscripts,
  getRealtimeVoiceSummary,
  listRealtimeVoiceSessions,
  sendRealtimeVoiceTurn,
  sendReceptionistTestMessage,
  type RealtimeVoiceEvent,
  type RealtimeVoiceSession,
  type RealtimeVoiceSessionType,
  type RealtimeVoiceSummary,
  type RealtimeVoiceTranscript,
} from "../../api/clinicApi";
import { useAuth } from "../../auth/useAuth";

function fmtDate(value: string | null | undefined) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
}

function parseReceptionistMetadata(metadataJson: string | null | undefined): {
  workflowState?: string;
  intent?: string;
  summary?: string;
  leadCreated?: boolean;
  leadId?: string;
  appointmentRequestCreated?: boolean;
  escalationRequired?: boolean;
  escalationReason?: string;
} {
  if (!metadataJson) return {};
  try {
    const parsed = JSON.parse(metadataJson);
    const receptionist = parsed?.receptionist ?? {};
    const outcome = receptionist?.outcome ?? {};
    return {
      workflowState: receptionist?.state ?? parsed?.workflowState,
      intent: receptionist?.intent ?? parsed?.intent,
      summary: receptionist?.summary,
      leadCreated: Boolean(outcome?.leadCreated),
      leadId: outcome?.leadId,
      appointmentRequestCreated: Boolean(outcome?.appointmentRequestCreated),
      escalationRequired: Boolean(outcome?.escalationRequired),
      escalationReason: outcome?.escalationReason,
    };
  } catch {
    return {};
  }
}

const DEFAULT_PROMPT_KEY_BY_TYPE: Record<RealtimeVoiceSessionType, string> = {
  AI_RECEPTIONIST: "realtime.voice.ai-receptionist.v1",
  APPOINTMENT_BOOKING: "realtime.voice.appointment-booking.v1",
  FOLLOW_UP_CALL: "realtime.voice.follow-up-reminder.v1",
  LEAD_QUALIFICATION: "realtime.voice.lead-qualification.v1",
  FAQ_ASSISTANT: "realtime.voice.clinic-faq.v1",
  MANUAL_TRANSFER: "realtime.voice.escalation-detection.v1",
};

export default function RealtimeAiPage() {
  const { accessToken, tenantId, rolesUpper } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [summary, setSummary] = useState<RealtimeVoiceSummary | null>(null);
  const [sessions, setSessions] = useState<RealtimeVoiceSession[]>([]);
  const [selectedSession, setSelectedSession] = useState<RealtimeVoiceSession | null>(null);
  const [events, setEvents] = useState<RealtimeVoiceEvent[]>([]);
  const [transcripts, setTranscripts] = useState<RealtimeVoiceTranscript[]>([]);
  const [sessionType, setSessionType] = useState<RealtimeVoiceSessionType>("AI_RECEPTIONIST");
  const [userText, setUserText] = useState("");

  const canManage = rolesUpper.some((role: string) => ["PLATFORM_ADMIN", "CLINIC_ADMIN", "RECEPTIONIST"].includes(role));

  async function loadAll() {
    if (!accessToken || !tenantId) return;
    setLoading(true);
    setError(null);
    try {
      const [summaryRes, sessionsRes] = await Promise.all([
        getRealtimeVoiceSummary(accessToken, tenantId),
        listRealtimeVoiceSessions(accessToken, tenantId),
      ]);
      setSummary(summaryRes);
      setSessions(sessionsRes.sessions);
      if (!selectedSession && sessionsRes.sessions.length > 0) {
        await loadSessionDetails(sessionsRes.sessions[0]);
      }
    } catch (e) {
      setError((e as Error).message || "Failed to load realtime AI");
    } finally {
      setLoading(false);
    }
  }

  async function loadSessionDetails(session: RealtimeVoiceSession) {
    if (!accessToken || !tenantId) return;
    setSelectedSession(session);
    const [eventsRes, transcriptsRes] = await Promise.all([
      getRealtimeVoiceSessionEvents(accessToken, tenantId, session.id),
      getRealtimeVoiceSessionTranscripts(accessToken, tenantId, session.id),
    ]);
    setEvents(eventsRes.events);
    setTranscripts(transcriptsRes.transcripts);
  }

  useEffect(() => {
    void loadAll();
  }, [accessToken, tenantId]);

  const escalations = useMemo(() => sessions.filter((s) => s.escalationRequired), [sessions]);

  return (
    <div style={{ padding: 16, display: "grid", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h2 style={{ margin: 0 }}>Realtime AI</h2>
          <p style={{ margin: "6px 0 0", color: "#666" }}>Realtime voice gateway foundation, session monitoring, escalation tracking, and transcript inspection.</p>
        </div>
        <button onClick={() => void loadAll()}>Refresh</button>
      </div>

      {error ? <div style={{ color: "#b00020" }}>{error}</div> : null}
      {loading ? <div>Loading realtime AI...</div> : null}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
        <Metric label="Active Sessions" value={summary?.activeSessions ?? 0} />
        <Metric label="Escalations" value={summary?.escalationCount ?? 0} />
        <Metric label="Failed Sessions" value={summary?.failedSessions ?? 0} />
        <Metric label="AI Latency(ms)" value={summary?.avgAiLatencyMs ?? 0} />
        <Metric label="STT Latency(ms)" value={summary?.avgSttLatencyMs ?? 0} />
        <Metric label="TTS Latency(ms)" value={summary?.avgTtsLatencyMs ?? 0} />
        <Metric label="Transcript Lag(ms)" value={summary?.avgTranscriptLatencyMs ?? 0} />
        <Metric label="WS Disconnects" value={summary?.websocketDisconnects ?? 0} />
        <Metric label="Interruptions" value={summary?.interruptionCount ?? 0} />
        <Metric label="STT Failures" value={summary?.sttFailures ?? 0} />
        <Metric label="TTS Failures" value={summary?.ttsFailures ?? 0} />
      </div>

      <section>
        <h3>AI Receptionist Status</h3>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <SimpleList title="STT Providers" items={(summary?.sttProviders ?? []).map((p) => `${p.providerName}: ${p.ready ? "READY" : "NOT_READY"}`)} />
          <SimpleList title="TTS Providers" items={(summary?.ttsProviders ?? []).map((p) => `${p.providerName}: ${p.ready ? "READY" : "NOT_READY"}`)} />
        </div>
        <div style={{ marginTop: 8, border: "1px solid #ddd", borderRadius: 8, padding: 10 }}>
          <h4 style={{ marginTop: 0 }}>Runtime Health</h4>
          <div>Status: <strong>{summary?.runtimeStatus?.status ?? "UNKNOWN"}</strong></div>
          <div>Model Ready: {summary?.runtimeStatus?.modelReady ? "YES" : "NO"}</div>
          <div>Runtime Active Sessions: {summary?.runtimeStatus?.activeSessions ?? 0}</div>
          <div>Runtime Uptime(s): {summary?.runtimeStatus?.uptimeSeconds ?? 0}</div>
          {summary?.runtimeStatus?.error ? <div style={{ color: "#b00020" }}>Runtime Error: {summary.runtimeStatus.error}</div> : null}
        </div>
      </section>

      <section>
        <h3>Active Sessions</h3>
        <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
          <select value={sessionType} onChange={(e) => setSessionType(e.target.value as RealtimeVoiceSessionType)}>
            {Object.keys(DEFAULT_PROMPT_KEY_BY_TYPE).map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <button
            disabled={!canManage || !accessToken || !tenantId}
            onClick={async () => {
              if (!accessToken || !tenantId) return;
              try {
                await createRealtimeVoiceSession(accessToken, tenantId, { sessionType, metadataJson: "{}" });
                await loadAll();
              } catch (e) {
                setError((e as Error).message || "Failed to create session");
              }
            }}
          >Create Session</button>
        </div>
        <Table
          columns={["Session", "Type", "Status", "Intent", "Outcome", "Escalation", "Started", "Actions"]}
          rows={sessions.map((s) => [
            s.id,
            s.sessionType,
            s.sessionStatus,
            parseReceptionistMetadata(s.metadataJson).intent || "-",
            parseReceptionistMetadata(s.metadataJson).workflowState || "-",
            s.escalationReason || "-",
            fmtDate(s.startedAt)
          ])}
          rowActions={sessions.map((s) => (
            <div key={s.id} style={{ display: "flex", gap: 6 }}>
              <button onClick={() => void loadSessionDetails(s)}>Inspect</button>
              <button
                disabled={!canManage || s.sessionStatus === "COMPLETED"}
                onClick={async () => {
                  if (!accessToken || !tenantId) return;
                  await completeRealtimeVoiceSession(accessToken, tenantId, s.id);
                  await loadAll();
                }}
              >Complete</button>
            </div>
          ))}
        />
      </section>

      <section>
        <h3>Escalations</h3>
        <Table columns={["Session", "Type", "Status", "Reason", "Assigned"]} rows={escalations.map((s) => [s.id, s.sessionType, s.sessionStatus, s.escalationReason || "-", s.assignedHumanUserId || "-"])} />
      </section>

      <section>
        <h3>Session Inspector</h3>
        {!selectedSession ? <div>Select a session from Active Sessions.</div> : (
          <div style={{ display: "grid", gap: 8 }}>
            <div><strong>Session:</strong> {selectedSession.id}</div>
            <div><strong>Provider:</strong> AI={selectedSession.aiProvider || "-"}, STT={selectedSession.sttProvider || "-"}, TTS={selectedSession.ttsProvider || "-"}</div>
            <div><strong>Workflow State:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).workflowState || "-"}</div>
            <div><strong>Intent:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).intent || "-"}</div>
            <div><strong>Lead Created:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).leadCreated ? "YES" : "NO"} {parseReceptionistMetadata(selectedSession.metadataJson).leadId ? `(Lead ${parseReceptionistMetadata(selectedSession.metadataJson).leadId})` : ""}</div>
            <div><strong>Appointment Request:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).appointmentRequestCreated ? "YES" : "NO"}</div>
            <div><strong>Escalation Flag:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).escalationRequired ? "YES" : "NO"}</div>
            <div><strong>Escalation Reason:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).escalationReason || "-"}</div>
            <div><strong>Session Summary:</strong> {parseReceptionistMetadata(selectedSession.metadataJson).summary || "-"}</div>
            <div><strong>Metadata:</strong> <code>{selectedSession.metadataJson || "{}"}</code></div>

            <div style={{ display: "flex", gap: 8 }}>
              <input
                style={{ flex: 1 }}
                value={userText}
                placeholder="Send a sample user text turn"
                onChange={(e) => setUserText(e.target.value)}
              />
              <button
                disabled={!canManage || !userText.trim() || !accessToken || !tenantId}
                onClick={async () => {
                  if (!accessToken || !tenantId || !selectedSession) return;
                  if (selectedSession.sessionType === "AI_RECEPTIONIST") {
                    await sendReceptionistTestMessage(accessToken, tenantId, {
                      sessionId: selectedSession.id,
                      text: userText,
                      patientContextJson: "{}",
                    });
                  } else {
                    await sendRealtimeVoiceTurn(accessToken, tenantId, selectedSession.id, {
                      text: userText,
                      promptKey: DEFAULT_PROMPT_KEY_BY_TYPE[selectedSession.sessionType],
                      patientContextJson: "{}",
                    });
                  }
                  setUserText("");
                  await loadSessionDetails(selectedSession);
                  await loadAll();
                }}
              >Send Turn</button>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
              <SimpleTimeline title="Transcript Timeline" items={transcripts.map((t) => `${fmtDate(t.timestamp)} [${t.speakerType}] ${t.text}`)} />
              <SimpleTimeline title="Event Timeline" items={events.map((e) => `${fmtDate(e.eventTimestamp)} [${e.eventType}] ${e.payloadSummary || "-"}`)} />
            </div>
          </div>
        )}
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

function Table({ columns, rows, rowActions }: { columns: string[]; rows: string[][]; rowActions?: React.ReactNode[] }) {
  return (
    <table style={{ width: "100%", borderCollapse: "collapse", border: "1px solid #ddd" }}>
      <thead>
        <tr>{columns.map((c) => <th key={c} style={{ textAlign: "left", padding: 6 }}>{c}</th>)}</tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr><td style={{ padding: 8 }} colSpan={columns.length}>No records</td></tr>
        ) : rows.map((r, idx) => (
          <tr key={idx}>
            {r.map((v, i) => <td key={i} style={{ padding: 6 }}>{v}</td>)}
            {rowActions ? <td style={{ padding: 6 }}>{rowActions[idx]}</td> : null}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function SimpleList({ title, items }: { title: string; items: string[] }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10 }}>
      <h4 style={{ marginTop: 0 }}>{title}</h4>
      {items.length === 0 ? <div style={{ color: "#666" }}>No data</div> : (
        <ul style={{ margin: 0, paddingLeft: 18 }}>{items.map((item, idx) => <li key={idx}>{item}</li>)}</ul>
      )}
    </div>
  );
}

function SimpleTimeline({ title, items }: { title: string; items: string[] }) {
  return (
    <div style={{ border: "1px solid #ddd", borderRadius: 8, padding: 10, maxHeight: 320, overflow: "auto" }}>
      <h4 style={{ marginTop: 0 }}>{title}</h4>
      {items.length === 0 ? <div style={{ color: "#666" }}>No timeline entries</div> : (
        <div style={{ display: "grid", gap: 6 }}>{items.map((item, idx) => <div key={idx}>{item}</div>)}</div>
      )}
    </div>
  );
}
