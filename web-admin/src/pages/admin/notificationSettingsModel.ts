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
  quietHoursSchedule: {
    weekdays: QuietHoursWeekday[];
    effectiveFrom: string | null;
    effectiveUntil: string | null;
  };
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

export type QuietHoursWeekday =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";

export type RateLimitField = keyof NotificationPolicyConfig["rateLimits"];

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

export const QUIET_HOUR_WEEKDAY_OPTIONS: Array<{ value: QuietHoursWeekday; label: string }> = [
  { value: "MONDAY", label: "Monday" },
  { value: "TUESDAY", label: "Tuesday" },
  { value: "WEDNESDAY", label: "Wednesday" },
  { value: "THURSDAY", label: "Thursday" },
  { value: "FRIDAY", label: "Friday" },
  { value: "SATURDAY", label: "Saturday" },
  { value: "SUNDAY", label: "Sunday" },
];

export const DEFAULT_RATE_LIMITS = {
  overallMessagesPerDay: 100,
  marketingPerDay: 20,
  reminderPerDay: 40,
  maximumPerHour: 12,
  perPatientPerDay: 5,
};

export const RATE_LIMIT_FIELD_SPECS: Array<{ key: RateLimitField; label: string }> = [
  { key: "overallMessagesPerDay", label: "Overall messages/day" },
  { key: "marketingPerDay", label: "Marketing/day" },
  { key: "reminderPerDay", label: "Reminder/day" },
  { key: "maximumPerHour", label: "Maximum/hour" },
  { key: "perPatientPerDay", label: "Per patient/day" },
];

const RATE_LIMIT_MAX_VALUE = 2_147_483_647;
const RATE_LIMIT_REQUIRED_MESSAGE = "Enter a whole number greater than zero.";
const RATE_LIMIT_WHOLE_NUMBER_MESSAGE = "Enter a whole number.";
const RATE_LIMIT_NEGATIVE_MESSAGE = "Value cannot be negative.";
const RATE_LIMIT_TOO_LARGE_MESSAGE = "Value is too large.";

export function rateLimitDraftsFromRateLimits(rateLimits: NotificationPolicyConfig["rateLimits"]): Record<RateLimitField, string> {
  return {
    overallMessagesPerDay: String(rateLimits.overallMessagesPerDay),
    marketingPerDay: String(rateLimits.marketingPerDay),
    reminderPerDay: String(rateLimits.reminderPerDay),
    maximumPerHour: String(rateLimits.maximumPerHour),
    perPatientPerDay: String(rateLimits.perPatientPerDay),
  };
}

export function rateLimitDraftsFromPolicy(policy: NotificationPolicyConfig): Record<RateLimitField, string> {
  return rateLimitDraftsFromRateLimits(policy.rateLimits);
}

export function rateLimitDraftsFromRawJson(rawJson: string | null | undefined): Record<RateLimitField, string> {
  const fallback = rateLimitDraftsFromRateLimits(DEFAULT_RATE_LIMITS);
  if (!rawJson || !rawJson.trim()) return fallback;
  try {
    const parsed = JSON.parse(rawJson) as unknown;
    if (!isObject(parsed) || !isObject(parsed.rateLimits)) return fallback;
    const rateLimits = parsed.rateLimits as Record<string, unknown>;
    return {
      overallMessagesPerDay: rateLimitDraftFromValue(rateLimits.overallMessagesPerDay, fallback.overallMessagesPerDay),
      marketingPerDay: rateLimitDraftFromValue(rateLimits.marketingPerDay, fallback.marketingPerDay),
      reminderPerDay: rateLimitDraftFromValue(rateLimits.reminderPerDay, fallback.reminderPerDay),
      maximumPerHour: rateLimitDraftFromValue(rateLimits.maximumPerHour, fallback.maximumPerHour),
      perPatientPerDay: rateLimitDraftFromValue(rateLimits.perPatientPerDay, fallback.perPatientPerDay),
    };
  } catch {
    return fallback;
  }
}

export function validateRateLimitDrafts(drafts: Record<RateLimitField, string>): Record<RateLimitField, string | null> {
  return {
    overallMessagesPerDay: validateRateLimitDraft(drafts.overallMessagesPerDay),
    marketingPerDay: validateRateLimitDraft(drafts.marketingPerDay),
    reminderPerDay: validateRateLimitDraft(drafts.reminderPerDay),
    maximumPerHour: validateRateLimitDraft(drafts.maximumPerHour),
    perPatientPerDay: validateRateLimitDraft(drafts.perPatientPerDay),
  };
}

export function rateLimitValuesFromDrafts(drafts: Record<RateLimitField, string>): NotificationPolicyConfig["rateLimits"] {
  const validated = validateRateLimitDrafts(drafts);
  const firstError = RATE_LIMIT_FIELD_SPECS.find((spec) => validated[spec.key]);
  if (firstError) {
    throw new Error(validated[firstError.key] || RATE_LIMIT_REQUIRED_MESSAGE);
  }
  return {
    overallMessagesPerDay: parseRateLimitDraft(drafts.overallMessagesPerDay).value!,
    marketingPerDay: parseRateLimitDraft(drafts.marketingPerDay).value!,
    reminderPerDay: parseRateLimitDraft(drafts.reminderPerDay).value!,
    maximumPerHour: parseRateLimitDraft(drafts.maximumPerHour).value!,
    perPatientPerDay: parseRateLimitDraft(drafts.perPatientPerDay).value!,
  };
}

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
    quietHoursSchedule: {
      weekdays: QUIET_HOUR_WEEKDAY_OPTIONS.map((option) => option.value),
      effectiveFrom: null,
      effectiveUntil: null,
    },
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

function rateLimitDraftFromValue(value: unknown, fallback: string): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  if (typeof value === "bigint") return value.toString();
  if (value == null) return fallback;
  return String(value);
}

function rateLimitValueFromPolicyValue(value: unknown, fallback: number): number {
  const draft = rateLimitDraftFromValue(value, String(fallback));
  const parsed = parseRateLimitDraft(draft);
  return parsed.value ?? fallback;
}

function parseRateLimitDraft(rawValue: string): { value: number | null; error: string | null } {
  const trimmed = rawValue.trim();
  if (!trimmed) {
    return { value: null, error: RATE_LIMIT_REQUIRED_MESSAGE };
  }
  if (!/^[+-]?\d+$/.test(trimmed)) {
    return { value: null, error: RATE_LIMIT_WHOLE_NUMBER_MESSAGE };
  }
  try {
    const parsed = BigInt(trimmed);
    if (parsed < 0n) {
      return { value: null, error: RATE_LIMIT_NEGATIVE_MESSAGE };
    }
    if (parsed === 0n) {
      return { value: null, error: RATE_LIMIT_REQUIRED_MESSAGE };
    }
    if (parsed > BigInt(RATE_LIMIT_MAX_VALUE)) {
      return { value: null, error: RATE_LIMIT_TOO_LARGE_MESSAGE };
    }
    return { value: Number(parsed), error: null };
  } catch {
    return { value: null, error: RATE_LIMIT_WHOLE_NUMBER_MESSAGE };
  }
}

function validateRateLimitDraft(rawValue: string): string | null {
  return parseRateLimitDraft(rawValue).error;
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
    if (isObject(parsed.quietHoursSchedule)) {
      if (Array.isArray(parsed.quietHoursSchedule.weekdays)) {
        const weekdays = parsed.quietHoursSchedule.weekdays.filter((item): item is QuietHoursWeekday =>
          typeof item === "string" && QUIET_HOUR_WEEKDAY_OPTIONS.some((option) => option.value === item)
        );
        if (weekdays.length) {
          next.quietHoursSchedule.weekdays = weekdays;
        }
      }
      if (typeof parsed.quietHoursSchedule.effectiveFrom === "string" || parsed.quietHoursSchedule.effectiveFrom === null) {
        next.quietHoursSchedule.effectiveFrom = parsed.quietHoursSchedule.effectiveFrom ? String(parsed.quietHoursSchedule.effectiveFrom) : null;
      }
      if (typeof parsed.quietHoursSchedule.effectiveUntil === "string" || parsed.quietHoursSchedule.effectiveUntil === null) {
        next.quietHoursSchedule.effectiveUntil = parsed.quietHoursSchedule.effectiveUntil ? String(parsed.quietHoursSchedule.effectiveUntil) : null;
      }
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
        overallMessagesPerDay: rateLimitValueFromPolicyValue(parsed.rateLimits.overallMessagesPerDay, next.rateLimits.overallMessagesPerDay),
        marketingPerDay: rateLimitValueFromPolicyValue(parsed.rateLimits.marketingPerDay, next.rateLimits.marketingPerDay),
        reminderPerDay: rateLimitValueFromPolicyValue(parsed.rateLimits.reminderPerDay, next.rateLimits.reminderPerDay),
        maximumPerHour: rateLimitValueFromPolicyValue(parsed.rateLimits.maximumPerHour, next.rateLimits.maximumPerHour),
        perPatientPerDay: rateLimitValueFromPolicyValue(parsed.rateLimits.perPatientPerDay, next.rateLimits.perPatientPerDay),
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
