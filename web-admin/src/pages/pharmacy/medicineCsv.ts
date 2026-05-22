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
  const headerWarnings = REQUIRED_COLUMNS.filter((required) => !normalizedHeaders.includes(required));
  const rows = parsed.slice(1).filter((row) => row.some((cell) => cell.trim() !== "")).map((row, index) => {
    const rowNumber = index + 2;
    const raw: Record<string, string> = {};
    for (const header of MEDICINE_IMPORT_COLUMNS) {
      raw[header] = extractCell(headers, row, header);
    }

    const medicineName = raw.medicineName;
    const type = raw.type;
    const strength = raw.strength;
    const defaultPrice = raw.defaultPrice;
    const taxPercent = raw.taxPercent;
    const active = raw.active;
    const errors: string[] = [];

    if (!medicineName) {
      errors.push("Medicine name is required");
    }
    if (!type) {
      errors.push("Type is required");
    }
    if (defaultPrice && !isNumeric(defaultPrice)) {
      errors.push("Default price must be numeric");
    }
    if (taxPercent && !isNumeric(taxPercent)) {
      errors.push("Tax percent must be numeric");
    }
    if (raw.defaultDurationDays && !isNumeric(raw.defaultDurationDays)) {
      errors.push("Default duration days must be numeric");
    }
    if (parseBoolean(active) === null && active.trim() !== "") {
      errors.push("Active must be true or false");
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
