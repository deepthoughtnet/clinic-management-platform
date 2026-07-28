import test from "node:test";
import assert from "node:assert/strict";

import {
  formatSlotGroupLabel,
  groupAvailableSlotsByDate,
  isFutureSelectableSlot,
} from "../src/utils/bookingSlots.js";

test("booking slots hide past selections and group by day", () => {
  const now = new Date("2026-06-28T09:00:00.000Z");
  const slots = [
    { appointmentDate: "2026-06-28", slotTime: "08:00", slotEndTime: "08:30", status: null, selectable: true },
    { appointmentDate: "2026-06-28", slotTime: "10:00", slotEndTime: "10:30", status: null, selectable: true },
    { appointmentDate: "2026-06-29", slotTime: "09:00", slotEndTime: null, status: null, selectable: true },
    { appointmentDate: "2026-06-30", slotTime: "11:00", slotEndTime: null, status: null, selectable: false },
  ];

  assert.equal(isFutureSelectableSlot(slots[0], now), false);
  assert.equal(isFutureSelectableSlot(slots[1], now), true);

  const groups = groupAvailableSlotsByDate(slots, now);
  assert.deepEqual(
    groups.map((group) => group.label),
    ["Today", "Tomorrow"],
  );
  assert.equal(groups[0].slots.length, 1);
  assert.equal(groups[0].slots[0].slotTime, "10:00");
  assert.equal(groups[1].slots.length, 1);
});

test("slot group labels fall back to a formatted date", () => {
  const now = new Date("2026-06-28T09:00:00.000Z");
  assert.equal(formatSlotGroupLabel("2026-06-30", now), "30/06/2026");
});
