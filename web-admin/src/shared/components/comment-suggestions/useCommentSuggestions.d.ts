import type { CommentSuggestionCategory, CommentSuggestionCategoryConfig } from "./commentSuggestionConfig.js";

export declare function useCommentSuggestionsState(
  category: CommentSuggestionCategory,
  remarks: string,
  query: string,
): {
  config: CommentSuggestionCategoryConfig;
  filteredSuggestions: string[];
  appendSuggestion: (suggestion: string) => string;
};
