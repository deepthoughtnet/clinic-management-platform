export type ImplementationStatus = "Complete" | "In Progress" | "Review" | "Blocked" | "Pending";

export type ProductModule = {
  id: string;
  name: string;
  area: string;
  owner: string;
  release: "R2" | "R3" | "R4";
  status: ImplementationStatus;
  completionPercent: number;
  productionReadinessPercent: number;
  uatPercent: number;
  backendPercent: number;
  frontendPercent: number;
  apiPercent: number;
  rbacPercent: number;
  auditPercent: number;
  pdfPercent: number;
  openDefects: number;
  criticalDefects: number;
  technicalDebt: number;
  notes: string;
};

export type WorkflowStep = {
  name: string;
  status: ImplementationStatus;
  backendStatus: ImplementationStatus;
  frontendStatus: ImplementationStatus;
  apiStatus: ImplementationStatus;
  uatStatus: ImplementationStatus;
  productionStatus: ImplementationStatus;
};

export type Workflow = {
  id: string;
  name: string;
  area: string;
  release: "R2" | "R3" | "R4";
  status: ImplementationStatus;
  completionPercent: number;
  productionReadinessPercent: number;
  owner: string;
  steps: WorkflowStep[];
  blockers: string[];
  notes: string;
};

export type Feature = {
  id: string;
  module: string;
  name: string;
  priority: "Critical" | "High" | "Medium" | "Low";
  release: "R2" | "R3" | "R4";
  owner: string;
  status: ImplementationStatus;
  backendStatus: ImplementationStatus;
  frontendStatus: ImplementationStatus;
  apiStatus: ImplementationStatus;
  uatStatus: ImplementationStatus;
  productionStatus: ImplementationStatus;
  defects: string;
  notes: string;
};

export type Capability = {
  id: string;
  name: string;
  coveragePercent: number;
  status: ImplementationStatus;
  modulesCovered: string[];
  gaps: string[];
  notes: string;
};

export type Release = {
  id: string;
  name: string;
  target: string;
  status: "In Progress" | "Planned" | "Future";
  progressPercent: number;
  includedModules: string[];
  blockers: string[];
  exitCriteria: string[];
};

export type Defect = {
  id: string;
  module: string;
  title: string;
  priority: "Critical" | "High" | "Medium" | "Low";
  status: "Open" | "In Progress" | "Resolved" | "Closed";
  owner: string;
  release: "R2" | "R3" | "R4";
  notes: string;
};

export type TechnicalDebt = {
  id: string;
  module: string;
  title: string;
  severity: "High" | "Medium" | "Low";
  category: string;
  status: "Open" | "Planned" | "Resolved";
  notes: string;
};

export type UATScenario = {
  id: string;
  module: string;
  workflow: string;
  scenario: string;
  status: "Pass" | "Review" | "Pending" | "Blocked";
  priority: "Critical" | "High" | "Medium" | "Low";
  tester: string;
  evidenceStatus: "Attached" | "Partial" | "Pending";
  notes: string;
};

function makeModule(module: Omit<ProductModule, "backendPercent" | "frontendPercent" | "apiPercent" | "rbacPercent" | "auditPercent" | "pdfPercent"> & Partial<Pick<ProductModule, "backendPercent" | "frontendPercent" | "apiPercent" | "rbacPercent" | "auditPercent" | "pdfPercent">>): ProductModule {
  return {
    ...module,
    backendPercent: module.backendPercent ?? Math.min(100, module.completionPercent + 1),
    frontendPercent: module.frontendPercent ?? Math.min(100, module.completionPercent),
    apiPercent: module.apiPercent ?? Math.min(100, module.completionPercent - 1),
    rbacPercent: module.rbacPercent ?? 90,
    auditPercent: module.auditPercent ?? 88,
    pdfPercent: module.pdfPercent ?? 70,
  };
}

function makeFeature(feature: Feature): Feature {
  return feature;
}

function makeWorkflow(workflow: Workflow): Workflow {
  return workflow;
}

function makeCapability(capability: Capability): Capability {
  return capability;
}

function makeRelease(release: Release): Release {
  return release;
}

function makeDefect(defect: Defect): Defect {
  return defect;
}

function makeDebt(debt: TechnicalDebt): TechnicalDebt {
  return debt;
}

function makeScenario(scenario: UATScenario): UATScenario {
  return scenario;
}

export const productModules: ProductModule[] = [
  makeModule({ id: "ops-dashboard", name: "Dashboard", area: "Operations", owner: "Product Ops", release: "R2", status: "Complete", completionPercent: 90, productionReadinessPercent: 88, uatPercent: 84, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Daily operational overview and launch pad for core workflows." }),
  makeModule({ id: "ops-day-board", name: "Day Board", area: "Operations", owner: "Product Ops", release: "R2", status: "Complete", completionPercent: 98, productionReadinessPercent: 96, uatPercent: 94, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Reception flow and visit orchestration are stable." }),
  makeModule({ id: "ops-appointments", name: "Appointments", area: "Operations", owner: "Product Ops", release: "R2", status: "Complete", completionPercent: 98, productionReadinessPercent: 96, uatPercent: 95, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Appointment scheduling is feature-complete for R2." }),
  makeModule({ id: "ops-notifications", name: "Notifications", area: "Operations", owner: "Platform Eng", release: "R2", status: "In Progress", completionPercent: 85, productionReadinessPercent: 78, uatPercent: 74, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Notification templates and delivery hardening remain." }),
  makeModule({ id: "ops-my-queue", name: "My Queue", area: "Operations", owner: "Clinical UX", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 92, uatPercent: 90, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Doctor-facing queue flow is stable." }),
  makeModule({ id: "ops-queue", name: "Queue", area: "Operations", owner: "Clinical UX", release: "R2", status: "Complete", completionPercent: 96, productionReadinessPercent: 93, uatPercent: 91, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Reception queue is aligned with day board." }),
  makeModule({ id: "ops-doctor-availability", name: "Doctor Availability", area: "Operations", owner: "Scheduling Team", release: "R2", status: "Complete", completionPercent: 97, productionReadinessPercent: 94, uatPercent: 92, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Availability publishing and slot discovery are stable." }),

  makeModule({ id: "clinical-patients", name: "Patients", area: "Clinical", owner: "Clinical Platform", release: "R2", status: "Complete", completionPercent: 96, productionReadinessPercent: 94, uatPercent: 92, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Patient registry is production-suitable." }),
  makeModule({ id: "clinical-consultations", name: "Consultations", area: "Clinical", owner: "Clinical Platform", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 92, uatPercent: 90, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Consultation workspace is largely complete." }),
  makeModule({ id: "clinical-vaccinations", name: "Vaccinations", area: "Clinical", owner: "Clinical Platform", release: "R3", status: "In Progress", completionPercent: 75, productionReadinessPercent: 58, uatPercent: 52, openDefects: 2, criticalDefects: 0, technicalDebt: 2, notes: "Workflow is partially wired and needs end-to-end validation." }),
  makeModule({ id: "clinical-laboratory", name: "Laboratory", area: "Clinical", owner: "Lab Product", release: "R2", status: "Blocked", completionPercent: 45, productionReadinessPercent: 30, uatPercent: 22, backendPercent: 48, frontendPercent: 46, apiPercent: 40, rbacPercent: 62, auditPercent: 58, pdfPercent: 38, openDefects: 4, criticalDefects: 1, technicalDebt: 8, notes: "Next major focus; PDF, result verification, and workflow completion remain." }),

  makeModule({ id: "pharmacy-dashboard", name: "Pharmacy Dashboard", area: "Pharmacy", owner: "Pharmacy Product", release: "R2", status: "Complete", completionPercent: 97, productionReadinessPercent: 95, uatPercent: 93, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Operational dashboard is stable." }),
  makeModule({ id: "pharmacy-prescription-register", name: "Prescription Register", area: "Pharmacy", owner: "Pharmacy Product", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 92, uatPercent: 90, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Prescription intake is production-ready." }),
  makeModule({ id: "pharmacy-dispense-queue", name: "Dispense Queue", area: "Pharmacy", owner: "Pharmacy Product", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 92, uatPercent: 91, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Queue handling and handoff flow are stable." }),
  makeModule({ id: "pharmacy-procure", name: "Procure", area: "Pharmacy", owner: "Procurement Team", release: "R2", status: "Complete", completionPercent: 97, productionReadinessPercent: 95, uatPercent: 92, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "PO creation and procurement flow are complete." }),
  makeModule({ id: "pharmacy-reconcile", name: "Reconcile", area: "Pharmacy", owner: "Procurement Team", release: "R2", status: "Complete", completionPercent: 96, productionReadinessPercent: 90, uatPercent: 89, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Three-way match is functional with a minor UI hardening backlog." }),
  makeModule({ id: "pharmacy-medicine-master", name: "Medicine Master", area: "Pharmacy", owner: "Inventory Team", release: "R2", status: "Complete", completionPercent: 98, productionReadinessPercent: 97, uatPercent: 95, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Master data and item catalog are stable." }),
  makeModule({ id: "pharmacy-inventory", name: "Inventory", area: "Pharmacy", owner: "Inventory Team", release: "R2", status: "Complete", completionPercent: 97, productionReadinessPercent: 95, uatPercent: 92, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Inventory movement and batch tracking are stable." }),
  makeModule({ id: "pharmacy-reports-audit", name: "Reports & Audit", area: "Pharmacy", owner: "Platform Ops", release: "R2", status: "In Progress", completionPercent: 90, productionReadinessPercent: 82, uatPercent: 76, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Audit drill-down and export coverage remain to be hardened." }),

  makeModule({ id: "finance-billing", name: "Billing", area: "Finance", owner: "Billing Team", release: "R2", status: "Complete", completionPercent: 94, productionReadinessPercent: 91, uatPercent: 88, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Core billing is ready; regression focus remains." }),
  makeModule({ id: "finance-cash-counter", name: "Cash Counter", area: "Finance", owner: "Billing Team", release: "R2", status: "In Progress", completionPercent: 88, productionReadinessPercent: 80, uatPercent: 74, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Daily closing and reconciliation hardening remain." }),
  makeModule({ id: "finance-payments", name: "Payments", area: "Finance", owner: "Billing Team", release: "R3", status: "In Progress", completionPercent: 70, productionReadinessPercent: 54, uatPercent: 48, openDefects: 2, criticalDefects: 0, technicalDebt: 2, notes: "Future-hardening work for full payment workflows." }),
  makeModule({ id: "finance-refunds", name: "Refunds", area: "Finance", owner: "Billing Team", release: "R3", status: "Review", completionPercent: 80, productionReadinessPercent: 64, uatPercent: 60, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Refund review and audit handling need final validation." }),

  makeModule({ id: "engage-campaigns", name: "Campaigns", area: "Engage", owner: "Engage Product", release: "R2", status: "Complete", completionPercent: 90, productionReadinessPercent: 86, uatPercent: 84, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Campaign orchestration is stable." }),
  makeModule({ id: "engage-analytics", name: "Analytics", area: "Engage", owner: "Engage Product", release: "R3", status: "In Progress", completionPercent: 75, productionReadinessPercent: 60, uatPercent: 55, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Analytical views need broader dataset coverage." }),
  makeModule({ id: "engage-ops-console", name: "Ops Console", area: "Engage", owner: "Engage Ops", release: "R2", status: "Complete", completionPercent: 90, productionReadinessPercent: 87, uatPercent: 83, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Operational controls are in place." }),
  makeModule({ id: "engage-messaging", name: "Messaging", area: "Engage", owner: "Engage Ops", release: "R2", status: "In Progress", completionPercent: 85, productionReadinessPercent: 76, uatPercent: 73, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Messaging workflows are usable and need hardening." }),
  makeModule({ id: "engage-reminders", name: "Reminders", area: "Engage", owner: "Engage Ops", release: "R2", status: "In Progress", completionPercent: 80, productionReadinessPercent: 68, uatPercent: 64, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Reminder delivery and retry behaviors need tuning." }),
  makeModule({ id: "engage-patient-engagement", name: "Patient Engagement", area: "Engage", owner: "Engage Product", release: "R2", status: "In Progress", completionPercent: 82, productionReadinessPercent: 70, uatPercent: 66, openDefects: 1, criticalDefects: 0, technicalDebt: 0, notes: "Engagement journey coverage is solid but incomplete." }),
  makeModule({ id: "engage-leads", name: "Leads", area: "Engage", owner: "Engage Product", release: "R2", status: "Complete", completionPercent: 92, productionReadinessPercent: 88, uatPercent: 86, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Lead capture and routing are production-grade." }),
  makeModule({ id: "engage-webinar-automation", name: "Webinar Automation", area: "Engage", owner: "Engage Product", release: "R2", status: "Complete", completionPercent: 90, productionReadinessPercent: 85, uatPercent: 83, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Automation flow is complete." }),
  makeModule({ id: "engage-ai-calls", name: "AI Calls", area: "Engage", owner: "AIVA Team", release: "R2", status: "Complete", completionPercent: 88, productionReadinessPercent: 84, uatPercent: 81, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Voice call automation is nearing hardening." }),

  makeModule({ id: "ai-active-conversations", name: "Active Conversations", area: "AI Receptionist", owner: "AIVA Team", release: "R2", status: "Complete", completionPercent: 88, productionReadinessPercent: 84, uatPercent: 81, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Agent conversation inbox is stable." }),
  makeModule({ id: "ai-callback-queue", name: "Callback Queue", area: "AI Receptionist", owner: "AIVA Team", release: "R2", status: "In Progress", completionPercent: 80, productionReadinessPercent: 66, uatPercent: 60, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Queue handoff and callback semantics need polish." }),
  makeModule({ id: "ai-escalation-queue", name: "Escalation Queue", area: "AI Receptionist", owner: "AIVA Team", release: "R2", status: "In Progress", completionPercent: 80, productionReadinessPercent: 65, uatPercent: 60, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Escalation routing is functional but needs tuning." }),
  makeModule({ id: "ai-appointment-handover", name: "Appointment Handover", area: "AI Receptionist", owner: "AIVA Team", release: "R2", status: "In Progress", completionPercent: 78, productionReadinessPercent: 63, uatPercent: 58, openDefects: 1, criticalDefects: 0, technicalDebt: 0, notes: "Handover to live scheduling remains to be validated." }),

  makeModule({ id: "admin-users-roles", name: "Users & Roles", area: "Administration", owner: "Platform Admin", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 93, uatPercent: 91, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Core identity administration is stable." }),
  makeModule({ id: "admin-rbac", name: "RBAC", area: "Administration", owner: "Platform Admin", release: "R2", status: "Complete", completionPercent: 95, productionReadinessPercent: 92, uatPercent: 89, openDefects: 0, criticalDefects: 0, technicalDebt: 1, notes: "Role and permission enforcement is healthy." }),
  makeModule({ id: "admin-clinic-settings", name: "Clinic Settings", area: "Administration", owner: "Platform Admin", release: "R2", status: "In Progress", completionPercent: 85, productionReadinessPercent: 76, uatPercent: 72, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Clinic defaults and configuration hardening remain." }),
  makeModule({ id: "admin-audit-settings", name: "Audit Settings", area: "Administration", owner: "Platform Ops", release: "R2", status: "In Progress", completionPercent: 85, productionReadinessPercent: 74, uatPercent: 70, openDefects: 1, criticalDefects: 0, technicalDebt: 0, notes: "Audit scope and coverage settings are still being refined." }),

  makeModule({ id: "platform-dashboard", name: "Platform Dashboard", area: "Platform", owner: "Platform Team", release: "R2", status: "In Progress", completionPercent: 85, productionReadinessPercent: 78, uatPercent: 72, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Operational platform overview is usable." }),
  makeModule({ id: "platform-tenants", name: "Tenants", area: "Platform", owner: "Platform Team", release: "R2", status: "Complete", completionPercent: 92, productionReadinessPercent: 90, uatPercent: 88, openDefects: 0, criticalDefects: 0, technicalDebt: 0, notes: "Tenant administration is production-suitable." }),
  makeModule({ id: "platform-plans", name: "Plans / Modules", area: "Platform", owner: "Platform Team", release: "R2", status: "In Progress", completionPercent: 82, productionReadinessPercent: 70, uatPercent: 67, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Module entitlement mapping is functional." }),
  makeModule({ id: "platform-product-implementation", name: "Product Implementation", area: "Platform", owner: "Platform Product", release: "R2", status: "In Progress", completionPercent: 20, productionReadinessPercent: 18, uatPercent: 12, backendPercent: 0, frontendPercent: 20, apiPercent: 0, rbacPercent: 0, auditPercent: 0, pdfPercent: 0, openDefects: 0, criticalDefects: 0, technicalDebt: 2, notes: "Static control center for the implementation register." }),
  makeModule({ id: "platform-subscriptions", name: "Subscriptions", area: "Platform", owner: "Platform Team", release: "R3", status: "Review", completionPercent: 20, productionReadinessPercent: 12, uatPercent: 10, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Future subscription workflow placeholder." }),
  makeModule({ id: "platform-analytics", name: "Platform Analytics", area: "Platform", owner: "Platform Team", release: "R3", status: "Review", completionPercent: 25, productionReadinessPercent: 16, uatPercent: 12, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Future analytics backbone and dashboards." }),
  makeModule({ id: "platform-system-health", name: "System Health", area: "Platform", owner: "Platform Team", release: "R3", status: "Review", completionPercent: 30, productionReadinessPercent: 20, uatPercent: 16, openDefects: 1, criticalDefects: 0, technicalDebt: 1, notes: "Monitoring and runtime visibility are planned next." }),
];

export const workflows: Workflow[] = [
  makeWorkflow({
    id: "patient-journey",
    name: "Patient Journey",
    area: "Operations / Clinical",
    release: "R2",
    status: "Complete",
    completionPercent: 88,
    productionReadinessPercent: 84,
    owner: "Clinical Platform",
    blockers: ["Laboratory end-to-end still limits the full patient journey"],
    notes: "Front-door to follow-up journey spans clinic, billing, pharmacy, and patient portal.",
    steps: [
      { name: "Appointment", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Reception", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Doctor Consultation", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Billing", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Pharmacy / Laboratory", status: "In Progress", backendStatus: "Complete", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "In Progress", productionStatus: "In Progress" },
      { name: "Patient Portal", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "Review", productionStatus: "Review" },
      { name: "Follow-up", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
    ],
  }),
  makeWorkflow({
    id: "consultation-to-pharmacy",
    name: "Consultation to Pharmacy",
    area: "Pharmacy",
    release: "R2",
    status: "Complete",
    completionPercent: 95,
    productionReadinessPercent: 92,
    owner: "Pharmacy Product",
    blockers: ["Minor UI polish in reconciliation drawer"],
    notes: "Prescription to FEFO allocation and inventory movement is verified.",
    steps: [
      { name: "Doctor Prescription", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Billing", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Dispense Queue", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Pharmacist Review", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "POS Sale", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "FEFO Allocation", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Inventory Movement", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Receipt", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
    ],
  }),
  makeWorkflow({
    id: "procure-to-pay",
    name: "Procure to Pay",
    area: "Pharmacy / Finance",
    release: "R2",
    status: "In Progress",
    completionPercent: 90,
    productionReadinessPercent: 84,
    owner: "Procurement Team",
    blockers: ["Payment and ledger posting remain future hardening items"],
    notes: "Procurement through reconciliation is complete; payment is intentionally out of scope for Phase 1.",
    steps: [
      { name: "Supplier", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Purchase Order", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Generate PO", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Supplier Invoice", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "GRN", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Inventory", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Batch", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Reconciliation", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Ready for Payment", status: "Review", backendStatus: "Complete", frontendStatus: "In Progress", apiStatus: "Review", uatStatus: "Review", productionStatus: "Review" },
      { name: "Payment", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Ledger", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
    ],
  }),
  makeWorkflow({
    id: "laboratory-workflow",
    name: "Laboratory Workflow",
    area: "Clinical",
    release: "R2",
    status: "Blocked",
    completionPercent: 45,
    productionReadinessPercent: 30,
    owner: "Lab Product",
    blockers: ["PDF report generation framework", "Sample collection flow", "Result verification"],
    notes: "Laboratory remains the next major focus area.",
    steps: [
      { name: "Doctor Orders Test", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review" },
      { name: "Billing Completed", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review" },
      { name: "Sample Collection", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Barcode Printed", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Lab Queue", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Technician Processing", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Result Entry", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Pathologist Review", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Approved", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Report PDF", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Patient Portal", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
      { name: "Doctor Timeline", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending" },
    ],
  }),
  makeWorkflow({
    id: "billing-to-cash-counter",
    name: "Billing to Cash Counter",
    area: "Finance",
    release: "R2",
    status: "In Progress",
    completionPercent: 85,
    productionReadinessPercent: 78,
    owner: "Billing Team",
    blockers: ["Daily close regression coverage", "Refund hardening"],
    notes: "Cash counter is operational but still needs regression and closing polish.",
    steps: [
      { name: "Bill Created", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Payment Collection", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review" },
      { name: "Receipt", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review" },
      { name: "Refund if needed", status: "Review", backendStatus: "Review", frontendStatus: "Review", apiStatus: "Review", uatStatus: "Review", productionStatus: "Review" },
      { name: "Daily Cash Closing", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "In Progress", productionStatus: "In Progress" },
      { name: "Audit", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "In Progress", productionStatus: "In Progress" },
    ],
  }),
  makeWorkflow({
    id: "ai-receptionist-workflow",
    name: "AI Receptionist Workflow",
    area: "AI Receptionist",
    release: "R2",
    status: "In Progress",
    completionPercent: 82,
    productionReadinessPercent: 74,
    owner: "AIVA Team",
    blockers: ["Escalation queue tuning", "Appointment handover hardening"],
    notes: "Automated conversation-to-booking flow is operational and expanding.",
    steps: [
      { name: "Patient Conversation", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Intent Detection", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Appointment Search", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Slot Suggestion", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Confirmation", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete" },
      { name: "Handover", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "In Progress", productionStatus: "In Progress" },
      { name: "Callback / Escalation", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "In Progress", productionStatus: "In Progress" },
    ],
  }),
];

export const features: Feature[] = [
  makeFeature({ id: "pharmacy-medicine-master", module: "Pharmacy", name: "Medicine Master", priority: "Critical", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Master data flows are complete." }),
  makeFeature({ id: "pharmacy-procurement", module: "Pharmacy", name: "Procurement", priority: "Critical", release: "R2", owner: "Procurement Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "PO creation and procurement review complete." }),
  makeFeature({ id: "pharmacy-supplier-invoice", module: "Pharmacy", name: "Supplier Invoice", priority: "Critical", release: "R2", owner: "Procurement Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Invoice intake is stable." }),
  makeFeature({ id: "pharmacy-grn", module: "Pharmacy", name: "GRN", priority: "Critical", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Goods receipt flow is stable." }),
  makeFeature({ id: "pharmacy-inventory-transactions", module: "Pharmacy", name: "Inventory Transactions", priority: "Critical", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Inventory mutation is functional." }),
  makeFeature({ id: "pharmacy-batch-management", module: "Pharmacy", name: "Batch Management", priority: "Critical", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Batch creation and tracking complete." }),
  makeFeature({ id: "pharmacy-pos-sale", module: "Pharmacy", name: "POS Sale", priority: "Critical", release: "R2", owner: "Pharmacy Product", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Checkout and receipt flow complete." }),
  makeFeature({ id: "pharmacy-three-way-reconciliation", module: "Pharmacy", name: "Three-way Reconciliation", priority: "Critical", release: "R2", owner: "Procurement Team", status: "In Progress", backendStatus: "Complete", frontendStatus: "In Progress", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review", defects: "1 minor UI bug", notes: "Derived matching is complete; UI hardening remains." }),
  makeFeature({ id: "pharmacy-physical-count", module: "Pharmacy", name: "Physical Count", priority: "High", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Posted sessions can be reviewed read-only." }),
  makeFeature({ id: "pharmacy-stock-adjustments", module: "Pharmacy", name: "Stock Adjustments", priority: "High", release: "R2", owner: "Inventory Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Adjustment requests and approvals are functional." }),
  makeFeature({ id: "pharmacy-reports-audit", module: "Pharmacy", name: "Reports & Audit", priority: "High", release: "R2", owner: "Platform Ops", status: "In Progress", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review", defects: "1 open", notes: "Audit drill-down and exports still need polish." }),
  makeFeature({ id: "pharmacy-payment-workflow", module: "Pharmacy", name: "Payment Workflow", priority: "High", release: "R3", owner: "Finance Team", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Future", notes: "Intentionally deferred from Phase 1." }),
  makeFeature({ id: "pharmacy-pdf-documents", module: "Pharmacy", name: "PDF Documents", priority: "High", release: "R3", owner: "Platform Team", status: "Review", backendStatus: "In Progress", frontendStatus: "Review", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Shared PDF framework will unblock procurement and lab documents." }),

  makeFeature({ id: "lab-test-master", module: "Clinical", name: "Lab Test Master", priority: "Critical", release: "R2", owner: "Lab Product", status: "In Progress", backendStatus: "In Progress", frontendStatus: "Review", apiStatus: "In Progress", uatStatus: "Pending", productionStatus: "Pending", defects: "Review needed", notes: "Catalog definition needs final workflow validation." }),
  makeFeature({ id: "lab-test-ordering", module: "Clinical", name: "Test Ordering", priority: "Critical", release: "R2", owner: "Lab Product", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Order placement is partially wired." }),
  makeFeature({ id: "lab-billing-integration", module: "Clinical", name: "Billing Integration", priority: "High", release: "R2", owner: "Billing Team", status: "Review", backendStatus: "Complete", frontendStatus: "Review", apiStatus: "Complete", uatStatus: "Pending", productionStatus: "Pending", defects: "Validation needed", notes: "Requires regression against billing and cash flow." }),
  makeFeature({ id: "lab-sample-collection", module: "Clinical", name: "Sample Collection", priority: "High", release: "R2", owner: "Lab Product", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Future phase." }),
  makeFeature({ id: "lab-barcode-printing", module: "Clinical", name: "Barcode Printing", priority: "High", release: "R2", owner: "Lab Product", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Shared PDF/labeling framework required." }),
  makeFeature({ id: "lab-queue", module: "Clinical", name: "Lab Work Queue", priority: "High", release: "R2", owner: "Lab Product", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Pending queue orchestration." }),
  makeFeature({ id: "lab-result-entry", module: "Clinical", name: "Technician Result Entry", priority: "Critical", release: "R2", owner: "Lab Product", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Not started." }),
  makeFeature({ id: "lab-result-verification", module: "Clinical", name: "Result Verification", priority: "Critical", release: "R2", owner: "Lab Product", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Not started." }),
  makeFeature({ id: "lab-approval", module: "Clinical", name: "Doctor / Pathologist Approval", priority: "High", release: "R2", owner: "Clinical Platform", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Not started." }),
  makeFeature({ id: "lab-report-pdf", module: "Clinical", name: "Patient Report PDF", priority: "Critical", release: "R2", owner: "Platform Team", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "PDF framework dependency." }),
  makeFeature({ id: "lab-portal", module: "Clinical", name: "Patient Portal Integration", priority: "High", release: "R2", owner: "Patient Portal", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Portal publishing pending." }),
  makeFeature({ id: "lab-audit", module: "Clinical", name: "Audit Trail", priority: "High", release: "R2", owner: "Platform Ops", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Pending", notes: "Audit coverage to be added." }),

  makeFeature({ id: "finance-billing-feature", module: "Finance", name: "Billing", priority: "Critical", release: "R2", owner: "Billing Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Mostly complete." }),
  makeFeature({ id: "finance-cash-counter-feature", module: "Finance", name: "Cash Counter", priority: "High", release: "R2", owner: "Billing Team", status: "Review", backendStatus: "Complete", frontendStatus: "Review", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review", defects: "Review", notes: "Daily closing and mismatch flows need signoff." }),
  makeFeature({ id: "finance-payments-feature", module: "Finance", name: "Payments", priority: "High", release: "R3", owner: "Billing Team", status: "In Progress", backendStatus: "In Progress", frontendStatus: "In Progress", apiStatus: "In Progress", uatStatus: "Pending", productionStatus: "Pending", defects: "Partial", notes: "Partially complete." }),
  makeFeature({ id: "finance-refunds-feature", module: "Finance", name: "Refunds", priority: "High", release: "R3", owner: "Billing Team", status: "Review", backendStatus: "Complete", frontendStatus: "Review", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Pending", defects: "Review", notes: "Refund reconciliation needs validation." }),

  makeFeature({ id: "platform-tenant-management", module: "Platform", name: "Tenant Management", priority: "Critical", release: "R2", owner: "Platform Team", status: "Complete", backendStatus: "Complete", frontendStatus: "Complete", apiStatus: "Complete", uatStatus: "Complete", productionStatus: "Complete", defects: "0 open", notes: "Mostly complete." }),
  makeFeature({ id: "platform-plans-modules", module: "Platform", name: "Plans / Modules", priority: "High", release: "R2", owner: "Platform Team", status: "Review", backendStatus: "Complete", frontendStatus: "Review", apiStatus: "Complete", uatStatus: "Review", productionStatus: "Review", defects: "Review", notes: "Module matrix and entitlement mapping in review." }),
  makeFeature({ id: "platform-product-implementation-feature", module: "Platform", name: "Product Implementation", priority: "High", release: "R2", owner: "Platform Product", status: "In Progress", backendStatus: "Pending", frontendStatus: "In Progress", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "New", notes: "Static phase introduced." }),
  makeFeature({ id: "platform-subscriptions-feature", module: "Platform", name: "Subscriptions", priority: "Low", release: "R3", owner: "Platform Team", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Future", notes: "Future phase." }),
  makeFeature({ id: "platform-analytics-feature", module: "Platform", name: "Platform Analytics", priority: "Low", release: "R3", owner: "Platform Team", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Future", notes: "Future phase." }),
  makeFeature({ id: "platform-health-feature", module: "Platform", name: "System Health", priority: "Low", release: "R3", owner: "Platform Team", status: "Pending", backendStatus: "Pending", frontendStatus: "Pending", apiStatus: "Pending", uatStatus: "Pending", productionStatus: "Pending", defects: "Future", notes: "Future phase." }),
];

export const capabilities: Capability[] = [
  makeCapability({ id: "workflow-header", name: "Workflow Header", coveragePercent: 100, status: "Complete", modulesCovered: ["Operations", "Clinical", "Pharmacy", "Finance", "Platform"], gaps: [], notes: "Global workflow guidance is complete." }),
  makeCapability({ id: "validation-framework", name: "Validation Framework", coveragePercent: 100, status: "Complete", modulesCovered: ["Clinical", "Pharmacy", "Finance"], gaps: [], notes: "Core validation is consistent across high-risk flows." }),
  makeCapability({ id: "help-cms", name: "Help CMS", coveragePercent: 95, status: "Complete", modulesCovered: ["Platform", "Operations"], gaps: ["Few page associations"], notes: "Help content coverage is strong." }),
  makeCapability({ id: "rbac", name: "RBAC", coveragePercent: 95, status: "Complete", modulesCovered: ["Platform", "Administration", "Pharmacy"], gaps: ["Subscription-based admin flows"], notes: "Role enforcement is mature." }),
  makeCapability({ id: "audit-trail", name: "Audit Trail", coveragePercent: 90, status: "In Progress", modulesCovered: ["Pharmacy", "Finance", "Platform"], gaps: ["Some read-only drill-downs"], notes: "Audit coverage is good but not universal." }),
  makeCapability({ id: "search-framework", name: "Search Framework", coveragePercent: 90, status: "In Progress", modulesCovered: ["Operations", "Pharmacy", "Clinical"], gaps: ["Cross-entity search refinements"], notes: "Search primitives are in place." }),
  makeCapability({ id: "notification-engine", name: "Notification Engine", coveragePercent: 82, status: "In Progress", modulesCovered: ["Operations", "Engage", "AI Receptionist"], gaps: ["Template hardening", "Retry visibility"], notes: "Delivery flows are active." }),
  makeCapability({ id: "pdf-framework", name: "PDF Framework", coveragePercent: 55, status: "Blocked", modulesCovered: ["Pharmacy", "Clinical"], gaps: ["Report PDF", "Procurement docs", "Receipt templates"], notes: "Shared PDF framework remains the key blocker." }),
  makeCapability({ id: "reporting-framework", name: "Reporting Framework", coveragePercent: 75, status: "In Progress", modulesCovered: ["Finance", "Platform", "Pharmacy"], gaps: ["More audit filters"], notes: "Reporting is functional but incomplete." }),
  makeCapability({ id: "ai-assistant", name: "AI Assistant", coveragePercent: 86, status: "In Progress", modulesCovered: ["AI Receptionist", "CarePilot"], gaps: ["Human handover tuning"], notes: "Conversational assistance is strong." }),
  makeCapability({ id: "voice-ai", name: "Voice AI", coveragePercent: 88, status: "In Progress", modulesCovered: ["AI Calls", "AI Receptionist"], gaps: ["Call callback corner cases"], notes: "Voice workflows are nearly production-ready." }),
  makeCapability({ id: "logging", name: "Logging", coveragePercent: 82, status: "In Progress", modulesCovered: ["Platform", "Operations"], gaps: ["Structured search and export"], notes: "Observability is usable." }),
  makeCapability({ id: "monitoring", name: "Monitoring", coveragePercent: 70, status: "Review", modulesCovered: ["Platform"], gaps: ["System health dashboard"], notes: "Monitoring is a major backlog item." }),
  makeCapability({ id: "performance", name: "Performance", coveragePercent: 80, status: "In Progress", modulesCovered: ["Dashboard", "Billing", "Pharmacy"], gaps: ["Dashboard tuning"], notes: "Responsive across primary flows." }),
  makeCapability({ id: "accessibility", name: "Accessibility", coveragePercent: 65, status: "Review", modulesCovered: ["Platform", "Clinical", "Pharmacy"], gaps: ["Keyboard focus polish", "More contrast checks"], notes: "Needs systematic hardening." }),
  makeCapability({ id: "localization", name: "Localization", coveragePercent: 45, status: "Blocked", modulesCovered: ["Platform", "Patient Portal"], gaps: ["Language content model", "Locale formatting"], notes: "Future expansion item." }),
  makeCapability({ id: "security-hardening", name: "Security Hardening", coveragePercent: 80, status: "In Progress", modulesCovered: ["Platform", "Administration", "Finance"], gaps: ["Final RBAC and audit sweep"], notes: "Security baseline is solid but not frozen." }),
];

export const releases: Release[] = [
  makeRelease({
    id: "r2",
    name: "R2 OPD Production Candidate",
    target: "Near-term UAT release",
    status: "In Progress",
    progressPercent: 92,
    includedModules: ["Reception", "Doctor Consultation", "Billing", "Pharmacy", "Inventory", "Laboratory", "Patient Portal", "Public Website", "AIVA", "AI Assistant"],
    blockers: ["Laboratory end-to-end complete", "Pharmacy hardening bugs", "Billing and cash counter regression", "Patient portal report flow", "PDF framework", "RBAC and audit validation"],
    exitCriteria: ["Laboratory end-to-end complete", "Pharmacy production-hardening bugs closed", "Billing and cash counter regression complete", "Patient portal report flow complete", "PDF framework ready", "RBAC and audit validated", "Critical defects zero"],
  }),
  makeRelease({
    id: "r3",
    name: "Hospital Lite",
    target: "Planned release",
    status: "Planned",
    progressPercent: 22,
    includedModules: ["IPD Admission", "Ward", "Bed Management", "Nursing", "Doctor Rounds", "IPD Billing", "Discharge Summary", "Lab / Pharmacy horizontal integration"],
    blockers: ["Scope freeze not started"],
    exitCriteria: ["IPD core flows defined", "Ward operations modeled", "Billing and discharge choreography ready"],
  }),
  makeRelease({
    id: "r4",
    name: "Enterprise + AI Expansion",
    target: "Future",
    status: "Future",
    progressPercent: 8,
    includedModules: ["OT", "ICU", "Insurance / TPA", "Advanced analytics", "LIS integration", "Machine integration", "AI clinical assistant", "Mobile app", "Enterprise reporting"],
    blockers: ["R3 delivery", "Enterprise integration roadmap"],
    exitCriteria: ["Enterprise scope approved", "AI and integrations architecture frozen"],
  }),
];

export const defects: Defect[] = [
  makeDefect({ id: "def-001", module: "Laboratory", title: "Lab result PDF rendering lacks final template", priority: "Critical", status: "Open", owner: "Platform Team", release: "R2", notes: "Blocking patient-facing report output." }),
  makeDefect({ id: "def-002", module: "Laboratory", title: "Sample collection screen missing barcode scan state", priority: "High", status: "Open", owner: "Lab Product", release: "R2", notes: "Collection flow cannot be fully completed." }),
  makeDefect({ id: "def-003", module: "Laboratory", title: "Result verification needs approval audit entry", priority: "High", status: "Open", owner: "Platform Ops", release: "R2", notes: "Audit completeness issue." }),
  makeDefect({ id: "def-004", module: "Laboratory", title: "Doctor timeline does not yet surface lab report events", priority: "Medium", status: "Open", owner: "Clinical Platform", release: "R2", notes: "Visibility gap for clinicians." }),
  makeDefect({ id: "def-005", module: "Pharmacy", title: "Reconciliation drawer label order needs polish", priority: "Medium", status: "Open", owner: "Pharmacy Product", release: "R2", notes: "Minor UI issue." }),
  makeDefect({ id: "def-006", module: "Pharmacy", title: "Reports & Audit export filters need refinement", priority: "Medium", status: "Open", owner: "Platform Ops", release: "R2", notes: "Audit drill-down is usable but incomplete." }),
  makeDefect({ id: "def-007", module: "Finance", title: "Cash counter closing notes validation edge case", priority: "Medium", status: "Open", owner: "Billing Team", release: "R2", notes: "Needs final regression." }),
  makeDefect({ id: "def-008", module: "Finance", title: "Refund review state lacks summary badge", priority: "Low", status: "Open", owner: "Billing Team", release: "R3", notes: "Cosmetic but important for review workflow." }),
  makeDefect({ id: "def-009", module: "Engage", title: "Reminder retry audit trail incomplete", priority: "Low", status: "Open", owner: "Engage Ops", release: "R2", notes: "Needs event logging hardening." }),
  makeDefect({ id: "def-010", module: "AI Receptionist", title: "Appointment handover confidence badge missing", priority: "Low", status: "Open", owner: "AIVA Team", release: "R2", notes: "Minor interaction polish." }),
  makeDefect({ id: "def-011", module: "Administration", title: "Audit settings page needs final labels", priority: "Low", status: "Open", owner: "Platform Ops", release: "R2", notes: "Minor admin polish." }),
  makeDefect({ id: "def-012", module: "Platform", title: "Product Implementation read-only register has no backend source yet", priority: "Low", status: "Open", owner: "Platform Product", release: "R2", notes: "Expected for static phase." }),
  makeDefect({ id: "def-013", module: "Platform", title: "System health dashboard not yet implemented", priority: "Medium", status: "Open", owner: "Platform Team", release: "R3", notes: "Roadmap item." }),
  makeDefect({ id: "def-014", module: "Operations", title: "Notification deliverability summary needs better breakdown", priority: "Low", status: "Open", owner: "Platform Eng", release: "R2", notes: "Non-blocking." }),
  makeDefect({ id: "def-015", module: "Pharmacy", title: "Reconciliation match badge recalculation now derived but needs final review", priority: "Low", status: "Closed", owner: "Pharmacy Product", release: "R2", notes: "Resolved by current change." }),
];

export const technicalDebtItems: TechnicalDebt[] = [
  makeDebt({ id: "td-001", module: "Operations", title: "Unify workflow header behavior across all operations screens", severity: "Low", category: "UX consistency", status: "Open", notes: "Minor standardization work." }),
  makeDebt({ id: "td-002", module: "Operations", title: "Notification center needs template governance", severity: "Medium", category: "Workflow hardening", status: "Planned", notes: "Can be deferred." }),
  makeDebt({ id: "td-003", module: "Clinical", title: "Laboratory state machine needs finalization", severity: "High", category: "Workflow engine", status: "Open", notes: "Blocking lab release hardening." }),
  makeDebt({ id: "td-004", module: "Clinical", title: "Patient portal report visibility requires service abstraction", severity: "Medium", category: "Integration", status: "Planned", notes: "Will align with PDF framework." }),
  makeDebt({ id: "td-005", module: "Pharmacy", title: "PDF document framework missing common renderer", severity: "High", category: "Documents", status: "Open", notes: "Cross-cutting blocker." }),
  makeDebt({ id: "td-006", module: "Pharmacy", title: "Reconciliation drawer still uses staged workflow overlay", severity: "Low", category: "State management", status: "Open", notes: "Static batch phase only." }),
  makeDebt({ id: "td-007", module: "Finance", title: "Daily close calculations need final regression harness", severity: "Medium", category: "Regression", status: "Planned", notes: "Important before R2 candidate." }),
  makeDebt({ id: "td-008", module: "Finance", title: "Refund audit history needs unified event model", severity: "Low", category: "Audit", status: "Open", notes: "Not blocking." }),
  makeDebt({ id: "td-009", module: "Engage", title: "Reminder retries need queue-level observability", severity: "Low", category: "Observability", status: "Open", notes: "Minor visibility gap." }),
  makeDebt({ id: "td-010", module: "AI Receptionist", title: "Handover confidence thresholds need tuning data", severity: "Medium", category: "AI ops", status: "Open", notes: "Important for production tuning." }),
  makeDebt({ id: "td-011", module: "Administration", title: "RBAC matrix needs final export and review mode", severity: "Low", category: "Security", status: "Planned", notes: "Can be completed before scope freeze." }),
  makeDebt({ id: "td-012", module: "Platform", title: "Product Implementation register should migrate to API later", severity: "Low", category: "Architecture", status: "Open", notes: "Phase 2 replacement point." }),
];

export const uatScenarios: UATScenario[] = [
  makeScenario({ id: "uat-001", module: "Operations", workflow: "Patient Journey", scenario: "Create patient and appointment", status: "Pass", priority: "Critical", tester: "QA Lead", evidenceStatus: "Attached", notes: "Baseline journey passes." }),
  makeScenario({ id: "uat-002", module: "Clinical", workflow: "Consultation", scenario: "Doctor consultation with prescription", status: "Pass", priority: "Critical", tester: "Clinical QA", evidenceStatus: "Attached", notes: "Consultation flow stable." }),
  makeScenario({ id: "uat-003", module: "Finance", workflow: "Billing to Cash Counter", scenario: "Billing for consultation", status: "Pass", priority: "Critical", tester: "Billing QA", evidenceStatus: "Attached", notes: "Billing regression passes." }),
  makeScenario({ id: "uat-004", module: "Pharmacy", workflow: "Pharmacy", scenario: "Pharmacy medicine master CRUD", status: "Pass", priority: "High", tester: "Pharmacy QA", evidenceStatus: "Attached", notes: "Master data operations pass." }),
  makeScenario({ id: "uat-005", module: "Pharmacy", workflow: "Procure to Pay", scenario: "Pharmacy procurement to GRN", status: "Pass", priority: "High", tester: "Procurement QA", evidenceStatus: "Attached", notes: "Documents align end-to-end." }),
  makeScenario({ id: "uat-006", module: "Pharmacy", workflow: "Consultation to Pharmacy", scenario: "Pharmacy POS sale", status: "Pass", priority: "Critical", tester: "POS QA", evidenceStatus: "Attached", notes: "Sale and receipt flow pass." }),
  makeScenario({ id: "uat-007", module: "Pharmacy", workflow: "Procure to Pay", scenario: "Pharmacy reconciliation approval", status: "Review", priority: "High", tester: "QA Lead", evidenceStatus: "Partial", notes: "Minor defect under review." }),
  makeScenario({ id: "uat-008", module: "Clinical", workflow: "Laboratory Workflow", scenario: "Laboratory test ordering", status: "Pending", priority: "Critical", tester: "Lab QA", evidenceStatus: "Pending", notes: "Pending implementation." }),
  makeScenario({ id: "uat-009", module: "Clinical", workflow: "Laboratory Workflow", scenario: "Laboratory sample collection", status: "Pending", priority: "Critical", tester: "Lab QA", evidenceStatus: "Pending", notes: "Pending implementation." }),
  makeScenario({ id: "uat-010", module: "Clinical", workflow: "Laboratory Workflow", scenario: "Laboratory result entry", status: "Pending", priority: "Critical", tester: "Lab QA", evidenceStatus: "Pending", notes: "Pending implementation." }),
  makeScenario({ id: "uat-011", module: "Clinical", workflow: "Laboratory Workflow", scenario: "Laboratory report PDF", status: "Pending", priority: "Critical", tester: "Lab QA", evidenceStatus: "Pending", notes: "Pending PDF framework." }),
  makeScenario({ id: "uat-012", module: "Clinical", workflow: "Patient Journey", scenario: "Patient portal report visibility", status: "Pending", priority: "High", tester: "Portal QA", evidenceStatus: "Pending", notes: "Blocked on lab report PDF." }),
  makeScenario({ id: "uat-013", module: "Finance", workflow: "Billing to Cash Counter", scenario: "Cash counter daily closing", status: "Review", priority: "High", tester: "Billing QA", evidenceStatus: "Partial", notes: "Regression review in progress." }),
  makeScenario({ id: "uat-014", module: "Finance", workflow: "Billing to Cash Counter", scenario: "Refund flow", status: "Review", priority: "High", tester: "Billing QA", evidenceStatus: "Partial", notes: "Refund audit needs one more pass." }),
  makeScenario({ id: "uat-015", module: "AI Receptionist", workflow: "AI Receptionist Workflow", scenario: "AI receptionist booking handover", status: "Review", priority: "High", tester: "AIVA QA", evidenceStatus: "Partial", notes: "Handover confidence tuning ongoing." }),
];

export const overviewNotes = [
  "Pharmacy core flow is feature-complete for R2.",
  "Procurement → GRN → Inventory → POS → Reconciliation is verified.",
  "Laboratory is the next major focus area.",
  "Product Implementation is introduced as the living implementation register.",
  "Payment workflow remains in production-hardening backlog.",
];

export const criticalBlockers = [
  "Laboratory end-to-end workflow incomplete",
  "Pharmacy payment workflow pending",
  "PDF generation framework incomplete",
  "Platform subscriptions pending",
  "System health dashboard pending",
];
