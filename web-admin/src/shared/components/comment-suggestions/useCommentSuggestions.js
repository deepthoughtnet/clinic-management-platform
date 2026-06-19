import { appendSuggestionToRemarks, filterSuggestionChips, getCommentSuggestionCategoryConfig } from "./commentSuggestionConfig.js";

export function useCommentSuggestionsState(category, remarks, query) {
  const config = getCommentSuggestionCategoryConfig(category);
  const filteredSuggestions = filterSuggestionChips(config.suggestions, query);
  return {
    config,
    filteredSuggestions,
    appendSuggestion: (suggestion) => appendSuggestionToRemarks(remarks, suggestion),
  };
}
