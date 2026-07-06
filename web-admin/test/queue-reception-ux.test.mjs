import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("queue reception ux keeps payment, cancellation, and action rules in source", () => {
  const source = readSource("pages/appointments/QueuePage.tsx");

  assert.ok(source.includes('label="Reason / comment"'));
  assert.ok(source.includes('Reason/comment is required.'));
  assert.ok(source.includes('Mark No Show'));
  assert.ok(source.includes('Cancel Appointment'));
  assert.ok(source.includes('Payment has already been collected for this appointment.'));
  assert.ok(source.includes('process the refund from Billing'));
  assert.ok(source.includes('paymentState: FeeStatus | "DEFERRED"'));
  assert.ok(source.includes('paymentState === "DEFERRED"'));
  assert.ok(source.includes('priorityLabel(priority'));
  assert.ok(source.includes('Pay After Consultation'));
  assert.ok(source.includes('Collect Fee'));
  assert.ok(source.includes('Clinical Intake'));
  assert.ok(source.includes('Open Patient'));
  assert.ok(source.includes('visiblePrimaryActions.length === 0 ? ('));
  assert.ok(source.includes('View Details'));
  assert.ok(source.includes('Audit'));
  assert.ok(source.includes('Receipt'));
  assert.ok(source.includes('Print'));
  assert.ok(source.includes('Email'));
  assert.ok(source.includes('WhatsApp'));
  assert.ok(source.includes('rowActions.filter((action) => action.visible).slice(0, 3)'));
  assert.ok(source.includes('InfoOutlinedIcon'));
  assert.ok(source.includes('openPaymentPopover'));
  assert.ok(source.includes('Payment details'));
  assert.ok(source.includes('Receipt number'));
  assert.ok(source.includes('Remaining due'));
  assert.ok(source.includes('label={appointment.displayReference || (appointment.tokenNumber != null ? `APT-${appointment.tokenNumber}` : "—")}'));
  assert.ok(!source.includes('TableCell sx={{ width: 128 }}>Check-in</TableCell>'));
});
