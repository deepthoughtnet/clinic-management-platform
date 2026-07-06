const BUSINESS_STATUS_LABELS = {
  SCHEDULED: "Appointment Booked",
  BOOKED: "Appointment Booked",
  AWAITING_PAYMENT: "Awaiting Payment",
  PAYMENT_PENDING: "Awaiting Payment",
  UNPAID: "Awaiting Payment",
  PAID: "Ready for Check-in",
  CHECKED_IN: "Waiting for Doctor",
  WAITING: "Waiting for Doctor",
  IN_CONSULTATION: "In Consultation",
  CONSULTATION_COMPLETED: "Consultation Completed",
  COMPLETED: "Visit Completed",
  CANCELLED: "Cancelled",
  NO_SHOW: "No-show",
  PARTIALLY_BOOKED: "Partially booked",
};

const BUSINESS_STATUS_TONES = {
  SCHEDULED: "info",
  BOOKED: "info",
  AWAITING_PAYMENT: "warning",
  PAYMENT_PENDING: "warning",
  UNPAID: "warning",
  PAID: "success",
  CHECKED_IN: "primary",
  WAITING: "warning",
  IN_CONSULTATION: "secondary",
  CONSULTATION_COMPLETED: "success",
  COMPLETED: "success",
  CANCELLED: "default",
  NO_SHOW: "default",
  PARTIALLY_BOOKED: "warning",
};

const JOURNEY_STAGES = [
  "APPOINTMENT",
  "REGISTRATION",
  "PAYMENT",
  "CHECK_IN",
  "WAITING",
  "CONSULTATION",
  "PRESCRIPTION",
  "LABORATORY",
  "PHARMACY",
  "BILLING_COMPLETE",
  "COMPLETED",
];

function normalizeValue(value) {
  return String(value || "").trim().toUpperCase();
}

function toDate(value) {
  if (value instanceof Date) {
    return value;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function getWorkflowStatusLabel(status) {
  const normalized = normalizeValue(status);
  if (!normalized) return null;
  return BUSINESS_STATUS_LABELS[normalized] || normalized.replaceAll("_", " ").toLowerCase().replace(/(^|\s)\S/g, (match) => match.toUpperCase());
}

export function getWorkflowStatusTone(status) {
  const normalized = normalizeValue(status);
  return BUSINESS_STATUS_TONES[normalized] || "default";
}

export function getAppointmentTokenValue(appointment) {
  if (!appointment) return null;
  const displayReference = String(appointment.displayReference || "").trim();
  if (displayReference) return displayReference;
  if (appointment.tokenNumber != null) return `APT-${appointment.tokenNumber}`;
  const token = String(appointment.token || "").trim();
  return token || null;
}

export function getAppointmentTokenLabel(appointment) {
  const token = getAppointmentTokenValue(appointment);
  return token ? `Token ${token}` : "Token not assigned";
}

export function formatRelativeBookingTime(createdAt, now = new Date()) {
  const start = toDate(createdAt);
  if (!start) return null;
  const current = toDate(now) || new Date();
  const diffMs = Math.max(0, current.getTime() - start.getTime());
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;

  if (diffMs < minute) return "Booked just now";
  if (diffMs < hour) return `Booked ${Math.floor(diffMs / minute)} min ago`;
  if (diffMs < day) return `Booked ${Math.floor(diffMs / hour)} hr ago`;
  if (diffMs < 2 * day) return "Booked yesterday";
  return `Booked ${Math.floor(diffMs / day)} days ago`;
}

export function getNextWorkflowAction(context = {}) {
  const status = normalizeValue(context.status);
  const paymentStatus = normalizeValue(context.paymentStatus || context.feeStatus || context.billStatus);
  const billDueAmount = typeof context.billDueAmount === "number" ? context.billDueAmount : null;

  if (paymentStatus === "AWAITING_PAYMENT" || paymentStatus === "PAYMENT_PENDING" || paymentStatus === "UNPAID" || (billDueAmount != null && billDueAmount > 0)) {
    return { key: "collect-fee", label: "Collect Fee", tone: "primary", helperText: "Payment required before check-in." };
  }

  if (status === "BOOKED" || status === "SCHEDULED" || status === "PARTIALLY_BOOKED") {
    return { key: "check-in", label: "Check-in", tone: "primary", helperText: "Move the patient into the waiting queue." };
  }

  if (status === "CHECKED_IN" || status === "WAITING") {
    return { key: "start-consultation", label: "Start Consultation", tone: "primary", helperText: "Open the consultation workspace." };
  }

  if (status === "IN_CONSULTATION") {
    return { key: "continue-consultation", label: "Continue Consultation", tone: "primary", helperText: "Resume the active consultation." };
  }

  if (status === "CONSULTATION_COMPLETED" || status === "COMPLETED") {
    return { key: "complete-workflow", label: "Complete Billing / Pharmacy / Lab", tone: "success", helperText: "Finish the downstream workflow." };
  }

  if (status === "CANCELLED" || status === "NO_SHOW") {
    return { key: "view-details", label: "View Details", tone: "default", helperText: "No active workflow action remains." };
  }

  return { key: "view-details", label: "View Details", tone: "default", helperText: null };
}

export function derivePatientJourneyStage(context = {}) {
  const status = normalizeValue(context.status || context.appointmentStatus);
  const paymentStatus = normalizeValue(context.paymentStatus || context.feeStatus || context.billStatus);
  const consultationStatus = normalizeValue(context.consultationStatus);
  const prescriptionStatus = normalizeValue(context.prescriptionStatus);
  const labStatus = normalizeValue(context.labStatus);
  const pharmacyStatus = normalizeValue(context.pharmacyStatus);
  const registrationComplete = context.registrationComplete !== false
    && context.patientRegistered !== false
    && context.patientCreated !== false;

  if (paymentStatus === "AWAITING_PAYMENT" || paymentStatus === "PAYMENT_PENDING" || paymentStatus === "UNPAID") {
    return "PAYMENT";
  }
  if (status === "SCHEDULED" || status === "BOOKED" || status === "PARTIALLY_BOOKED") {
    if (!registrationComplete) {
      return "REGISTRATION";
    }
    return "APPOINTMENT";
  }
  if (status === "PAID") {
    return "CHECK_IN";
  }
  if (status === "CHECKED_IN") {
    return "CHECK_IN";
  }
  if (status === "WAITING") {
    return "WAITING";
  }
  if (status === "IN_CONSULTATION") {
    return "CONSULTATION";
  }
  if (consultationStatus === "COMPLETED" || status === "CONSULTATION_COMPLETED" || status === "COMPLETED") {
    if (!prescriptionStatus || prescriptionStatus === "DRAFT" || prescriptionStatus === "PENDING") {
      return "PRESCRIPTION";
    }
    if (labStatus && !["PUBLISHED", "DELIVERED", "DOCTOR_REVIEWED", "COMPLETED"].includes(labStatus)) {
      return "LABORATORY";
    }
    if (pharmacyStatus && !["COMPLETED", "DISPENSED", "DELIVERED"].includes(pharmacyStatus)) {
      return "PHARMACY";
    }
    if (paymentStatus === "AWAITING_PAYMENT" || paymentStatus === "PAYMENT_PENDING" || paymentStatus === "UNPAID") {
      return "PAYMENT";
    }
    if (paymentStatus === "PAID" || status === "COMPLETED") {
      return "BILLING_COMPLETE";
    }
    return "COMPLETED";
  }
  if (status === "CANCELLED" || status === "NO_SHOW") {
    return "COMPLETED";
  }
  return "APPOINTMENT";
}

export function getPatientJourneyStageLabel(stage) {
  switch (stage) {
    case "APPOINTMENT":
      return "Appointment";
    case "REGISTRATION":
      return "Registration";
    case "PAYMENT":
      return "Payment";
    case "CHECK_IN":
      return "Check-in";
    case "WAITING":
      return "Waiting";
    case "CONSULTATION":
      return "Consultation";
    case "PRESCRIPTION":
      return "Prescription";
    case "LABORATORY":
      return "Laboratory";
    case "PHARMACY":
      return "Pharmacy";
    case "BILLING_COMPLETE":
      return "Billing Complete";
    case "COMPLETED":
      return "Visit Completed";
    default:
      return stage;
  }
}

export function getPatientJourneyStages() {
  return [...JOURNEY_STAGES];
}

export function getPatientJourneyStageIndex(stage) {
  const normalized = normalizeValue(stage);
  const index = JOURNEY_STAGES.indexOf(normalized);
  return index < 0 ? 0 : index;
}

export { normalizeValue as normalizeWorkflowValue };
