export const MEDICINE_IMPORT_COLUMNS = [
  "medicineName",
  "genericName",
  "brandName",
  "category",
  "type",
  "strength",
  "unit",
  "defaultDosage",
  "defaultFrequency",
  "defaultDurationDays",
  "defaultTiming",
  "instructions",
  "manufacturer",
  "barcode",
  "qrCode",
  "externalCode",
  "defaultPrice",
  "taxPercent",
  "active",
] as const;

const REQUIRED_COLUMNS = ["medicineName", "type"] as const;
const MEDICINE_TYPE_VALUES = ["TABLET", "CAPSULE", "SYRUP", "INJECTION", "DROP", "OINTMENT", "SACHET", "OTHER"] as const;
const TIMING_VALUES = ["BEFORE_FOOD", "AFTER_FOOD", "WITH_FOOD", "ANYTIME"] as const;
const MEDICINE_IDENTITY_SEP = "\u001f";

export type MedicineImportPreviewRow = {
  rowNumber: number;
  medicineName: string;
  type: string;
  strength: string;
  defaultPrice: string;
  taxPercent: string;
  active: string;
  errors: string[];
  raw: Record<string, string>;
};

export type MedicineImportPreview = {
  columns: string[];
  rows: MedicineImportPreviewRow[];
  headerWarnings: string[];
  summary: {
    totalRows: number;
    validRows: number;
    invalidRows: number;
  };
};

const HEADER_ALIASES: Record<string, string> = {
  form: "type",
  dosageForm: "type",
  instructions: "instructions",
  defaultinstructions: "instructions",
  taxrate: "taxPercent",
  defaultprice: "defaultPrice",
  medicine_type: "type",
};

function normalizeHeader(header: string): string {
  const cleaned = header.trim().replace(/^\uFEFF/, "");
  const lower = cleaned.toLowerCase();
  return HEADER_ALIASES[lower] || cleaned;
}

function parseBoolean(value: string): boolean | null {
  const normalized = value.trim().toLowerCase();
  if (!normalized) return null;
  if (["true", "yes", "y", "1"].includes(normalized)) return true;
  if (["false", "no", "n", "0"].includes(normalized)) return false;
  return null;
}

function isNumeric(value: string): boolean {
  if (!value.trim()) return true;
  return !Number.isNaN(Number(value));
}

function hasAtMostTwoDecimals(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed) return true;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) && Math.round(parsed * 100) === parsed * 100;
}

function isAllowedBarcode(value: string): boolean {
  return /^[A-Za-z0-9/_-]+$/.test(value);
}

function normalizeText(value: string | null | undefined): string {
  return value == null ? "" : value.trim();
}

function medicineIdentityKey(medicineName: string, medicineType: string, strength: string) {
  return [medicineName.trim().toLowerCase(), medicineType.trim().toUpperCase(), strength.trim().toLowerCase()].join(MEDICINE_IDENTITY_SEP);
}

function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentCell = "";
  let inQuotes = false;

  for (let index = 0; index < text.length; index += 1) {
    const char = text[index];
    const next = text[index + 1];

    if (inQuotes) {
      if (char === "\"" && next === "\"") {
        currentCell += "\"";
        index += 1;
        continue;
      }
      if (char === "\"") {
        inQuotes = false;
        continue;
      }
      currentCell += char;
      continue;
    }

    if (char === "\"") {
      inQuotes = true;
      continue;
    }

    if (char === ",") {
      currentRow.push(currentCell);
      currentCell = "";
      continue;
    }

    if (char === "\n") {
      currentRow.push(currentCell);
      rows.push(currentRow);
      currentRow = [];
      currentCell = "";
      continue;
    }

    if (char === "\r") {
      continue;
    }

    currentCell += char;
  }

  currentRow.push(currentCell);
  if (currentRow.length > 1 || currentRow[0].trim() !== "") {
    rows.push(currentRow);
  }

  return rows;
}

function extractCell(headers: string[], row: string[], columnName: string): string {
  const index = headers.indexOf(columnName);
  if (index === -1) return "";
  return (row[index] || "").trim();
}

export function buildMedicineTemplateCsv(): string {
  return [
    MEDICINE_IMPORT_COLUMNS.join(","),
    [
      "Paracetamol 650",
      "Paracetamol",
      "Dolo",
      "Analgesic",
      "Tablet",
      "650",
      "mg",
      "1 tablet",
      "Twice daily",
      "5",
      "AFTER_FOOD",
      "Take after meals",
      "Micro Labs",
      "PARA-650-001",
      "PARA-650-001",
      "PARA-650-001",
      "25.00",
      "5",
      "true",
    ].join(","),
  ].join("\n");
}

export function parseMedicineImportPreview(text: string): MedicineImportPreview {
  const parsed = parseCsv(text);
  if (parsed.length === 0) {
    return {
      columns: [],
      rows: [],
      headerWarnings: ["The selected file is empty."],
      summary: { totalRows: 0, validRows: 0, invalidRows: 0 },
    };
  }

  const headers = parsed[0].map(normalizeHeader);
  const normalizedHeaders = headers.map((header) => header.trim()).filter(Boolean);
  const headerWarnings = [
    ...REQUIRED_COLUMNS.filter((required) => !normalizedHeaders.includes(required)).map((required) => `Missing required header: ${required}`),
    ...(normalizedHeaders.filter((header) => !MEDICINE_IMPORT_COLUMNS.includes(header as typeof MEDICINE_IMPORT_COLUMNS[number])).length
      ? [`Unsupported headers will be ignored: ${normalizedHeaders.filter((header) => !MEDICINE_IMPORT_COLUMNS.includes(header as typeof MEDICINE_IMPORT_COLUMNS[number])).join(", ")}`]
      : []),
  ];
  const seenIdentities = new Set<string>();
  const seenCodes = new Set<string>();
  const rows = parsed.slice(1).filter((row) => row.some((cell) => cell.trim() !== "")).map((row, index) => {
    const rowNumber = index + 2;
    const raw: Record<string, string> = {};
    for (const header of MEDICINE_IMPORT_COLUMNS) {
      raw[header] = extractCell(headers, row, header);
    }

    const medicineName = normalizeText(raw.medicineName);
    const type = normalizeText(raw.type);
    const strength = normalizeText(raw.strength);
    const barcode = normalizeText(raw.barcode);
    const qrCode = normalizeText(raw.qrCode);
    const externalCode = normalizeText(raw.externalCode);
    const genericName = normalizeText(raw.genericName);
    const brandName = normalizeText(raw.brandName);
    const category = normalizeText(raw.category);
    const dosageForm = normalizeText(raw.dosageForm);
    const unit = normalizeText(raw.unit);
    const manufacturer = normalizeText(raw.manufacturer);
    const defaultDosage = normalizeText(raw.defaultDosage);
    const defaultFrequency = normalizeText(raw.defaultFrequency);
    const defaultInstructions = normalizeText(raw.instructions);
    const defaultTiming = normalizeText(raw.defaultTiming);
    const defaultDurationDays = normalizeText(raw.defaultDurationDays);
    const defaultPrice = normalizeText(raw.defaultPrice);
    const taxPercent = normalizeText(raw.taxPercent);
    const active = normalizeText(raw.active);
    const errors: string[] = [];

    if (!medicineName) {
      errors.push("Medicine name is required");
    } else if (medicineName.length < 2 || medicineName.length > 60 || !/[A-Za-z0-9]/.test(medicineName)) {
      errors.push("Medicine name must be 2 to 60 characters and include a letter or number");
    }
    if (!type) {
      errors.push("Type is required");
    } else if (!MEDICINE_TYPE_VALUES.includes(type.toUpperCase() as (typeof MEDICINE_TYPE_VALUES)[number])) {
      errors.push("Type must be one of TABLET, CAPSULE, SYRUP, INJECTION, DROP, OINTMENT, SACHET, OTHER");
    }
    if (!strength) {
      errors.push("Strength is required");
    } else if (strength.length > 60 || !/[A-Za-z0-9]/.test(strength)) {
      errors.push("Strength is required and must include a letter or number");
    }
    if (barcode && (!isAllowedBarcode(barcode) || barcode.length > 60)) {
      errors.push("Barcode can use letters, numbers, dashes, underscores, and slashes only");
    }
    if (qrCode && qrCode.length > 60) {
      errors.push("QR code must be 60 characters or fewer");
    }
    if (externalCode && externalCode.length > 60) {
      errors.push("External code must be 60 characters or fewer");
    }
    if (genericName && genericName.length > 60) {
      errors.push("Generic name must be 60 characters or fewer");
    } else if (genericName && !/[A-Za-z0-9]/.test(genericName)) {
      errors.push("Generic name must include a letter or number");
    }
    if (brandName && brandName.length > 60) {
      errors.push("Brand name must be 60 characters or fewer");
    } else if (brandName && !/[A-Za-z0-9]/.test(brandName)) {
      errors.push("Brand name must include a letter or number");
    }
    if (category && category.length > 60) {
      errors.push("Category must be 60 characters or fewer");
    }
    if (dosageForm && dosageForm.length > 60) {
      errors.push("Form must be 60 characters or fewer");
    }
    if (unit && unit.length > 60) {
      errors.push("Unit must be 60 characters or fewer");
    }
    if (manufacturer && manufacturer.length > 60) {
      errors.push("Manufacturer must be 60 characters or fewer");
    }
    if (defaultDosage && defaultDosage.length > 60) {
      errors.push("Default dosage must be 60 characters or fewer");
    }
    if (defaultFrequency && defaultFrequency.length > 60) {
      errors.push("Default frequency must be 60 characters or fewer");
    }
    if (defaultInstructions && defaultInstructions.length > 250) {
      errors.push("Default instructions must be 250 characters or fewer");
    }
    if (defaultTiming && !TIMING_VALUES.includes(defaultTiming.toUpperCase() as (typeof TIMING_VALUES)[number])) {
      errors.push("Default timing must be one of BEFORE_FOOD, AFTER_FOOD, WITH_FOOD, ANYTIME");
    }
    if (defaultDurationDays && (!isNumeric(defaultDurationDays) || Number(defaultDurationDays) < 1 || Number(defaultDurationDays) > 365 || !Number.isInteger(Number(defaultDurationDays)))) {
      errors.push("Default duration days must be a whole number between 1 and 365");
    }
    if (defaultPrice && (!isNumeric(defaultPrice) || Number(defaultPrice) < 0 || Number(defaultPrice) > 999999 || !hasAtMostTwoDecimals(defaultPrice))) {
      errors.push("Default price must be between 0 and 999999 with up to 2 decimal places");
    }
    if (taxPercent && (!isNumeric(taxPercent) || Number(taxPercent) < 0 || Number(taxPercent) > 100 || !hasAtMostTwoDecimals(taxPercent))) {
      errors.push("Tax percent must be between 0 and 100 with up to 2 decimal places");
    }
    if (active && parseBoolean(active) === null) {
      errors.push("Active must be true or false");
    }

    const identity = medicineIdentityKey(medicineName, type, strength);
    const barcodeKey = barcode ? barcode.toLowerCase() : "";
    const externalCodeKey = externalCode ? externalCode.toLowerCase() : "";
    if (identity && seenIdentities.has(identity)) {
      errors.push("Duplicate medicine identity in CSV");
    }
    if (barcodeKey && seenCodes.has(`barcode:${barcodeKey}`)) {
      errors.push("Duplicate barcode in CSV");
    }
    if (externalCodeKey && seenCodes.has(`external:${externalCodeKey}`)) {
      errors.push("Duplicate external code in CSV");
    }
    if (!errors.length) {
      seenIdentities.add(identity);
      if (barcodeKey) seenCodes.add(`barcode:${barcodeKey}`);
      if (externalCodeKey) seenCodes.add(`external:${externalCodeKey}`);
    }

    return {
      rowNumber,
      medicineName,
      type,
      strength,
      defaultPrice,
      taxPercent,
      active,
      errors,
      raw,
    };
  });

  const validRows = rows.filter((row) => row.errors.length === 0).length;
  const invalidRows = rows.length - validRows;

  return {
    columns: headers,
    rows,
    headerWarnings,
    summary: {
      totalRows: rows.length,
      validRows,
      invalidRows,
    },
  };
}
