import { parseCarePilotDateTimeInput } from "../shared/carepilotFormatting.js";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const DATETIME_LOCAL_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;
const LEAD_PHONE_MESSAGE = "Enter a valid 10-digit mobile number.";
const ENGAGE_ASSIGNEE_ROLES = new Set(["ENGAGE_EXECUTIVE", "ENGAGE_MANAGER", "CLINIC_ADMIN"]);

function trimmed(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeCommaSeparatedText(value) {
  if (typeof value !== "string") {
    return "";
  }
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean)
    .join(", ");
}

function normalizeCommaSeparatedTextForComparison(value) {
  const normalized = normalizeCommaSeparatedText(value);
  if (!normalized) {
    return "";
  }
  return normalized
    .split(", ")
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right, undefined, { sensitivity: "base" }))
    .join(", ");
}

function normalizeIndianMobileInput(value) {
  if (typeof value !== "string") {
    return value;
  }
  const digits = value.replace(/[^\d]/g, "");
  if (digits.length === 12 && digits.startsWith("91")) {
    return digits.slice(2);
  }
  return digits;
}

function toIsoOffsetDateTime(value) {
  const trimmedValue = trimmed(value);
  if (!trimmedValue) {
    return null;
  }
  const parsed = new Date(trimmedValue);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

function normalizeMembershipStatus(value) {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}

function normalizeRoleValue(value) {
  return typeof value === "string" ? value.trim().toUpperCase() : "";
}

function isEligibleEngageAssignee(user) {
  if (!user || !user.appUserId) {
    return false;
  }
  const membershipStatus = normalizeMembershipStatus(user.membershipStatus);
  const userStatus = normalizeRoleValue(user.userStatus || user.status);
  const membershipRole = normalizeRoleValue(user.membershipRole);
  return membershipStatus === "ACTIVE"
    && userStatus === "ACTIVE"
    && ENGAGE_ASSIGNEE_ROLES.has(membershipRole);
}

export function toLeadDateTimeInputValue(value) {
  if (!value) {
    return "";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return "";
  }
  const local = new Date(parsed.getTime() - (parsed.getTimezoneOffset() * 60 * 1000)).toISOString();
  return local.slice(0, 16);
}

export function validateLeadDraft(draft, clinicUsers = [], clinicTimeZone = "UTC") {
  const fieldErrors = {};
  const normalizedPhone = String(normalizeIndianMobileInput(draft.phone) || "").trim();
  const trimmedNextFollowUpAt = trimmed(draft.nextFollowUpAt);
  const trimmedEmail = trimmed(draft.email);
  const trimmedFirstName = trimmed(draft.firstName);
  const trimmedAssignedToAppUserId = trimmed(draft.assignedToAppUserId);
  const selectedUser = trimmedAssignedToAppUserId ? clinicUsers.find((user) => user.appUserId === trimmedAssignedToAppUserId) || null : null;

  if (!trimmedFirstName) {
    fieldErrors.firstName = "First name is required.";
  }
  if (!/^[6-9]\d{9}$/.test(normalizedPhone)) {
    fieldErrors.phone = LEAD_PHONE_MESSAGE;
  }
  if (trimmedEmail && !EMAIL_RE.test(trimmedEmail)) {
    fieldErrors.email = "Enter a valid email address.";
  }
  if (trimmedNextFollowUpAt && !DATETIME_LOCAL_RE.test(trimmedNextFollowUpAt)) {
    fieldErrors.nextFollowUpAt = "Select a valid follow-up date and time.";
  } else if (trimmedNextFollowUpAt) {
    const parsed = parseCarePilotDateTimeInput(trimmedNextFollowUpAt, clinicTimeZone);
    if (!parsed || new Date(parsed).getTime() <= Date.now()) {
      fieldErrors.nextFollowUpAt = "Follow-up date and time must be in the future.";
    }
  }
  if (trimmedAssignedToAppUserId) {
    if (!selectedUser || !isEligibleEngageAssignee(selectedUser)) {
      fieldErrors.assignedToAppUserId = "Select an active Engage user.";
    }
  }

  return {
    fieldErrors,
    normalizedPhone,
    normalizedNextFollowUpAt: trimmedNextFollowUpAt,
  };
}

export function filterEligibleEngageAssignees(clinicUsers = []) {
  return clinicUsers.filter((user) => isEligibleEngageAssignee(user));
}

export function validateFollowUpScheduleDraft(draft, clinicTimeZone) {
  const fieldErrors = {};
  const date = trimmed(draft?.date);
  const time = trimmed(draft?.time);
  if (!date) {
    fieldErrors.date = "Follow-up date is required.";
  }
  if (!time) {
    fieldErrors.time = "Follow-up time is required.";
  }
  if (date && time) {
    const parsed = parseCarePilotDateTimeInput(`${date}T${time}`, clinicTimeZone);
    if (!parsed || new Date(parsed).getTime() <= Date.now()) {
      const message = "Follow-up date and time must be in the future.";
      fieldErrors.date = message;
      fieldErrors.time = message;
    }
  }
  return {
    fieldErrors,
    nextFollowUpAt: date && time ? parseCarePilotDateTimeInput(`${date}T${time}`, clinicTimeZone) : null,
  };
}

export function toConvertedLeadMetadataSnapshot(source) {
  return {
    notes: trimmed(source?.notes),
    tags: normalizeCommaSeparatedTextForComparison(source?.tags),
    sourceDetails: trimmed(source?.sourceDetails),
    campaignId: trimmed(source?.campaignId),
    assignedToAppUserId: trimmed(source?.assignedToAppUserId),
  };
}

export function hasConvertedLeadMetadataChanges(current, baseline) {
  const currentSnapshot = toConvertedLeadMetadataSnapshot(current);
  const baselineSnapshot = toConvertedLeadMetadataSnapshot(baseline);
  return Object.keys(currentSnapshot).some((key) => currentSnapshot[key] !== baselineSnapshot[key]);
}

export function buildConvertedLeadMetadataPayload(draft, baseline) {
  const currentComparison = toConvertedLeadMetadataSnapshot(draft);
  const baselineComparison = toConvertedLeadMetadataSnapshot(baseline);
  const currentPayload = {
    notes: trimmed(draft?.notes),
    tags: normalizeCommaSeparatedText(draft?.tags),
    sourceDetails: trimmed(draft?.sourceDetails),
    campaignId: trimmed(draft?.campaignId),
    assignedToAppUserId: trimmed(draft?.assignedToAppUserId),
  };
  const payload = {};
  for (const key of Object.keys(currentPayload)) {
    if (currentComparison[key] === baselineComparison[key]) {
      continue;
    }
    payload[key] = currentPayload[key] || null;
  }
  return payload;
}

export function buildLeadCreatePayload(draft, normalizedPhone) {
  const nextFollowUpAt = toIsoOffsetDateTime(draft.nextFollowUpAt);
  return {
    firstName: trimmed(draft.firstName),
    lastName: trimmed(draft.lastName) || null,
    phone: normalizedPhone,
    email: trimmed(draft.email) || null,
    source: draft.source,
    sourceDetails: trimmed(draft.sourceDetails) || null,
    status: draft.status,
    priority: draft.priority,
    notes: trimmed(draft.notes) || null,
    tags: normalizeCommaSeparatedText(draft.tags) || null,
    campaignId: trimmed(draft.campaignId) || null,
    assignedToAppUserId: trimmed(draft.assignedToAppUserId) || null,
    nextFollowUpAt,
  };
}

export function mapLeadApiErrorToFieldErrors(message) {
  const fieldErrors = {};
  const text = typeof message === "string" ? message.trim() : "";
  if (!text) {
    return fieldErrors;
  }

  const fragments = text.split(",").map((item) => item.trim()).filter(Boolean);
  for (const fragment of fragments) {
    const indexed = fragment.match(/^([A-Za-z0-9_.]+):\s*(.+)$/);
    if (indexed) {
      const field = normalizeFieldName(indexed[1]);
      const resolved = resolveFieldMessage(field, indexed[2]);
      if (resolved && !fieldErrors[field]) {
        fieldErrors[field] = resolved;
      }
      continue;
    }

    const lower = fragment.toLowerCase();
    if (lower.includes("mobile number") || lower.includes("phone")) {
      fieldErrors.phone = LEAD_PHONE_MESSAGE;
    } else if (lower.includes("email")) {
      fieldErrors.email = "Enter a valid email address.";
    } else if (lower.includes("follow up") || lower.includes("follow-up")) {
      fieldErrors.nextFollowUpAt = "Select a valid follow-up date and time.";
    } else if (lower.includes("assignee") || lower.includes("assigned")) {
      fieldErrors.assignedToAppUserId = "Select an active Engage user.";
    } else if (lower.includes("first name")) {
      fieldErrors.firstName = "First name is required.";
    } else if (lower.includes("source")) {
      fieldErrors.source = "Select a lead source.";
    } else if (lower.includes("campaign")) {
      fieldErrors.campaignId = "Select a valid campaign.";
    }
  }

  return fieldErrors;
}

function normalizeFieldName(field) {
  const normalized = field.trim();
  if (normalized === "nextFollowUpAt") return "nextFollowUpAt";
  if (normalized === "assignedToAppUserId") return "assignedToAppUserId";
  if (normalized === "campaignId") return "campaignId";
  if (normalized === "firstName") return "firstName";
  if (normalized === "lastName") return "lastName";
  if (normalized === "phone") return "phone";
  if (normalized === "email") return "email";
  if (normalized === "source") return "source";
  if (normalized === "status") return "status";
  if (normalized === "priority") return "priority";
  return normalized;
}

function resolveFieldMessage(field, message) {
  const lower = String(message || "").toLowerCase();
  if (field === "phone" || lower.includes("mobile number")) {
    return LEAD_PHONE_MESSAGE;
  }
  if (field === "email" || lower.includes("email")) {
    return "Enter a valid email address.";
  }
  if (field === "nextFollowUpAt" || lower.includes("date") || lower.includes("time")) {
    return "Select a valid follow-up date and time.";
  }
  if (field === "assignedToAppUserId" || lower.includes("assignee")) {
    return "Select an active Engage user.";
  }
  if (field === "firstName") {
    return "First name is required.";
  }
  if (field === "source") {
    return "Select a lead source.";
  }
  if (field === "campaignId") {
    return "Select a valid campaign.";
  }
  return null;
}
