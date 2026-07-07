import type { VaccineMaster } from "../../api/clinicApi";

export const VACCINE_IMPORT_COLUMNS = [
  "vaccineName",
  "description",
  "manufacturer",
  "brandName",
  "vaccineGroup",
  "doseNumber",
  "route",
  "administrationSite",
  "storageTemperature",
  "ndcBarcode",
  "scheduleType",
  "ageGroup",
  "minAgeDays",
  "recommendedAgeDays",
  "maxAgeDays",
  "gapDays",
  "boosterGapDays",
  "boosterRules",
  "isRecurring",
  "recurrenceDays",
  "recommendationPolicy",
  "catchUpPolicy",
  "catchUpMaxAgeDays",
  "applicableAgeGroup",
  "clinicalIndications",
  "defaultPrice",
  "active",
] as const;

export const VACCINE_IMPORT_REQUIRED_COLUMNS = [
  "vaccineName",
  "description",
  "ageGroup",
  "gapDays",
  "defaultPrice",
  "active",
] as const;

export type VaccineImportPreviewRow = {
  rowNumber: number;
  vaccineName: string;
  description: string;
  manufacturer: string;
  brandName: string;
  vaccineGroup: string;
  doseNumber: string;
  route: string;
  administrationSite: string;
  storageTemperature: string;
  ndcBarcode: string;
  scheduleType: string;
  ageGroup: string;
  minAgeDays: string;
  recommendedAgeDays: string;
  maxAgeDays: string;
  gapDays: string;
  boosterGapDays: string;
  boosterRules: string;
  isRecurring: string;
  recurrenceDays: string;
  recommendationPolicy: string;
  catchUpPolicy: string;
  catchUpMaxAgeDays: string;
  applicableAgeGroup: string;
  clinicalIndications: string;
  defaultPrice: string;
  active: string;
  errors: string[];
  raw: Record<string, string>;
};

export type VaccineImportPreview = {
  columns: string[];
  rows: VaccineImportPreviewRow[];
  headerWarnings: string[];
  summary: {
    totalRows: number;
    validRows: number;
    invalidRows: number;
  };
};

const HEADER_ALIASES: Record<string, string> = {
  vaccine_name: "vaccineName",
  recommendedgapdays: "gapDays",
  defaultprice: "defaultPrice",
  isrecurring: "isRecurring",
};

function normalizeHeader(header: string): string {
  const cleaned = header.trim().replace(/^\uFEFF/, "");
  return HEADER_ALIASES[cleaned.toLowerCase()] || cleaned;
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

function extractCell(headers: string[], row: string[], column: string): string {
  const index = headers.indexOf(column);
  return index === -1 ? "" : (row[index] || "").trim();
}

function parseBoolean(value: string): boolean | null {
  const normalized = value.trim().toLowerCase();
  if (!normalized) return null;
  if (["true", "yes", "y", "1"].includes(normalized)) return true;
  if (["false", "no", "n", "0"].includes(normalized)) return false;
  return null;
}

function csvEscape(value: string | number | boolean | null | undefined): string {
  const raw = value == null ? "" : String(value);
  if (/["\n,]/.test(raw)) {
    return `"${raw.replaceAll("\"", "\"\"")}"`;
  }
  return raw;
}

function requiredHeadersPresent(headers: string[]) {
  return VACCINE_IMPORT_REQUIRED_COLUMNS.filter((required) => !headers.includes(required));
}

function masterKey(vaccineGroup: string, doseNumber: string, scheduleType: string): string {
  if (!vaccineGroup.trim() && !doseNumber.trim() && !scheduleType.trim()) {
    return "";
  }
  return [
    vaccineGroup.trim().toLowerCase(),
    doseNumber.trim(),
    scheduleType.trim().toUpperCase(),
  ].join("|");
}

function looksChildhoodSchedule(ageGroup: string, recommendedAgeDays: string): boolean {
  const numeric = recommendedAgeDays.trim() ? Number(recommendedAgeDays) : null;
  if (numeric != null && Number.isFinite(numeric) && numeric <= 17 * 365) {
    return true;
  }
  const token = ageGroup.trim().toUpperCase();
  return token.includes("NEWBORN")
    || token.includes("INFANT")
    || token.includes("TODDLER")
    || token.includes("CHILD")
    || token.includes("ADOLESCENT")
    || token.includes("WEEK")
    || token.includes("MONTH");
}

function inferRecommendationPolicy(scheduleType: string, isRecurring: string, ageGroup: string, recommendedAgeDays: string, explicit: string): string {
  if (explicit.trim()) {
    return explicit.trim().toUpperCase();
  }
  if (scheduleType.trim().toUpperCase() === "ADULT") {
    return "ADULT_ROUTINE";
  }
  if (parseBoolean(isRecurring) === true) {
    return "RECURRING";
  }
  if (looksChildhoodSchedule(ageGroup, recommendedAgeDays)) {
    return "STANDARD_CHILDHOOD";
  }
  return "CLINIC_CUSTOM";
}

function inferCatchUpPolicy(explicit: string): string {
  return explicit.trim() ? explicit.trim().toUpperCase() : "NONE";
}

function inferApplicableAgeGroup(ageGroup: string, recommendedAgeDays: string, explicit: string): string {
  if (explicit.trim()) {
    return explicit.trim().toUpperCase();
  }
  const token = ageGroup.trim().toUpperCase();
  if (token.includes("NEWBORN") || token.includes("BIRTH")) return "NEWBORN";
  if (token.includes("INFANT") || token.includes("WEEK") || token.includes("MONTH")) return "INFANT";
  if (token.includes("TODDLER") || token.includes("1 YEAR") || token.includes("2 YEAR") || token.includes("3 YEAR")) return "TODDLER";
  if (token.includes("CHILD") || token.includes("4 YEAR") || token.includes("5 YEAR") || token.includes("6 YEAR") || token.includes("7 YEAR") || token.includes("8 YEAR") || token.includes("9 YEAR")) return "CHILD";
  if (token.includes("ADOLESCENT") || token.includes("10 YEAR") || token.includes("11 YEAR") || token.includes("12 YEAR") || token.includes("13 YEAR") || token.includes("14 YEAR") || token.includes("15 YEAR") || token.includes("16 YEAR") || token.includes("17 YEAR")) return "ADOLESCENT";
  if (token.includes("OLDER") || token.includes("SENIOR")) return "OLDER_ADULT";
  if (token.includes("ADULT")) return "ADULT";
  const numeric = recommendedAgeDays.trim() ? Number(recommendedAgeDays) : null;
  if (numeric != null && Number.isFinite(numeric)) {
    if (numeric <= 28) return "NEWBORN";
    if (numeric <= 365) return "INFANT";
    if (numeric <= 3 * 365) return "TODDLER";
    if (numeric <= 9 * 365) return "CHILD";
    if (numeric <= 17 * 365) return "ADOLESCENT";
    if (numeric <= 59 * 365) return "ADULT";
    return "OLDER_ADULT";
  }
  return "ALL";
}

export function buildVaccineTemplateCsv(): string {
  return [
    VACCINE_IMPORT_COLUMNS.join(","),
    [
      "COVID Booster",
      "Annual booster",
      "Acme Labs",
      "Comvax",
      "ADULT BOOSTER",
      "1",
      "IM",
      "Deltoid",
      "2-8 C",
      "123456789",
      "ADULT",
      "Adults",
      "3650",
      "3650",
      "3650",
      "3650",
      "365",
      "Annual booster",
      "false",
      "365",
      "ADULT_ROUTINE",
      "NONE",
      "",
      "ADULT",
      "travel,occupational",
      "250.00",
      "true",
    ].join(","),
  ].join("\n");
}

export function buildVaccineExportCsv(rows: VaccineMaster[]): string {
  const output = [VACCINE_IMPORT_COLUMNS.join(",")];
  for (const row of rows) {
    output.push([
      csvEscape(row.vaccineName),
      csvEscape(row.description || ""),
      csvEscape(row.manufacturer || ""),
      csvEscape(row.brandName || ""),
      csvEscape(row.vaccineGroup || ""),
      csvEscape(row.doseNumber ?? ""),
      csvEscape(row.route || ""),
      csvEscape(row.administrationSite || ""),
      csvEscape(row.storageTemperature || ""),
      csvEscape(row.ndcBarcode || ""),
      csvEscape(row.scheduleType || ""),
      csvEscape(row.ageGroup || ""),
      csvEscape(row.minAgeDays ?? ""),
      csvEscape(row.recommendedAgeDays ?? ""),
      csvEscape(row.maxAgeDays ?? ""),
      csvEscape(row.gapDays ?? row.recommendedGapDays ?? ""),
      csvEscape(row.boosterGapDays ?? ""),
      csvEscape(row.boosterRules || ""),
      csvEscape(row.recurring),
      csvEscape(row.recurrenceDays ?? ""),
      csvEscape(row.recommendationPolicy || ""),
      csvEscape(row.catchUpPolicy || ""),
      csvEscape(row.catchUpMaxAgeDays ?? ""),
      csvEscape(row.applicableAgeGroup || ""),
      csvEscape(row.clinicalIndications || ""),
      csvEscape(row.defaultPrice != null ? row.defaultPrice.toFixed(2) : ""),
      csvEscape(row.active),
    ].join(","));
  }
  return output.join("\n");
}

export function parseVaccineImportPreview(text: string, existingNames: string[] = []): VaccineImportPreview {
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
  const headerWarnings = requiredHeadersPresent(normalizedHeaders);
  const seenNames = new Set(existingNames.map((name) => name.trim().toLowerCase()));
  const seenKeys = new Set<string>();

  const rows = parsed.slice(1).filter((row) => row.some((cell) => cell.trim() !== "")).map((row, index) => {
    const rowNumber = index + 2;
    const raw: Record<string, string> = {};
    for (const column of VACCINE_IMPORT_COLUMNS) {
      raw[column] = extractCell(headers, row, column);
    }
    const errors: string[] = [];
    const vaccineName = raw.vaccineName;
    const doseNumber = raw.doseNumber;
    const vaccineGroup = raw.vaccineGroup;
    const scheduleType = raw.scheduleType;
    const description = raw.description;
    const manufacturer = raw.manufacturer;
    const brandName = raw.brandName;
    const administrationSite = raw.administrationSite;
    const storageTemperature = raw.storageTemperature;
    const ndcBarcode = raw.ndcBarcode;
    const ageGroup = raw.ageGroup;
    const minAgeDays = raw.minAgeDays;
    const recommendedAgeDays = raw.recommendedAgeDays;
    const maxAgeDays = raw.maxAgeDays;
    const gapDays = raw.gapDays;
    const boosterGapDays = raw.boosterGapDays;
    const boosterRules = raw.boosterRules;
    const isRecurring = raw.isRecurring;
    const recurrenceDays = raw.recurrenceDays;
    const recommendationPolicy = raw.recommendationPolicy;
    const catchUpPolicy = raw.catchUpPolicy;
    const catchUpMaxAgeDays = raw.catchUpMaxAgeDays;
    const applicableAgeGroup = raw.applicableAgeGroup;
    const clinicalIndications = raw.clinicalIndications;
    const defaultPrice = raw.defaultPrice;
    const active = raw.active;
    const normalizedName = vaccineName.trim().toLowerCase();

    if (!vaccineName.trim()) {
      errors.push("Vaccine name is required");
    } else if (seenNames.has(normalizedName)) {
      errors.push("Duplicate vaccine name");
    } else {
      seenNames.add(normalizedName);
    }

    const combinationKey = masterKey(vaccineGroup, doseNumber, scheduleType);
    if (combinationKey) {
      if (seenKeys.has(combinationKey)) {
        errors.push("Duplicate vaccine group/dose/schedule combination");
      } else {
        seenKeys.add(combinationKey);
      }
    }

    if (description.length > 250) {
      errors.push("Description must be 250 characters or fewer");
    }
    if (manufacturer.length > 250) {
      errors.push("Manufacturer must be 250 characters or fewer");
    }
    if (brandName.length > 250) {
      errors.push("Brand name must be 250 characters or fewer");
    }
    if (vaccineGroup.length > 128) {
      errors.push("Vaccine group must be 128 characters or fewer");
    }
    if (doseNumber.trim()) {
      const parsedDose = Number(doseNumber);
      if (!Number.isInteger(parsedDose) || parsedDose < 0) {
        errors.push("Dose number must be a whole number 0 or greater");
      }
    }
    if (administrationSite.length > 128) {
      errors.push("Administration site must be 128 characters or fewer");
    }
    if (storageTemperature.length > 128) {
      errors.push("Storage temperature must be 128 characters or fewer");
    }
    if (ndcBarcode.length > 128) {
      errors.push("NDC barcode must be 128 characters or fewer");
    }
    if (routeIsPresent(raw.route) && !["IM", "SC", "ORAL", "NASAL", "ID"].includes(raw.route.trim().toUpperCase())) {
      errors.push("Route must be IM, SC, ORAL, NASAL, or ID");
    }
    if (scheduleType.trim() && !["UIP", "IAP", "CLINIC_CUSTOM", "TRAVEL", "ADULT"].includes(scheduleType.trim().toUpperCase())) {
      errors.push("Schedule type must be UIP, IAP, CLINIC_CUSTOM, TRAVEL, or ADULT");
    }
    if (ageGroup.length > 60) {
      errors.push("Age group must be 60 characters or fewer");
    }
    for (const [label, value] of [
      ["minAgeDays", minAgeDays],
      ["recommendedAgeDays", recommendedAgeDays],
      ["maxAgeDays", maxAgeDays],
      ["gapDays", gapDays],
      ["boosterGapDays", boosterGapDays],
      ["recurrenceDays", recurrenceDays],
    ] as Array<[string, string]>) {
      if (value.trim()) {
        const parsedValue = Number(value);
        if (!Number.isInteger(parsedValue) || parsedValue < 0) {
          errors.push(`${label} must be a whole number 0 or greater`);
        }
      }
    }
    if (boosterRules.length > 500) {
      errors.push("Booster rules must be 500 characters or fewer");
    }
    if (parseBoolean(isRecurring) === null && isRecurring.trim()) {
      errors.push("isRecurring must be true or false");
    }
    if (recommendationPolicy.trim() && !["STANDARD_CHILDHOOD", "CHILDHOOD_CATCHUP", "ADULT_ROUTINE", "ADULT_RISK_BASED", "PREGNANCY", "TRAVEL", "OCCUPATIONAL", "RECURRING", "CLINIC_CUSTOM"].includes(recommendationPolicy.trim().toUpperCase())) {
      errors.push("recommendationPolicy must be one of STANDARD_CHILDHOOD, CHILDHOOD_CATCHUP, ADULT_ROUTINE, ADULT_RISK_BASED, PREGNANCY, TRAVEL, OCCUPATIONAL, RECURRING, CLINIC_CUSTOM");
    }
    if (catchUpPolicy.trim() && !["NONE", "ALLOWED_UNTIL_AGE", "LIFETIME", "CLINICIAN_DECISION"].includes(catchUpPolicy.trim().toUpperCase())) {
      errors.push("catchUpPolicy must be one of NONE, ALLOWED_UNTIL_AGE, LIFETIME, CLINICIAN_DECISION");
    }
    if (catchUpMaxAgeDays.trim()) {
      const parsedCatchUpMaxAgeDays = Number(catchUpMaxAgeDays);
      if (!Number.isInteger(parsedCatchUpMaxAgeDays) || parsedCatchUpMaxAgeDays < 0) {
        errors.push("catchUpMaxAgeDays must be a whole number 0 or greater");
      }
    }
    if (applicableAgeGroup.trim() && !["NEWBORN", "INFANT", "TODDLER", "CHILD", "ADOLESCENT", "ADULT", "OLDER_ADULT", "ALL"].includes(applicableAgeGroup.trim().toUpperCase())) {
      errors.push("applicableAgeGroup must be one of NEWBORN, INFANT, TODDLER, CHILD, ADOLESCENT, ADULT, OLDER_ADULT, ALL");
    }
    if (clinicalIndications.length > 500) {
      errors.push("clinicalIndications must be 500 characters or fewer");
    }
    if (defaultPrice.trim()) {
      const parsedPrice = Number(defaultPrice);
      if (Number.isNaN(parsedPrice) || parsedPrice < 0) {
        errors.push("Default price must be 0 or greater");
      }
    }
    if (parseBoolean(active) === null) {
      errors.push("Active must be true or false");
    }

    return {
      rowNumber,
      vaccineName,
      description,
      manufacturer,
      brandName,
      vaccineGroup,
      doseNumber,
      route: raw.route,
      administrationSite,
      storageTemperature,
      ndcBarcode,
      scheduleType,
      ageGroup,
      minAgeDays,
      recommendedAgeDays,
      maxAgeDays,
      gapDays,
      boosterGapDays,
      boosterRules,
      isRecurring,
      recurrenceDays,
      recommendationPolicy: inferRecommendationPolicy(scheduleType, isRecurring, ageGroup, recommendedAgeDays, recommendationPolicy),
      catchUpPolicy: inferCatchUpPolicy(catchUpPolicy),
      catchUpMaxAgeDays,
      applicableAgeGroup: inferApplicableAgeGroup(ageGroup, recommendedAgeDays, applicableAgeGroup),
      clinicalIndications,
      defaultPrice,
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

function routeIsPresent(value: string) {
  return value.trim().length > 0;
}
