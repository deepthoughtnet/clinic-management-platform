export type CommentSuggestionCategory =
  | "DISPENSING_BOUGHT_EXTERNALLY"
  | "DISPENSING_UNAVAILABLE"
  | "DISPENSING_PATIENT_DECLINED"
  | "DISPENSING_CANCELLED"
  | "INVENTORY_ADJUSTMENT"
  | "BILLING_ADJUSTMENT"
  | "APPOINTMENT_CANCELLATION"
  | "LAB_REJECTION"
  | "LEAD_LOST_REASON"
  | "REFUND";

export type CommentSuggestionCategoryConfig = {
  reasons: string[];
  suggestions: string[];
};

export declare function getCommentSuggestionCategoryConfig(category: CommentSuggestionCategory): CommentSuggestionCategoryConfig;
export declare function appendSuggestionToRemarks(remarks: string, suggestion: string): string;
export declare function filterSuggestionChips(suggestions: readonly string[], query: string): string[];
export declare function listCommentSuggestionCategories(): string[];
export declare const commentSuggestionConfig: Record<CommentSuggestionCategory, CommentSuggestionCategoryConfig>;
