type FieldHelpKey =
  | "medicine"
  | "location"
  | "batchNumber"
  | "expiryDate"
  | "quantityOnHand"
  | "purchaseRate"
  | "mrp"
  | "reorderLevel"
  | "barcode"
  | "qrCode"
  | "externalCode"
  | "appointmentType"
  | "queueStatus"
  | "visitType"
  | "sampleId"
  | "resultValue"
  | "paymentMode"
  | "discountPercent"
  | "leadSource"
  | "gst"
  | "tax";

const FIELD_HELP_TEXT: Record<FieldHelpKey, string> = {
  medicine: "Search medicine name, generic, brand, barcode, QR code, or external code.",
  location: "Stock location or default pharmacy workspace where the batch will be stored.",
  batchNumber: "Supplier/manufacturer batch printed on the medicine strip or carton.",
  expiryDate: "Expired medicines cannot be dispensed.",
  quantityOnHand: "Physical stock quantity currently available in this batch.",
  purchaseRate: "Cost per unit paid to the supplier for this batch.",
  mrp: "Maximum retail price used for POS and billing.",
  reorderLevel: "Minimum quantity below which medicine is considered low stock.",
  barcode: "Scan the medicine barcode if it is available on the pack.",
  qrCode: "Scan the medicine QR code if it is printed on the label.",
  externalCode: "Supplier, distributor, or ERP code linked to the stock batch.",
  appointmentType: "Choose the appointment category used for scheduling and queue handling.",
  queueStatus: "Current waiting or processing state of the appointment.",
  visitType: "Optional visit classification used by the clinic workflow.",
  sampleId: "Unique sample identifier used to track a lab specimen.",
  resultValue: "Measured value entered for a lab parameter or result line.",
  paymentMode: "Select the payment method used to settle the transaction.",
  discountPercent: "Percentage discount applied to the bill or line item.",
  leadSource: "Source that created or captured the lead.",
  gst: "GST percentage applied to the product, service, or invoice line.",
  tax: "Tax percentage applied to the item or service.",
};

export function getFieldHelpText(key: FieldHelpKey): string {
  return FIELD_HELP_TEXT[key];
}

export function getFieldHelpKeys(): FieldHelpKey[] {
  return Object.keys(FIELD_HELP_TEXT) as FieldHelpKey[];
}
