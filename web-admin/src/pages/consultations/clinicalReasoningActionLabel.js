const ACTION_LABELS = {
  ORDER_TEST: "Prepare Order",
  ORDER_LAB: "Prepare Order",
  CREATE_ORDER: "Create Order",
  COMPLETE_PENDING_ORDER: "Continue Order",
  REVIEW_EXISTING_RESULT: "Review Result",
  ESCALATE: "Escalate",
  MONITOR: "Monitor",
  EDUCATE_PATIENT: "Patient Education",
};

const INTERNAL_ACTION_CODES = Object.keys(ACTION_LABELS);
const INTERNAL_ACTION_PATTERN = new RegExp(`\\b(?:${INTERNAL_ACTION_CODES.join("|")})\\b`, "g");

function cleanSpacing(value) {
  return value
    .replace(/\s+([,.;:!?])/g, "$1")
    .replace(/\(\s+/g, "(")
    .replace(/\s+\)/g, ")")
    .replace(/\s{2,}/g, " ")
    .trim();
}

export function humanizeClinicalReasoningActionType(value) {
  const normalized = String(value || "").trim().toUpperCase();
  if (!normalized) {
    return null;
  }
  return ACTION_LABELS[normalized] || "Recommended action";
}

export function sanitizeClinicalReasoningNarrative(value) {
  const text = String(value || "").trim();
  if (!text) {
    return "";
  }
  const stripped = text.replace(INTERNAL_ACTION_PATTERN, " ");
  return cleanSpacing(stripped);
}
