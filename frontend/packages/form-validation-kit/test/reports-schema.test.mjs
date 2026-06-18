import test from "node:test";
import assert from "node:assert/strict";

import { reportDateRangeSchema, reportExportSchema, reportFilterSchema } from "../dist/index.js";

test("report filter accepts a valid date range and optional filters", () => {
  const result = reportFilterSchema.safeParse({
    from: "2026-01-01",
    to: "2026-01-31",
    doctorUserId: "550e8400-e29b-41d4-a716-446655440000",
    patientId: "550e8400-e29b-41d4-a716-446655440001",
    status: "COMPLETED",
    paymentMode: "CASH",
    source: "MANUAL",
  });

  assert.equal(result.success, true);
});

test("report date range rejects inverted dates", () => {
  const result = reportDateRangeSchema.safeParse({
    from: "2026-02-01",
    to: "2026-01-01",
  });

  assert.equal(result.success, false);
});

test("report export schema accepts the default format", () => {
  const result = reportExportSchema.safeParse({
    format: "CSV",
    fileName: "lab-ops-report",
  });

  assert.equal(result.success, true);
});
