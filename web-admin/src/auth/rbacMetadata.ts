export type RbacRoleCategory =
  | "tenant-business"
  | "tenant-technical"
  | "platform-internal"
  | "service-system"
  | "legacy-compatibility";

export type RbacRoleStatus =
  | "Active"
  | "Technical"
  | "Legacy/Compatibility"
  | "Backend-only"
  | "Service/System";

export type RbacActionType =
  | "Read"
  | "Create"
  | "Update"
  | "Delete"
  | "Manage"
  | "Approve"
  | "Execute/Run"
  | "Export"
  | "Override"
  | "Collect Payment"
  | "Finalize"
  | "Assign"
  | "Reset Password"
  | "Send"
  | "Publish"
  | "Cancel"
  | "Review"
  | "Activate"
  | "Reject"
  | "Submit"
  | "Other";

export type RoleMetadata = {
  role: string;
  displayName: string;
  category: RbacRoleCategory;
  status: RbacRoleStatus;
  assignableToHuman: boolean;
  businessVisible: boolean;
  order: number;
  summary: string;
  description: string;
};

export type PermissionMetadata = {
  key: string;
  module: string;
  label: string;
  action: RbacActionType;
  description: string;
  sensitive: boolean;
  tenantFacing: boolean;
  deprecated: boolean;
};

export type GroupedPermission = {
  module: string;
  permissions: PermissionMetadata[];
};

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Admin",
  AUDITOR: "Auditor",
  BILLING_USER: "Billing User",
  CLINIC_ADMIN: "Clinic Admin",
  DOCTOR: "Doctor",
  ENGAGE_EXECUTIVE: "Engage Executive",
  ENGAGE_MANAGER: "Engage Manager",
  LAB_ASSISTANT: "Lab Assistant",
  LAB_APPROVER: "Lab Approver",
  LAB_FRONT_DESK: "Lab Front Desk",
  LAB_TECHNICIAN: "Lab Technician",
  PHARMA: "Pharma",
  PHARMACIST: "Pharmacist",
  PHARMACY: "Pharmacy",
  PHARMACY_INVENTORY_MANAGER: "Pharmacy Inventory Manager",
  PHARMACY_POS_USER: "Pharmacy POS User",
  PLATFORM_ADMIN: "Platform Admin",
  PLATFORM_TENANT_SUPPORT: "Platform Support",
  RECEPTIONIST: "Receptionist",
  SERVICE_AGENT: "Service Agent",
  TENANT_ADMIN: "Tenant Admin",
};

export const BUSINESS_ROLE_KEYS = [
  "CLINIC_ADMIN",
  "ENGAGE_MANAGER",
  "ENGAGE_EXECUTIVE",
  "DOCTOR",
  "RECEPTIONIST",
  "BILLING_USER",
  "AUDITOR",
  "LAB_TECHNICIAN",
  "LAB_ASSISTANT",
  "LAB_APPROVER",
  "LAB_FRONT_DESK",
  "PHARMACIST",
  "PHARMACY_INVENTORY_MANAGER",
  "PHARMACY_POS_USER",
] as const;

export const TECHNICAL_ROLE_KEYS = [
  "ADMIN",
  "TENANT_ADMIN",
  "PLATFORM_ADMIN",
  "PLATFORM_TENANT_SUPPORT",
  "SERVICE_AGENT",
] as const;

export const DEFAULT_ROLE_KEY = "CLINIC_ADMIN";

const ROLE_METADATA: Record<string, RoleMetadata> = {
  ADMIN: {
    role: "ADMIN",
    displayName: "Admin",
    category: "legacy-compatibility",
    status: "Legacy/Compatibility",
    assignableToHuman: true,
    businessVisible: false,
    order: 90,
    summary: "Legacy tenant administration compatibility role.",
    description: "Compatibility alias that currently shares the tenant admin permission bundle. It is not the primary business-facing clinic admin role.",
  },
  AUDITOR: {
    role: "AUDITOR",
    displayName: "Auditor",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 7,
    summary: "Read-only audit and reporting role.",
    description: "Tenant auditor role intended for read-only review of clinical, operational, billing, and audit data.",
  },
  BILLING_USER: {
    role: "BILLING_USER",
    displayName: "Billing User",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 6,
    summary: "Finance-facing role for billing and payment collection.",
    description: "Operational billing role responsible for invoices, receipts, payment collection, and billing reports.",
  },
  CLINIC_ADMIN: {
    role: "CLINIC_ADMIN",
    displayName: "Clinic Admin",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 1,
    summary: "Primary tenant administrator for clinic operations.",
    description: "Primary tenant-facing admin role used for clinic setup, user administration, and cross-module operational oversight.",
  },
  DOCTOR: {
    role: "DOCTOR",
    displayName: "Doctor",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 3,
    summary: "Clinical role for consultations and prescriptions.",
    description: "Clinical user responsible for consultation review, diagnosis, prescriptions, and related patient-facing clinical workflows.",
  },
  ENGAGE_EXECUTIVE: {
    role: "ENGAGE_EXECUTIVE",
    displayName: "Engage Executive",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 14,
    summary: "Operational CARE role with limited execution rights.",
    description: "Operational CARE role for lead handling, reminders, webinar operations, and campaign work without broad approval or administration rights.",
  },
  ENGAGE_MANAGER: {
    role: "ENGAGE_MANAGER",
    displayName: "Engage Manager",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 13,
    summary: "CARE manager role for campaigns, leads, and webinars.",
    description: "CARE manager role responsible for campaign management, leads, webinar workflows, and operational oversight.",
  },
  LAB_ASSISTANT: {
    role: "LAB_ASSISTANT",
    displayName: "Lab Assistant",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 9,
    summary: "Sample collection role for laboratory operations.",
    description: "Laboratory support role focused on sample collection and related read-only workflow support.",
  },
  LAB_APPROVER: {
    role: "LAB_APPROVER",
    displayName: "Lab Approver",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 10,
    summary: "Maker-checker approval role for laboratory results.",
    description: "Laboratory checker role responsible for result review, verification, report generation, and approval workflows.",
  },
  LAB_FRONT_DESK: {
    role: "LAB_FRONT_DESK",
    displayName: "Lab Front Desk",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 8,
    summary: "Front-desk laboratory role for order intake and payment collection.",
    description: "Laboratory front-desk role that handles walk-ins, order intake, and selected payment workflows.",
  },
  LAB_TECHNICIAN: {
    role: "LAB_TECHNICIAN",
    displayName: "Lab Technician",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 9,
    summary: "Execution role for sample collection and result entry.",
    description: "Laboratory execution role responsible for sample collection, result entry, and related lab operations without approval rights.",
  },
  PLATFORM_ADMIN: {
    role: "PLATFORM_ADMIN",
    displayName: "Platform Admin",
    category: "platform-internal",
    status: "Technical",
    assignableToHuman: false,
    businessVisible: false,
    order: 100,
    summary: "Platform-wide internal administrative role.",
    description: "Internal platform role with access to cross-tenant and commercial administration features. Not a normal tenant business role.",
  },
  PLATFORM_TENANT_SUPPORT: {
    role: "PLATFORM_TENANT_SUPPORT",
    displayName: "Platform Support",
    category: "platform-internal",
    status: "Technical",
    assignableToHuman: false,
    businessVisible: false,
    order: 101,
    summary: "Read-only support role for platform assistance.",
    description: "Internal platform support role for reviewing tenant state and assisting with support operations.",
  },
  PHARMA: {
    role: "PHARMA",
    displayName: "Pharma",
    category: "legacy-compatibility",
    status: "Legacy/Compatibility",
    assignableToHuman: false,
    businessVisible: false,
    order: 95,
    summary: "Legacy pharmacy alias role.",
    description: "Compatibility role retained for older pharmacy workflows and role mappings.",
  },
  PHARMACIST: {
    role: "PHARMACIST",
    displayName: "Pharmacist",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 11,
    summary: "Dispensing and medication-focused pharmacy role.",
    description: "Pharmacy user responsible for dispensing, medication review, and selected billing and inventory workflows.",
  },
  PHARMACY: {
    role: "PHARMACY",
    displayName: "Pharmacy",
    category: "legacy-compatibility",
    status: "Legacy/Compatibility",
    assignableToHuman: false,
    businessVisible: false,
    order: 96,
    summary: "Legacy pharmacy alias role.",
    description: "Compatibility role retained for older pharmacy workflow mappings.",
  },
  PHARMACY_INVENTORY_MANAGER: {
    role: "PHARMACY_INVENTORY_MANAGER",
    displayName: "Pharmacy Inventory Manager",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 12,
    summary: "Stock and inventory management role.",
    description: "Pharmacy inventory role responsible for stock records, adjustments, counts, and related reporting.",
  },
  PHARMACY_POS_USER: {
    role: "PHARMACY_POS_USER",
    displayName: "Pharmacy POS User",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 12,
    summary: "Point-of-sale dispensing and payment role.",
    description: "Pharmacy point-of-sale role focused on sales, dispensing, and collection flows without full inventory administration.",
  },
  RECEPTIONIST: {
    role: "RECEPTIONIST",
    displayName: "Receptionist",
    category: "tenant-business",
    status: "Active",
    assignableToHuman: true,
    businessVisible: true,
    order: 4,
    summary: "Front-desk operational role for intake, appointments, and queue handling.",
    description: "Front-desk operational role responsible for patient registration, appointments, queue management, and selected billing tasks.",
  },
  SERVICE_AGENT: {
    role: "SERVICE_AGENT",
    displayName: "Service Agent",
    category: "service-system",
    status: "Service/System",
    assignableToHuman: false,
    businessVisible: false,
    order: 102,
    summary: "Non-human service identity.",
    description: "Service account used by automation or system integrations. It should not be treated like a normal human tenant role.",
  },
  TENANT_ADMIN: {
    role: "TENANT_ADMIN",
    displayName: "Tenant Admin",
    category: "tenant-technical",
    status: "Technical",
    assignableToHuman: true,
    businessVisible: false,
    order: 91,
    summary: "Compatibility tenant-admin role with the same permission bundle as ADMIN.",
    description: "Tenant administration compatibility role that shares the same permission bundle as ADMIN. It is technical rather than a normal business-facing clinic role.",
  },
};

const SENSITIVE_PERMISSION_KEYS = new Set([
  "audit.export",
  "billing.create",
  "billing.update",
  "clinic.approve",
  "clinic.reject",
  "clinic.profile.update",
  "decisioning.execution.override",
  "engage.campaign.activate",
  "engage.campaign.approve",
  "engage.campaign.manage",
  "engage.campaign.review",
  "engage.campaign.submit",
  "engage.provider.admin",
  "engage.webinar.create",
  "engage.webinar.edit",
  "engage.webinar.publish",
  "engage.webinar.cancel",
  "engage.webinar.run.automation",
  "inventory.create",
  "inventory.update",
  "inventory.manage",
  "lab.order.generate_report",
  "lab.order.review",
  "lab.order.result_entry",
  "lab.order.collect_sample",
  "payment.collect",
  "patient.document.delete",
  "prescription.finalize",
  "prescription.send",
  "tenant.users.manage",
  "tenant.users.reset.password",
  "tenant.users.role.assign",
  "platform.provider_connection.approve",
  "platform.provider_connection.reject",
  "platform.provider_connection.unlink",
  "platform.provider_connection.reconcile",
  "platform.provider_connection.identity_override",
  "platform.provider_connection.audit",
  "commercial.catalog.manage",
  "commercial.plans.manage",
  "commercial.plans.publish",
  "commercial.overrides.create",
  "commercial.overrides.submit",
  "commercial.overrides.review",
  "commercial.overrides.activate",
  "commercial.overrides.cancel",
  "commercial.overrides.rollback",
  "commercial.overrides.manage",
  "commercial.entitlements.regenerate",
  "commercial.runtime.allowlist.manage",
  "clinic_generation.clinic.approve",
  "clinic_generation.clinic.reject",
  "clinic_generation.clinic.issue",
  "clinic_generation.clinic.cancel",
  "clinic_generation.numbering.manage",
  "reconciliation.batch.manage",
  "reconciliation.run",
  "reconciliation.match.manage",
  "reconciliation.exception.manage",
  "ai_copilot.clinic.run",
  "ai_copilot.reconciliation.run",
  "ai_copilot.run",
  "ai.voice.test",
]);

const PERMISSION_LABEL_OVERRIDES: Record<string, string> = {
  "ai.voice.test": "Run Voice Test",
  "ai_copilot.clinic.read": "View Clinic AI Copilot",
  "ai_copilot.clinic.run": "Run Clinic AI Copilot",
  "ai_copilot.read": "View AI Copilot",
  "ai_copilot.reconciliation.read": "View Reconciliation AI Copilot",
  "ai_copilot.reconciliation.run": "Run Reconciliation AI Copilot",
  "ai_copilot.run": "Run AI Copilot",
  "agent.intake.create": "Create Agent Intake",
  "agent.intake.read": "View Agent Intake",
  "agent_intake.item.ignore": "Ignore Intake Item",
  "agent_intake.retry": "Retry Intake",
  "agent_intake.run": "Run Intake",
  "agent_intake.run.read": "View Intake Runs",
  "agent_intake.source.manage": "Manage Intake Sources",
  "agent_intake.source.read": "View Intake Sources",
  "appointment.checkin.payment_bypass": "Bypass Check-in Payment",
  "appointment.cancel": "Cancel Appointment",
  "appointment.create": "Create Appointment",
  "appointment.manage": "Manage Appointments",
  "appointment.read": "View Appointments",
  "appointment.update": "Update Appointment",
  "audit.export": "Export Audit Log",
  "audit.read": "View Audit Log",
  "billing.create": "Create Billing Record",
  "billing.read": "View Billing",
  "billing.receipt": "Issue Receipt",
  "billing.update": "Update Billing Record",
  "clinic.approve": "Approve Clinic Review",
  "clinic.archive": "Archive Clinic",
  "clinic.dashboard.read": "View Clinic Dashboard",
  "clinic.doctor.manage": "Manage Clinic Doctors",
  "clinic.doctor.read": "View Clinic Doctors",
  "clinic.doctor.resubmission": "Resubmit Clinic Doctor",
  "clinic.profile.read": "View Clinic Profile",
  "clinic.profile.update": "Update Clinic Profile",
  "clinic.reject": "Reject Clinic Review",
  "clinic.read": "View Clinic",
  "clinic.review": "Review Clinic",
  "clinic.submit_for_approval": "Submit Clinic for Approval",
  "clinic.update": "Update Clinic",
  "commercial.entitlements.regenerate": "Regenerate Entitlements",
  "commercial.entitlements.view": "View Entitlements",
  "commercial.overrides.activate": "Activate Override",
  "commercial.overrides.cancel": "Cancel Override",
  "commercial.overrides.create": "Create Override",
  "commercial.overrides.manage": "Manage Overrides",
  "commercial.overrides.reject": "Reject Override",
  "commercial.overrides.reconcile": "Reconcile Override",
  "commercial.overrides.review": "Review Override",
  "commercial.overrides.rollback": "Rollback Override",
  "commercial.overrides.submit": "Submit Override",
  "commercial.overrides.view": "View Overrides",
  "commercial.plans.manage": "Manage Plans",
  "commercial.plans.publish": "Publish Plans",
  "commercial.runtime.allowlist.manage": "Manage Runtime Allowlist",
  "consultation.complete": "Finalize Consultation",
  "consultation.create": "Create Consultation",
  "consultation.read": "View Consultation",
  "consultation.update": "Update Consultation",
  "decisioning.execution.override": "Override Decisioning Execution",
  "decisioning.execution.read": "View Decisioning Execution",
  "decisioning.execution.run": "Run Decisioning Execution",
  "decisioning.policy.manage": "Manage Decisioning Policies",
  "decisioning.policy.read": "View Decisioning Policies",
  "discover.provider.application.approve": "Approve Provider Application",
  "discover.provider.application.history.view": "View Provider Application History",
  "discover.provider.application.publish": "Publish Provider Application",
  "discover.provider.application.read": "View Provider Application",
  "discover.provider.application.reject": "Reject Provider Application",
  "discover.provider.application.request.changes": "Request Provider Application Changes",
  "discover.provider.application.review": "Review Provider Application",
  "engage.ai.operate": "Operate Engage AI",
  "engage.analytics.view": "View Engage Analytics",
  "engage.audit.view": "View Engage Audit",
  "engage.campaign.activate": "Activate Campaign",
  "engage.campaign.approve": "Approve Campaign",
  "engage.campaign.lookup": "Lookup Campaign",
  "engage.campaign.manage": "Manage Campaigns",
  "engage.campaign.review": "Review Campaign",
  "engage.campaign.submit": "Submit Campaign",
  "engage.campaign.view": "View Campaigns",
  "engage.lead.assign": "Assign Leads",
  "engage.lead.book.appointment": "Book Appointment from Lead",
  "engage.lead.convert": "Convert Lead",
  "engage.lead.create": "Create Lead",
  "engage.lead.edit": "Edit Lead",
  "engage.lead.export": "Export Leads",
  "engage.lead.follow.up": "Follow Up Leads",
  "engage.lead.import": "Import Leads",
  "engage.lead.view": "View Leads",
  "engage.lead.view.all": "View All Leads",
  "engage.lead.view.audit": "View Lead Audit",
  "engage.message.send": "Send Engage Message",
  "engage.ops.view": "View Engage Operations",
  "engage.provider.admin": "Manage Engage Providers",
  "engage.provider.view": "View Engage Providers",
  "engage.reception.operate": "Operate Engage Reception",
  "engage.reminder.operate": "Operate Reminders",
  "engage.reminder.view": "View Reminders",
  "engage.template.manage": "Manage Templates",
  "engage.template.view": "View Templates",
  "engage.view": "View Engage",
  "engage.webinar.cancel": "Cancel Webinar",
  "engage.webinar.create": "Create Webinar",
  "engage.webinar.edit": "Edit Webinar",
  "engage.webinar.export": "Export Webinar",
  "engage.webinar.manage.registrations": "Manage Webinar Registrations",
  "engage.webinar.publish": "Publish Webinar",
  "engage.webinar.record.attendance": "Record Webinar Attendance",
  "engage.webinar.run.automation": "Run Webinar Automation",
  "engage.webinar.view": "View Webinars",
  "engage.webinar.view.analytics": "View Webinar Analytics",
  "engage.webinar.view.audit": "View Webinar Audit",
  "inventory.create": "Create Inventory Record",
  "inventory.manage": "Manage Inventory",
  "inventory.read": "View Inventory",
  "inventory.update": "Update Inventory",
  "lab.order.collect_payment": "Collect Lab Order Payment",
  "lab.order.collect_sample": "Collect Sample",
  "lab.order.generate_report": "Generate Lab Report",
  "lab.order.read": "View Lab Orders",
  "lab.order.result_entry": "Enter Lab Result",
  "lab.order.review": "Review Lab Result",
  "lab.test.manage": "Manage Lab Tests",
  "lab.test.read": "View Lab Tests",
  "medicine.read": "View Medicines",
  "notification.center.read": "View Notification Center",
  "notification.manage": "Manage Notifications",
  "notification.read": "View Notifications",
  "notification.retry": "Retry Notification",
  "notification.send": "Send Notification",
  "patient.create": "Create Patient",
  "patient.document.delete": "Delete Patient Document",
  "patient.document.manage": "Manage Patient Documents",
  "patient.document.read": "View Patient Documents",
  "patient.document.upload": "Upload Patient Document",
  "patient.read": "View Patients",
  "patient.update": "Update Patient",
  "payment.collect": "Collect Payment",
  "platform.provider_connection.approve": "Approve Provider Connection",
  "platform.provider_connection.audit": "Audit Provider Connections",
  "platform.provider_connection.identity_override": "Override Provider Identity",
  "platform.provider_connection.propose": "Propose Provider Connection",
  "platform.provider_connection.reconcile": "Reconcile Provider Connection",
  "platform.provider_connection.reject": "Reject Provider Connection",
  "platform.provider_connection.unlink": "Unlink Provider Connection",
  "platform.provider_connection.view": "View Provider Connections",
  "platform.tenants.manage": "Manage Tenants",
  "platform.tenants.read": "View Tenants",
  "platform.tenant.create": "Create Tenant",
  "platform.tenant.read": "View Tenant",
  "platform.tenant.update": "Update Tenant",
  "prescription.finalize": "Finalize Prescription",
  "prescription.print": "Print Prescription",
  "prescription.read": "View Prescriptions",
  "prescription.send": "Send Prescription",
  "reconciliation.batch.manage": "Manage Reconciliation Batches",
  "reconciliation.batch.read": "View Reconciliation Batches",
  "reconciliation.exception.manage": "Manage Reconciliation Exceptions",
  "reconciliation.exception.read": "View Reconciliation Exceptions",
  "reconciliation.match.manage": "Manage Reconciliation Matches",
  "reconciliation.match.read": "View Reconciliation Matches",
  "reconciliation.run": "Run Reconciliation",
  "reconciliation.statement.upload": "Upload Reconciliation Statement",
  "report.read": "View Reports",
  "tenant.users.manage": "Manage Users",
  "tenant.users.read": "View Users",
  "tenant.users.reset.password": "Reset User Password",
  "tenant.users.role.assign": "Assign User Roles",
  "user.manage": "Manage Users",
  "user.read": "View Users",
  "queue.read": "View Queue",
  "queue.update": "Manage Queue",
};

const ACTION_VERBS: Array<[RegExp, RbacActionType]> = [
  [/\.read$/, "Read"],
  [/\.create$/, "Create"],
  [/\.update$/, "Update"],
  [/\.delete$/, "Delete"],
  [/\.manage$/, "Manage"],
  [/\.approve$/, "Approve"],
  [/\.review$/, "Review"],
  [/\.run$/, "Execute/Run"],
  [/\.export$/, "Export"],
  [/\.override$/, "Override"],
  [/\.collect_payment$/, "Collect Payment"],
  [/\.finalize$/, "Finalize"],
  [/\.assign$/, "Assign"],
  [/\.reset\.password$/, "Reset Password"],
  [/\.send$/, "Send"],
  [/\.publish$/, "Publish"],
  [/\.cancel$/, "Cancel"],
  [/\.activate$/, "Activate"],
  [/\.reject$/, "Reject"],
  [/\.submit$/, "Submit"],
];

function normalizePermissionKey(permission: string): string {
  return permission.trim().toLowerCase();
}

function titleCase(value: string): string {
  return value
    .replace(/[_\.]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function moduleFor(permission: string): string {
  const normalized = normalizePermissionKey(permission);
  if (normalized.startsWith("appointment.")) return "Appointments";
  if (normalized.startsWith("audit.")) return "Audit";
  if (normalized.startsWith("billing.") || normalized.startsWith("payment.")) return "Billing";
  if (normalized.startsWith("carepilot.") || normalized.startsWith("engage.")) return "CARE";
  if (normalized.startsWith("clinic_generation.")) return "Clinic Generation";
  if (normalized.startsWith("clinic.")) return "Clinic";
  if (normalized.startsWith("commercial.")) return "Commercial";
  if (normalized.startsWith("consultation.")) return "Consultations";
  if (normalized.startsWith("decisioning.")) return "Decisioning";
  if (normalized.startsWith("discover.provider.application.")) return "Discover";
  if (normalized.startsWith("ai_copilot.") || normalized.startsWith("ai.")) return "AI";
  if (normalized.startsWith("lab.")) return "Laboratory";
  if (normalized.startsWith("medicine.") || normalized.startsWith("inventory.")) return "Pharmacy";
  if (normalized.startsWith("notification.")) return "Notifications";
  if (normalized.startsWith("patient.")) return "Patients";
  if (normalized.startsWith("platform.provider_connection.")) return "Platform";
  if (normalized.startsWith("platform.")) return "Platform";
  if (normalized.startsWith("prescription.")) return "Prescriptions";
  if (normalized.startsWith("queue.")) return "Queue";
  if (normalized.startsWith("reconciliation.")) return "Reconciliation";
  if (normalized.startsWith("report.")) return "Reports";
  if (normalized.startsWith("tenant.users.") || normalized.startsWith("user.")) return "Users";
  if (normalized.startsWith("agent.intake.") || normalized.startsWith("agent_intake.")) return "Agent Intake";
  return "Other";
}

function actionFor(permission: string): RbacActionType {
  const normalized = normalizePermissionKey(permission);
  for (const [pattern, action] of ACTION_VERBS) {
    if (pattern.test(normalized)) return action;
  }
  if (normalized.includes(".view")) return "Read";
  if (normalized.includes(".operate")) return "Manage";
  return "Other";
}

function moduleSubject(module: string): string {
  switch (module) {
    case "AI":
      return "AI-assisted workflows";
    case "Appointments":
      return "appointment workflows";
    case "Audit":
      return "audit data";
    case "Billing":
      return "billing and payment workflows";
    case "CARE":
      return "CARE and engagement workflows";
    case "Clinic":
      return "clinic configuration and clinic records";
    case "Clinic Generation":
      return "clinic generation workflows";
    case "Commercial":
      return "commercial platform data";
    case "Consultations":
      return "consultations";
    case "Decisioning":
      return "decisioning workflows";
    case "Discover":
      return "provider application workflows";
    case "Laboratory":
      return "laboratory workflows";
    case "Notifications":
      return "notification workflows";
    case "Patients":
      return "patient records";
    case "Pharmacy":
      return "pharmacy workflows";
    case "Platform":
      return "platform administration data";
    case "Prescriptions":
      return "prescriptions";
    case "Queue":
      return "queue workflows";
    case "Reconciliation":
      return "reconciliation workflows";
    case "Reports":
      return "reports";
    case "Users":
      return "users and identities";
    case "Agent Intake":
      return "agent intake workflows";
    default:
      return "this workflow";
  }
}

function defaultDescription(permission: string, module: string, action: RbacActionType): string {
  const subject = moduleSubject(module);
  switch (action) {
    case "Read":
      return `View ${subject}.`;
    case "Create":
      return `Create ${subject}.`;
    case "Update":
      return `Update ${subject}.`;
    case "Delete":
      return `Delete ${subject}.`;
    case "Manage":
      return `Manage ${subject}.`;
    case "Approve":
      return `Approve ${subject}.`;
    case "Execute/Run":
      return `Run ${subject}.`;
    case "Export":
      return `Export ${subject}.`;
    case "Override":
      return `Override ${subject}.`;
    case "Collect Payment":
      return `Collect payment for ${subject}.`;
    case "Finalize":
      return `Finalize ${subject}.`;
    case "Assign":
      return `Assign ${subject}.`;
    case "Reset Password":
      return `Reset passwords for ${subject}.`;
    case "Send":
      return `Send ${subject}.`;
    case "Publish":
      return `Publish ${subject}.`;
    case "Cancel":
      return `Cancel ${subject}.`;
    case "Review":
      return `Review ${subject}.`;
    case "Activate":
      return `Activate ${subject}.`;
    case "Reject":
      return `Reject ${subject}.`;
    case "Submit":
      return `Submit ${subject}.`;
    default:
      return `Access ${subject}.`;
  }
}

function labelFor(permission: string, module: string, action: RbacActionType): string {
  const normalized = normalizePermissionKey(permission);
  const override = PERMISSION_LABEL_OVERRIDES[normalized];
  if (override) return override;

  const subject = permission
    .split(".")
    .slice(1)
    .join(".")
    .replace(/[_\.]/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  const normalizedSubject = subject || module.toLowerCase();
  const title = titleCase(normalizedSubject);

  switch (action) {
    case "Read":
      return `View ${title}`;
    case "Create":
      return `Create ${title}`;
    case "Update":
      return `Update ${title}`;
    case "Delete":
      return `Delete ${title}`;
    case "Manage":
      return `Manage ${title}`;
    case "Approve":
      return `Approve ${title}`;
    case "Execute/Run":
      return `Run ${title}`;
    case "Export":
      return `Export ${title}`;
    case "Override":
      return `Override ${title}`;
    case "Collect Payment":
      return `Collect Payment`;
    case "Finalize":
      return `Finalize ${title}`;
    case "Assign":
      return `Assign ${title}`;
    case "Reset Password":
      return `Reset Password`;
    case "Send":
      return `Send ${title}`;
    case "Publish":
      return `Publish ${title}`;
    case "Cancel":
      return `Cancel ${title}`;
    case "Review":
      return `Review ${title}`;
    case "Activate":
      return `Activate ${title}`;
    case "Reject":
      return `Reject ${title}`;
    case "Submit":
      return `Submit ${title}`;
    default:
      return titleCase(permission);
  }
}

export function getRoleMetadata(role: string | null | undefined): RoleMetadata {
  const normalized = (role || "").trim().toUpperCase();
  return (
    ROLE_METADATA[normalized] || {
      role: normalized || "UNKNOWN",
      displayName: normalized ? titleCase(normalized) : "Unknown",
      category: "tenant-technical",
      status: "Technical",
      assignableToHuman: false,
      businessVisible: false,
      order: 999,
      summary: "Uncatalogued role.",
      description: "This role is present in the backend authorization model but is not explicitly catalogued in the frontend metadata.",
    }
  );
}

export function formatRoleLabel(role: string | null | undefined): string {
  return getRoleMetadata(role).displayName;
}

export function isBusinessRole(role: string | null | undefined): boolean {
  return getRoleMetadata(role).businessVisible;
}

export function getRoleViewCategoryLabel(role: string | null | undefined): string {
  const metadata = getRoleMetadata(role);
  switch (metadata.category) {
    case "tenant-business":
      return "Tenant Business Role";
    case "tenant-technical":
      return "Tenant Technical Role";
    case "platform-internal":
      return "Platform/Internal Role";
    case "service-system":
      return "Service/System Role";
    case "legacy-compatibility":
      return "Legacy/Compatibility Role";
    default:
      return "Role";
  }
}

export function buildPermissionMetadata(permission: string): PermissionMetadata {
  const module = moduleFor(permission);
  const action = actionFor(permission);
  const normalized = normalizePermissionKey(permission);
  const label = labelFor(permission, module, action);
  return {
    key: permission,
    module,
    label,
    action,
    description: defaultDescription(permission, module, action),
    sensitive: SENSITIVE_PERMISSION_KEYS.has(normalized),
    tenantFacing: module !== "Commercial" && module !== "Decisioning" && module !== "Reconciliation" && module !== "Platform" && module !== "Clinic Generation" && module !== "Discover" && module !== "Agent Intake",
    deprecated: normalized === "pharma" || normalized === "pharmacy",
  };
}

export function getPermissionMetadata(permission: string): PermissionMetadata {
  return buildPermissionMetadata(permission);
}

export function groupPermissionsByModule(permissions: string[], search = "", moduleFilter = "All modules", sensitiveOnly = false): GroupedPermission[] {
  const normalizedSearch = search.trim().toLowerCase();
  const normalizedModule = moduleFilter.trim();
  const grouped = new Map<string, PermissionMetadata[]>();

  for (const permission of permissions) {
    const metadata = buildPermissionMetadata(permission);
    const haystack = [metadata.key, metadata.label, metadata.description, metadata.module].join(" ").toLowerCase();
    if (normalizedSearch && !haystack.includes(normalizedSearch)) continue;
    if (normalizedModule !== "All modules" && metadata.module !== normalizedModule) continue;
    if (sensitiveOnly && !metadata.sensitive) continue;

    const bucket = grouped.get(metadata.module) || [];
    if (!bucket.some((item) => item.key === metadata.key)) {
      bucket.push(metadata);
    }
    grouped.set(metadata.module, bucket);
  }

  return Array.from(grouped.entries())
    .map(([module, values]) => ({
      module,
      permissions: values.sort((left, right) => left.label.localeCompare(right.label)),
    }))
    .sort((left, right) => left.module.localeCompare(right.module));
}

export function getFilteredPermissionCount(permissions: string[], search = "", moduleFilter = "All modules", sensitiveOnly = false): number {
  return groupPermissionsByModule(permissions, search, moduleFilter, sensitiveOnly)
    .reduce((total, group) => total + group.permissions.length, 0);
}

export function getSensitivePermissionCount(permissions: string[]): number {
  return permissions
    .map((permission) => buildPermissionMetadata(permission))
    .filter((permission) => permission.sensitive)
    .length;
}

export function getVisibleRoles<T extends { role: string }>(roles: T[], viewMode: "business" | "technical"): T[] {
  return roles
    .filter((role) => {
      const metadata = getRoleMetadata(role.role);
      return viewMode === "technical" ? metadata.category !== "service-system" : metadata.businessVisible;
    })
    .sort((left, right) => getRoleMetadata(left.role).order - getRoleMetadata(right.role).order);
}

export function getRoleSummary(role: string | null | undefined): string {
  return getRoleMetadata(role).summary;
}

export function getRoleDescription(role: string | null | undefined): string {
  return getRoleMetadata(role).description;
}
