import test from "node:test";
import assert from "node:assert/strict";

import { leadCreateSchema, leadImportSchema, leadUpdateSchema } from "../dist/index.js";

test("lead schema accepts a valid payload", () => {
  const result = leadCreateSchema.safeParse({
    firstName: "Asha",
    lastName: "Patel",
    phone: "9876543210",
    email: "asha@example.com",
    source: "WEBSITE",
    status: "NEW",
    priority: "MEDIUM",
    notes: "Call in the afternoon",
    tags: "new,web",
    nextFollowUpAt: "2026-06-18T12:30",
  });

  assert.equal(result.success, true);
});

test("lead schema normalizes indian mobile prefixes", () => {
  const result = leadCreateSchema.safeParse({
    firstName: "Asha",
    phone: "+91 98765 43210",
  });

  assert.equal(result.success, true);
  if (result.success) {
    assert.equal(result.data.phone, "9876543210");
  }
});

test("lead schema rejects an invalid email", () => {
  const result = leadUpdateSchema.safeParse({
    firstName: "Asha",
    phone: "9876543210",
    email: "invalid-email",
  });

  assert.equal(result.success, false);
});

test("lead schema rejects an invalid Indian mobile number", () => {
  const result = leadCreateSchema.safeParse({
    firstName: "Asha",
    phone: "12345",
  });

  assert.equal(result.success, false);
});

test("lead import schema accepts a csv file", () => {
  const result = leadImportSchema.safeParse({
    file: {
      name: "leads.csv",
      size: 1024,
      type: "text/csv",
    },
  });

  assert.equal(result.success, true);
});
