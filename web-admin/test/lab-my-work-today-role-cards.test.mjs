import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
const source = fs.readFileSync(path.join(root, "src/pages/lab/LabPage.tsx"), "utf8");

test("lab My Work Today uses explicit lab roles instead of permission-only card groups", () => {
  assert.ok(source.includes('auth.rolesUpper.includes("LAB_FRONT_DESK")'));
  assert.ok(source.includes('auth.rolesUpper.includes("LAB_ASSISTANT")'));
  assert.ok(source.includes('auth.rolesUpper.includes("LAB_TECHNICIAN")'));
  assert.ok(source.includes('auth.rolesUpper.includes("LAB_APPROVER")'));
  assert.ok(source.includes("if (isLabAssistantRole)"));
  assert.ok(source.includes("if (isLabTechnicianRole)"));
  assert.ok(source.includes("if (isLabApproverRole)"));
  assert.ok(!source.includes('addWorkCard({ key: "work-samples-collected"'));
  assert.ok(!source.includes('addWorkCard({ key: "work-published-today"'));
});

test("technician actionable cards stay separate from assistant collection cards", () => {
  const workTodayStart = source.indexOf("const workToday");
  const technicianStart = source.indexOf("if (isLabTechnicianRole)", workTodayStart);
  const approverStart = source.indexOf("if (isLabApproverRole)", technicianStart);
  const technicianBlock = source.slice(technicianStart, approverStart);
  assert.ok(technicianBlock.includes("work-work-queue"));
  assert.ok(technicianBlock.includes("work-results-pending-entry"));
  assert.ok(technicianBlock.includes("work-critical-results"));
  assert.ok(!technicianBlock.includes("work-pending-sample-collection"));
  assert.ok(!technicianBlock.includes("work-recollection-required"));
});
