const COMMENT_SUGGESTION_CONFIG = {
  DISPENSING_BOUGHT_EXTERNALLY: {
    reasons: ["PATIENT_PREFERENCE", "PRICE_DIFFERENCE", "INSURANCE_REQUIREMENT", "OUTSIDE_PHARMACY", "FAMILY_REQUEST"],
    suggestions: [
      "Patient purchased medicines from local pharmacy.",
      "Patient preferred outside pharmacy.",
      "Insurance-approved pharmacy used.",
      "Family member will bring medicines later.",
      "Patient requested external purchase.",
    ],
  },
  DISPENSING_UNAVAILABLE: {
    reasons: ["OUT_OF_STOCK", "SUPPLIER_UNAVAILABLE", "DISCONTINUED", "BATCH_EXPIRED", "ALTERNATIVE_ADVISED"],
    suggestions: [
      "Medicine currently unavailable.",
      "Supplier replenishment pending.",
      "Medicine discontinued.",
      "Batch expired and removed from stock.",
      "Alternative medicine advised.",
    ],
  },
  DISPENSING_PATIENT_DECLINED: {
    reasons: ["COST_CONCERN", "SECOND_OPINION", "PATIENT_REFUSED", "NOT_REQUIRED_NOW", "WILL_DECIDE_LATER"],
    suggestions: [
      "Patient declined medicines.",
      "Patient requested second opinion.",
      "Patient refused antibiotics.",
      "Patient declined due to financial reasons.",
      "Patient will decide later.",
    ],
  },
  DISPENSING_CANCELLED: {
    reasons: ["DOCTOR_CANCELLED", "VISIT_CANCELLED", "DUPLICATE_PRESCRIPTION", "INCORRECT_PRESCRIPTION", "CREATED_BY_MISTAKE"],
    suggestions: [
      "Prescription cancelled by doctor.",
      "Consultation cancelled.",
      "Duplicate prescription closed.",
      "Incorrect prescription closed.",
      "Prescription was created by mistake.",
    ],
  },
  INVENTORY_ADJUSTMENT: {
    reasons: ["STOCK_CORRECTION", "DAMAGED_STOCK", "EXPIRED_STOCK", "PHYSICAL_COUNT_MISMATCH", "OPENING_BALANCE"],
    suggestions: [
      "Stock corrected after physical verification.",
      "Damaged stock adjusted.",
      "Expired stock removed.",
      "Physical count mismatch corrected.",
      "Opening balance entered.",
    ],
  },
  INVENTORY_CUSTOMER_RETURN: {
    reasons: ["REUSABLE", "DAMAGED", "EXPIRED", "NOT_SELLABLE"],
    suggestions: [
      "Reusable customer return restocked.",
      "Customer return marked damaged.",
      "Customer return marked expired.",
      "Customer return not suitable for sale.",
    ],
  },
  INVENTORY_VENDOR_RETURN: {
    reasons: ["EXPIRED", "DAMAGED", "DEFECTIVE", "SHORT_SUPPLY", "SUPPLIER_REQUEST"],
    suggestions: [
      "Vendor returned due to expiry.",
      "Vendor returned due to damage.",
      "Vendor returned due to defect.",
      "Vendor return raised for shortage.",
      "Supplier requested stock return.",
    ],
  },
  INVENTORY_WRITE_OFF: {
    reasons: ["EXPIRED_STOCK", "DAMAGED_STOCK", "LOST_STOCK", "SPILLAGE", "THEFT", "DATA_CORRECTION"],
    suggestions: [
      "Expired stock written off.",
      "Damaged stock written off.",
      "Lost stock written off.",
      "Spillage written off.",
      "Theft-related write-off recorded.",
      "Data correction write-off recorded.",
    ],
  },
  INVENTORY_VENDOR_RECONCILIATION: {
    reasons: ["MANUAL_REVIEW", "QTY_VARIANCE", "BATCH_MISMATCH", "DUPLICATE_ROW", "OCR_ERROR"],
    suggestions: [
      "Vendor sheet uploaded for review.",
      "Quantity variance requires confirmation.",
      "Batch mapping needs correction.",
      "Duplicate vendor row detected.",
      "OCR extraction needs manual review.",
    ],
  },
  BILLING_ADJUSTMENT: {
    reasons: ["DISCOUNT_APPROVED", "BILL_CORRECTION", "ROUNDING_ADJUSTMENT", "PACKAGE_ADJUSTMENT", "DUPLICATE_CHARGE_REMOVED"],
    suggestions: [
      "Discount approved by clinic admin.",
      "Billing correction applied.",
      "Rounding adjustment applied.",
      "Package adjustment applied.",
      "Duplicate charge removed.",
    ],
  },
  APPOINTMENT_CANCELLATION: {
    reasons: ["PATIENT_REQUEST", "DOCTOR_UNAVAILABLE", "DUPLICATE_APPOINTMENT", "NO_SHOW", "EMERGENCY_RESCHEDULE"],
    suggestions: [
      "Patient requested cancellation.",
      "Doctor unavailable.",
      "Duplicate appointment cancelled.",
      "Patient did not arrive.",
      "Appointment rescheduled due to emergency.",
    ],
  },
  LAB_REJECTION: {
    reasons: ["SAMPLE_HEMOLYZED", "INSUFFICIENT_SAMPLE", "SAMPLE_MISMATCH", "REPEAT_SAMPLE_REQUIRED", "TEST_CANCELLED"],
    suggestions: [
      "Sample hemolyzed.",
      "Insufficient sample quantity.",
      "Sample details mismatch.",
      "Repeat sample required.",
      "Lab test cancelled.",
    ],
  },
  LEAD_LOST_REASON: {
    reasons: ["NOT_INTERESTED", "PRICE_CONCERN", "CHOSE_COMPETITOR", "NOT_REACHABLE", "FUTURE_FOLLOW_UP"],
    suggestions: [
      "Lead is not interested.",
      "Lead has pricing concerns.",
      "Lead chose another provider.",
      "Lead not reachable after multiple attempts.",
      "Lead requested follow-up later.",
    ],
  },
  REFUND: {
    reasons: ["DUPLICATE_PAYMENT", "SERVICE_CANCELLED", "BILLING_ERROR", "PATIENT_REQUEST", "ADMIN_APPROVED"],
    suggestions: [
      "Duplicate payment refunded.",
      "Service cancelled and refund approved.",
      "Billing error corrected through refund.",
      "Patient requested refund.",
      "Refund approved by clinic admin.",
    ],
  },
};

function normalizeText(value) {
  return String(value || "").trim().toLowerCase();
}

export function getCommentSuggestionCategoryConfig(category) {
  return COMMENT_SUGGESTION_CONFIG[category] || { reasons: [], suggestions: [] };
}

export function appendSuggestionToRemarks(remarks, suggestion) {
  const current = String(remarks || "").trim();
  const addition = String(suggestion || "").trim();
  if (!addition) return current;
  return current ? `${current}\n${addition}` : addition;
}

export function filterSuggestionChips(suggestions, query) {
  const term = normalizeText(query);
  if (!term) return [...new Set(suggestions)];
  const seen = new Set();
  return suggestions.filter((suggestion) => {
    const normalized = normalizeText(suggestion);
    if (!normalized || seen.has(normalized)) return false;
    const match = normalized.includes(term);
    if (match) seen.add(normalized);
    return match;
  });
}

export function listCommentSuggestionCategories() {
  return Object.keys(COMMENT_SUGGESTION_CONFIG);
}

export const commentSuggestionConfig = COMMENT_SUGGESTION_CONFIG;
