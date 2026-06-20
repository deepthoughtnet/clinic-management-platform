import {
  billingBillDraftSchema,
  billingCreatePaymentSchema,
  billingRefundSchema,
} from "@deepthoughtnet/form-validation-kit";

type ZodIssue = { path?: ReadonlyArray<unknown>; message: string };

type BillLineDraft = {
  itemType: string;
  itemName: string;
  quantity: string;
  unitPrice: string;
  lineDiscountAmount: string;
  taxAmount: string;
  referenceId: string;
  sortOrder: string;
};

type BillDraft = {
  patientId: string;
  consultationId: string;
  appointmentId: string;
  billDate: string;
  discountType: string;
  discountValue: string;
  discountReason: string;
  taxAmount: string;
  notes: string;
  lines: BillLineDraft[];
};

type PaymentDraft = {
  paymentDate: string;
  amount: string;
  paymentMode: string;
  referenceNumber: string;
  notes: string;
};

type RefundDraft = {
  amount: string;
  refundMode: string;
  reason: string;
  notes: string;
};

export type FieldErrorMap = Record<string, string>;

function normalizeDiscountType(value: string) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "AMOUNT") return "FLAT";
  if (normalized === "PERCENTAGE") return "PERCENT";
  return normalized;
}

function normalizeLineType(value: string) {
  const normalized = String(value || "").trim().toUpperCase();
  if (normalized === "TEST") return "LAB_TEST";
  if (normalized === "VACCINATION") return "OTHER_CHARGE";
  if (normalized === "SERVICE") return "OTHER_CHARGE";
  if (normalized === "OTHER") return "OTHER_CHARGE";
  return normalized;
}

function toLinePayload(line: BillLineDraft) {
  return {
    item: line.itemName,
    type: normalizeLineType(line.itemType),
    quantity: line.quantity,
    unit: line.unitPrice,
    discount: line.lineDiscountAmount,
    tax: line.taxAmount,
  };
}

export function zodFieldErrors(error: { issues?: ZodIssue[] } | null | undefined) {
  const fieldErrors: FieldErrorMap = {};
  for (const issue of error?.issues || []) {
    const path = issue.path?.map((part) => String(part)).join(".");
    if (!path) continue;
    if (!(path in fieldErrors)) {
      fieldErrors[path] = issue.message;
    }
  }
  return fieldErrors;
}

export function validateBillingDraft(form: BillDraft, source: string) {
  const parsed = billingBillDraftSchema.safeParse({
    patientId: form.patientId,
    billDate: form.billDate,
    source,
    discountType: normalizeDiscountType(form.discountType),
    discountValue: form.discountType === "NONE" ? "" : form.discountValue,
    discountReason: form.discountReason,
    consultationId: form.consultationId,
    appointmentId: form.appointmentId,
    notes: form.notes,
    lines: form.lines.map(toLinePayload),
  });
  return parsed.success ? { success: true as const, data: parsed.data, fieldErrors: {} as FieldErrorMap } : { success: false as const, data: null, fieldErrors: zodFieldErrors(parsed.error) };
}

export function validateBillingPayment(form: PaymentDraft) {
  const parsed = billingCreatePaymentSchema.safeParse({
    paymentAmount: form.amount,
    paymentMode: form.paymentMode,
    referenceNumber: form.referenceNumber,
    notes: form.notes,
  });
  return parsed.success ? { success: true as const, data: parsed.data, fieldErrors: {} as FieldErrorMap } : { success: false as const, data: null, fieldErrors: zodFieldErrors(parsed.error) };
}

export function validateBillingRefund(form: RefundDraft) {
  const parsed = billingRefundSchema.safeParse({
    amount: form.amount,
    refundMode: form.refundMode,
    reason: form.reason,
    notes: form.notes,
  });
  return parsed.success ? { success: true as const, data: parsed.data, fieldErrors: {} as FieldErrorMap } : { success: false as const, data: null, fieldErrors: zodFieldErrors(parsed.error) };
}

export function getFirstBillingInvalidField(fieldErrors: FieldErrorMap) {
  const ordered = [
    "patientId",
    "billDate",
    "source",
    "discountType",
    "discountValue",
    "discountReason",
    "consultationId",
    "appointmentId",
    "lines.0.item",
    "lines.0.type",
    "lines.0.quantity",
    "lines.0.unit",
    "lines.0.discount",
    "lines.0.tax",
    "amount",
    "paymentAmount",
    "paymentMode",
    "referenceNumber",
    "reason",
  ];
  return ordered.find((key) => key in fieldErrors) || Object.keys(fieldErrors)[0] || null;
}
