import test from "node:test";
import assert from "node:assert/strict";

import {
  formatDisplayDate,
  formatDisplayDateTime,
  formatDisplayDateTimeFromParts,
  formatDisplayTime,
} from "../src/utils/dateDisplay.js";

test("public web date helpers render dd/mm/yyyy and 12-hour time", () => {
  assert.equal(formatDisplayDate("2026-06-29"), "29/06/2026");
  assert.equal(formatDisplayTime("16:30"), "04:30 PM");
  assert.equal(formatDisplayDateTimeFromParts("2026-06-29", "16:30"), "29/06/2026 04:30 PM");
  assert.equal(formatDisplayDateTime("2026-06-29T16:30:00.000Z"), "29/06/2026 04:30 PM");
});
