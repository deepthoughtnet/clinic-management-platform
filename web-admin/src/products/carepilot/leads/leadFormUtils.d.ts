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
  displayName?: string | null;
  username?: string | null;
  membershipStatus?: string | null;
  userStatus?: string | null;
  status?: string | null;
  membershipRole?: string | null;
};

export type LeadConvertedMetadataLike = {
  notes?: string | null;
  tags?: string | null;
  sourceDetails?: string | null;
  campaignId?: string | null;
  assignedToAppUserId?: string | null;
};

export declare function toLeadDateTimeInputValue(value?: string | null): string;
export declare function validateLeadDraft(draft: LeadDraftLike, clinicUsers?: ClinicUserLike[], clinicTimeZone?: string): {
  fieldErrors: Record<string, string>;
  normalizedPhone: string;
  normalizedNextFollowUpAt: string;
};
export declare function filterEligibleEngageAssignees(clinicUsers?: ClinicUserLike[]): ClinicUserLike[];
export declare function validateFollowUpScheduleDraft(
  draft: { date?: string | null; time?: string | null } | null | undefined,
  clinicTimeZone?: string | null,
): {
  fieldErrors: Record<string, string>;
  nextFollowUpAt: string | null;
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
export declare function toConvertedLeadMetadataSnapshot(source: LeadConvertedMetadataLike | LeadDraftLike | null | undefined): {
  notes: string;
  tags: string;
  sourceDetails: string;
  campaignId: string;
  assignedToAppUserId: string;
};
export declare function hasConvertedLeadMetadataChanges(current: LeadConvertedMetadataLike | LeadDraftLike | null | undefined, baseline: LeadConvertedMetadataLike | LeadDraftLike | null | undefined): boolean;
export declare function buildConvertedLeadMetadataPayload(
  draft: LeadConvertedMetadataLike | LeadDraftLike | null | undefined,
  baseline: LeadConvertedMetadataLike | LeadDraftLike | null | undefined,
): {
  notes?: string | null;
  tags?: string | null;
  sourceDetails?: string | null;
  campaignId?: string | null;
  assignedToAppUserId?: string | null;
};
export declare function mapLeadApiErrorToFieldErrors(message: string | null | undefined): Record<string, string>;
