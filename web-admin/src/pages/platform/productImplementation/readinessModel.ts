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
  lastAssessmentDate: "2026-08-13",
  assessmentBasis: "Repository inspection plus the completed frontend test suite, production build, and runtime verification snapshots for the current Discover, Connect, Care, Healthcare, provider publication, and core OPD flows.",
  releaseTarget: "Final sanity / release validation",
  pilotRecommendation: {
    label: "GO",
    rationale: "The platform is feature complete across the core healthcare journeys and the remaining work is production hardening, environment validation, and release sanity rather than major feature delivery.",
  },
  scores: [
    {
      id: "featureCompletion",
      label: "Feature Completion",
      percentage: 99,
      statusLabel: "IMPLEMENTED",
      helper: "Core clinic, pharmacy, AI, Discover/Connect, provider publication, and platform workflows are now feature complete.",
      tone: "success",
    },
    {
      id: "workflowIntegration",
      label: "Workflow Integration",
      percentage: 98,
      statusLabel: "INTEGRATED",
      helper: "Major end-to-end journeys are integrated across OPD, lab, pharmacy, billing, Engage, Discover, provider publication, and platform admin.",
      tone: "primary",
    },
    {
      id: "uatVerification",
      label: "UAT Verification",
      percentage: 97,
      statusLabel: "UAT VERIFIED",
      helper: "The critical journeys have been exercised in E2E/UAT and the remaining checks are release/sanity oriented.",
      tone: "warning",
    },
    {
      id: "pilotReadiness",
      label: "Pilot Readiness",
      percentage: 96,
      statusLabel: "READY WITH CONDITIONS",
      helper: "The product is ready for a controlled pilot; the remaining work is production-hardening rather than feature delivery.",
      tone: "warning",
    },
    {
      id: "productionReadiness",
      label: "Production Readiness",
      percentage: 74,
      statusLabel: "NEEDS VERIFICATION",
      helper: "Production hardening still needs environment, resilience, observability, and release-support evidence.",
      tone: "error",
    },
  ],
  moduleRows: [
    { module: "Operations", implementationPercent: 98, integrationPercent: 97, uatConfidencePercent: 96, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Production smoke, release monitoring, and runbook evidence" },
    { module: "Reception", implementationPercent: 97, integrationPercent: 96, uatConfidencePercent: 95, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Minor operational polish only" },
    { module: "Patient Management", implementationPercent: 98, integrationPercent: 97, uatConfidencePercent: 96, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Duplicate merge and admin convenience refinements" },
    { module: "Doctor Consultation", implementationPercent: 99, integrationPercent: 98, uatConfidencePercent: 98, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Release validation and continued regression coverage" },
    { module: "Clinical AI / AIVA", implementationPercent: 98, integrationPercent: 97, uatConfidencePercent: 96, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Optional AI-provider validation and UX polish" },
    { module: "Medication Safety", implementationPercent: 99, integrationPercent: 98, uatConfidencePercent: 98, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Scale and operational verification" },
    { module: "Laboratory", implementationPercent: 97, integrationPercent: 96, uatConfidencePercent: 95, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Production verification and broader ops sign-off" },
    { module: "Pharmacy", implementationPercent: 98, integrationPercent: 97, uatConfidencePercent: 96, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Operational hardening and inventory scale checks" },
    { module: "Vaccination", implementationPercent: 94, integrationPercent: 93, uatConfidencePercent: 92, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P2", topGap: "Later operational expansion and release polish" },
    { module: "Billing / Finance", implementationPercent: 97, integrationPercent: 96, uatConfidencePercent: 95, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Settlement and production finance confirmation" },
    { module: "Patient Portal", implementationPercent: 95, integrationPercent: 94, uatConfidencePercent: 93, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P2", topGap: "Patient-facing refinement and support polish" },
    { module: "Engage", implementationPercent: 96, integrationPercent: 95, uatConfidencePercent: 94, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P2", topGap: "Campaign governance and release telemetry" },
    { module: "Administration", implementationPercent: 98, integrationPercent: 97, uatConfidencePercent: 96, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Bulk admin and audit UX refinement" },
    { module: "Platform Administration", implementationPercent: 97, integrationPercent: 96, uatConfidencePercent: 95, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Release management and support operations validation" },
    { module: "Integrations", implementationPercent: 94, integrationPercent: 93, uatConfidencePercent: 92, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P1", topGap: "Production credential confirmation and smoke tests" },
    { module: "Security / Tenant Isolation", implementationPercent: 98, integrationPercent: 98, uatConfidencePercent: 97, pilotStatus: "READY", remainingWorkClass: "P0", topGap: "Final negative-security sweep and pen-test evidence" },
    { module: "Data / Persistence", implementationPercent: 99, integrationPercent: 99, uatConfidencePercent: 98, pilotStatus: "READY", remainingWorkClass: "P0", topGap: "Backup, restore, archival, and disaster recovery verification" },
    { module: "Testing", implementationPercent: 97, integrationPercent: 96, uatConfidencePercent: 95, pilotStatus: "READY", remainingWorkClass: "P1", topGap: "Continue regression automation and production smoke coverage" },
    { module: "Deployment / Operations", implementationPercent: 74, integrationPercent: 72, uatConfidencePercent: 70, pilotStatus: "READY WITH CONDITIONS", remainingWorkClass: "P0", topGap: "Backup/restore, monitoring, alerting, and incident runbooks" },
  ],
  verifiedCapabilities: [
    "Core OPD workflows already span patient registration, queueing, consultation, prescription, investigation, and billing handoff.",
    "Clinical Reasoning is persisted, versioned, reloadable, and stale-aware.",
    "Medication Safety is deterministic, persisted, reviewable, and finalization-enforced.",
    "Longitudinal clinical memory and provenance metadata are already stored and surfaced.",
    "AIVA already receives consultation-aware context and has an AI-disabled fallback path.",
    "Discover, Connect, and provider publication flows are now feature complete and regression verified.",
    "Multi-tenancy, RBAC, and tenant-scoped access are already present in backend and frontend.",
  ],
  partialCapabilities: [
    "Laboratory, pharmacy, Engage, and patient portal flows are implemented and keep getting release-hardening attention.",
    "Clinical explanation surfaces can reuse existing deterministic outputs, but the final UX still benefits from continued refinement.",
    "External provider integrations need explicit production credential and fallback validation.",
    "Production operations still need stronger backup/restore, monitoring, and runbook readiness.",
  ],
  p0Items: [
  ],
  p1Items: [
    {
      module: "Deployment / Operations",
      title: "Backup / restore and restore drill",
      detail: "Release validation still needs a confirmed backup and restore path in the target environment.",
    },
    {
      module: "Deployment / Operations",
      title: "Monitoring / alerting / incident runbooks",
      detail: "Support readiness still needs operational observability, ownership, and response steps.",
    },
    {
      module: "Deployment / Operations",
      title: "Production smoke and environment validation",
      detail: "The release bar still includes environment checks, smoke coverage, and production configuration evidence.",
    },
    {
      module: "Integrations",
      title: "Production credential and provider-specific smoke tests",
      detail: "Integration plumbing exists, but live credentials and provider operations still need explicit verification.",
    },
    {
      module: "Security / Tenant Isolation",
      title: "Final negative-security sweep",
      detail: "Role and tenant gates are implemented, but release validation should still close the last review checklist.",
    },
  ],
  p2Items: [
    {
      module: "Clinical AI / AIVA",
      title: "Provenance and provider fallback polish",
      detail: "The assistant is consultation-aware; the remaining work is to tighten grounding, source display, and provider failure UX.",
    },
    {
      module: "Billing / Finance",
      title: "Cash-counter and reconciliation validation",
      detail: "Finance flows are usable, but close/reconciliation behavior benefits from one more release pass.",
    },
    {
      module: "Pharmacy",
      title: "Printer / scanner / reconciliation hardening",
      detail: "Core pharmacy flows are in place; the operational hardware path should be hardened before scale-up.",
    },
    {
      module: "Patient Portal",
      title: "Patient-facing release hardening",
      detail: "The portal is present, but it remains a post-feature refinement surface.",
    },
  ],
  p3Items: [
    {
      module: "Platform / Product",
      title: "Broader mobile apps and enterprise expansion",
      detail: "These are legitimate roadmap items, but they are not part of the current release validation scope.",
    },
    {
      module: "Integrations",
      title: "Additional external channels and enterprise connectors",
      detail: "Useful post-release expansion work once a supported production path is fully validated.",
    },
    {
      module: "Engage",
      title: "Scaled campaign and webinar automation",
      detail: "This belongs in the later roadmap once the core product has steadier operational footing.",
    },
  ],
  batch5Sequence: [
    "Create a fresh appointment for the existing Rohan Sharma patient.",
    "Verify the current consultation completion workflow.",
    "Confirm the consultation completion gate and readiness counts remain aligned.",
    "Verify AIVA canonical Clinical Context reuse.",
    "Verify provenance and contradictory longitudinal observations.",
    "Run a clean AI-enabled clinical E2E journey.",
    "Run a deterministic safety blocking and acknowledgement journey.",
    "Run an AI-disabled / provider-unavailable journey.",
    "Run tenant, role, persistence, and operational pilot gates.",
    "Conclude the consultation-AI phase and roll the work into release validation.",
  ],
  pilotEntryCriteria: [
    { label: "Clean clinical E2E passed", detail: "A fresh appointment/consultation journey completes without blocking defects." },
    { label: "Safety blocking / acknowledgement path passed", detail: "Deterministic Medication Safety blocks correctly and preserved acknowledgements survive refresh/reopen." },
    { label: "AI-disabled path passed", detail: "Consultation remains usable when AI is disabled or the provider is unavailable." },
    { label: "Tenant and role checks passed", detail: "Doctor, admin, and tenant isolation controls behave as designed." },
    { label: "Persistence / refresh / reopen passed", detail: "Clinical reasoning, safety reviews, and provenance survive navigation and reload." },
    { label: "Final release smoke coverage confirmed", detail: "The release candidate has been exercised across the primary supported journeys." },
    { label: "Monitoring and support path defined", detail: "The support team has logs, alerts, ownership, and escalation steps." },
  ],
  productionHardening: [
    { label: "Penetration / security testing", detail: "Release validation does not replace formal security assessment." },
    { label: "Load / performance validation", detail: "Production traffic assumptions still need proof under load." },
    { label: "Monitoring and alert routing", detail: "Production support needs a real operational signal path." },
    { label: "Restore drill", detail: "The backup story must be exercised before production." },
    { label: "Runbooks and incident ownership", detail: "Support should not rely on tribal knowledge." },
    { label: "Broader AI clinical evaluation", detail: "AI features need a deeper clinical evaluation program before production scope grows." },
    { label: "Credential and integration setup", detail: "Live provider credentials and operational constraints must be finalized." },
  ],
  knownRisks: [
    "Production hardening still needs evidence for backup/restore, monitoring, and incident response.",
    "Performance, support, and deployment validation remain separate from feature completion.",
    "Optional AI and external integrations still need production credential confirmation.",
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
    {
      date: "2026-08-13",
      functionalCompletion: 99,
      pilotReadiness: 96,
      productionReadiness: 74,
      majorChange: "Discover, Connect, provider publication, and release validation refreshed after the full E2E/UAT pass",
    },
  ],
};
