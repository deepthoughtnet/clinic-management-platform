export type ReadinessTone = "success" | "primary" | "warning" | "error" | "info";

export type ReadinessScore = {
  id: "featureCompletion" | "workflowIntegration" | "uatVerification" | "pilotReadiness" | "productionReadiness";
  label: string;
  percentage: number;
  statusLabel: "IMPLEMENTED" | "INTEGRATED" | "UAT VERIFIED" | "READY WITH CONDITIONS" | "NEEDS VERIFICATION";
  helper: string;
  tone: ReadinessTone;
};

export type PilotRecommendation = "GO" | "CONDITIONAL GO" | "NO-GO";

export type RemainingWorkClass = "P0" | "P1" | "P2" | "P3";

export type ModuleReadinessRow = {
  module: string;
  implementationPercent: number;
  integrationPercent: number;
  uatConfidencePercent: number;
  pilotStatus: "READY" | "READY WITH CONDITIONS" | "NEEDS FOCUSED UAT" | "BLOCKED" | "DEFERRED FROM PILOT";
  remainingWorkClass: RemainingWorkClass;
  topGap: string;
};

export type PriorityItem = {
  module: string;
  title: string;
  detail: string;
};

export type ReadinessChecklistItem = {
  label: string;
  detail: string;
};

export type ReadinessModel = {
  lastAssessmentDate: string;
  assessmentBasis: string;
  releaseTarget: string;
  pilotRecommendation: {
    label: PilotRecommendation;
    rationale: string;
  };
  scores: ReadinessScore[];
  moduleRows: ModuleReadinessRow[];
  verifiedCapabilities: string[];
  partialCapabilities: string[];
  p0Items: PriorityItem[];
  p1Items: PriorityItem[];
  p2Items: PriorityItem[];
  p3Items: PriorityItem[];
  batch5Sequence: string[];
  pilotEntryCriteria: ReadinessChecklistItem[];
  productionHardening: ReadinessChecklistItem[];
  knownRisks: string[];
  openQuestions: string[];
  history: Array<{
    date: string;
    functionalCompletion: number;
    pilotReadiness: number;
    productionReadiness: number;
    majorChange: string;
  }>;
};

export const readinessModel: ReadinessModel = {
  lastAssessmentDate: "2026-07-14",
  assessmentBasis: "Repository inspection plus runtime verification snapshots of the active consultation, safety review, and reasoning flows.",
  releaseTarget: "Controlled pilot for the core OPD clinical flow",
  pilotRecommendation: {
    label: "CONDITIONAL GO",
    rationale: "The core OPD journey is implemented, integrated, and largely UAT-verified, but consultation-completion alignment, operational hardening, and a few non-core modules still need pilot-preparation work.",
  },
  scores: [
    {
      id: "featureCompletion",
      label: "Feature Completion",
      percentage: 84,
      statusLabel: "IMPLEMENTED",
      helper: "Core clinic, pharmacy, AI, and platform workflows are substantially implemented.",
      tone: "success",
    },
    {
      id: "workflowIntegration",
      label: "Workflow Integration",
      percentage: 79,
      statusLabel: "INTEGRATED",
      helper: "Primary clinical and pharmacy flows are integrated, with a few operational joins still tightening.",
      tone: "primary",
    },
    {
      id: "uatVerification",
      label: "UAT Verification",
      percentage: 74,
      statusLabel: "UAT VERIFIED",
      helper: "Major journeys have been exercised in UAT, but secondary areas still need cleaner evidence.",
      tone: "warning",
    },
    {
      id: "pilotReadiness",
      label: "Pilot Readiness",
      percentage: 67,
      statusLabel: "READY WITH CONDITIONS",
      helper: "A controlled pilot is feasible if the scope is constrained and the P0 items are closed.",
      tone: "warning",
    },
    {
      id: "productionReadiness",
      label: "Production Readiness",
      percentage: 56,
      statusLabel: "NEEDS VERIFICATION",
      helper: "Production hardening still lags behind pilot readiness in ops, security, and restore posture.",
      tone: "error",
    },
  ],
  moduleRows: [
    { module: "Operations", implementationPercent: 88, integrationPercent: 84, uatConfidencePercent: 81, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Queue/day-board polish and live handoff tuning" },
    { module: "Reception", implementationPercent: 86, integrationPercent: 83, uatConfidencePercent: 80, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Check-in and intake edge-case coverage" },
    { module: "Patient Management", implementationPercent: 90, integrationPercent: 88, uatConfidencePercent: 86, pilotStatus: "READY", remainingWorkClass: "P2", topGap: "Duplicate merge workflow" },
    { module: "Doctor Consultation", implementationPercent: 91, integrationPercent: 89, uatConfidencePercent: 88, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P0", topGap: "Consultation completion review alignment" },
    { module: "Clinical AI / AIVA", implementationPercent: 84, integrationPercent: 80, uatConfidencePercent: 76, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Provenance/gating polish and provider fallback verification" },
    { module: "Medication Safety", implementationPercent: 95, integrationPercent: 93, uatConfidencePercent: 92, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Open review-generation semantics questions" },
    { module: "Laboratory", implementationPercent: 64, integrationPercent: 58, uatConfidencePercent: 48, pilotStatus: "NEEDS FOCUSED UAT", remainingWorkClass: "P0", topGap: "End-to-end result publication and contradiction handling" },
    { module: "Pharmacy", implementationPercent: 89, integrationPercent: 87, uatConfidencePercent: 84, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Hardware/print/reconciliation polish" },
    { module: "Vaccination", implementationPercent: 60, integrationPercent: 55, uatConfidencePercent: 42, pilotStatus: "DEFERRED FROM PILOT", remainingWorkClass: "P3", topGap: "Workflow completion" },
    { module: "Billing / Finance", implementationPercent: 86, integrationPercent: 84, uatConfidencePercent: 82, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Reconciliation and close validation" },
    { module: "Patient Portal", implementationPercent: 68, integrationPercent: 62, uatConfidencePercent: 54, pilotStatus: "DEFERRED FROM PILOT", remainingWorkClass: "P2", topGap: "Patient-facing release hardening" },
    { module: "Engage", implementationPercent: 71, integrationPercent: 67, uatConfidencePercent: 60, pilotStatus: "DEFERRED FROM PILOT", remainingWorkClass: "P2", topGap: "Provider operations and campaign governance" },
    { module: "Administration", implementationPercent: 88, integrationPercent: 86, uatConfidencePercent: 84, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Bulk admin and audit UX polish" },
    { module: "Platform Administration", implementationPercent: 84, integrationPercent: 82, uatConfidencePercent: 79, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Commercial admin and tenant lifecycle extras" },
    { module: "Integrations", implementationPercent: 66, integrationPercent: 61, uatConfidencePercent: 55, pilotStatus: "NEEDS FOCUSED UAT", remainingWorkClass: "P1", topGap: "External credentials and provider-specific operations" },
    { module: "Security / Tenant Isolation", implementationPercent: 90, integrationPercent: 88, uatConfidencePercent: 86, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P0", topGap: "Penetration and negative-security sweep" },
    { module: "Data / Persistence", implementationPercent: 92, integrationPercent: 91, uatConfidencePercent: 89, pilotStatus: "READY", remainingWorkClass: "P0", topGap: "Restore drill and archival verification" },
    { module: "Testing", implementationPercent: 78, integrationPercent: 75, uatConfidencePercent: 72, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Broader regression automation" },
    { module: "Deployment / Operations", implementationPercent: 60, integrationPercent: 56, uatConfidencePercent: 48, pilotStatus: "BLOCKED", remainingWorkClass: "P0", topGap: "Backup/restore, monitoring, and incident runbooks" },
  ],
  verifiedCapabilities: [
    "Canonical Clinical Context already aggregates consultation, prescriptions, documents, labs, longitudinal memory, and AI-ready prompt context.",
    "Clinical Reasoning is persisted, versioned, reloadable, and stale-aware.",
    "Medication Safety is deterministic, persisted, reviewable, and finalization-enforced.",
    "Longitudinal clinical memory and provenance metadata are already stored and surfaced.",
    "AIVA already receives consultation-aware context and has an AI-disabled fallback path.",
    "Patient, appointment, consultation, prescription, billing, pharmacy, and inventory core flows are implemented.",
    "Multi-tenancy, RBAC, and tenant-scoped access are already present in backend and frontend.",
  ],
  partialCapabilities: [
    "Consultation completion review is richer in the frontend than in the backend guard.",
    "Laboratory end-to-end publication and contradiction handling still need focused runtime verification.",
    "Patient portal and Engage are implemented, but they are not yet the preferred pilot surface.",
    "Production operations need stronger backup/restore, monitoring, and runbook readiness.",
    "Clinical explanation surfaces can reuse existing deterministic outputs, but the final UX still needs consolidation.",
    "External provider integrations need explicit production credential and fallback validation.",
  ],
  p0Items: [
    {
      module: "Doctor Consultation",
      title: "Consultation completion review alignment",
      detail: "The backend completion gate still needs to agree with the richer readiness checklist in the workspace.",
    },
    {
      module: "Deployment / Operations",
      title: "Backup / restore and restore drill",
      detail: "Controlled pilot requires a confirmed backup and restore path outside the local test database.",
    },
    {
      module: "Deployment / Operations",
      title: "Monitoring / alerting / incident runbooks",
      detail: "A pilot cannot rely on the UI alone; operational observability and response steps must be in place.",
    },
    {
      module: "Laboratory",
      title: "Lab end-to-end publication and contradiction handling",
      detail: "The laboratory workflow still needs clean runtime verification before broader pilot claims.",
    },
    {
      module: "Security / Tenant Isolation",
      title: "Negative-security sweep across pilot paths",
      detail: "Role and tenant gates are implemented, but pilot entry should include a final sweep of the active surfaces.",
    },
  ],
  p1Items: [
    {
      module: "Clinical AI / AIVA",
      title: "Provenance and provider fallback polish",
      detail: "The assistant is consultation-aware; the remaining work is to tighten grounding, source display, and provider failure UX.",
    },
    {
      module: "Billing / Finance",
      title: "Cash-counter and reconciliation validation",
      detail: "Finance flows are usable, but close/reconciliation behavior needs another pilot-prep pass.",
    },
    {
      module: "Pharmacy",
      title: "Printer / scanner / reconciliation hardening",
      detail: "Core pharmacy flows are in place; the operational hardware path should be hardened before scale-up.",
    },
    {
      module: "Integrations",
      title: "Production credential and provider-specific smoke tests",
      detail: "Integration plumbing exists, but live credentials and provider operations still need explicit verification.",
    },
    {
      module: "Testing",
      title: "Broader regression automation",
      detail: "Coverage is good enough for development, but pilot operations need a wider and cleaner automated gate.",
    },
  ],
  p2Items: [
    {
      module: "Patient Portal",
      title: "Patient-facing release hardening",
      detail: "The portal is present, but it is not the first pilot surface and can remain focused on refinement.",
    },
    {
      module: "Engage",
      title: "Provider operations and campaign governance",
      detail: "Engagement automation should be expanded after the core OPD pilot is steady.",
    },
    {
      module: "Vaccination",
      title: "Workflow completion",
      detail: "Vaccination is deferred from the first pilot and belongs in later operational expansion.",
    },
    {
      module: "Clinical AI / AIVA",
      title: "Clinical explanation UX polish",
      detail: "The deterministic and consultation-aware foundation exists; the explanation layer can be tightened later.",
    },
  ],
  p3Items: [
    {
      module: "Platform / Product",
      title: "Broader mobile apps and enterprise expansion",
      detail: "These are legitimate roadmap items, but they are not part of the controlled pilot scope.",
    },
    {
      module: "Integrations",
      title: "Additional external channels and enterprise connectors",
      detail: "Useful post-pilot expansion work once a supported production path is fully validated.",
    },
    {
      module: "Engage",
      title: "Scaled campaign and webinar automation",
      detail: "This belongs in the post-pilot roadmap once the core product has steadier operational footing.",
    },
  ],
  batch5Sequence: [
    "Create a fresh appointment for the existing Rohan Sharma patient.",
    "Verify the current consultation completion workflow.",
    "Fix only the confirmed completion/readiness gaps.",
    "Verify AIVA canonical Clinical Context reuse.",
    "Verify provenance and contradictory longitudinal observations.",
    "Run a clean AI-enabled clinical E2E journey.",
    "Run a deterministic safety blocking and acknowledgement journey.",
    "Run an AI-disabled / provider-unavailable journey.",
    "Run tenant, role, persistence, and operational pilot gates.",
    "Conclude the consultation-AI phase.",
  ],
  pilotEntryCriteria: [
    { label: "Clean clinical E2E passed", detail: "A fresh appointment/consultation journey completes without blocking defects." },
    { label: "Safety blocking / acknowledgement path passed", detail: "Deterministic Medication Safety blocks correctly and preserved acknowledgements survive refresh/reopen." },
    { label: "AI-disabled path passed", detail: "Consultation remains usable when AI is disabled or the provider is unavailable." },
    { label: "Tenant and role checks passed", detail: "Doctor, admin, and tenant isolation controls behave as designed." },
    { label: "Persistence / refresh / reopen passed", detail: "Clinical reasoning, safety reviews, and provenance survive navigation and reload." },
    { label: "Backup and restore readiness confirmed", detail: "A pilot-safe restore path has been validated on the target environment." },
    { label: "Monitoring and support path defined", detail: "The support team has logs, alerts, ownership, and escalation steps." },
  ],
  productionHardening: [
    { label: "Penetration / security testing", detail: "Pilot does not replace formal security assessment." },
    { label: "Load / performance validation", detail: "Production traffic assumptions still need proof under load." },
    { label: "Monitoring and alert routing", detail: "Production support needs a real operational signal path." },
    { label: "Restore drill", detail: "The backup story must be exercised before production." },
    { label: "Runbooks and incident ownership", detail: "Support should not rely on tribal knowledge." },
    { label: "Broader AI clinical evaluation", detail: "AI features need a deeper clinical evaluation program before production scope grows." },
    { label: "Credential and integration setup", detail: "Live provider credentials and operational constraints must be finalized." },
  ],
  knownRisks: [
    "The consultation completion gate still needs backend and frontend alignment.",
    "Laboratory and patient-portal paths are implemented but remain the least polished parts of the current OPD journey.",
    "Production operations are still too thin to treat pilot and production readiness as the same bar.",
  ],
  openQuestions: [
    "How many distinct Medication Safety generations should be treated as the effective current review when a snapshot recurs later in the same prescription timeline?",
    "Should acknowledgement/override data remain tied only to the review row, or should the finalized snapshot expose a smaller read-only projection for clinician UX?",
  ],
  history: [
    {
      date: "2026-05-25",
      functionalCompletion: 72,
      pilotReadiness: 76,
      productionReadiness: 61,
      majorChange: "Initial repository inspection assessment",
    },
    {
      date: "2026-07-14",
      functionalCompletion: 84,
      pilotReadiness: 67,
      productionReadiness: 56,
      majorChange: "Batch 5 runtime verification and readiness consolidation",
    },
  ],
};

