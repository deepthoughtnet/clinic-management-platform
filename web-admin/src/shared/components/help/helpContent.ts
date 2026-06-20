import type { HelpPageRecord, HelpSearchResult, HelpSectionRecord } from "./helpTypes";

export type HelpDescriptionContent = {
  title?: string;
  description?: string;
};

export type HelpWorkflowStep = {
  title?: string;
  description?: string;
  icon?: string;
};

export type HelpFieldTableRow = {
  fieldName?: string;
  required?: boolean;
  description?: string;
  example?: string;
  maxLength?: number | string;
  rule?: string;
  type?: string;
};

export type HelpValidationRule = {
  field?: string;
  rule?: string;
  required?: boolean;
  maxLength?: number | string;
  example?: string;
  type?: string;
};

export type HelpFaqItem = {
  question?: string;
  answer?: string;
};

export type HelpCommonErrorItem = {
  error?: string;
  cause?: string;
  resolution?: string;
  answer?: string;
  description?: string;
};

export type HelpRelatedPageItem = {
  title?: string;
  pageKey?: string;
  description?: string;
  url?: string;
};

export type HelpAttachmentItem = {
  type?: string;
  url?: string;
  displayOrder?: number;
  title?: string;
  description?: string;
};

export type HelpSectionPresentation = {
  label: string;
  iconKey: string;
  defaultExpanded: boolean;
  visible: boolean;
};

const HELP_SECTION_PRESENTATION: Record<string, HelpSectionPresentation> = {
  DESCRIPTION: { label: "Description", iconKey: "description", defaultExpanded: true, visible: true },
  WORKFLOW: { label: "Workflow", iconKey: "workflow", defaultExpanded: true, visible: true },
  FIELD_TABLE: { label: "Field Table", iconKey: "fieldTable", defaultExpanded: false, visible: false },
  VALIDATION_RULES: { label: "Validation Rules", iconKey: "validation", defaultExpanded: false, visible: true },
  REPORT_TYPES: { label: "Report Types", iconKey: "table", defaultExpanded: false, visible: true },
  FILTERS: { label: "Filters", iconKey: "validation", defaultExpanded: false, visible: true },
  EXPORT_CSV: { label: "Export CSV", iconKey: "download", defaultExpanded: false, visible: true },
  QUICK_ACTIONS: { label: "Quick Actions", iconKey: "links", defaultExpanded: false, visible: true },
  DASHBOARD_CARDS: { label: "Dashboard Cards", iconKey: "dashboardCards", defaultExpanded: false, visible: true },
  PERMISSIONS: { label: "Permissions", iconKey: "permissions", defaultExpanded: false, visible: true },
  ROLES: { label: "Roles", iconKey: "roles", defaultExpanded: false, visible: true },
  COMMON_ERRORS: { label: "Common Errors", iconKey: "errors", defaultExpanded: false, visible: true },
  COMMON_ISSUES: { label: "Common Issues", iconKey: "errors", defaultExpanded: false, visible: true },
  FAQ: { label: "FAQ", iconKey: "faq", defaultExpanded: false, visible: true },
  RELATED_PAGES: { label: "Related Pages", iconKey: "relatedPages", defaultExpanded: false, visible: true },
  BEST_PRACTICES: { label: "Best Practices", iconKey: "bestPractices", defaultExpanded: false, visible: true },
  TIPS: { label: "Tips", iconKey: "tips", defaultExpanded: false, visible: true },
  KNOWN_LIMITATIONS: { label: "Known Limitations", iconKey: "limitations", defaultExpanded: false, visible: true },
  VIDEOS: { label: "Videos", iconKey: "videos", defaultExpanded: false, visible: true },
  IMAGES: { label: "Images", iconKey: "images", defaultExpanded: false, visible: true },
  LINKS: { label: "Links", iconKey: "links", defaultExpanded: false, visible: true },
  AUDIT: { label: "Audit", iconKey: "audit", defaultExpanded: false, visible: true },
};

export function getHelpSectionPresentation(sectionType: string): HelpSectionPresentation {
  const normalized = (sectionType || "").trim().toUpperCase();
  return HELP_SECTION_PRESENTATION[normalized] || {
    label: normalized ? normalized.replaceAll("_", " ").toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase()) : "Section",
    iconKey: "default",
    defaultExpanded: false,
    visible: true,
  };
}

export function isVisibleHelpSectionType(sectionType: string): boolean {
  return getHelpSectionPresentation(sectionType).visible;
}

export function isDefaultExpandedHelpSectionType(sectionType: string): boolean {
  return getHelpSectionPresentation(sectionType).defaultExpanded;
}

export function buildDefaultExpandedHelpSections(page: HelpPageRecord | null | undefined): Record<string, boolean> {
  if (!page) return {};
  return Object.fromEntries(
    page.sections
      .filter((section) => section.active && isVisibleHelpSectionType(section.sectionType))
      .map((section) => [section.sectionKey, isDefaultExpandedHelpSectionType(section.sectionType)]),
  );
}

function collectSearchableText(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => collectSearchableText(item)).join(" ");
  }
  if (typeof value === "object") {
    return Object.entries(value as Record<string, unknown>)
      .map(([key, entry]) => `${key} ${collectSearchableText(entry)}`)
      .join(" ");
  }
  return "";
}

export function safeParseHelpJson<T>(contentJson: string | null | undefined): T | null {
  if (!contentJson) return null;
  try {
    return JSON.parse(contentJson) as T;
  } catch {
    return null;
  }
}

export function normalizeHelpSearchQuery(value: string): string {
  return value.replace(/\s+/g, " ").trim().toLowerCase();
}

function stringifySection(section: HelpSectionRecord): string {
  const parsed = safeParseHelpJson<unknown>(section.contentJson);
  const attachmentText = section.attachments
    .map((attachment) => [attachment.type, attachment.url].filter(Boolean).join(" "))
    .join(" ");
  return [
    section.sectionKey,
    section.sectionType,
    section.contentJson ?? "",
    attachmentText,
    collectSearchableText(parsed),
  ]
    .join(" ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

export function filterHelpSections(page: HelpPageRecord | null | undefined, query: string): HelpSectionRecord[] {
  if (!page) return [];
  const normalized = normalizeHelpSearchQuery(query);
  const sections = page.sections.filter((section) => section.active && isVisibleHelpSectionType(section.sectionType));
  if (!normalized) return sections;
  return sections.filter((section) => stringifySection(section).includes(normalized));
}

export function searchHelpSearchResults(results: HelpSearchResult[], query: string): HelpSearchResult[] {
  const normalized = normalizeHelpSearchQuery(query);
  if (!normalized) return results;
  return results.filter((result) => {
    if (!isVisibleHelpSectionType(result.sectionType)) return false;
    const haystack = [result.pageTitle, result.pageKey, result.sectionKey, result.sectionType, result.snippet].join(" ").toLowerCase();
    return haystack.includes(normalized);
  });
}
