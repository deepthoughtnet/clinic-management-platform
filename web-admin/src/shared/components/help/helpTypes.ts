export type HelpAttachmentType = "IMAGE" | "VIDEO" | "PDF" | "LINK";

export type HelpAttachmentRecord = {
  id: string;
  type: HelpAttachmentType;
  url: string;
  displayOrder: number | null;
};

export type HelpContentRecord = {
  id: string;
  languageCode: string;
  contentJson: string;
  version: number;
  status: string;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
};

export type HelpSectionType =
  | "DESCRIPTION"
  | "WORKFLOW"
  | "FIELD_TABLE"
  | "VALIDATION_RULES"
  | "REPORT_TYPES"
  | "FILTERS"
  | "EXPORT_CSV"
  | "QUICK_ACTIONS"
  | "DASHBOARD_CARDS"
  | "PERMISSIONS"
  | "COMMON_ERRORS"
  | "COMMON_ISSUES"
  | "BEST_PRACTICES"
  | "FAQ"
  | "RELATED_PAGES"
  | "VIDEOS"
  | "IMAGES"
  | "LINKS"
  | "AUDIT"
  | "ROLES"
  | "TIPS"
  | "KNOWN_LIMITATIONS";

export type HelpSectionRecord = {
  id: string;
  sectionKey: string;
  sectionType: HelpSectionType | string;
  displayOrder: number;
  collapsible: boolean;
  active: boolean;
  contentJson: string | null;
  contentLanguageCode: string | null;
  contentVersion: number | null;
  contentStatus: string | null;
  attachments: HelpAttachmentRecord[];
  contents: HelpContentRecord[];
};

export type HelpPageRecord = {
  id: string;
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: string;
  version: number;
  active: boolean;
  createdBy: string | null;
  updatedBy: string | null;
  createdAt: string;
  updatedAt: string;
  availableVersions: number[];
  sections: HelpSectionRecord[];
};

export type HelpPageSummary = {
  id: string;
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: string;
  version: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type HelpSearchResult = {
  pageKey: string;
  pageTitle: string;
  moduleKey: string;
  sectionKey: string;
  sectionType: string;
  snippet: string;
  languageCode: string;
};

export type HelpSectionUpsertRequest = {
  sectionKey: string;
  sectionType: string;
  displayOrder: number | null;
  collapsible: boolean;
  active: boolean;
  contentJson: string;
};

export type HelpPageUpsertRequest = {
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string | null;
  status: string;
  active: boolean;
  sections: HelpSectionUpsertRequest[];
};

export type HelpPageLifecycleRequest = {
  pageKey: string;
  version: number | null;
};

export type HelpPageDraft = {
  moduleKey: string;
  pageKey: string;
  title: string;
  icon: string;
  status: string;
  active: boolean;
  version: number;
  availableVersions: number[];
  sections: HelpSectionDraft[];
};

export type HelpSectionDraft = {
  sectionKey: string;
  sectionType: string;
  displayOrder: number;
  collapsible: boolean;
  active: boolean;
  contentJson: string;
};
