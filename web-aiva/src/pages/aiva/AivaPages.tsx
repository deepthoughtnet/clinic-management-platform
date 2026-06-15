import { useEffect, useMemo, useRef, useState, type ReactNode, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  buildPatientPortalVoiceWebSocketUrl,
  postPatientPortalSessionJson,
  type PatientPortalCareAiMessageRequest,
  type PatientPortalCareAiMessageResponse,
  type PatientPortalCareAiResetResponse,
  type PatientPortalCareAiStateResponse,
  type PatientPortalPatientSession,
} from "../../api/patientPortal";

type AivaMessage = {
  role: "user" | "assistant";
  text: string;
  time: string;
};

type AivaActivity = {
  title: string;
  detail: string;
  status: "done" | "active" | "queued";
};

type AivaConversation = {
  id: string;
  title: string;
  preview: string;
  group: "Today" | "Yesterday" | "Previous 7 Days";
  updatedAt: string;
};

type AivaVoiceStatus = "idle" | "connecting" | "connected" | "listening" | "thinking" | "speaking" | "error" | "disconnected";

type AivaLatencyMetrics = {
  speechRecognitionMs: number | null;
  reasoningMs: number | null;
  voiceResponseMs: number | null;
};

type AivaRuntimeSessionDraft = {
  sessionToken: string;
  tenantId: string;
  tenantCode: string;
  phone: string;
  language: string;
  patientLabel: string;
};

type AivaLiveSession = PatientPortalPatientSession & {
  patientLabel: string;
  language: string;
};

const DEMO_PROMPTS = [
  "Book an appointment with Dr Vikas tomorrow",
  "Reschedule my appointment",
  "Cancel my appointment",
  "Show my prescription",
  "Show my lab reports",
  "Show my pending bills",
] as const;

const PLATFORM_CAPABILITIES = [
  {
    title: "Voice AI",
    description: "Natural speech input and spoken responses for appointment, billing, and clinical navigation.",
  },
  {
    title: "Chat AI",
    description: "Conversational assistance for guided actions, follow-up questions, and intent clarification.",
  },
  {
    title: "Agent Workflows",
    description: "Tool-backed workflows that can plan, route, and complete multi-step healthcare tasks.",
  },
  {
    title: "Human Handoff",
    description: "Escalation and callback handoff when a request needs a person instead of a bot.",
  },
  {
    title: "Enterprise Integrations",
    description: "Reuses business systems and enterprise workflows without duplicating runtime code.",
  },
  {
    title: "Powered by AIVA Runtime",
    description: "AIVA v1 is a product layer on top of the AIVA Runtime, voice gateway, and tools.",
  },
] as const;

const ARCHITECTURE_CHANNELS = ["Voice", "Chat", "Web", "WhatsApp future"] as const;

const AIVA_PLATFORM_LAYERS = [
  "Voice Runtime",
  "Agent Runtime",
  "Tool Registry",
  "Prompt Registry",
  "Memory Engine",
  "RAG Engine",
  "Evaluation Engine",
  "Cost Tracking",
  "Multi-Tenant Admin",
] as const;

const CAREAI_RUNTIME_TODAY = [
  "AIVA Runtime",
  "STT",
  "LLM",
  "TTS",
  "Workflow Engine",
  "Appointment Tools",
  "Billing Tools",
  "Lab Tools",
  "Human Handoff",
] as const;

const BUSINESS_SYSTEMS = ["Business systems", "Future finance automation", "Future HRMS", "Future CRM"] as const;

const AVAILABLE_TODAY = [
  "Voice assistant",
  "Chat assistant",
  "Appointment booking",
  "Reschedule/cancel guidance",
  "Prescription lookup",
  "Lab report lookup",
  "Billing lookup",
  "Human handoff",
] as const;

const COMING_NEXT = [
  "RAG Engine",
  "Embeddings",
  "Vector Search",
  "Prompt Registry",
  "Prompt Versioning",
  "Tool Registry",
  "Model Gateway",
  "LLM Call Logging",
  "Evaluation Tests",
  "Cost Tracking",
  "Multi-tenant AI Admin",
] as const;

const AGENT_MARKETPLACE = [
  {
    name: "Voice Receptionist Agent",
    description: "Greets users, captures intent, and routes requests to the right workflow.",
    tools: ["Intent routing", "Directory lookup", "Handoff"],
    status: "Available",
  },
  {
    name: "Appointment Agent",
    description: "Books, reschedules, and cancels appointments while confirming outcomes.",
    tools: ["Availability", "Booking", "Reschedule"],
    status: "Available",
  },
  {
    name: "Patient Support Agent",
    description: "Answers account, documents, and general support questions.",
    tools: ["Chat", "Knowledge lookups", "Escalation"],
    status: "Demo",
  },
  {
    name: "Billing Assistant Agent",
    description: "Handles invoice status, receipts, reminders, and payment follow-up.",
    tools: ["Invoice lookup", "Receipt retrieval", "Reminder"],
    status: "Available",
  },
  {
    name: "Lab Report Agent",
    description: "Surfaces report status, download links, and structured results.",
    tools: ["Document lookup", "Report status", "Download"],
    status: "Available",
  },
  {
    name: "Follow-up Agent",
    description: "Detects upcoming follow-up dates and prompts the next step.",
    tools: ["Schedule check", "Reminder", "Escalation"],
    status: "Demo",
  },
  {
    name: "Human Handoff Agent",
    description: "Transfers the conversation to a person when the workflow needs manual support.",
    tools: ["Escalation", "Callback", "Queue routing"],
    status: "Available",
  },
  {
    name: "Workflow Orchestration Agent",
    description: "Coordinates multi-step tasks across tools, teams, and channels.",
    tools: ["Workflow planning", "Tool calls", "Verification"],
    status: "Foundation",
  },
] as const;

const PLATFORM_MODULES = [
  {
    name: "Voice Runtime",
    status: "Available",
    description: "Runs voice capture, speech playback, and conversational routing.",
    value: "Live speech experiences with clear control and feedback.",
  },
  {
    name: "Agent Runtime",
    status: "Available",
    description: "Coordinates prompts, tool calls, and response composition.",
    value: "Fast multi-step actions without manual orchestration.",
  },
  {
    name: "Tool Registry",
    status: "Foundation",
    description: "Catalogs available actions and workflow tools for the runtime.",
    value: "Structured reuse of capabilities across products.",
  },
  {
    name: "Prompt Registry",
    status: "Foundation",
    description: "Stores prompt templates and product-specific system instructions.",
    value: "Consistent behavior across assistants and channels.",
  },
  {
    name: "Memory Engine",
    status: "Foundation",
    description: "Maintains conversational context and safe short-term memory.",
    value: "Less repetition, more continuity.",
  },
  {
    name: "RAG Engine",
    status: "Planned",
    description: "Retrieval layer for grounded answers and enterprise knowledge.",
    value: "Better answers from trusted sources.",
  },
  {
    name: "Evaluation Engine",
    status: "Planned",
    description: "Automated checks for assistant quality, safety, and regressions.",
    value: "Higher confidence in releases and prompts.",
  },
  {
    name: "Cost Tracking",
    status: "Planned",
    description: "Tracks model and workflow usage for operational visibility.",
    value: "Better control over AI spend.",
  },
  {
    name: "Multi-Tenant Admin",
    status: "Foundation",
    description: "Tenant-aware controls for AIVA configuration and access.",
    value: "Safer enterprise rollout at scale.",
  },
  {
    name: "Observability & Logs",
    status: "Foundation",
    description: "Operational traces, events, and diagnostics for support teams.",
    value: "Faster troubleshooting and support.",
  },
  {
    name: "Guardrails",
    status: "Foundation",
    description: "Policy checks, scoped access, and safe failure handling.",
    value: "Controlled assistant behavior in production.",
  },
] as const;

const INDUSTRY_SHOWCASE = [
  {
    name: "Healthcare",
    voice: "Book my appointment tomorrow",
    chat: "Show my latest document summary",
    workflow: "Triage request, route to specialist, confirm outcome",
    example: "Book my appointment tomorrow",
  },
  {
    name: "Finance Operations",
    voice: "Check invoice status",
    chat: "Send reminder for unpaid invoice",
    workflow: "Verify balance, create reminder, update case",
    example: "Check invoice status and send reminder",
  },
  {
    name: "HR & Employee Services",
    voice: "Apply leave for next Friday",
    chat: "Check my policy balance",
    workflow: "Review policy, route approval, send confirmation",
    example: "Apply leave for next Friday",
  },
  {
    name: "Customer Support",
    voice: "Raise a ticket",
    chat: "Escalate this issue to an agent",
    workflow: "Create ticket, classify priority, hand off",
    example: "Raise a ticket and escalate to agent",
  },
  {
    name: "Field Operations",
    voice: "Schedule technician visit",
    chat: "Confirm site access and time window",
    workflow: "Check schedule, assign resource, notify user",
    example: "Schedule technician visit",
  },
  {
    name: "Education & Training",
    voice: "Explain my course progress",
    chat: "Show training status",
    workflow: "Fetch progress, summarize next steps, notify learner",
    example: "Explain my course progress",
  },
  {
    name: "Legal / Compliance",
    voice: "Summarize compliance checklist",
    chat: "Show policy completion",
    workflow: "Review checklist, flag exceptions, document results",
    example: "Summarize compliance checklist",
  },
  {
    name: "Retail / Sales",
    voice: "Check order status",
    chat: "Create return request",
    workflow: "Lookup order, validate status, create return",
    example: "Check order status and create return request",
  },
] as const;

const AIVA_WORKFLOW_STAGES = [
  "Understand intent",
  "Identify required data",
  "Check availability",
  "Execute workflow action",
  "Confirm result",
] as const;

const WORKFLOW_STEPS = [
  {
    title: "Intent detected",
    detail: "Voice or text request is understood and normalized.",
    status: "done",
  },
  {
    title: "Plan assembled",
    detail: "Agent chooses whether to answer, look up data, or call a tool.",
    status: "done",
  },
  {
    title: "Tool execution",
    detail: "AIVA Runtime can invoke business workflows for appointments, billing, documents, and lookups.",
    status: "active",
  },
  {
    title: "Human handoff",
    detail: "Escalate to staff when policy or confidence requires a person.",
    status: "queued",
  },
] as const;

const AIVA_HISTORY: AivaConversation[] = [
  {
    id: "today-booking",
    title: "Book appointment with Dr Vikas",
    preview: "AIVA searched availability and reserved a slot.",
    group: "Today",
    updatedAt: "10:05",
  },
  {
    id: "today-billing",
    title: "Check my bill",
    preview: "AIVA summarized dues and receipt status.",
    group: "Today",
    updatedAt: "09:38",
  },
  {
    id: "yesterday-lab",
    title: "Show lab reports",
    preview: "AIVA fetched the latest report-ready state.",
    group: "Yesterday",
    updatedAt: "Yesterday",
  },
  {
    id: "week-prescription",
    title: "Show prescription",
    preview: "AIVA retrieved the latest published prescription.",
    group: "Previous 7 Days",
    updatedAt: "Mon",
  },
];

const AIVA_WORKFLOW = [
  { title: "Understand Intent", detail: "Normalize the request and identify the user goal." },
  { title: "Search Records", detail: "Look up the patient, doctor, bill, lab, or prescription context." },
  { title: "Execute Tool", detail: "Call the existing AIVA tools and workflows." },
  { title: "Verify Result", detail: "Confirm the result before speaking or sending confirmation." },
  { title: "Respond", detail: "Speak or chat the outcome and hand off if needed." },
] as const;

const AIVA_WORKFLOW_CARDS = [
  {
    title: "Appointment booking",
    prompt: "Book an appointment tomorrow",
    steps: ["Understand intent", "Identify required data", "Check availability", "Execute workflow action", "Confirm result"],
  },
  {
    title: "Reschedule",
    prompt: "Reschedule my appointment",
    steps: ["Understand intent", "Locate existing booking", "Find alternatives", "Execute workflow action", "Confirm result"],
  },
  {
    title: "Cancellation guidance",
    prompt: "Cancel my appointment",
    steps: ["Understand intent", "Check policy", "Route guidance", "Escalate if needed", "Confirm result"],
  },
  {
    title: "Bill lookup",
    prompt: "Check my bill",
    steps: ["Understand intent", "Identify account", "Load balance", "Execute workflow action", "Confirm result"],
  },
  {
    title: "Prescription lookup",
    prompt: "Show prescription",
    steps: ["Understand intent", "Identify latest document", "Fetch record", "Execute workflow action", "Confirm result"],
  },
  {
    title: "Report lookup",
    prompt: "Show lab reports",
    steps: ["Understand intent", "Identify latest report", "Fetch report", "Execute workflow action", "Confirm result"],
  },
  {
    title: "Human handoff",
    prompt: "Talk to receptionist",
    steps: ["Understand intent", "Assess confidence", "Escalate", "Route handoff", "Confirm result"],
  },
] as const;

function AivaSection({
  eyebrow,
  title,
  subtitle,
  children,
}: {
  eyebrow: string;
  title: string;
  subtitle: string;
  children: ReactNode;
}) {
  return (
    <section className="aiva-section">
      <div className="aiva-section-heading">
        <span className="eyebrow">{eyebrow}</span>
        <h2>{title}</h2>
        <p>{subtitle}</p>
      </div>
      {children}
    </section>
  );
}

function AivaFeatureGrid() {
  return (
    <div className="aiva-feature-grid">
      {PLATFORM_CAPABILITIES.map((feature) => (
        <article key={feature.title} className="aiva-card aiva-feature-card">
          <strong>{feature.title}</strong>
          <p>{feature.description}</p>
        </article>
      ))}
    </div>
  );
}

function AivaPillGrid({ items }: { items: readonly string[] }) {
  return (
    <div className="aiva-pill-grid">
      {items.map((item) => (
        <span key={item} className="aiva-pill">
          {item}
        </span>
      ))}
    </div>
  );
}

function safeTimeLabel(date = new Date()) {
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

function sanitizeText(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

function readJsonStorage<T>(key: string): T | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) {
      return null;
    }
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

function maskToken(value: string | null | undefined) {
  if (!value) {
    return "Missing";
  }
  if (value.length <= 8) {
    return "Present";
  }
  return `${value.slice(0, 4)}…${value.slice(-4)}`;
}

function buildDemoResponse(prompt: string) {
  const normalized = prompt.toLowerCase();
  if (normalized.includes("appointment") && normalized.includes("book")) {
    return [
      "✓ Understand Intent",
      "✓ Search Doctor",
      "✓ Check Availability",
      "✓ Reserve Slot",
      "✓ Send Confirmation",
    ].join("\n");
  }
  if (normalized.includes("reschedule")) {
    return [
      "I can reschedule your visit.",
      "",
      "| Step | Status |",
      "| --- | --- |",
      "| Check existing booking | ✓ |",
      "| Search alternative slots | ✓ |",
      "| Confirm with you | Pending |",
    ].join("\n");
  }
  if (normalized.includes("cancel")) {
    return "To cancel or reschedule, please use AIVA Runtime or contact reception.";
  }
  if (normalized.includes("prescription")) {
    return "I found your latest prescription. It is ready for secure document viewing and PDF download if enabled.";
  }
  if (normalized.includes("lab")) {
    return "Your latest report is ready. You can view and download it in the secure workspace.";
  }
  if (normalized.includes("bill") || normalized.includes("receipt")) {
    return "I found your pending bill and the latest receipt state. Your due amount is ready for review.";
  }
  if (normalized.includes("doctor availability")) {
    return "Available doctors: Dr Vikas, Dr Meera, Dr Shah. Earliest slot tomorrow 10:30 AM.";
  }
  if (normalized.includes("receptionist")) {
    return "I can connect you to the receptionist team for handoff, scheduling, and follow-up support.";
  }
  return [
    "I can route that through the AIVA Runtime.",
    "",
    "- Understand intent",
    "- Search records",
    "- Execute tool",
    "- Verify result",
    "- Respond",
  ].join("\n");
}

function renderMarkdownText(text: string) {
  const lines = text.split("\n");
  const blocks: ReactNode[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (!line.trim()) {
      index += 1;
      continue;
    }
    if (line.startsWith("|")) {
      const tableRows: string[][] = [];
      while (index < lines.length && lines[index].startsWith("|")) {
        tableRows.push(lines[index].split("|").map((item) => item.trim()).filter(Boolean));
        index += 1;
      }
      const [header, ...rows] = tableRows;
      blocks.push(
        <div key={`table-${index}`} className="aiva-table">
          <div className="aiva-table-row aiva-table-head">
            {header?.map((cell) => (
              <span key={cell}>{cell}</span>
            ))}
          </div>
          {rows.map((row, rowIndex) => (
            <div key={`${row.join("-")}-${rowIndex}`} className="aiva-table-row">
              {row.map((cell) => (
                <span key={cell}>{cell}</span>
              ))}
            </div>
          ))}
        </div>,
      );
      continue;
    }
    if (/^[-*]\s+/.test(line) || /^\d+\.\s+/.test(line)) {
      const ordered = /^\d+\.\s+/.test(line);
      const items: string[] = [];
      while (index < lines.length && (/^[-*]\s+/.test(lines[index]) || /^\d+\.\s+/.test(lines[index]))) {
        items.push(lines[index].replace(/^[-*\d.]+\s+/, ""));
        index += 1;
      }
      const ListTag = ordered ? "ol" : "ul";
      blocks.push(
        <ListTag key={`list-${index}`} className="aiva-rich-list">
          {items.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ListTag>,
      );
      continue;
    }
    const paragraphs: string[] = [line];
    index += 1;
    while (index < lines.length && lines[index].trim() && !lines[index].startsWith("|") && !/^[-*]\s+/.test(lines[index]) && !/^\d+\.\s+/.test(lines[index])) {
      paragraphs.push(lines[index]);
      index += 1;
    }
    blocks.push(
      <p key={`p-${index}`}>
        {paragraphs.join(" ")}
      </p>,
    );
  }

  return <>{blocks}</>;
}

function readAivaRuntimeDraft(searchParams?: URLSearchParams): AivaRuntimeSessionDraft {
  if (typeof window === "undefined") {
    return {
      sessionToken: "",
      tenantId: "",
      tenantCode: "aiva",
      phone: "",
      language: "auto",
      patientLabel: "Demo visitor",
    };
  }
  const search = searchParams ?? new URLSearchParams(window.location.search);
  const storedSession = readJsonStorage<PatientPortalPatientSession & { patientLabel?: string }>("clinic-web-public-patient-session");
  return {
    sessionToken:
    search.get("sessionToken")
    ?? search.get("patientSessionToken")
    ?? window.localStorage.getItem("aiva.sessionToken")
    ?? window.localStorage.getItem("aiva.patientSessionToken")
    ?? storedSession?.patientSessionToken
    ?? "",
    tenantId:
    search.get("tenantId")
    ?? window.localStorage.getItem("aiva.tenantId")
    ?? storedSession?.tenantId
    ?? "",
    tenantCode:
    search.get("tenantCode")
    ?? search.get("clinicCode")
    ?? window.localStorage.getItem("aiva.tenantCode")
    ?? storedSession?.tenantCode
    ?? "aiva",
    phone:
    search.get("phone")
    ?? window.localStorage.getItem("aiva.phone")
    ?? storedSession?.phone
    ?? "",
    language:
    search.get("language")
    ?? window.localStorage.getItem("aiva.language")
    ?? "auto",
    patientLabel:
    search.get("patientLabel")
    ?? window.localStorage.getItem("aiva.patientLabel")
    ?? storedSession?.patientLabel
    ?? "Demo visitor",
  };
}

function buildAivaLiveSession(runtimeDraft: AivaRuntimeSessionDraft): AivaLiveSession | null {
  if (!runtimeDraft.sessionToken.trim() || !runtimeDraft.tenantId.trim()) {
    return null;
  }
  const liveSession: AivaLiveSession = {
    mode: "otp",
    sessionRole: "patient",
    tenantId: runtimeDraft.tenantId.trim(),
    tenantCode: runtimeDraft.tenantCode.trim() || "aiva",
    phone: runtimeDraft.phone.trim() || "0000000000",
    createdAt: new Date().toISOString(),
    patientSessionToken: runtimeDraft.sessionToken.trim(),
    patientLabel: runtimeDraft.patientLabel.trim() || "Demo visitor",
    language: runtimeDraft.language.trim() || "auto",
  };
  return liveSession;
}

function persistAivaRuntimeDraft(runtimeDraft: AivaRuntimeSessionDraft) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.localStorage.setItem("aiva.sessionToken", runtimeDraft.sessionToken.trim());
    window.localStorage.setItem("aiva.tenantId", runtimeDraft.tenantId.trim());
    window.localStorage.setItem("aiva.tenantCode", runtimeDraft.tenantCode.trim() || "aiva");
    window.localStorage.setItem("aiva.phone", runtimeDraft.phone.trim());
    window.localStorage.setItem("aiva.patientLabel", runtimeDraft.patientLabel.trim());
    window.localStorage.setItem("aiva.language", runtimeDraft.language.trim() || "auto");
  } catch {
    // Ignore storage failures.
  }
}

function describeMissingRuntimeFields(runtimeDraft: AivaRuntimeSessionDraft) {
  const missing: string[] = [];
  if (!runtimeDraft.sessionToken.trim()) {
    missing.push("sessionToken");
  }
  if (!runtimeDraft.tenantId.trim()) {
    missing.push("tenantId");
  }
  if (missing.length === 0) {
    return "Live runtime session is ready.";
  }
  return `Missing ${missing.join(" and ")}.`;
}

const AIVA_VOICE_AUDIO_BASE64_CHUNK_SIZE = 24 * 1024;
const AIVA_DEFAULT_RUNTIME_MESSAGE = "Live AIVA voice requires a valid runtime session. Use demo mode or open from a verified session.";
const AIVA_SEEDED_RUNTIME_SESSION = {
  sessionToken: import.meta.env.VITE_AIVA_DEMO_SESSION_TOKEN?.trim() ?? "",
  tenantId: import.meta.env.VITE_AIVA_DEMO_TENANT_ID?.trim() ?? "",
  tenantCode: import.meta.env.VITE_AIVA_DEMO_TENANT_CODE?.trim() ?? "aiva",
  phone: import.meta.env.VITE_AIVA_DEMO_PHONE?.trim() ?? "",
  language: import.meta.env.VITE_AIVA_DEMO_LANGUAGE?.trim() ?? "auto",
  patientLabel: "Demo visitor",
};

function selectAivaVoiceMimeType() {
  const candidates = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/ogg"];
  return candidates.find((candidate) => MediaRecorder.isTypeSupported(candidate)) || "";
}

function resolveAivaVoiceAudioExtension(type: string) {
  return type.toLowerCase().includes("ogg") ? "ogg" : "webm";
}

async function blobToBase64(blob: Blob) {
  const buffer = await blob.arrayBuffer();
  let binary = "";
  const bytes = new Uint8Array(buffer);
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize));
  }
  return btoa(binary);
}

function splitAivaVoiceBase64Chunks(base64: string, chunkSize = AIVA_VOICE_AUDIO_BASE64_CHUNK_SIZE) {
  const chunks: string[] = [];
  for (let index = 0; index < base64.length; index += chunkSize) {
    chunks.push(base64.slice(index, index + chunkSize));
  }
  return chunks;
}

function aivaVoiceStatusLabel(status: AivaVoiceStatus) {
  switch (status) {
    case "idle":
      return "Idle";
    case "connecting":
      return "Connecting";
    case "connected":
      return "Connected";
    case "listening":
      return "Listening";
    case "thinking":
      return "Thinking";
    case "speaking":
      return "Speaking";
    case "error":
      return "Error";
    case "disconnected":
      return "Disconnected";
    default:
      return status;
  }
}

function buildResponse(prompt: string) {
  const normalized = prompt.toLowerCase();
  if (normalized.includes("appointment") && normalized.includes("book")) {
    return "I can help book that visit. I would check doctor availability, confirm the slot, and then hand you the booking summary.";
  }
  if (normalized.includes("reschedule")) {
    return "I can prepare a reschedule request and guide you to the existing appointment before any change is finalized.";
  }
  if (normalized.includes("cancel")) {
    return "I can surface cancellation guidance and route the request to the AIVA handoff flow if policy requires it.";
  }
  if (normalized.includes("prescription")) {
    return "I can surface the latest published prescription and open the secure document when the session permits it.";
  }
  if (normalized.includes("lab")) {
    return "I can retrieve the latest report status and point to the secure download when it is ready.";
  }
  if (normalized.includes("bill") || normalized.includes("receipt")) {
    return "I can look up outstanding dues, recent payments, and the latest receipt summary.";
  }
  return "I can route that request into the AIVA Runtime and decide whether a tool call or human handoff is the safest next step.";
}

export function AivaLandingPage({ runtimeHref }: { runtimeHref: string }) {
  return (
    <div className="aiva-page">
      <section className="aiva-hero aiva-shell-card">
        <div className="aiva-hero-copy">
          <span className="eyebrow">AIVA</span>
          <h1>AI Voice Intelligence &amp; Agentic Workflow Platform</h1>
          <p className="aiva-tagline">Talk. Understand. Act.</p>
          <p className="aiva-hero-description">
            AIVA is the product layer for reusable AI voice and agent workflows. It presents the AIVA Runtime as an enterprise platform for
            conversations, tooling, and human handoff without rebuilding the engine underneath.
          </p>
          <div className="cta-row">
            <Link className="primary-button" to="/demo">
              Talk to AIVA
            </Link>
            <Link className="secondary-button" to="/architecture">
              View Architecture
            </Link>
            <a className="ghost-button" href={runtimeHref}>
              Open live AIVA runtime
            </a>
          </div>
        </div>
        <div className="aiva-hero-panel">
          <div className="aiva-signal-card">
            <span className="aiva-kicker">Powered by AIVA Runtime</span>
            <h3>AIVA Runtime</h3>
            <p>STT, LLM, TTS, workflows, business tools, document tools, and human handoff remain the execution layer.</p>
          </div>
          <div className="aiva-signal-grid">
            {ARCHITECTURE_CHANNELS.map((channel) => (
              <div key={channel} className="aiva-signal">
                {channel}
              </div>
            ))}
          </div>
          <div className="aiva-runtime-strip">
            <span>Voice</span>
            <span>Chat</span>
            <span>Web</span>
            <span>Workflow tools</span>
          </div>
        </div>
      </section>

      <AivaSection
        eyebrow="Runtime connection"
        title="Connect, inspect, and troubleshoot the runtime"
        subtitle="AIVA uses an optional live adapter for voice and chat. Demo mode still works when no session is supplied."
      >
        <div className="aiva-preview-grid">
          <article className="aiva-card">
            <strong>Demo runtime</strong>
            <p className="aiva-muted">Safe preview mode with local workflow simulation and no sensitive data exposure.</p>
          </article>
          <article className="aiva-card">
            <strong>Live runtime</strong>
            <p className="aiva-muted">Uses the existing AIVA adapter when session and tenant context are available.</p>
          </article>
          <article className="aiva-card">
            <strong>Diagnostics</strong>
            <p className="aiva-muted">Connection health, socket status, and API status are visible on the demo experience.</p>
          </article>
        </div>
      </AivaSection>

      <AivaSection
        eyebrow="Platform"
        title="Reusable enterprise assistant capabilities"
        subtitle="AIVA is designed as a productized platform surface, not a separate backend runtime."
      >
        <AivaFeatureGrid />
      </AivaSection>

      <AivaSection eyebrow="Agent marketplace" title="Productized agents for enterprise workflows" subtitle="Preview a reusable catalog of assistant roles on the new Agents page.">
        <div className="aiva-preview-grid">
          {AGENT_MARKETPLACE.slice(0, 4).map((agent) => (
            <article key={agent.name} className="aiva-card">
              <strong>{agent.name}</strong>
              <p className="aiva-muted">{agent.description}</p>
            </article>
          ))}
        </div>
      </AivaSection>

      <AivaSection eyebrow="Platform modules" title="Core modules and roadmap layers" subtitle="AIVA v1 is a UI/product layer with a clear path to deeper platform capabilities.">
        <div className="aiva-preview-grid">
          {PLATFORM_MODULES.slice(0, 4).map((module) => (
            <article key={module.name} className="aiva-card">
              <strong>{module.name}</strong>
              <p className="aiva-muted">{module.status} · {module.description}</p>
            </article>
          ))}
        </div>
      </AivaSection>

      <AivaSection eyebrow="Multi-industry showcase" title="Works across business workflows" subtitle="AIVA is not limited to one vertical. Browse the industries page for examples.">
        <div className="aiva-preview-grid">
          {INDUSTRY_SHOWCASE.slice(0, 4).map((industry) => (
            <article key={industry.name} className="aiva-card">
              <strong>{industry.name}</strong>
              <p className="aiva-muted">{industry.example}</p>
            </article>
          ))}
        </div>
      </AivaSection>

      <AivaSection
        eyebrow="Runtime"
        title="Powered by AIVA Runtime"
        subtitle="The launch point is a reusable engine that can be extended into new products without destabilizing AIVA v1."
      >
        <div className="aiva-runtime-grid">
          <article className="aiva-card">
            <strong>Today</strong>
            <AivaPillGrid items={CAREAI_RUNTIME_TODAY} />
          </article>
          <article className="aiva-card">
            <strong>What AIVA adds</strong>
            <p className="aiva-muted">
              A stronger brand, a dedicated microsite, enterprise messaging, and a clear path to reusable voice and agent workflows.
            </p>
            <AivaPillGrid items={["Product storytelling", "Sandbox demo", "Architecture view", "Roadmap view"]} />
          </article>
        </div>
      </AivaSection>
    </div>
  );
}

function renderHistoryPreview(conversation: AivaConversation) {
  return (
    <div className="aiva-history-card">
      <strong>{conversation.title}</strong>
      <p>{conversation.preview}</p>
      <span>{conversation.updatedAt}</span>
    </div>
  );
}

export function AivaDemoPage({ runtimeHref }: { runtimeHref: string }) {
  const [searchParams] = useSearchParams();
  const [runtimeDraft, setRuntimeDraft] = useState<AivaRuntimeSessionDraft>(() => readAivaRuntimeDraft(searchParams));
  const liveSession = useMemo(() => buildAivaLiveSession(runtimeDraft), [runtimeDraft]);
  const [conversationSearch, setConversationSearch] = useState("");
  const [activeConversationId, setActiveConversationId] = useState(AIVA_HISTORY[0].id);
  const [conversations, setConversations] = useState<AivaConversation[]>(AIVA_HISTORY);
  const [messages, setMessages] = useState<AivaMessage[]>([
    {
      role: "assistant",
      text: "Welcome to AIVA. I can speak, chat, and route requests into AIVA Runtime when a live session is configured.",
      time: "09:00",
    },
  ]);
  const [draft, setDraft] = useState("");
  const [selectedPrompt, setSelectedPrompt] = useState<string>(DEMO_PROMPTS[0]);
  const [assistantTyping, setAssistantTyping] = useState(false);
  const [chatBusy, setChatBusy] = useState(false);
  const [resetBusy, setResetBusy] = useState(false);
  const [voiceStatus, setVoiceStatus] = useState<AivaVoiceStatus>("idle");
  const [voiceTranscript, setVoiceTranscript] = useState("");
  const [voiceAssistant, setVoiceAssistant] = useState("");
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const [voiceInfo, setVoiceInfo] = useState<string | null>(null);
  const [voiceMuted, setVoiceMuted] = useState(false);
  const [speakerEnabled, setSpeakerEnabled] = useState(true);
  const [autoVad, setAutoVad] = useState(true);
  const [voiceLanguage, setVoiceLanguage] = useState("Auto Detect");
  const [voiceGender, setVoiceGender] = useState("Female");
  const [voiceSpeed, setVoiceSpeed] = useState("Normal");
  const [voiceLevel, setVoiceLevel] = useState(0);
  const [voicePeak, setVoicePeak] = useState(0);
  const [voiceEvents, setVoiceEvents] = useState<string[]>([
    liveSession ? "Live AIVA Runtime ready" : "Demo mode active",
  ]);
  const [careAiState, setCareAiState] = useState<PatientPortalCareAiStateResponse | null>(null);
  const [latencyMetrics, setLatencyMetrics] = useState<AivaLatencyMetrics>({
    speechRecognitionMs: null,
    reasoningMs: null,
    voiceResponseMs: null,
  });
  const [workflowIndex, setWorkflowIndex] = useState(0);
  const [runtimeConnected, setRuntimeConnected] = useState(!liveSession);
  const [runtimeLastPing, setRuntimeLastPing] = useState(liveSession ? "Not connected" : "Connected");
  const [runtimeHealth, setRuntimeHealth] = useState(liveSession ? "Not connected" : "Healthy");
  const [runtimeSocketStatus, setRuntimeSocketStatus] = useState(liveSession ? "Closed" : "Open");
  const [runtimeSocketReadyState, setRuntimeSocketReadyState] = useState(liveSession ? "3 (closed)" : "1 (open)");
  const [chatApiStatus, setChatApiStatus] = useState(liveSession ? "Not connected" : "Demo");
  const [runtimeConnectedAt, setRuntimeConnectedAt] = useState<string | null>(liveSession ? null : safeTimeLabel());
  const [runtimeLastError, setRuntimeLastError] = useState<string | null>(null);
  const [runtimeSocketCloseCode, setRuntimeSocketCloseCode] = useState<string>("—");
  const [runtimeSocketCloseReason, setRuntimeSocketCloseReason] = useState<string>("—");
  const [runtimeBootstrapMessage, setRuntimeBootstrapMessage] = useState<string | null>(
    liveSession ? "Live runtime session available." : AIVA_DEFAULT_RUNTIME_MESSAGE,
  );
  const [showTechnicalDetails, setShowTechnicalDetails] = useState(false);
  const [showWorkflowSimulator, setShowWorkflowSimulator] = useState(false);
  const [activity, setActivity] = useState<AivaActivity[]>([
    { title: "Session ready", detail: liveSession ? "Live runtime available" : "Demo-safe prompt mode", status: "done" },
    { title: "Intent routing", detail: "Waiting for prompt", status: "active" },
    { title: "Tool execution", detail: "Uses the AIVA Runtime when live session data is supplied", status: "queued" },
  ]);
  const [recentContext, setRecentContext] = useState([
    { label: "Last appointment", value: "Tomorrow · Dr Vikas" },
    { label: "Last doctor", value: "Dr Vikas" },
    { label: "Last interaction", value: "Appointment booking" },
    { label: "Last workflow", value: "AIVA booking flow" },
  ]);

  const chatEndRef = useRef<HTMLDivElement | null>(null);
  const voiceSocketRef = useRef<WebSocket | null>(null);
  const voiceRecorderRef = useRef<MediaRecorder | null>(null);
  const voiceStreamRef = useRef<MediaStream | null>(null);
  const voiceChunksRef = useRef<Blob[]>([]);
  const voiceAudioRef = useRef<HTMLAudioElement | null>(null);
  const voiceAudioContextRef = useRef<AudioContext | null>(null);
  const voiceAnalyserRef = useRef<AnalyserNode | null>(null);
  const voiceSourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null);
  const voiceLevelDataRef = useRef<Uint8Array | null>(null);
  const voiceMonitoringIntervalRef = useRef<number | null>(null);
  const voiceHeartbeatRef = useRef<number | null>(null);
  const voiceSessionStartedAtRef = useRef<number | null>(null);
  const voiceSpeechStartedAtRef = useRef<number | null>(null);
  const voiceLastSpeechAtRef = useRef<number | null>(null);
  const voiceTurnSubmittedAtRef = useRef<number | null>(null);
  const voicePendingAudioRef = useRef<Map<number, string>>(new Map());
  const voiceExpectedAudioChunksRef = useRef(0);
  const voiceVoiceStatusRef = useRef<AivaVoiceStatus>("idle");
  const voiceAudioUrlRef = useRef<string | null>(null);
  const voiceAutoStopTriggeredRef = useRef(false);
  const voiceAssistantAudioPlayingRef = useRef(false);
  const voiceEndedByUserRef = useRef(false);
  const voicePendingAutoPlayRef = useRef(false);
  const voiceAutoplayUnlockAttemptedRef = useRef(false);

  const filteredConversations = useMemo(
    () =>
      conversations.filter((conversation) => {
        const search = conversationSearch.trim().toLowerCase();
        return !search || conversation.title.toLowerCase().includes(search) || conversation.preview.toLowerCase().includes(search);
      }),
    [conversationSearch, conversations],
  );

  const workflowStatuses = useMemo(
    () =>
      AIVA_WORKFLOW.map((step, index) => ({
        ...step,
        active: index === workflowIndex,
        complete: index < workflowIndex,
      })),
    [workflowIndex],
  );

  const runtimeMode = liveSession ? "Live runtime connected" : "Demo mode";

  useEffect(() => {
    setRuntimeBootstrapMessage(liveSession ? "Live runtime session ready." : describeMissingRuntimeFields(runtimeDraft));
  }, [liveSession, runtimeDraft]);

  useEffect(() => {
    if (liveSession) {
      persistAivaRuntimeDraft(runtimeDraft);
    }
  }, [liveSession, runtimeDraft]);

  useEffect(() => {
    const nextDraft = readAivaRuntimeDraft(searchParams);
    if (
      nextDraft.sessionToken
      || nextDraft.tenantId
      || nextDraft.tenantCode !== "aiva"
      || nextDraft.phone
      || nextDraft.language !== "auto"
      || nextDraft.patientLabel !== "Demo visitor"
    ) {
      setRuntimeDraft((current) => ({
        sessionToken: nextDraft.sessionToken || current.sessionToken,
        tenantId: nextDraft.tenantId || current.tenantId,
        tenantCode: nextDraft.tenantCode || current.tenantCode,
        phone: nextDraft.phone || current.phone,
        language: nextDraft.language || current.language,
        patientLabel: nextDraft.patientLabel || current.patientLabel,
      }));
    }
  }, [searchParams.toString()]);

  function updateRuntimeSnapshot(next: {
    connected?: boolean;
    health?: string;
    socketStatus?: string;
    socketReadyState?: string;
    chatStatus?: string;
    ping?: string;
    connectedAt?: string | null;
    lastError?: string | null;
    closeCode?: string;
    closeReason?: string;
  }) {
    if (next.connected != null) setRuntimeConnected(next.connected);
    if (next.health) setRuntimeHealth(next.health);
    if (next.socketStatus) setRuntimeSocketStatus(next.socketStatus);
    if (next.socketReadyState) setRuntimeSocketReadyState(next.socketReadyState);
    if (next.chatStatus) setChatApiStatus(next.chatStatus);
    if (next.ping) setRuntimeLastPing(next.ping);
    if (Object.prototype.hasOwnProperty.call(next, "connectedAt")) setRuntimeConnectedAt(next.connectedAt ?? null);
    if (Object.prototype.hasOwnProperty.call(next, "lastError")) setRuntimeLastError(next.lastError ?? null);
    if (Object.prototype.hasOwnProperty.call(next, "closeCode")) setRuntimeSocketCloseCode(next.closeCode ?? "—");
    if (Object.prototype.hasOwnProperty.call(next, "closeReason")) setRuntimeSocketCloseReason(next.closeReason ?? "—");
  }

  function updateVoiceStatus(nextStatus: AivaVoiceStatus) {
    voiceVoiceStatusRef.current = nextStatus;
    setVoiceStatus(nextStatus);
  }

  function appendVoiceEvent(message: string) {
    setVoiceEvents((current) => [...current.slice(-11), `${safeTimeLabel()} • ${message}`]);
  }

  function setRuntimeConnectedState() {
    const connectedAt = safeTimeLabel();
    setRuntimeConnected(true);
    setRuntimeLastPing(connectedAt);
    setRuntimeHealth("Healthy");
    setRuntimeSocketStatus("Open");
    setRuntimeSocketReadyState("1 (open)");
    setRuntimeSocketCloseCode("—");
    setRuntimeSocketCloseReason("—");
    setChatApiStatus(liveSession ? "Connected" : "Demo");
    setRuntimeConnectedAt(connectedAt);
  }

  function runtimeDisplayMode() {
    return liveSession ? "Live" : "Demo";
  }

  function replaceVoiceAudioUrl(nextUrl: string | null) {
    setVoiceAudioUrl((current) => {
      if (current && current !== nextUrl) {
        URL.revokeObjectURL(current);
      }
      return nextUrl;
    });
    voiceAudioUrlRef.current = nextUrl;
  }

  async function unlockVoicePlaybackElement() {
    if (!voiceAudioRef.current || voiceAutoplayUnlockAttemptedRef.current) {
      return;
    }
    voiceAutoplayUnlockAttemptedRef.current = true;
    const audioElement = voiceAudioRef.current;
    const previousMuted = audioElement.muted;
    const previousSrc = audioElement.src;
    try {
      audioElement.muted = true;
      audioElement.src = "data:audio/wav;base64,UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAIA+AAACABAAZGF0YQAAAAA=";
      audioElement.load();
      await audioElement.play();
      audioElement.pause();
      audioElement.currentTime = 0;
      appendVoiceEvent("AUDIO_UNLOCK_READY");
    } catch (error) {
      appendVoiceEvent(`AUDIO_UNLOCK_FAILED ${error instanceof Error ? error.message : "unknown"}`);
    } finally {
      audioElement.pause();
      audioElement.currentTime = 0;
      audioElement.src = previousSrc;
      if (previousSrc) {
        audioElement.load();
      } else {
        audioElement.removeAttribute("src");
        audioElement.load();
      }
      audioElement.muted = previousMuted || voiceMuted;
    }
  }

  async function handleVoiceReplyPlayback() {
    if (!voiceAudioRef.current || !voiceAudioUrl) {
      return;
    }
    try {
      voicePendingAutoPlayRef.current = false;
      if (voiceAudioRef.current.src !== voiceAudioUrl) {
        voiceAudioRef.current.src = voiceAudioUrl;
      }
      voiceAudioRef.current.load();
      voiceAudioRef.current.currentTime = 0;
      updateVoiceStatus("speaking");
      setVoiceInfo("AIVA is speaking.");
      await voiceAudioRef.current.play();
      appendVoiceEvent("ASSISTANT_AUDIO_PLAYING");
    } catch (error) {
      setVoiceError(error instanceof Error ? error.message : "Playback blocked. Please try again.");
      updateVoiceStatus("error");
      setRuntimeLastError(error instanceof Error ? error.message : "Playback blocked. Please try again.");
      appendVoiceEvent("AUDIO_PLAY_FAILED");
    }
  }

  const [voiceAudioUrl, setVoiceAudioUrl] = useState<string | null>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages]);

  useEffect(() => {
    return () => {
      if (voiceAudioUrlRef.current) {
        URL.revokeObjectURL(voiceAudioUrlRef.current);
      }
      if (voiceSocketRef.current) {
        voiceSocketRef.current.close();
      }
      if (voiceMonitoringIntervalRef.current != null) {
        window.clearInterval(voiceMonitoringIntervalRef.current);
      }
      if (voiceHeartbeatRef.current != null) {
        window.clearInterval(voiceHeartbeatRef.current);
      }
    };
  }, []);

  useEffect(() => {
    if (voiceAudioRef.current) {
      voiceAudioRef.current.muted = voiceMuted;
    }
  }, [voiceMuted, voiceAudioUrl]);

  function seedConversation(conversationId: string) {
    if (conversationId === "today-booking") {
      return [
        { role: "user" as const, text: "Book appointment with Dr Vikas tomorrow.", time: "09:40" },
        { role: "assistant" as const, text: "Checking availability... ", time: "09:40" },
        { role: "assistant" as const, text: "✓ Understand Intent\n✓ Search Doctor\n✓ Check Availability\n✓ Reserve Slot\n✓ Send Confirmation", time: "09:41" },
      ];
    }
    if (conversationId === "today-billing") {
      return [
        { role: "user" as const, text: "Check my pending bills.", time: "08:55" },
        { role: "assistant" as const, text: "Your receipt is available in the secure workspace.", time: "08:56" },
      ];
    }
    if (conversationId === "yesterday-lab") {
      return [
        { role: "user" as const, text: "Show my lab reports.", time: "Yesterday" },
        { role: "assistant" as const, text: "Your report is ready. You can view/download it in the secure workspace.", time: "Yesterday" },
      ];
    }
    return [
      { role: "user" as const, text: "Show my prescription.", time: "Mon" },
      { role: "assistant" as const, text: "Your prescription is ready for PDF download.", time: "Mon" },
    ];
  }

  function openConversation(conversation: AivaConversation) {
    setActiveConversationId(conversation.id);
    setMessages(seedConversation(conversation.id));
    setWorkflowIndex(1);
    setVoiceTranscript("");
    setVoiceAssistant("");
    setAssistantTyping(false);
    setChatBusy(false);
    setVoiceError(null);
    setVoiceStatus("idle");
    appendVoiceEvent(`Conversation opened: ${conversation.title}`);
  }

  function createConversationFromPrompt(prompt: string, assistantPreview: string) {
    const now = safeTimeLabel();
    const conversation: AivaConversation = {
      id: `thread-${Date.now()}`,
      title: prompt.length > 40 ? `${prompt.slice(0, 40)}…` : prompt,
      preview: assistantPreview,
      group: "Today",
      updatedAt: now,
    };
    setConversations((current) => [conversation, ...current.filter((item) => item.id !== activeConversationId)]);
    setActiveConversationId(conversation.id);
  }

  function updateMemory(prompt: string) {
    const normalized = prompt.toLowerCase();
    setRecentContext([
      { label: "Last appointment", value: normalized.includes("appointment") ? "Tomorrow · Dr Vikas" : "On hold" },
      { label: "Last doctor", value: normalized.includes("doctor") ? "Dr Vikas" : "Dr Meera" },
      { label: "Last interaction", value: prompt },
      { label: "Last workflow", value: normalized.includes("bill") ? "Billing lookup" : normalized.includes("lab") ? "Report lookup" : "AIVA conversation" },
    ]);
  }

  function updateActivity(prompt: string, assistantText: string) {
    const normalized = prompt.toLowerCase();
    const bookingSteps = normalized.includes("book")
      ? [
          { title: "Search Doctor", detail: "Matched Dr Vikas to the request", status: "done" as const },
          { title: "Check Availability", detail: "Located open slots for tomorrow", status: "done" as const },
          { title: "Create Appointment", detail: "Reserved the selected slot", status: "done" as const },
          { title: "Send Confirmation", detail: "Prepared the confirmation response", status: "active" as const },
        ]
      : normalized.includes("reschedule")
        ? [
            { title: "Load Current Appointment", detail: "Loaded the existing visit", status: "done" as const },
            { title: "Search New Slot", detail: "Found alternate times", status: "done" as const },
            { title: "Verify Result", detail: "Waiting for user confirmation", status: "active" as const },
          ]
        : normalized.includes("lab")
          ? [
              { title: "Search Lab Orders", detail: "Fetching report-ready records", status: "done" as const },
              { title: "Check Report Status", detail: "Confirmed the latest lab report", status: "done" as const },
              { title: "Prepare Download", detail: "Opening secure download", status: "active" as const },
            ]
          : normalized.includes("bill") || normalized.includes("receipt")
            ? [
                { title: "Load Account", detail: "Loaded dues and receipts", status: "done" as const },
                { title: "Check Payment", detail: "Matched payment and refund state", status: "done" as const },
                { title: "Show Receipt", detail: "Receipt is ready in the secure workspace", status: "active" as const },
              ]
            : [
                { title: "Understand Intent", detail: prompt, status: "done" as const },
                { title: "Execute Tool", detail: assistantText, status: "active" as const },
                { title: "Respond", detail: "Return a clear answer with a safe handoff if needed", status: "queued" as const },
              ];
    setActivity((current) => [...bookingSteps, ...current.slice(0, 1)].slice(0, 4));
  }

  function syncWorkflowFromPrompt(prompt: string) {
    const normalized = prompt.toLowerCase();
    if (normalized.includes("book")) {
      setWorkflowIndex(4);
      return;
    }
    if (normalized.includes("reschedule") || normalized.includes("cancel")) {
      setWorkflowIndex(3);
      return;
    }
    if (normalized.includes("lab") || normalized.includes("bill") || normalized.includes("receipt")) {
      setWorkflowIndex(2);
      return;
    }
    setWorkflowIndex(1);
  }

  async function sendPrompt(prompt: string, source: "chat" | "voice" | "chip" = "chat") {
    const text = sanitizeText(prompt);
    if (!text || chatBusy) {
      return;
    }

    const timestamp = safeTimeLabel();
    const userMessage: AivaMessage = { role: "user", text, time: timestamp };
    setMessages((current) => [...current, userMessage]);
    setSelectedPrompt(text);
    setAssistantTyping(true);
    setChatBusy(true);
    setVoiceStatus("thinking");
    setVoiceTranscript(text);
    setVoiceAssistant("");
    setVoiceError(null);
    setVoiceEvents((current) => [...current.slice(-10), `${timestamp} • ${source.toUpperCase()} prompt received`]);
    updateMemory(text);
    syncWorkflowFromPrompt(text);

    const startedAt = performance.now();

    try {
      if (liveSession) {
        const request: PatientPortalCareAiMessageRequest = {
          message: text,
          language: voiceLanguage === "Auto Detect" ? "auto" : voiceLanguage.toLowerCase(),
        };
        const response = await postPatientPortalSessionJson<PatientPortalCareAiMessageResponse>(
          "/api/patient-portal/careai/message",
          request,
          liveSession,
        );
        const assistantText = response.assistantMessage || "I completed the requested AIVA workflow.";
        setCareAiState(response.state);
        setVoiceAssistant(assistantText);
        setMessages((current) => [...current, { role: "assistant", text: assistantText, time: safeTimeLabel() }]);
        updateActivity(text, assistantText);
        createConversationFromPrompt(text, assistantText);
        setVoiceEvents((current) => [...current.slice(-10), `${safeTimeLabel()} • Live AIVA response ready`]);
      } else {
        await new Promise((resolve) => window.setTimeout(resolve, 650));
        const assistantText = buildDemoResponse(text);
        setVoiceAssistant(assistantText);
        setMessages((current) => [...current, { role: "assistant", text: assistantText, time: safeTimeLabel() }]);
        updateActivity(text, assistantText);
        createConversationFromPrompt(text, assistantText);
        setVoiceEvents((current) => [...current.slice(-10), `${safeTimeLabel()} • Demo response ready`]);
      }
      const finishedAt = performance.now();
      setLatencyMetrics({
        speechRecognitionMs: Math.max(120, Math.round(finishedAt - startedAt) - 280),
        reasoningMs: Math.max(180, Math.round(finishedAt - startedAt) - 120),
        voiceResponseMs: Math.max(250, Math.round(finishedAt - startedAt)),
      });
      setWorkflowIndex((current) => Math.min(current + 1, AIVA_WORKFLOW.length - 1));
    } catch (error) {
      const message = error instanceof Error ? error.message : "AIVA could not process the request.";
      setVoiceError(message);
      setMessages((current) => [...current, { role: "assistant", text: message, time: safeTimeLabel() }]);
      setVoiceEvents((current) => [...current.slice(-10), `${safeTimeLabel()} • ERROR ${message}`]);
    } finally {
      setAssistantTyping(false);
      setChatBusy(false);
      setVoiceStatus("idle");
    }
  }

  async function handleChatSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await sendPrompt(draft || selectedPrompt, "chat");
    setDraft("");
  }

  async function handlePromptClick(prompt: string) {
    setSelectedPrompt(prompt);
    await sendPrompt(prompt, "chip");
    setDraft("");
  }

  async function handleNewConversation() {
    await handleResetRuntime();
  }

  function updateRuntimeDraftField<K extends keyof AivaRuntimeSessionDraft>(field: K, value: AivaRuntimeSessionDraft[K]) {
    setRuntimeDraft((current) => {
      const nextDraft = {
        ...current,
        [field]: value,
      };
      setRuntimeBootstrapMessage(describeMissingRuntimeFields(nextDraft));
      return nextDraft;
    });
  }

  function handleUseSeededRuntimeSession() {
    if (!AIVA_SEEDED_RUNTIME_SESSION.sessionToken || !AIVA_SEEDED_RUNTIME_SESSION.tenantId) {
      return;
    }
    const nextDraft: AivaRuntimeSessionDraft = {
      ...AIVA_SEEDED_RUNTIME_SESSION,
    };
    setRuntimeDraft(nextDraft);
    setRuntimeBootstrapMessage("Seeded demo runtime session loaded.");
  }

  function clearVoiceAudio() {
    if (voiceAudioUrlRef.current) {
      URL.revokeObjectURL(voiceAudioUrlRef.current);
      voiceAudioUrlRef.current = null;
    }
    setVoiceAudioUrl(null);
  }

  function startVoiceMonitoring(stream: MediaStream) {
    if (voiceMonitoringIntervalRef.current != null) {
      window.clearInterval(voiceMonitoringIntervalRef.current);
    }
    const AudioContextCtor = window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) {
      return;
    }
    const contextInstance = new AudioContextCtor();
    const source = contextInstance.createMediaStreamSource(stream);
    const analyser = contextInstance.createAnalyser();
    analyser.fftSize = 2048;
    const data = new Uint8Array(analyser.fftSize);
    source.connect(analyser);
    voiceAudioContextRef.current = contextInstance;
    voiceSourceNodeRef.current = source;
    voiceAnalyserRef.current = analyser;
    voiceLevelDataRef.current = data;
    voiceMonitoringIntervalRef.current = window.setInterval(() => {
      if (!voiceAnalyserRef.current || !voiceLevelDataRef.current) {
        return;
      }
      voiceAnalyserRef.current.getByteTimeDomainData(voiceLevelDataRef.current as Uint8Array<ArrayBuffer>);
      let sumSquares = 0;
      let peak = 0;
      for (const sample of voiceLevelDataRef.current) {
        const normalized = (sample - 128) / 128;
        const absolute = Math.abs(normalized);
        sumSquares += normalized * normalized;
        if (absolute > peak) {
          peak = absolute;
        }
      }
      const rms = Math.sqrt(sumSquares / voiceLevelDataRef.current.length);
      setVoiceLevel(rms);
      setVoicePeak(peak);
      if (rms > 0.03 && voiceVoiceStatusRef.current === "listening") {
        updateVoiceStatus("thinking");
      }
      if (autoVad && voiceVoiceStatusRef.current === "thinking" && voiceLastSpeechAtRef.current && Date.now() - voiceLastSpeechAtRef.current > 1200 && !voiceAutoStopTriggeredRef.current) {
        voiceAutoStopTriggeredRef.current = true;
        void handleVoiceStopTurn();
      }
    }, 100);
  }

  function stopVoiceMonitoring() {
    if (voiceMonitoringIntervalRef.current != null) {
      window.clearInterval(voiceMonitoringIntervalRef.current);
      voiceMonitoringIntervalRef.current = null;
    }
    voiceSourceNodeRef.current?.disconnect();
    voiceAnalyserRef.current?.disconnect();
    voiceSourceNodeRef.current = null;
    voiceAnalyserRef.current = null;
    voiceLevelDataRef.current = null;
    if (voiceAudioContextRef.current) {
      void voiceAudioContextRef.current.close().catch(() => undefined);
      voiceAudioContextRef.current = null;
    }
    setVoiceLevel(0);
    setVoicePeak(0);
  }

  function cleanupVoiceSession() {
    if (voiceHeartbeatRef.current != null) {
      window.clearInterval(voiceHeartbeatRef.current);
      voiceHeartbeatRef.current = null;
    }
    stopVoiceMonitoring();
    if (voiceRecorderRef.current && voiceRecorderRef.current.state !== "inactive") {
      voiceRecorderRef.current.stop();
    }
    voiceRecorderRef.current = null;
    if (voiceStreamRef.current) {
      voiceStreamRef.current.getTracks().forEach((track) => track.stop());
      voiceStreamRef.current = null;
    }
    if (voiceSocketRef.current) {
      const socket = voiceSocketRef.current;
      voiceSocketRef.current = null;
      try {
        socket.send(JSON.stringify({ type: "session.close" }));
      } catch {
        // noop
      }
      socket.close();
    }
    updateRuntimeSnapshot({
      connected: liveSession ? false : runtimeConnected,
      socketStatus: "Closed",
      socketReadyState: "3 (closed)",
      ping: "Not connected",
      health: liveSession ? "Not connected" : runtimeHealth,
      chatStatus: liveSession ? "Not connected" : "Demo",
    });
  }

  function startVoiceHeartbeat(socket: WebSocket) {
    if (voiceHeartbeatRef.current != null) {
      window.clearInterval(voiceHeartbeatRef.current);
    }
    voiceHeartbeatRef.current = window.setInterval(() => {
      if (socket.readyState !== WebSocket.OPEN) {
        if (voiceHeartbeatRef.current != null) {
          window.clearInterval(voiceHeartbeatRef.current);
          voiceHeartbeatRef.current = null;
        }
        return;
      }
      socket.send(JSON.stringify({ type: "heartbeat" }));
    }, 15000);
  }

  function ensureVoiceSocket() {
    if (!liveSession) {
      return null;
    }
    if (voiceSocketRef.current && voiceSocketRef.current.readyState === WebSocket.OPEN) {
      return voiceSocketRef.current;
    }
    cleanupVoiceSession();
    const socket = new WebSocket(buildPatientPortalVoiceWebSocketUrl(liveSession));
    voiceSocketRef.current = socket;
    updateVoiceStatus("connecting");
    appendVoiceEvent("CONNECTING");
    updateRuntimeSnapshot({
      connected: true,
      socketStatus: "Connecting",
      socketReadyState: "0 (connecting)",
      chatStatus: "Connecting",
      ping: "Connecting",
      health: "Healthy",
      connectedAt: safeTimeLabel(),
      lastError: null,
    });
    socket.onopen = () => {
      updateVoiceStatus("connected");
      setVoiceInfo("AIVA is connected.");
      appendVoiceEvent("CONNECTED");
      setRuntimeConnectedState();
      setRuntimeSocketCloseCode("—");
      setRuntimeSocketCloseReason("—");
      socket.send(
        JSON.stringify({
          type: "session.start",
          language: liveSession?.language || (voiceLanguage === "Auto Detect" ? "auto" : voiceLanguage.toLowerCase()),
          resumeSessionId: null,
        }),
      );
    };
    socket.onmessage = (rawMessage) => {
      let payload: Record<string, unknown>;
      try {
        payload = JSON.parse(rawMessage.data) as Record<string, unknown>;
      } catch {
        setVoiceError("AIVA voice returned an unreadable response.");
        updateVoiceStatus("error");
        return;
      }
      const type = String(payload.type || "");
      if (type === "session.connected") {
        appendVoiceEvent("SESSION_CONNECTED");
        return;
      }
      if (type === "session.started") {
        voiceSessionStartedAtRef.current = Date.now();
        updateVoiceStatus("listening");
        setVoiceInfo("AIVA is listening.");
        setVoiceError(null);
        updateRuntimeSnapshot({ ping: safeTimeLabel(), socketStatus: "Open", socketReadyState: "1 (open)", chatStatus: "Connected", health: "Healthy" });
        startVoiceHeartbeat(socket);
        appendVoiceEvent("SESSION_STARTED");
        return;
      }
      if (type === "stt.started" || type === "turn.started") {
        updateVoiceStatus("thinking");
        setVoiceInfo("AIVA is thinking.");
        appendVoiceEvent(type === "stt.started" ? "STT_STARTED" : "TURN_STARTED");
        return;
      }
      if (type === "heartbeat") {
        return;
      }
      if (type === "audio.chunk.received") {
        appendVoiceEvent(`AUDIO_CHUNK_RECEIVED ${String(payload.sequence || 0)}/${String(payload.totalChunks || "?")}`);
        return;
      }
      if (type === "turn.audio.received") {
        appendVoiceEvent(`TURN_AUDIO_RECEIVED ${String(payload.sequence || 0)}/${String(payload.totalChunks || "?")}`);
        return;
      }
      if (type === "transcript.final") {
        const transcript = String(payload.text || "").trim();
        if (transcript) {
          updateVoiceStatus("thinking");
          setVoiceInfo("AIVA is thinking.");
          setVoiceTranscript(transcript);
          setMessages((current) => [...current, { role: "user", text: transcript, time: safeTimeLabel() }]);
          voiceSpeechStartedAtRef.current = voiceSpeechStartedAtRef.current ?? Date.now();
          voiceLastSpeechAtRef.current = Date.now();
          appendVoiceEvent(`TRANSCRIPT: ${transcript}`);
        }
        return;
      }
      if (type === "assistant.text") {
        const assistantText = String(payload.text || "").trim();
        if (assistantText) {
          updateVoiceStatus("speaking");
          setVoiceInfo("AIVA is speaking.");
          setVoiceAssistant(assistantText);
          setMessages((current) => [...current, { role: "assistant", text: assistantText, time: safeTimeLabel() }]);
          setAssistantTyping(false);
          appendVoiceEvent("ASSISTANT_TEXT");
        }
        const nextState = payload.state as PatientPortalCareAiStateResponse | undefined;
        if (nextState) {
          setCareAiState(nextState);
        }
        return;
      }
      if (type === "turn.careai.complete") {
        appendVoiceEvent("AIVA_COMPLETE");
        return;
      }
      if (type === "assistant.audio.chunk") {
        const sequence = Number(payload.sequence || 0);
        const totalChunks = Number(payload.totalChunks || 0);
        const chunk = String(payload.audioBase64Chunk || "");
        if (sequence > 0 && chunk) {
          voicePendingAudioRef.current.set(sequence, chunk);
          voiceExpectedAudioChunksRef.current = totalChunks;
        }
        return;
      }
      if (type === "assistant.audio.end") {
        const totalChunks = voiceExpectedAudioChunksRef.current;
        const chunks: string[] = [];
        for (let index = 1; index <= totalChunks; index += 1) {
          const chunk = voicePendingAudioRef.current.get(index);
          if (!chunk) {
            setVoiceError("Voice playback data was incomplete.");
            updateVoiceStatus("error");
            return;
          }
          chunks.push(chunk);
        }
        voicePendingAudioRef.current.clear();
        voiceExpectedAudioChunksRef.current = 0;
        const contentType = String(payload.contentType || "audio/wav");
        const binary = atob(chunks.join(""));
        const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
        replaceVoiceAudioUrl(URL.createObjectURL(new Blob([bytes], { type: contentType })));
        voicePendingAutoPlayRef.current = true;
        updateVoiceStatus("speaking");
        setVoiceInfo("AIVA is speaking.");
        return;
      }
      if (type === "turn.tts.complete") {
        appendVoiceEvent("TTS_COMPLETE");
        return;
      }
      if (type === "turn.complete") {
        const totalDurationMs = Number((payload.metrics as { totalDurationMs?: number } | undefined)?.totalDurationMs || 0);
        const sttDurationMs = Number((payload.metrics as { sttDurationMs?: number } | undefined)?.sttDurationMs || 0);
        const careAiDurationMs = Number((payload.metrics as { careAiDurationMs?: number } | undefined)?.careAiDurationMs || 0);
        const ttsDurationMs = Number((payload.metrics as { ttsDurationMs?: number } | undefined)?.ttsDurationMs || 0);
        setLatencyMetrics({
          speechRecognitionMs: sttDurationMs || null,
          reasoningMs: careAiDurationMs || null,
          voiceResponseMs: ttsDurationMs || totalDurationMs || null,
        });
        setAssistantTyping(false);
        setVoiceStatus(voiceEndedByUserRef.current ? "disconnected" : voiceSocketRef.current ? "listening" : "idle");
        setVoiceInfo(voiceEndedByUserRef.current ? "AIVA session disconnected." : "AIVA is listening.");
        appendVoiceEvent("TURN_COMPLETE");
        setWorkflowIndex((current) => Math.min(current + 1, AIVA_WORKFLOW.length - 1));
        return;
      }
      if (type === "session.closed") {
        updateVoiceStatus("disconnected");
        setVoiceInfo("AIVA session disconnected.");
        appendVoiceEvent("SESSION_CLOSED");
        return;
      }
      if (type === "session.timeout") {
        updateVoiceStatus("error");
        setVoiceError("AIVA voice session timed out. Please retry.");
        setRuntimeLastError("AIVA voice session timed out. Please retry.");
        appendVoiceEvent(`SESSION_TIMEOUT ${String(payload.reason || "timeout")}`);
        return;
      }
      if (type === "error") {
        setVoiceError(String(payload.message || "AIVA voice could not process that request."));
        updateVoiceStatus("error");
        setRuntimeLastError(String(payload.message || "AIVA voice could not process that request."));
        appendVoiceEvent("SERVER_ERROR");
      }
    };
    socket.onerror = () => {
      setVoiceError("Voice connection failed. Please retry.");
      updateVoiceStatus("error");
      setRuntimeLastError("Voice connection failed. Please retry.");
      updateRuntimeSnapshot({
        connected: false,
        socketStatus: "Error",
        socketReadyState: "3 (closed)",
        chatStatus: "Error",
        ping: safeTimeLabel(),
        health: "Degraded",
        lastError: "Voice connection failed. Please retry.",
        closeCode: "Error",
        closeReason: "Socket error",
      });
    };
    socket.onclose = (event) => {
      if (voiceVoiceStatusRef.current !== "error") {
        updateVoiceStatus(voiceEndedByUserRef.current ? "disconnected" : "idle");
      }
      updateRuntimeSnapshot({
        connected: false,
        socketStatus: "Closed",
        socketReadyState: `3 (closed)`,
        chatStatus: liveSession ? "Not connected" : "Demo",
        ping: safeTimeLabel(),
        health: liveSession ? "Not connected" : "Healthy",
        closeCode: String(event.code),
        closeReason: event.reason || "Closed",
      });
      if (voiceEndedByUserRef.current) {
        setVoiceInfo("AIVA session disconnected.");
      }
    };
    return socket;
  }

  async function startVoiceTurn() {
    if (!liveSession) {
      updateVoiceStatus("listening");
      appendVoiceEvent("DEMO_VOICE_START");
      setVoiceTranscript(selectedPrompt);
      setAssistantTyping(true);
      setTimeout(() => {
        const assistantText = buildDemoResponse(selectedPrompt);
        setVoiceAssistant(assistantText);
        setMessages((current) => [
          ...current,
          { role: "user", text: selectedPrompt, time: safeTimeLabel() },
          { role: "assistant", text: assistantText, time: safeTimeLabel() },
        ]);
        setVoiceEvents((current) => [...current.slice(-10), `${safeTimeLabel()} • Demo voice response`]);
        setAssistantTyping(false);
        updateVoiceStatus("idle");
        setLatencyMetrics({ speechRecognitionMs: 184, reasoningMs: 412, voiceResponseMs: 620 });
      }, 750);
      return;
    }

    const socket = ensureVoiceSocket();
    if (!socket) {
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setVoiceError("Microphone access is not available in this browser.");
      updateVoiceStatus("error");
      setRuntimeLastError("Microphone access is not available in this browser.");
      return;
    }
    try {
      await unlockVoicePlaybackElement();
      voiceEndedByUserRef.current = false;
      voiceAutoStopTriggeredRef.current = false;
      setVoiceError(null);
      setVoiceTranscript("");
      setVoiceAssistant("");
      setAssistantTyping(true);
      setVoiceInfo("AIVA is listening.");
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      voiceStreamRef.current = stream;
      const mimeType = selectAivaVoiceMimeType();
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      const recordingType = recorder.mimeType || mimeType || "audio/webm";
      const filename = `aiva-careai-${Date.now()}.${resolveAivaVoiceAudioExtension(recordingType)}`;
      voiceChunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          voiceChunksRef.current.push(event.data);
        }
      };
      recorder.onstop = async () => {
        stopVoiceMonitoring();
        if (!voiceSocketRef.current || voiceSocketRef.current.readyState !== WebSocket.OPEN) {
          return;
        }
        const blob = new Blob(voiceChunksRef.current, { type: recorder.mimeType || recordingType || "audio/webm" });
        if (blob.size === 0) {
          setVoiceError("No voice input was captured.");
          updateVoiceStatus("connected");
          setVoiceInfo("AIVA is connected.");
          return;
        }
        updateVoiceStatus("thinking");
        setVoiceInfo("AIVA is thinking.");
        const audioBase64 = await blobToBase64(blob);
        const chunks = splitAivaVoiceBase64Chunks(audioBase64);
        chunks.forEach((audioBase64Chunk, index) => {
          voiceSocketRef.current?.send(
            JSON.stringify({
              type: "audio.chunk",
              sequence: index + 1,
              totalChunks: chunks.length,
              contentType: recorder.mimeType || recordingType || "audio/webm",
              filename,
              audioBase64Chunk,
            }),
          );
        });
        voiceSocketRef.current.send(
          JSON.stringify({
            type: "audio.end",
            totalChunks: chunks.length,
            contentType: recorder.mimeType || recordingType || "audio/webm",
            filename,
          }),
        );
        voiceTurnSubmittedAtRef.current = performance.now();
        appendVoiceEvent(`VOICE_UPLOAD ${chunks.length} chunks`);
      };
      voiceRecorderRef.current = recorder;
      startVoiceMonitoring(stream);
      recorder.start();
      updateVoiceStatus("listening");
      setVoiceInfo("AIVA is listening.");
      appendVoiceEvent("MIC_OPEN");
    } catch (error) {
      setVoiceError(error instanceof Error ? error.message : "Microphone access failed.");
      updateVoiceStatus("error");
      setRuntimeLastError(error instanceof Error ? error.message : "Microphone access failed.");
    }
  }

  function handleVoiceStopTurn() {
    if (voiceRecorderRef.current && voiceRecorderRef.current.state !== "inactive") {
      voiceRecorderRef.current.stop();
      return;
    }
    updateVoiceStatus(voiceSocketRef.current ? "connected" : "idle");
    setVoiceInfo(voiceSocketRef.current ? "AIVA is connected." : null);
  }

  function handleVoiceEnd() {
    voiceEndedByUserRef.current = true;
    voiceAutoplayUnlockAttemptedRef.current = false;
    cleanupVoiceSession();
    clearVoiceAudio();
    updateVoiceStatus("disconnected");
    setVoiceTranscript("");
    setVoiceAssistant("");
    setVoiceError(null);
    setAssistantTyping(false);
    setVoiceInfo("AIVA session disconnected.");
  }

  async function handleConnectRuntime() {
    if (!liveSession) {
      setRuntimeBootstrapMessage(describeMissingRuntimeFields(runtimeDraft));
      updateRuntimeSnapshot({
        connected: false,
        socketStatus: "Closed",
        socketReadyState: "3 (closed)",
        chatStatus: "Demo",
        ping: safeTimeLabel(),
        health: "Demo",
        lastError: describeMissingRuntimeFields(runtimeDraft),
      });
      return;
    }
    try {
      await ensureVoiceSocket();
      updateRuntimeSnapshot({
        connected: true,
        socketStatus: "Open",
        socketReadyState: "1 (open)",
        chatStatus: "Connected",
        ping: safeTimeLabel(),
        health: "Healthy",
        connectedAt: safeTimeLabel(),
        lastError: null,
      });
      setRuntimeConnected(true);
      setRuntimeBootstrapMessage("Live runtime session ready.");
    } catch {
      updateRuntimeSnapshot({
        connected: false,
        socketStatus: "Error",
        socketReadyState: "3 (closed)",
        chatStatus: "Error",
        ping: safeTimeLabel(),
        health: "Degraded",
        lastError: "Unable to connect runtime",
      });
      setRuntimeBootstrapMessage("Unable to connect live runtime.");
    }
  }

  function handleDisconnectRuntime() {
    voiceEndedByUserRef.current = true;
    voiceAutoplayUnlockAttemptedRef.current = false;
    cleanupVoiceSession();
    clearVoiceAudio();
    setRuntimeConnected(false);
    updateRuntimeSnapshot({
      connected: false,
      socketStatus: "Closed",
      socketReadyState: "3 (closed)",
      chatStatus: liveSession ? "Not connected" : "Demo",
      ping: safeTimeLabel(),
      health: liveSession ? "Not connected" : "Demo",
      lastError: runtimeLastError,
    });
    updateVoiceStatus("disconnected");
    setVoiceInfo("AIVA session disconnected.");
    setRuntimeBootstrapMessage(liveSession ? "Live runtime session ready." : AIVA_DEFAULT_RUNTIME_MESSAGE);
  }

  async function handleResetRuntime() {
    setResetBusy(true);
    try {
      voiceAutoplayUnlockAttemptedRef.current = false;
      clearVoiceAudio();
      setMessages([
        {
          role: "assistant",
          text: "A fresh AIVA conversation is ready. Speak or type your request when you are ready.",
          time: safeTimeLabel(),
        },
      ]);
      setDraft("");
      setSelectedPrompt(DEMO_PROMPTS[0]);
      setAssistantTyping(false);
      setChatBusy(false);
      setWorkflowIndex(0);
      setVoiceTranscript("");
      setVoiceAssistant("");
      setVoiceStatus("idle");
      setVoiceError(null);
      setVoiceInfo(null);
      setCareAiState(null);
      setLatencyMetrics({ speechRecognitionMs: null, reasoningMs: null, voiceResponseMs: null });
      setActivity([
        { title: "New conversation", detail: "Conversation reset", status: "done" },
        { title: "Intent routing", detail: "Waiting for prompt", status: "active" },
        { title: "Tool execution", detail: liveSession ? "Live runtime ready" : "Demo runtime ready", status: "queued" },
      ]);
      setRecentContext([
        { label: "Last appointment", value: "None yet" },
        { label: "Last doctor", value: "None yet" },
        { label: "Last interaction", value: "None yet" },
        { label: "Last workflow", value: "None yet" },
      ]);
      setConversations(AIVA_HISTORY);
      setActiveConversationId("today-booking");
      if (liveSession) {
        await postPatientPortalSessionJson<PatientPortalCareAiResetResponse>("/api/patient-portal/careai/reset", {}, liveSession);
      }
      updateRuntimeSnapshot({
        connected: runtimeConnected,
        socketStatus: runtimeConnected ? "Open" : "Closed",
        socketReadyState: runtimeConnected ? "1 (open)" : "3 (closed)",
        chatStatus: liveSession ? "Connected" : "Demo",
        ping: safeTimeLabel(),
        health: runtimeConnected ? "Healthy" : liveSession ? "Not connected" : "Demo",
        lastError: null,
      });
      setRuntimeBootstrapMessage(liveSession ? "Live runtime session ready." : AIVA_DEFAULT_RUNTIME_MESSAGE);
    } catch {
      // Keep the UI usable even if the runtime reset fails.
    } finally {
      setResetBusy(false);
    }
  }

  async function handleCopyDiagnostics() {
    const summary = [
      `AIVA mode: ${runtimeDisplayMode()}`,
      `Runtime connection: ${runtimeConnected ? "Connected" : "Not connected"}`,
      `Runtime health: ${runtimeHealth}`,
      `Voice socket: ${runtimeSocketStatus}`,
      `Ready state: ${runtimeSocketReadyState}`,
      `WebSocket URL: ${websocketUrl}`,
      `Close code: ${runtimeSocketCloseCode}`,
      `Close reason: ${runtimeSocketCloseReason}`,
      `Chat API: ${chatApiStatus}`,
      `Last ping: ${runtimeLastPing}`,
      `Session token: ${maskToken(liveSession?.patientSessionToken)}`,
      `Tenant id: ${maskToken(liveSession?.tenantId)}`,
      `Language: ${liveSession?.language || voiceLanguage}`,
    ].join(" | ");
    try {
      await navigator.clipboard.writeText(summary);
      appendVoiceEvent("DIAGNOSTICS COPIED");
    } catch {
      appendVoiceEvent("DIAGNOSTICS COPY FAILED");
    }
  }

  useEffect(() => {
    if (!voicePendingAutoPlayRef.current || !voiceAudioUrl || !voiceAudioRef.current) {
      return;
    }
    voicePendingAutoPlayRef.current = false;
    if (!speakerEnabled) {
      return;
    }
    void voiceAudioRef.current.play().catch(() => undefined);
  }, [speakerEnabled, voiceAudioUrl]);

  useEffect(() => {
    if (!voiceAudioRef.current) {
      return;
    }
    voiceAudioRef.current.muted = voiceMuted;
  }, [voiceMuted, voiceAudioUrl]);

  useEffect(() => {
    return () => {
      cleanupVoiceSession();
      clearVoiceAudio();
    };
  }, []);

  function handleAudioEnded() {
    voiceAssistantAudioPlayingRef.current = false;
    voicePendingAutoPlayRef.current = false;
    updateVoiceStatus(voiceSocketRef.current ? "listening" : "idle");
    setVoiceInfo(voiceSocketRef.current ? "AIVA is listening." : null);
  }

  const visibleConversations = filteredConversations.reduce<Record<AivaConversation["group"], AivaConversation[]>>(
    (accumulator, conversation) => {
      accumulator[conversation.group].push(conversation);
      return accumulator;
    },
    { Today: [], Yesterday: [], "Previous 7 Days": [] },
  );

  const statusLabel = aivaVoiceStatusLabel(voiceStatus);
  const websocketUrl = liveSession ? buildPatientPortalVoiceWebSocketUrl(liveSession) : "Not connected";
  const voiceHelperText =
    voiceStatus === "listening"
      ? "Listening - speak now."
      : voiceStatus === "connecting"
        ? "Connecting to AIVA Runtime."
        : voiceStatus === "thinking"
          ? "AIVA is processing your request."
          : voiceStatus === "speaking"
            ? "AIVA is responding."
            : voiceStatus === "error"
              ? "AIVA hit an error. Try again."
              : voiceStatus === "disconnected"
                ? "AIVA session disconnected."
                : "AIVA is ready.";

  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card aiva-demo-hero">
        <div className="aiva-demo-heading">
          <span className="eyebrow">AIVA demo</span>
          <h1>Voice and chat, powered by AIVA Runtime</h1>
          <p>
            AIVA stays demo-safe by default. When a live session is supplied through the runtime adapter, the same runtime handles
            conversations, voice, and tool execution.
          </p>
          <div className="cta-row">
            <button className="primary-button" type="button" onClick={() => void startVoiceTurn()}>
              Talk to AIVA
            </button>
            <button className="secondary-button" type="button" onClick={() => void sendPrompt(selectedPrompt, "chat")}>
              Start Chat Conversation
            </button>
            <a className="ghost-button" href={runtimeHref}>
              Open live AIVA runtime
            </a>
          </div>
        </div>
        <div className="aiva-header-meta">
          <div className="aiva-meta-card">
            <span>Mode</span>
            <strong>{runtimeMode}</strong>
          </div>
          <div className="aiva-meta-card">
            <span>Session</span>
            <strong>{liveSession ? "Live runtime session available" : "Safe demo mode"}</strong>
          </div>
          <div className="aiva-meta-card">
            <span>Runtime source of truth</span>
            <strong>AIVA Runtime</strong>
          </div>
        </div>
      </section>

      <section className="aiva-runtime-panel aiva-shell-card">
        <div className="aiva-panel-heading">
          <div>
            <span className="aiva-kicker">Runtime connection</span>
            <h2>AIVA Runtime connection</h2>
          </div>
          <div className={`aiva-status-badge ${runtimeConnected ? "listening" : "error"}`}>
            <span className="aiva-status-dot" />
            {runtimeConnected ? (liveSession ? "Live AIVA Runtime" : "Demo runtime") : "Not connected"}
          </div>
        </div>
        <div className="aiva-runtime-panel-grid">
          <article className="aiva-card">
            <strong>Mode</strong>
            <p className="aiva-muted">{runtimeDisplayMode()}</p>
          </article>
          <article className="aiva-card">
            <strong>Connection health</strong>
            <p className="aiva-muted">{runtimeHealth}</p>
          </article>
          <article className="aiva-card">
            <strong>Last ping</strong>
            <p className="aiva-muted">{runtimeLastPing}</p>
          </article>
          <article className="aiva-card">
            <strong>Voice socket</strong>
            <p className="aiva-muted">{runtimeSocketStatus}</p>
          </article>
          <article className="aiva-card">
            <strong>Chat API</strong>
            <p className="aiva-muted">{chatApiStatus}</p>
          </article>
          <article className="aiva-card">
            <strong>sessionToken</strong>
            <p className="aiva-muted">{runtimeDraft.sessionToken.trim() ? "Present" : "Missing"}</p>
          </article>
          <article className="aiva-card">
            <strong>tenantId</strong>
            <p className="aiva-muted">{runtimeDraft.tenantId.trim() ? "Present" : "Missing"}</p>
          </article>
        </div>
        <div className="aiva-runtime-form">
          <div className="aiva-runtime-form-grid">
            <label>
              <span>sessionToken</span>
              <input
                value={runtimeDraft.sessionToken}
                onChange={(event) => updateRuntimeDraftField("sessionToken", event.target.value)}
                placeholder="Paste runtime session token"
                autoComplete="off"
              />
            </label>
            <label>
              <span>tenantId</span>
              <input
                value={runtimeDraft.tenantId}
                onChange={(event) => updateRuntimeDraftField("tenantId", event.target.value)}
                placeholder="Paste tenant id"
                autoComplete="off"
              />
            </label>
            <label>
              <span>language</span>
              <input
                value={runtimeDraft.language}
                onChange={(event) => updateRuntimeDraftField("language", event.target.value)}
                placeholder="auto"
                autoComplete="off"
              />
            </label>
          </div>
          <div className="aiva-runtime-form-meta">
            <span>{runtimeBootstrapMessage}</span>
            {AIVA_SEEDED_RUNTIME_SESSION.sessionToken && AIVA_SEEDED_RUNTIME_SESSION.tenantId ? (
              <button className="ghost-button" type="button" onClick={handleUseSeededRuntimeSession}>
                Use demo runtime session
              </button>
            ) : null}
          </div>
        </div>
        <div className="aiva-runtime-panel-actions">
          <button className="primary-button" type="button" onClick={() => void handleConnectRuntime()}>
            Connect runtime
          </button>
          <button className="secondary-button" type="button" onClick={handleDisconnectRuntime}>
            Disconnect
          </button>
          <button className="ghost-button" type="button" onClick={() => void handleResetRuntime()}>
            Reset session
          </button>
          <button className="ghost-button" type="button" onClick={() => void handleCopyDiagnostics()}>
            Copy diagnostic summary
          </button>
        </div>
        <details className="aiva-technical-details" open={showTechnicalDetails} onToggle={(event) => setShowTechnicalDetails(event.currentTarget.open)}>
          <summary>Technical details</summary>
          <div className="aiva-technical-grid">
            <div>
              <span>Runtime endpoint</span>
              <strong>AIVA Runtime</strong>
            </div>
            <div>
              <span>WebSocket URL</span>
              <strong>{websocketUrl}</strong>
            </div>
            <div>
              <span>Connection policy</span>
              <strong>{liveSession ? "Live adapter enabled" : "Demo adapter only"}</strong>
            </div>
            <div>
              <span>Voice channel</span>
              <strong>{voiceStatus}</strong>
            </div>
            <div>
              <span>Chat channel</span>
              <strong>{chatBusy ? "Busy" : "Available"}</strong>
            </div>
            <div>
              <span>Ready state</span>
              <strong>{runtimeSocketReadyState}</strong>
            </div>
            <div>
              <span>Close code</span>
              <strong>{runtimeSocketCloseCode}</strong>
            </div>
            <div>
              <span>Close reason</span>
              <strong>{runtimeSocketCloseReason}</strong>
            </div>
            <div>
              <span>Runtime mode</span>
              <strong>{runtimeDisplayMode()}</strong>
            </div>
            <div>
              <span>Session token</span>
              <strong>{maskToken(liveSession?.patientSessionToken)}</strong>
            </div>
            <div>
              <span>Tenant id</span>
              <strong>{maskToken(liveSession?.tenantId)}</strong>
            </div>
            <div>
              <span>Language</span>
              <strong>{liveSession?.language || voiceLanguage}</strong>
            </div>
            <div>
              <span>Last event</span>
              <strong>{voiceEvents.length > 0 ? voiceEvents[voiceEvents.length - 1] : "No events yet"}</strong>
            </div>
            <div>
              <span>Last error</span>
              <strong>{voiceError || runtimeLastError || "None"}</strong>
            </div>
            <div>
              <span>Connected at</span>
              <strong>{runtimeConnectedAt || "Not connected"}</strong>
            </div>
          </div>
        </details>
      </section>

      <AivaSection
        eyebrow="Workflow simulator"
        title="Every prompt maps to an agentic path"
        subtitle="Demo mode stages the workflow. Live mode can mirror runtime activity if the adapter emits events."
      >
        <div className="aiva-workflow-shell">
          <div className="aiva-workflow-toolbar">
            <span className="aiva-muted">Workflow simulator stays compact by default.</span>
            <button
              className="ghost-button aiva-workflow-toggle"
              type="button"
              onClick={() => setShowWorkflowSimulator((current) => !current)}
            >
              {showWorkflowSimulator ? "Hide workflow simulator" : "Show workflow simulator"}
            </button>
          </div>
          <div className="aiva-workflow-grid">
            {AIVA_WORKFLOW_CARDS.map((card) => {
              const active = selectedPrompt.toLowerCase().includes(card.prompt.split(" ")[0].toLowerCase()) || selectedPrompt === card.prompt;
              return (
                <details key={card.title} className="aiva-workflow-details" open={showWorkflowSimulator || active}>
                  <summary className="aiva-workflow-summary">
                    <div className="aiva-workflow-card-head">
                      <strong>{card.title}</strong>
                      <span>{active ? "Active" : "Ready"}</span>
                    </div>
                  </summary>
                  <article className={`aiva-card aiva-workflow-card${active ? " active" : ""}`}>
                    <p className="aiva-muted">{card.prompt}</p>
                    <ol className="aiva-workflow-steps">
                      {card.steps.map((step, index) => (
                        <li key={step} className={index <= workflowIndex ? "done" : ""}>
                          {step}
                        </li>
                      ))}
                    </ol>
                  </article>
                </details>
              );
            })}
          </div>
        </div>
      </AivaSection>

      <section className="aiva-demo-layout">
        <aside className="aiva-card aiva-left-rail">
          <div className="aiva-panel-heading">
            <div>
              <span className="aiva-kicker">AIVA logo</span>
              <h2>Conversation History</h2>
            </div>
            <button className="ghost-button" type="button" onClick={() => void handleResetRuntime()}>
              {resetBusy ? "Resetting..." : "New Conversation"}
            </button>
          </div>
          <div className="aiva-filter-row">
            <input value={conversationSearch} onChange={(event) => setConversationSearch(event.target.value)} placeholder="Search conversations" />
          </div>
          <div className="aiva-history-groups">
            {(Object.keys(visibleConversations) as Array<AivaConversation["group"]>).map((group) => (
              <div key={group} className="aiva-history-group">
                <strong>{group}</strong>
                <div className="aiva-history-list">
                  {visibleConversations[group].map((conversation) => (
                    <button
                      key={conversation.id}
                      type="button"
                      className={`aiva-history-entry${conversation.id === activeConversationId ? " active" : ""}`}
                      onClick={() => openConversation(conversation)}
                    >
                      {renderHistoryPreview(conversation)}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </aside>

        <main className="aiva-card aiva-center-stage">
          <div className="aiva-panel-heading">
            <div>
              <span className="aiva-kicker">Large Voice Experience</span>
              <h2>AIVA</h2>
            </div>
          <div className={`aiva-status-badge ${voiceStatus}`}>
            <span className="aiva-status-dot" />
            {statusLabel}
          </div>
        </div>
        <div className="aiva-voice-helper">
          <strong>{voiceHelperText}</strong>
          <span>{voiceEvents.length > 0 ? voiceEvents[voiceEvents.length - 1] : "No events yet"}</span>
        </div>

          <div className="aiva-voice-stage">
            <div className="aiva-voice-ring">
              <div className="aiva-waveform" aria-hidden="true">
                {Array.from({ length: 18 }).map((_, index) => (
                  <span
                    key={`wave-${index}`}
                    style={{
                      animationDelay: `${index * 80}ms`,
                      height: `${Math.max(10, 14 + (voiceLevel * 180 + (index % 4) * 5))}px`,
                    }}
                  />
                ))}
              </div>
              <button className="aiva-mic-button" type="button" onClick={() => void startVoiceTurn()} disabled={voiceStatus === "connecting"}>
                🎤
              </button>
            </div>
            <div className="aiva-voice-actions">
              <button className="primary-button" type="button" onClick={() => void startVoiceTurn()} disabled={voiceStatus === "connecting"}>
                {voiceStatus === "idle" ? "Talk to AIVA" : "Speak now"}
              </button>
              <button className="secondary-button" type="button" onClick={handleVoiceStopTurn}>
                Stop
              </button>
              <button className="ghost-button" type="button" onClick={() => setVoiceMuted((current) => !current)}>
                {voiceMuted ? "Unmute" : "Mute"}
              </button>
              <button className="ghost-button" type="button" onClick={handleVoiceEnd}>
                End Call
              </button>
            </div>
            <div className="aiva-voice-state">
              {voiceInfo || (liveSession ? runtimeBootstrapMessage || "Live runtime session ready." : AIVA_DEFAULT_RUNTIME_MESSAGE)}
            </div>
            <div className="aiva-voice-config-grid">
              <label>
                <span>Voice</span>
                <select value={voiceGender} onChange={(event) => setVoiceGender(event.target.value)}>
                  <option>Male</option>
                  <option>Female</option>
                </select>
              </label>
              <label>
                <span>Language</span>
                <select value={voiceLanguage} onChange={(event) => setVoiceLanguage(event.target.value)}>
                  <option>English</option>
                  <option>Hindi</option>
                  <option>Auto Detect</option>
                </select>
              </label>
              <label>
                <span>Speaking Speed</span>
                <select value={voiceSpeed} onChange={(event) => setVoiceSpeed(event.target.value)}>
                  <option>Slow</option>
                  <option>Normal</option>
                  <option>Fast</option>
                </select>
              </label>
              <label>
                <span>Auto VAD</span>
                <button type="button" className={`toggle-button${autoVad ? " active" : ""}`} onClick={() => setAutoVad((current) => !current)}>
                  {autoVad ? "Enabled" : "Disabled"}
                </button>
              </label>
            </div>
            <div className="aiva-voice-meter-row">
              <div className="aiva-voice-meter">
                <span>Speech Recognition</span>
                <strong>{latencyMetrics.speechRecognitionMs ? `${latencyMetrics.speechRecognitionMs} ms` : "—"}</strong>
              </div>
              <div className="aiva-voice-meter">
                <span>Reasoning</span>
                <strong>{latencyMetrics.reasoningMs ? `${latencyMetrics.reasoningMs} ms` : "—"}</strong>
              </div>
              <div className="aiva-voice-meter">
                <span>Voice Response</span>
                <strong>{latencyMetrics.voiceResponseMs ? `${latencyMetrics.voiceResponseMs} ms` : "—"}</strong>
              </div>
            </div>
            <div className="aiva-voice-transcript">
              <span className="aiva-kicker">Live transcript</span>
              <p>{voiceTranscript || "Speak a query to see the live transcript here."}</p>
              <p className="aiva-muted">{voiceAssistant || "AIVA response will appear here after the runtime resolves the request."}</p>
              {voiceAudioUrl ? (
                <button className="ghost-button" type="button" onClick={() => void handleVoiceReplyPlayback()}>
                  Play AIVA response
                </button>
              ) : null}
            </div>
          </div>

          <div className="aiva-chat-panel-stack">
            <div className="aiva-chat-stream aiva-chat-stream-large" aria-label="AIVA conversation">
              {messages.map((message, index) => (
                <div key={`${message.role}-${message.time}-${index}`} className={`aiva-chat-bubble ${message.role}`}>
                  <div className="aiva-chat-bubble-meta">
                    <strong>{message.role === "user" ? "You" : "AIVA"}</strong>
                    <span>{message.time}</span>
                  </div>
                  {renderMarkdownText(message.text)}
                </div>
              ))}
              {assistantTyping ? (
                <div className="aiva-chat-bubble assistant aiva-typing">
                  <div className="aiva-chat-bubble-meta">
                    <strong>AIVA</strong>
                    <span>thinking</span>
                  </div>
                  <p>AIVA is thinking...</p>
                </div>
              ) : null}
              <div ref={chatEndRef} />
            </div>
            <form className="aiva-input-shell" onSubmit={(event) => void handleChatSubmit(event)}>
              <input
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                placeholder="Ask AIVA to book, reschedule, cancel, or look up records"
                aria-label="AIVA chat prompt"
              />
              <button className="primary-button" type="submit" disabled={chatBusy}>
                {chatBusy ? "Sending..." : "Send text message"}
              </button>
            </form>
            <div className="aiva-prompt-grid">
              {DEMO_PROMPTS.map((prompt) => (
                <button key={prompt} className="aiva-prompt-chip" type="button" onClick={() => void handlePromptClick(prompt)}>
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        </main>

        <aside className="aiva-card aiva-right-rail">
          <div className="aiva-panel-heading">
            <div>
              <span className="aiva-kicker">Agent Activity</span>
              <h2>Tool calls</h2>
            </div>
          </div>
          <div className="aiva-timeline">
            {activity.map((entry) => (
              <div key={entry.title} className="aiva-timeline-item">
                <span className={`aiva-timeline-dot ${entry.status}`} />
                <div>
                  <strong>{entry.title}</strong>
                  <p>{entry.detail}</p>
                </div>
              </div>
            ))}
          </div>

          <div className="aiva-panel-section">
            <span className="aiva-kicker">Workflow</span>
            <div className="aiva-status-list">
              {workflowStatuses.map((step) => (
                <div key={step.title} className={`aiva-status-item${step.active ? " active" : ""}${step.complete ? " complete" : ""}`}>
                  <strong>{step.title}</strong>
                  <p>{step.detail}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="aiva-panel-section">
            <span className="aiva-kicker">Conversation memory</span>
            <div className="aiva-memory-grid">
              {recentContext.map((item) => (
                <div key={item.label} className="aiva-memory-card">
                  <span>{item.label}</span>
                  <strong>{item.value}</strong>
                </div>
              ))}
            </div>
          </div>

          <div className="aiva-panel-section">
            <span className="aiva-kicker">Voice settings</span>
            <div className="aiva-settings-stack">
              <button type="button" className={`toggle-button${speakerEnabled ? " active" : ""}`} onClick={() => setSpeakerEnabled((current) => !current)}>
                Speaker {speakerEnabled ? "On" : "Off"}
              </button>
              <button type="button" className={`toggle-button${voiceMuted ? " active" : ""}`} onClick={() => setVoiceMuted((current) => !current)}>
                Mute {voiceMuted ? "On" : "Off"}
              </button>
            </div>
          </div>

          <div className="aiva-panel-section">
            <span className="aiva-kicker">Runtime</span>
            <p className="aiva-muted">
              {liveSession ? "Live AIVA Runtime is configured." : AIVA_DEFAULT_RUNTIME_MESSAGE}
            </p>
            <AivaPillGrid items={["STT", "LLM", "TTS", "Voice gateway", "Chat API"]} />
            {careAiState ? (
              <div className="aiva-memory-card">
                <span>Runtime state</span>
                <strong>{careAiState.currentIntent ?? careAiState.lastAction ?? "Connected"}</strong>
              </div>
            ) : null}
          </div>

          <div className="aiva-panel-section">
            <span className="aiva-kicker">Conversation list</span>
            <div className="aiva-runtime-strip aiva-runtime-strip-compact">
              <span>{conversations.length} sessions</span>
              <span>{runtimeMode}</span>
            </div>
          </div>
        </aside>
      </section>

      {voiceError ? <div className="aiva-inline-error">{voiceError}</div> : null}
      <audio ref={voiceAudioRef} className="aiva-hidden-audio" muted={voiceMuted} controls={false} src={voiceAudioUrl || undefined} onEnded={handleAudioEnded} />
    </div>
  );
}

export function AivaArchitecturePage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA architecture</span>
          <h1>How the platform is layered</h1>
          <p>AIVA is the product layer. AIVA Runtime remains the execution layer. This keeps the microsite separate from the live assistant stack.</p>
        </div>
      </section>

      <section className="aiva-architecture-grid">
        <article className="aiva-card">
          <span className="aiva-kicker">Channels</span>
          <AivaPillGrid items={ARCHITECTURE_CHANNELS} />
        </article>
        <article className="aiva-card">
          <span className="aiva-kicker">AIVA platform</span>
          <AivaPillGrid items={AIVA_PLATFORM_LAYERS} />
        </article>
        <article className="aiva-card">
          <span className="aiva-kicker">Runtime today</span>
          <AivaPillGrid items={CAREAI_RUNTIME_TODAY} />
        </article>
        <article className="aiva-card">
          <span className="aiva-kicker">Business systems</span>
          <AivaPillGrid items={BUSINESS_SYSTEMS} />
        </article>
      </section>
    </div>
  );
}

export function AivaRoadmapPage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA roadmap</span>
          <h1>What ships now, and what comes next</h1>
          <p>Available Today focuses on the productized runtime that already exists. Coming Next is the platform scaffold for a deeper AI suite.</p>
        </div>
      </section>

      <section className="aiva-roadmap-grid">
        <article className="aiva-card">
          <span className="aiva-kicker">Available Today</span>
          <AivaPillGrid items={AVAILABLE_TODAY} />
        </article>
        <article className="aiva-card">
          <span className="aiva-kicker">Coming Next</span>
          <AivaPillGrid items={COMING_NEXT} />
        </article>
      </section>
    </div>
  );
}

function AivaCardGrid({
  children,
}: {
  children: ReactNode;
}) {
  return <div className="aiva-card-grid">{children}</div>;
}

export function AivaAgentsPage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA agents</span>
          <h1>Agent Marketplace</h1>
          <p>Browse reusable assistants designed for enterprise voice, chat, and workflow automation.</p>
        </div>
      </section>
      <section className="aiva-section">
        <AivaCardGrid>
          {AGENT_MARKETPLACE.map((agent) => (
            <article key={agent.name} className="aiva-card aiva-agent-card">
              <div className="aiva-agent-card-head">
                <strong>{agent.name}</strong>
                <span className="aiva-status-chip">{agent.status}</span>
              </div>
              <p className="aiva-muted">{agent.description}</p>
              <div className="aiva-agent-meta">
                <span>Channels</span>
                <strong>Voice / Chat / Web / WhatsApp future</strong>
              </div>
              <div className="aiva-agent-meta">
                <span>Tools</span>
                <strong>{agent.tools.join(" · ")}</strong>
              </div>
              <Link className="secondary-button wide-button" to="/demo">
                Try in demo
              </Link>
            </article>
          ))}
        </AivaCardGrid>
      </section>
    </div>
  );
}

export function AivaPlatformPage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA platform</span>
          <h1>Platform Modules</h1>
          <p>AIVA v1 is a product layer today. Foundation and planned modules are shown clearly.</p>
        </div>
      </section>
      <section className="aiva-section">
        <AivaCardGrid>
          {PLATFORM_MODULES.map((module) => (
            <article key={module.name} className="aiva-card aiva-agent-card">
              <div className="aiva-agent-card-head">
                <strong>{module.name}</strong>
                <span className="aiva-status-chip">{module.status}</span>
              </div>
              <p className="aiva-muted">{module.description}</p>
              <div className="aiva-agent-meta">
                <span>Enterprise value</span>
                <strong>{module.value}</strong>
              </div>
            </article>
          ))}
        </AivaCardGrid>
      </section>
    </div>
  );
}

export function AivaIndustriesPage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA industries</span>
          <h1>Multi-Industry Showcase</h1>
          <p>See how one voice and agent platform can support many business workflows.</p>
        </div>
      </section>
      <section className="aiva-section">
        <AivaCardGrid>
          {INDUSTRY_SHOWCASE.map((industry) => (
            <article key={industry.name} className="aiva-card aiva-agent-card">
              <div className="aiva-agent-card-head">
                <strong>{industry.name}</strong>
                <span className="aiva-status-chip">Use case</span>
              </div>
              <div className="aiva-industry-stack">
                <div><span>Voice</span><strong>{industry.voice}</strong></div>
                <div><span>Chat</span><strong>{industry.chat}</strong></div>
                <div><span>Workflow</span><strong>{industry.workflow}</strong></div>
                <div><span>Example prompt</span><strong>{industry.example}</strong></div>
              </div>
            </article>
          ))}
        </AivaCardGrid>
      </section>
    </div>
  );
}

export function AivaAnalyticsPage() {
  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA analytics</span>
          <h1>Platform telemetry preview</h1>
          <p>Optional overview for product and support teams. This is a UI-only preview in AIVA v1.</p>
        </div>
      </section>
      <section className="aiva-section">
        <AivaCardGrid>
          {[
            { label: "Conversations", value: "24" },
            { label: "Voice sessions", value: "18" },
            { label: "Workflow completions", value: "42" },
            { label: "Escalations", value: "3" },
          ].map((item) => (
            <article key={item.label} className="aiva-card">
              <strong>{item.value}</strong>
              <p className="aiva-muted">{item.label}</p>
            </article>
          ))}
        </AivaCardGrid>
      </section>
    </div>
  );
}
