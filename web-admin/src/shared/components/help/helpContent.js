export function safeParseHelpJson(contentJson) {
  if (!contentJson) return null;
  try {
    return JSON.parse(contentJson);
  } catch {
    return null;
  }
}

export function normalizeHelpSearchQuery(value) {
  return String(value || "").replace(/\s+/g, " ").trim().toLowerCase();
}

const HELP_SECTION_PRESENTATION = {
  DESCRIPTION: { label: "Description", defaultExpanded: true, visible: true },
  WORKFLOW: { label: "Workflow", defaultExpanded: true, visible: true },
  FIELD_TABLE: { label: "Field Table", defaultExpanded: false, visible: false },
  VALIDATION_RULES: { label: "Validation Rules", defaultExpanded: false, visible: true },
  REPORT_TYPES: { label: "Report Types", defaultExpanded: false, visible: true },
  FILTERS: { label: "Filters", defaultExpanded: false, visible: true },
  EXPORT_CSV: { label: "Export CSV", defaultExpanded: false, visible: true },
  TAB_GUIDE: { label: "Tab Guide", defaultExpanded: false, visible: true },
  QUICK_ACTIONS: { label: "Quick Actions", defaultExpanded: false, visible: true },
  DASHBOARD_CARDS: { label: "Dashboard Cards", defaultExpanded: false, visible: true },
  PERMISSIONS: { label: "Permissions", defaultExpanded: false, visible: true },
  ROLES: { label: "Roles", defaultExpanded: false, visible: true },
  COMMON_ERRORS: { label: "Common Errors", defaultExpanded: false, visible: true },
  COMMON_ISSUES: { label: "Common Issues", defaultExpanded: false, visible: true },
  FAQ: { label: "FAQ", defaultExpanded: false, visible: true },
  RELATED_PAGES: { label: "Related Pages", defaultExpanded: false, visible: true },
  BEST_PRACTICES: { label: "Best Practices", defaultExpanded: false, visible: true },
  TIPS: { label: "Tips", defaultExpanded: false, visible: true },
  KNOWN_LIMITATIONS: { label: "Known Limitations", defaultExpanded: false, visible: true },
  VIDEOS: { label: "Videos", defaultExpanded: false, visible: true },
  IMAGES: { label: "Images", defaultExpanded: false, visible: true },
  LINKS: { label: "Links", defaultExpanded: false, visible: true },
  AUDIT: { label: "Audit", defaultExpanded: false, visible: true },
};

export function getHelpSectionPresentation(sectionType) {
  const normalized = String(sectionType || "").trim().toUpperCase();
  return HELP_SECTION_PRESENTATION[normalized] || {
    label: normalized ? normalized.replace(/_/g, " ").toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase()) : "Section",
    defaultExpanded: false,
    visible: true,
  };
}

export function isVisibleHelpSectionType(sectionType) {
  return getHelpSectionPresentation(sectionType).visible;
}

export function isDefaultExpandedHelpSectionType(sectionType) {
  return getHelpSectionPresentation(sectionType).defaultExpanded;
}

export function buildDefaultExpandedHelpSections(page) {
  if (!page) return {};
  return Object.fromEntries((page.sections || []).filter((section) => section.active && isVisibleHelpSectionType(section.sectionType)).map((section) => [section.sectionKey, isDefaultExpandedHelpSectionType(section.sectionType)]));
}

function collectSearchableText(value) {
  if (value == null) return "";
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => collectSearchableText(item)).join(" ");
  }
  if (typeof value === "object") {
    return Object.entries(value)
      .map(([key, entry]) => `${key} ${collectSearchableText(entry)}`)
      .join(" ");
  }
  return "";
}

function stringifySection(section) {
  const parsed = safeParseHelpJson(section.contentJson);
  return [
    section.sectionKey,
    section.sectionType,
    section.contentJson || "",
    Array.isArray(section.attachments) ? section.attachments.map((attachment) => [attachment.type, attachment.url].filter(Boolean).join(" ")).join(" ") : "",
    collectSearchableText(parsed),
  ]
    .join(" ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

export function filterHelpSections(page, query) {
  if (!page) return [];
  const normalized = normalizeHelpSearchQuery(query);
  const sections = (page.sections || []).filter((section) => section.active && isVisibleHelpSectionType(section.sectionType));
  if (!normalized) return sections;
  return sections.filter((section) => stringifySection(section).includes(normalized));
}
