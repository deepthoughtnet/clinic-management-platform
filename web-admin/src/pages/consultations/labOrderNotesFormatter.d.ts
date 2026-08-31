export const LAB_ORDER_NOTES_MAX_LENGTH: number;

export interface BuildAiLabOrderNotesInput {
  generatedAt?: string | null;
  reason?: string | null;
  supportingEvidence?: Array<string | null | undefined> | string | null;
  suggestedPriority?: string | null;
  duplicateWarnings?: Array<string | null | undefined> | string | null;
  maxLength?: number;
}

export function buildAiLabOrderNotes(input: BuildAiLabOrderNotesInput): string;
