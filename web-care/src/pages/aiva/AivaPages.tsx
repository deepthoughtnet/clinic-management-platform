import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import type { PatientPortalSession } from "../../api/patientPortal";
import { isPatientPortalPatientSession } from "../../api/patientPortal";
import { formatDisplayTime } from "../../utils/dateDisplay";

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
    description: "Reuses existing clinic, billing, lab, and patient portal systems without duplicating runtime code.",
  },
  {
    title: "Powered by AIVA",
    description: "AIVA v1 is a product layer on top of the existing AIVA runtime, voice gateway, and tools.",
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
  "Existing AIVA Runtime",
  "STT",
  "LLM",
  "TTS",
  "Workflow Engine",
  "Appointment Tools",
  "Billing Tools",
  "Lab Tools",
  "Human Handoff",
] as const;

const BUSINESS_SYSTEMS = ["Jeevanam Healthcare", "Future Finance Automation", "Future HRMS", "Future CRM"] as const;

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
    detail: "AIVA runtime can invoke clinic tools for appointments, bills, prescriptions, and lab lookup.",
    status: "active",
  },
  {
    title: "Human handoff",
    detail: "Escalate to staff when policy or confidence requires a person.",
    status: "queued",
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

function liveRuntimePath(session: PatientPortalSession | null) {
  if (isPatientPortalPatientSession(session)) {
    return "/patient/careai";
  }
  return "/patient/login?next=%2Fpatient%2Fcareai";
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
    return "I can surface cancellation guidance and route the request to the clinic or AIVA handoff flow if policy requires it.";
  }
  if (normalized.includes("prescription")) {
    return "I can surface the latest published prescription and open the patient-safe document when the session permits it.";
  }
  if (normalized.includes("lab")) {
    return "I can retrieve the latest lab report status and point to the patient portal download when it is ready.";
  }
  if (normalized.includes("bill") || normalized.includes("receipt")) {
    return "I can look up outstanding dues, recent payments, and the latest receipt summary.";
  }
  return "I can route that request into the existing AIVA runtime and decide whether a tool call or human handoff is the safest next step.";
}

export function AivaLandingPage({ session }: { session: PatientPortalSession | null }) {
  return (
    <div className="aiva-page">
      <section className="aiva-hero aiva-shell-card">
        <div className="aiva-hero-copy">
          <span className="eyebrow">AIVA</span>
          <h1>AI Voice Intelligence &amp; Agentic Workflow Platform</h1>
          <p className="aiva-tagline">Talk. Understand. Act.</p>
          <p className="aiva-hero-description">
            AIVA is the product layer for reusable AI voice and agent workflows. It presents the existing AIVA runtime as an enterprise platform
            for conversations, tooling, and human handoff without rebuilding the engine underneath.
          </p>
          <div className="cta-row">
            <Link className="primary-button" to="/aiva/demo">
              Talk to AIVA
            </Link>
            <Link className="secondary-button" to="/aiva/architecture">
              View Architecture
            </Link>
            <Link className="ghost-button" to={liveRuntimePath(session)}>
              Open live AIVA runtime
            </Link>
          </div>
        </div>
        <div className="aiva-hero-panel">
          <div className="aiva-signal-card">
            <span className="aiva-kicker">Powered by existing runtime</span>
            <h3>AIVA Runtime</h3>
            <p>STT, LLM, TTS, workflows, appointment tools, billing tools, lab tools, and human handoff remain the execution layer.</p>
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
        eyebrow="Platform"
        title="Reusable enterprise assistant capabilities"
        subtitle="AIVA is designed as a productized platform surface, not a separate backend runtime."
      >
        <AivaFeatureGrid />
      </AivaSection>

      <AivaSection
        eyebrow="Runtime"
        title="Powered by AIVA"
        subtitle="The launch point is a reusable engine that can be extended into new products without destabilizing the clinic platform."
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

export function AivaDemoPage({ session }: { session: PatientPortalSession | null }) {
  const [selectedPrompt, setSelectedPrompt] = useState<string>(DEMO_PROMPTS[0]);
  const [messages, setMessages] = useState<AivaMessage[]>([
    {
      role: "assistant",
      text: "Welcome to AIVA demo mode. This microsite uses demo-safe content unless you open the live AIVA runtime from a verified patient session.",
      time: "09:00",
    },
  ]);
  const [activity, setActivity] = useState<AivaActivity[]>([
    { title: "Session ready", detail: "Demo-safe microsite loaded", status: "done" },
    { title: "Intent routing", detail: "Waiting for prompt", status: "active" },
    { title: "Tool execution", detail: "Uses the existing AIVA runtime when opened live", status: "queued" },
  ]);
  const [workflowStage, setWorkflowStage] = useState(1);

  const livePath = liveRuntimePath(session);
  const liveSessionLabel = isPatientPortalPatientSession(session) ? "Live patient session detected" : "Demo mode";

  const workflowStatuses = useMemo(
    () =>
      WORKFLOW_STEPS.map((step, index) => ({
        ...step,
        active: index === workflowStage,
      })),
    [workflowStage],
  );

  function runPrompt(prompt: string) {
    setSelectedPrompt(prompt);
    const assistantText = buildResponse(prompt);
    const timestamp = formatDisplayTime(new Date());
    setMessages((current) => [
      ...current,
      { role: "user", text: prompt, time: timestamp },
      { role: "assistant", text: assistantText, time: timestamp },
    ]);
    setActivity((current) => [
      { title: "Prompt captured", detail: prompt, status: "done" },
      { title: "Intent normalized", detail: assistantText, status: "active" },
      ...current.slice(0, 1),
    ]);
    setWorkflowStage((current) => (current + 1) % WORKFLOW_STEPS.length);
  }

  return (
    <div className="aiva-page">
      <section className="aiva-page-header aiva-shell-card">
        <div>
          <span className="eyebrow">AIVA demo</span>
          <h1>Try the assistant flow without exposing real patient data</h1>
          <p>
            AIVA v1 is demo-safe by default. If you have a verified patient session, you can open the live AIVA runtime for real appointment,
            billing, prescription, and lab lookups.
          </p>
        </div>
        <div className="aiva-header-meta">
          <div className="aiva-meta-card">
            <span>Mode</span>
            <strong>{liveSessionLabel}</strong>
          </div>
          <div className="aiva-meta-card">
            <span>Runtime</span>
            <strong>Existing AIVA runtime</strong>
          </div>
          <div className="aiva-meta-card">
            <span>Safety</span>
            <strong>No real patient data exposed</strong>
          </div>
        </div>
      </section>

      <section className="aiva-demo-grid">
        <article className="aiva-card aiva-voice-panel">
          <span className="aiva-kicker">Voice panel</span>
          <h2>Voice-first interaction</h2>
          <p className="aiva-muted">
            This panel previews the voice experience. Live microphone and voice gateway interactions remain in the existing AIVA runtime.
          </p>
          <div className="aiva-tall-stat">
            <strong>Talk to AIVA</strong>
            <span>Appointment booking, reschedule guidance, prescription lookup, lab lookup, billing lookup, handoff</span>
          </div>
          <AivaPillGrid items={["STT", "LLM", "TTS", "Workflow engine", "Human handoff"]} />
          <div className="cta-row">
            <Link className="primary-button" to={livePath}>
              Open live AIVA runtime
            </Link>
            <Link className="ghost-button" to="/aiva/architecture">
              Review architecture
            </Link>
          </div>
        </article>

        <article className="aiva-card aiva-chat-panel">
          <span className="aiva-kicker">Chat panel</span>
          <h2>Demo-safe chat flow</h2>
          <div className="aiva-chat-stream" aria-label="AIVA demo conversation">
            {messages.map((message, index) => (
              <div key={`${message.role}-${message.time}-${index}`} className={`aiva-chat-bubble ${message.role}`}>
                <div className="aiva-chat-bubble-meta">
                  <strong>{message.role === "user" ? "You" : "AIVA"}</strong>
                  <span>{message.time}</span>
                </div>
                <p>{message.text}</p>
              </div>
            ))}
          </div>
          <div className="aiva-input-shell">
            <input
              value={selectedPrompt}
              onChange={(event) => setSelectedPrompt(event.target.value)}
              placeholder="Type a demo prompt"
              aria-label="AIVA demo prompt"
            />
            <button className="primary-button" type="button" onClick={() => runPrompt(selectedPrompt.trim() || DEMO_PROMPTS[0])}>
              Send
            </button>
          </div>
          <div className="aiva-prompt-grid">
            {DEMO_PROMPTS.map((prompt) => (
              <button key={prompt} className="aiva-prompt-chip" type="button" onClick={() => runPrompt(prompt)}>
                {prompt}
              </button>
            ))}
          </div>
        </article>
      </section>

      <section className="aiva-demo-grid aiva-demo-grid-secondary">
        <article className="aiva-card">
          <span className="aiva-kicker">Suggested prompts</span>
          <h2>Sample prompts for AIVA</h2>
          <AivaPillGrid items={DEMO_PROMPTS} />
        </article>

        <article className="aiva-card">
          <span className="aiva-kicker">Agent activity</span>
          <h2>Workflow timeline</h2>
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
        </article>

        <article className="aiva-card">
          <span className="aiva-kicker">Workflow status</span>
          <h2>Current execution path</h2>
          <div className="aiva-status-list">
            {workflowStatuses.map((step) => (
              <div key={step.title} className={`aiva-status-item ${step.active ? "active" : ""}`}>
                <strong>{step.title}</strong>
                <p>{step.detail}</p>
              </div>
            ))}
          </div>
        </article>
      </section>
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
          <p>
            AIVA is the product layer. AIVA Runtime remains the execution layer. This keeps the microsite separate from the live assistant stack.
          </p>
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
