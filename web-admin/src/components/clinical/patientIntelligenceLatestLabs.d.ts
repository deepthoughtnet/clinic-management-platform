export interface TrustedLongitudinalLabConcept {
  conceptFamily: string;
  conceptKey: string;
  label: string;
  valueText: string | null;
  valueUnit: string | null;
  sourceDocumentTitle: string | null;
  sourceDocumentType: string | null;
  sourceDocumentId: string | null;
  observedOn: string | null;
  confidence: number | null;
  verificationStatus: string | null;
  evidenceText: string | null;
}

export function buildLatestTrustedLabProjection(history: TrustedLongitudinalLabConcept[] | null | undefined): TrustedLongitudinalLabConcept[];
export function isTrustedLongitudinalLabConcept(concept: TrustedLongitudinalLabConcept | null | undefined): boolean;

