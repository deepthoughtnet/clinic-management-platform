import { formatCarePilotDateTimeInput, parseCarePilotDateTimeInput } from "../shared/carepilotFormatting.js";

const DATE_TIME_LOCAL_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const START_REQUIRED_MESSAGE = "Start date/time is required.";
const END_REQUIRED_MESSAGE = "End date/time is required.";

const WEBINAR_TYPES = new Set(["HEALTH_AWARENESS", "WELLNESS", "CLINIC_EVENT", "MARKETING", "EDUCATIONAL", "OTHER"]);

function trimmed(value) {
  return typeof value === "string" ? value.trim() : "";
}

function normalizeOptionalString(value) {
  const text = trimmed(value);
  return text || null;
}

function toIsoOffsetDateTime(value) {
  const input = trimmed(value);
  if (!input) {
    return null;
  }
  const parsed = new Date(input);
  return Number.isNaN(parsed.getTime()) ? null : parsed.toISOString();
}

function isValidUrl(value) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}

function isNonNegativeInteger(value) {
  return Number.isInteger(value) && value >= 0;
}

function validateDateTimeField(value, requiredMessage) {
  const input = trimmed(value);
  if (!input) {
    return requiredMessage;
  }
  if (!DATE_TIME_LOCAL_RE.test(input)) {
    return "Select a valid date and time.";
  }
  return undefined;
}

function validateOptionalEmail(value) {
  const input = trimmed(value);
  if (!input) {
    return undefined;
  }
  return EMAIL_RE.test(input) ? undefined : "Enter a valid email address.";
}

function validateOptionalUrl(value) {
  const input = trimmed(value);
  if (!input) {
    return undefined;
  }
  return isValidUrl(input) ? undefined : "Enter a valid webinar URL.";
}

function validateOptionalCapacity(value) {
  const input = trimmed(value);
  if (!input) {
    return undefined;
  }
  const parsed = Number(input);
  return isNonNegativeInteger(parsed) ? undefined : "Capacity must be zero or greater.";
}

export function validateWebinarDraft(draft) {
  const fieldErrors = {};

  const title = trimmed(draft.title);
  if (!title) {
    fieldErrors.title = "Title is required and must be 60 characters or fewer.";
  } else if (title.length > 60) {
    fieldErrors.title = "Title is required and must be 60 characters or fewer.";
  }

  if (!WEBINAR_TYPES.has(String(draft.webinarType || "").trim())) {
    fieldErrors.webinarType = "Select a valid webinar type.";
  }

  const startError = validateDateTimeField(draft.scheduledStartAt, START_REQUIRED_MESSAGE);
  const endError = validateDateTimeField(draft.scheduledEndAt, END_REQUIRED_MESSAGE);
  if (startError) {
    fieldErrors.scheduledStartAt = startError;
  }
  if (endError) {
    fieldErrors.scheduledEndAt = endError;
  }

  const start = trimmed(draft.scheduledStartAt);
  const end = trimmed(draft.scheduledEndAt);
  if (start && end && start > end) {
    fieldErrors.scheduledEndAt = "End date/time must be on or after start date/time.";
  }

  const timezone = trimmed(draft.timezone);
  if (!timezone) {
    fieldErrors.timezone = "Timezone is required and must be 60 characters or fewer.";
  } else if (timezone.length > 60) {
    fieldErrors.timezone = "Timezone is required and must be 60 characters or fewer.";
  }

  if (trimmed(draft.description).length > 250) {
    fieldErrors.description = "Description must be 250 characters or fewer.";
  }

  const webinarUrlError = validateOptionalUrl(draft.webinarUrl);
  if (webinarUrlError) {
    fieldErrors.webinarUrl = webinarUrlError;
  } else if (trimmed(draft.webinarUrl).length > 250) {
    fieldErrors.webinarUrl = "Webinar URL must be 250 characters or fewer.";
  }

  const organizerName = trimmed(draft.organizerName);
  if (organizerName.length > 60) {
    fieldErrors.organizerName = "Organizer name must be 60 characters or fewer.";
  }

  const organizerEmailError = validateOptionalEmail(draft.organizerEmail);
  if (organizerEmailError) {
    fieldErrors.organizerEmail = organizerEmailError;
  }

  const tags = trimmed(draft.tags);
  if (tags.length > 250) {
    fieldErrors.tags = "Tags must be 250 characters or fewer.";
  }

  const capacityError = validateOptionalCapacity(draft.capacity);
  if (capacityError) {
    fieldErrors.capacity = capacityError;
  }

  return fieldErrors;
}

export function buildWebinarPayload(form) {
  const timezone = trimmed(form.timezone);
  return {
    title: trimmed(form.title),
    description: normalizeOptionalString(form.description),
    webinarType: form.webinarType,
    campaignId: normalizeOptionalString(form.campaignId),
    webinarUrl: normalizeOptionalString(form.webinarUrl),
    organizerName: normalizeOptionalString(form.organizerName),
    organizerEmail: normalizeOptionalString(form.organizerEmail),
    scheduledStartAt: parseCarePilotDateTimeInput(form.scheduledStartAt, timezone),
    scheduledEndAt: parseCarePilotDateTimeInput(form.scheduledEndAt, timezone),
    timezone,
    capacity: trimmed(form.capacity) ? Number(form.capacity) : null,
    registrationEnabled: Boolean(form.registrationEnabled),
    reminderEnabled: Boolean(form.reminderEnabled),
    followupEnabled: Boolean(form.followupEnabled),
    tags: normalizeOptionalString(form.tags),
    status: form.status,
  };
}

export function getWebinarDateFieldErrors(draft) {
  const errors = validateWebinarDraft(draft);
  return {
    scheduledStartAt: errors.scheduledStartAt,
    scheduledEndAt: errors.scheduledEndAt,
  };
}

export { formatCarePilotDateTimeInput, parseCarePilotDateTimeInput };
