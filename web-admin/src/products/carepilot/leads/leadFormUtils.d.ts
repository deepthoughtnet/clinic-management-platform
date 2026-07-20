export type LeadDraftLike = {
  firstName: string;
  lastName: string;
  phone: string;
  email: string;
  source: string;
  sourceDetails: string;
  status: string;
  priority: string;
  notes: string;
  tags: string;
  nextFollowUpAt: string;
  campaignId: string;
  assignedToAppUserId: string;
};

export type ClinicUserLike = {
  appUserId: string;
  membershipStatus?: string | null;
};

export declare function toLeadDateTimeInputValue(value?: string | null): string;
export declare function validateLeadDraft(draft: LeadDraftLike, clinicUsers?: ClinicUserLike[]): {
  fieldErrors: Record<string, string>;
  normalizedPhone: string;
  normalizedNextFollowUpAt: string;
};
export declare function buildLeadCreatePayload(draft: LeadDraftLike, normalizedPhone: string): {
  firstName: string;
  lastName: string | null;
  phone: string;
  email: string | null;
  source: string;
  sourceDetails: string | null;
  status: string;
  priority: string;
  notes: string | null;
  tags: string | null;
  campaignId: string | null;
  assignedToAppUserId: string | null;
  nextFollowUpAt: string | null;
};
export declare function mapLeadApiErrorToFieldErrors(message: string | null | undefined): Record<string, string>;
