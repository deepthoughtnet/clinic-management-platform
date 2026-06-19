import test from "node:test";
import assert from "node:assert/strict";

import {
  appendSuggestionToRemarks,
  filterSuggestionChips,
  getCommentSuggestionCategoryConfig,
  listCommentSuggestionCategories,
} from "../src/shared/components/comment-suggestions/commentSuggestionConfig.js";

test("comment suggestion reasons render for a category", () => {
  const config = getCommentSuggestionCategoryConfig("DISPENSING_UNAVAILABLE");
  assert.deepEqual(config.reasons.slice(0, 2), ["OUT_OF_STOCK", "SUPPLIER_UNAVAILABLE"]);
});

test("comment suggestion chips append to remarks", () => {
  assert.equal(
    appendSuggestionToRemarks("Patient declined", "Family member will bring medicines later."),
    "Patient declined\nFamily member will bring medicines later.",
  );
});

test("comment suggestion filtering works", () => {
  const suggestions = getCommentSuggestionCategoryConfig("DISPENSING_CANCELLED").suggestions;
  assert.deepEqual(filterSuggestionChips(suggestions, "duplicate"), ["Duplicate prescription closed."]);
});

test("comment suggestion config is reusable across categories", () => {
  const categories = listCommentSuggestionCategories();
  assert.ok(categories.includes("BILLING_ADJUSTMENT"));
  assert.ok(categories.includes("APPOINTMENT_CANCELLATION"));
});
