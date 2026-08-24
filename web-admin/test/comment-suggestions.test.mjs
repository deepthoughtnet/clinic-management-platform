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

test("lab suggestion categories split result correction and specimen rejection reasons", () => {
  const correction = getCommentSuggestionCategoryConfig("LAB_RESULT_CORRECTION");
  const specimen = getCommentSuggestionCategoryConfig("LAB_SPECIMEN_REJECTION");

  assert.deepEqual(correction.reasons.slice(0, 3), ["INCORRECT_RESULT", "DATA_ENTRY_ERROR", "UNIT_MISMATCH"]);
  assert.deepEqual(specimen.reasons.slice(0, 3), ["SAMPLE_HEMOLYZED", "INSUFFICIENT_SAMPLE", "SAMPLE_MISMATCH"]);
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
  assert.ok(categories.includes("LAB_RESULT_CORRECTION"));
  assert.ok(categories.includes("LAB_SPECIMEN_REJECTION"));
});
