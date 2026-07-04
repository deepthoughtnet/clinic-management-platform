import type { ClinicalDocumentType } from "../../api/clinicApi";

export type DocumentTypeOption = {
  value: ClinicalDocumentType | string;
  label: string;
};

export declare const STANDARD_DOCUMENT_TYPE_ORDER: DocumentTypeOption[];
export declare const LEGACY_DOCUMENT_TYPE_ALIASES: DocumentTypeOption[];
export declare function buildDocumentTypeOptions(backendDocumentTypes?: DocumentTypeOption[]): DocumentTypeOption[];
export declare function documentTypeLabel(value: string | null | undefined): string;
export declare function documentTypeStorageKey(value: string | null | undefined): string;
