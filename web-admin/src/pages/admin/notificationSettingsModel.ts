import type {
  AdminNotificationChannel,
  AdminNotificationSettings,
  AdminTemplate,
  AdminTemplateCategory,
  AdminTemplateChannel,
  AdminTemplateType,
} from "../../api/clinicApi";

export const CHANNEL_ORDER: AdminNotificationChannel[] = ["IN_APP", "EMAIL", "SMS", "WHATSAPP"];

export type NotificationPolicyChannelMap = Record<AdminNotificationChannel, boolean>;

export type NotificationPolicyRowSpec = {
  key: string;
  label: string;
  description: string;
  templateType: AdminTemplateType;
  templateCategory: AdminTemplateCategory;
  templateChannel: AdminTemplateChannel;
  defaultChannels: NotificationPolicyChannelMap;
};

export type NotificationPolicySectionSpec = {
  key: string;
  title: string;
  description: string;
  icon: string;
  rows: NotificationPolicyRowSpec[];
};

export type NotificationPolicyConfig = {
  sections: Record<string, Record<string, NotificationPolicyChannelMap>>;
  quietHoursAppliesTo: string[];
  compliance: {
    transactionalMessagesEnabled: boolean;
    clinicalNotificationsEnabled: boolean;
    marketingEnabled: boolean;
    patientConsentRequired: boolean;
    retentionDays: number;
    auditEnabled: boolean;
    helpMessage: string;
  };
  rateLimits: {
    overallMessagesPerDay: number;
    marketingPerDay: number;
    reminderPerDay: number;
    maximumPerHour: number;
    perPatientPerDay: number;
  };
};

type RowSeed = Omit<NotificationPolicyRowSpec, "defaultChannels"> & { defaultChannels: Partial<NotificationPolicyChannelMap> };

function channelMap(overrides: Partial<NotificationPolicyChannelMap>): NotificationPolicyChannelMap {
  return {
    IN_APP: overrides.IN_APP ?? true,
    EMAIL: overrides.EMAIL ?? true,
    SMS: overrides.SMS ?? false,
    WHATSAPP: overrides.WHATSAPP ?? false,
  };
}

const row = (seed: RowSeed): NotificationPolicyRowSpec => ({
  ...seed,
  defaultChannels: channelMap(seed.defaultChannels),
});

export const NOTIFICATION_POLICY_SECTIONS: NotificationPolicySectionSpec[] = [
  {
    key: "appointments",
    title: "Appointments",
    description: "Default delivery policy for appointment lifecycle messages.",
    icon: "event",
    rows: [
      row({ key: "bookingConfirmation", label: "Booking Confirmation", description: "Appointment booking confirmations.", templateType: "NOTIFICATION", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: true } }),
      row({ key: "reminder24h", label: "24 Hour Reminder", description: "Reminder sent a day before the visit.", templateType: "REMINDER", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "reminder2h", label: "2 Hour Reminder", description: "Short-window reminder before the visit.", templateType: "REMINDER", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "SMS", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "cancellation", label: "Cancellation", description: "Patient-facing cancellation notice.", templateType: "NOTIFICATION", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "reschedule", label: "Reschedule", description: "Appointment moved to a new time.", templateType: "NOTIFICATION", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "noShow", label: "No Show", description: "Missed appointment follow-up notice.", templateType: "NOTIFICATION", templateCategory: "APPOINTMENT_REMINDER", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
    ],
  },
  {
    key: "billing",
    title: "Billing",
    description: "Bill, reminder, and payment lifecycle defaults.",
    icon: "receipt_long",
    rows: [
      row({ key: "billGenerated", label: "Bill Generated", description: "Invoice or bill issued to the patient.", templateType: "BILLING", templateCategory: "BILLING", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
      row({ key: "paymentReminder", label: "Payment Reminder", description: "Outstanding bill reminder before due date.", templateType: "BILLING", templateCategory: "BILLING", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "paymentReceived", label: "Payment Received", description: "Receipt or payment acknowledgement.", templateType: "BILLING", templateCategory: "BILLING", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
    ],
  },
  {
    key: "clinical",
    title: "Clinical",
    description: "Prescription and follow-up defaults for patient-safe care messages.",
    icon: "medical_services",
    rows: [
      row({ key: "prescriptionReady", label: "Prescription Ready", description: "Patient-visible prescription finalization notice.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: true } }),
      row({ key: "followUpReminder", label: "Follow-up Reminder", description: "Reminder for a care-team follow-up.", templateType: "REMINDER", templateCategory: "FOLLOW_UP", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
    ],
  },
  {
    key: "laboratory",
    title: "Laboratory",
    description: "Lab order and report readiness defaults.",
    icon: "science",
    rows: [
      row({ key: "labOrderCreated", label: "Lab Order Created", description: "A new lab order has been created.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "INTERNAL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
      row({ key: "sampleCollected", label: "Sample Collected", description: "Sample collection confirmation.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "INTERNAL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
      row({ key: "reportReady", label: "Report Ready", description: "Patient-visible report completion notice.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: true } }),
    ],
  },
  {
    key: "vaccination",
    title: "Vaccination",
    description: "Dose due and booster reminder defaults.",
    icon: "vaccines",
    rows: [
      row({ key: "vaccinationDue", label: "Vaccination Due", description: "Vaccination reminder before the scheduled due date.", templateType: "REMINDER", templateCategory: "VACCINATION", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "boosterReminder", label: "Booster Reminder", description: "Booster or repeat dose reminder.", templateType: "REMINDER", templateCategory: "VACCINATION", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
    ],
  },
  {
    key: "engage",
    title: "Engage",
    description: "Operational outreach defaults for CarePilot-style workflows.",
    icon: "campaign",
    rows: [
      row({ key: "leadFollowUp", label: "Lead Follow-up", description: "Follow-up on an engaged lead.", templateType: "CAMPAIGN", templateCategory: "LEAD", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: true, WHATSAPP: false } }),
      row({ key: "webinarReminder", label: "Webinar Reminder", description: "Reminder for a scheduled webinar.", templateType: "WEBINAR", templateCategory: "WEBINAR", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
      row({ key: "birthdayGreeting", label: "Birthday Greeting", description: "Patient birthday or wellness greeting.", templateType: "CAMPAIGN", templateCategory: "WELLNESS", templateChannel: "EMAIL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
    ],
  },
  {
    key: "system",
    title: "System",
    description: "Critical and maintenance notifications for tenant operations.",
    icon: "settings",
    rows: [
      row({ key: "maintenance", label: "Maintenance", description: "Planned maintenance or downtime notice.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "INTERNAL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
      row({ key: "platformAlerts", label: "Platform Alerts", description: "Critical platform operations alerts.", templateType: "NOTIFICATION", templateCategory: "GENERAL", templateChannel: "INTERNAL", defaultChannels: { IN_APP: true, EMAIL: true, SMS: false, WHATSAPP: false } }),
    ],
  },
];

export const QUIET_HOUR_SCOPE_OPTIONS = [
  { value: "appointments", label: "Appointment reminders" },
  { value: "billing", label: "Billing" },
  { value: "marketing", label: "Marketing" },
  { value: "followUp", label: "Follow-up" },
  { value: "vaccination", label: "Vaccination" },
];

export const DEFAULT_RATE_LIMITS = {
  overallMessagesPerDay: 100,
  marketingPerDay: 20,
  reminderPerDay: 40,
  maximumPerHour: 12,
  perPatientPerDay: 5,
};

export const DEFAULT_COMPLIANCE = {
  transactionalMessagesEnabled: true,
  clinicalNotificationsEnabled: true,
  marketingEnabled: false,
  patientConsentRequired: true,
  retentionDays: 365,
  auditEnabled: true,
  helpMessage: "Patient-specific preferences override tenant defaults.",
};

export function createDefaultNotificationPolicy(): NotificationPolicyConfig {
  const sections: NotificationPolicyConfig["sections"] = {};
  for (const section of NOTIFICATION_POLICY_SECTIONS) {
    const rows: Record<string, NotificationPolicyChannelMap> = {};
    for (const spec of section.rows) {
      rows[spec.key] = { ...spec.defaultChannels };
    }
    sections[section.key] = rows;
  }
  return {
    sections,
    quietHoursAppliesTo: QUIET_HOUR_SCOPE_OPTIONS.map((option) => option.value),
    compliance: { ...DEFAULT_COMPLIANCE },
    rateLimits: { ...DEFAULT_RATE_LIMITS },
  };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function normalizeChannelMap(value: unknown, fallback: NotificationPolicyChannelMap): NotificationPolicyChannelMap {
  if (!isObject(value)) return { ...fallback };
  return {
    IN_APP: Boolean(value.IN_APP ?? fallback.IN_APP),
    EMAIL: Boolean(value.EMAIL ?? fallback.EMAIL),
    SMS: Boolean(value.SMS ?? fallback.SMS),
    WHATSAPP: Boolean(value.WHATSAPP ?? fallback.WHATSAPP),
  };
}

export function parseNotificationPolicy(rawJson: string | null | undefined): NotificationPolicyConfig {
  const defaults = createDefaultNotificationPolicy();
  if (!rawJson || !rawJson.trim()) return defaults;
  try {
    const parsed = JSON.parse(rawJson) as unknown;
    if (!isObject(parsed)) return defaults;

    const next = createDefaultNotificationPolicy();
    if (isObject(parsed.sections)) {
      for (const section of NOTIFICATION_POLICY_SECTIONS) {
        const rawSection = parsed.sections[section.key];
        if (!isObject(rawSection)) continue;
        for (const rowSpec of section.rows) {
          next.sections[section.key][rowSpec.key] = normalizeChannelMap(rawSection[rowSpec.key], rowSpec.defaultChannels);
        }
      }
    }
    if (Array.isArray(parsed.quietHoursAppliesTo)) {
      next.quietHoursAppliesTo = parsed.quietHoursAppliesTo.filter((item): item is string => typeof item === "string");
    }
    if (isObject(parsed.compliance)) {
      next.compliance = {
        transactionalMessagesEnabled: Boolean(parsed.compliance.transactionalMessagesEnabled ?? next.compliance.transactionalMessagesEnabled),
        clinicalNotificationsEnabled: Boolean(parsed.compliance.clinicalNotificationsEnabled ?? next.compliance.clinicalNotificationsEnabled),
        marketingEnabled: Boolean(parsed.compliance.marketingEnabled ?? next.compliance.marketingEnabled),
        patientConsentRequired: Boolean(parsed.compliance.patientConsentRequired ?? next.compliance.patientConsentRequired),
        retentionDays: Number(parsed.compliance.retentionDays ?? next.compliance.retentionDays),
        auditEnabled: Boolean(parsed.compliance.auditEnabled ?? next.compliance.auditEnabled),
        helpMessage: String(parsed.compliance.helpMessage ?? next.compliance.helpMessage),
      };
    }
    if (isObject(parsed.rateLimits)) {
      next.rateLimits = {
        overallMessagesPerDay: Number(parsed.rateLimits.overallMessagesPerDay ?? next.rateLimits.overallMessagesPerDay),
        marketingPerDay: Number(parsed.rateLimits.marketingPerDay ?? next.rateLimits.marketingPerDay),
        reminderPerDay: Number(parsed.rateLimits.reminderPerDay ?? next.rateLimits.reminderPerDay),
        maximumPerHour: Number(parsed.rateLimits.maximumPerHour ?? next.rateLimits.maximumPerHour),
        perPatientPerDay: Number(parsed.rateLimits.perPatientPerDay ?? next.rateLimits.perPatientPerDay),
      };
    }
    return next;
  } catch {
    return defaults;
  }
}

export function serializeNotificationPolicy(policy: NotificationPolicyConfig): string {
  return JSON.stringify(policy);
}

export function policySectionByKey(sectionKey: string) {
  return NOTIFICATION_POLICY_SECTIONS.find((section) => section.key === sectionKey) ?? null;
}

export function selectCurrentTemplate(templates: AdminTemplate[], spec: NotificationPolicyRowSpec): AdminTemplate | null {
  const matches = templates.filter((template) =>
    template.templateType === spec.templateType
    && template.category === spec.templateCategory
    && template.channel === spec.templateChannel
  );
  if (matches.length === 0) return null;
  return [...matches].sort((left, right) => {
    if (left.systemTemplate !== right.systemTemplate) return left.systemTemplate ? -1 : 1;
    return right.updatedAt.localeCompare(left.updatedAt);
  })[0] ?? null;
}

export function notificationTypeFilterParams(spec: NotificationPolicyRowSpec): string {
  const params = new URLSearchParams({
    templateType: spec.templateType,
    category: spec.templateCategory,
    channel: spec.templateChannel,
  });
  return params.toString();
}

export function groupTitleForSection(sectionKey: string): string {
  return policySectionByKey(sectionKey)?.title ?? sectionKey;
}
