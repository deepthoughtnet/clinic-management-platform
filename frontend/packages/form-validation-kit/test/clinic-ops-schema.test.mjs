import test from "node:test";
import assert from "node:assert/strict";

import {
  dashboardFilterSchema,
  doctorAvailabilitySchema,
  doctorUnavailabilitySchema,
  notificationsFilterSchema,
  vaccinationMasterSchema,
  vaccinationRecordSchema,
} from "../dist/index.js";

test("vaccination master schema accepts a valid payload", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, true);
});

test("vaccination master schema rejects an invalid age range", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 365,
    recommendedAgeDays: 30,
    maxAgeDays: 90,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, false);
});

test("vaccination master schema accepts a blank default price", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    defaultPrice: "",
    active: true,
  });
  assert.equal(result.success, true);
});

test("vaccination master schema rejects missing inventory item when stock tracking is enabled", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    stockTrackingEnabled: true,
    inventoryItemId: "",
    catchUpPolicy: "NONE",
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, false);
});

test("vaccination master schema allows missing inventory item when stock tracking is disabled", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    stockTrackingEnabled: false,
    inventoryItemId: "",
    catchUpPolicy: "NONE",
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, true);
});

test("vaccination master schema rejects missing catch-up max age when policy requires it", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    catchUpPolicy: "ALLOWED_UNTIL_AGE",
    catchUpMaxAgeDays: "",
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, false);
});

test("vaccination master schema allows catch-up max age to remain optional for other policies", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    catchUpPolicy: "NONE",
    catchUpMaxAgeDays: "",
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, true);
});

test("vaccination master schema accepts clinical indications up to 1000 characters", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    clinicalIndications: "a".repeat(1000),
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, true);
});

test("vaccination master schema rejects clinical indications beyond 1000 characters", () => {
  const result = vaccinationMasterSchema.safeParse({
    vaccineName: "COVID Booster",
    description: "Annual booster",
    manufacturer: "Acme Labs",
    brandName: "Comvax",
    vaccineGroup: "COVID",
    doseNumber: 1,
    route: "IM",
    administrationSite: "Deltoid",
    storageTemperature: "2-8 C",
    ndcBarcode: "123456789",
    scheduleType: "ADULT",
    ageGroup: "Adults",
    minAgeDays: 0,
    recommendedAgeDays: 180,
    maxAgeDays: 365,
    gapDays: 180,
    recommendedGapDays: 180,
    boosterGapDays: 365,
    boosterRules: "Annual booster",
    recurring: false,
    recurrenceDays: 365,
    clinicalIndications: "a".repeat(1001),
    defaultPrice: 250,
    active: true,
  });
  assert.equal(result.success, false);
});

test("vaccination record schema rejects a next due date earlier than the given date", () => {
  const result = vaccinationRecordSchema.safeParse({
    patientId: "11111111-1111-1111-1111-111111111111",
    vaccineId: "22222222-2222-2222-2222-222222222222",
    doseNumber: 1,
    givenDate: "2026-06-20",
    patientDateOfBirth: "2026-06-01",
    nextDueDate: "2026-06-10",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    billId: "",
    addToBill: false,
    billItemUnitPrice: 0,
  });
  assert.equal(result.success, false);
});

test("vaccination record schema rejects a non-positive dose number", () => {
  const result = vaccinationRecordSchema.safeParse({
    patientId: "11111111-1111-1111-1111-111111111111",
    vaccineId: "22222222-2222-2222-2222-222222222222",
    doseNumber: 0,
    givenDate: "2026-06-20",
    patientDateOfBirth: "2026-06-01",
    nextDueDate: "",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    billId: "",
    addToBill: false,
    billItemUnitPrice: 0,
  });
  assert.equal(result.success, false);
});

test("vaccination record schema rejects future given dates and dates before birth", () => {
  const futureResult = vaccinationRecordSchema.safeParse({
    patientId: "11111111-1111-1111-1111-111111111111",
    vaccineId: "22222222-2222-2222-2222-222222222222",
    doseNumber: 1,
    givenDate: "2099-06-20",
    patientDateOfBirth: "2026-06-01",
    nextDueDate: "",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    billId: "",
    addToBill: false,
    billItemUnitPrice: 0,
  });
  assert.equal(futureResult.success, false);

  const birthResult = vaccinationRecordSchema.safeParse({
    patientId: "11111111-1111-1111-1111-111111111111",
    vaccineId: "22222222-2222-2222-2222-222222222222",
    doseNumber: 1,
    givenDate: "2026-05-20",
    patientDateOfBirth: "2026-06-01",
    nextDueDate: "",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    billId: "",
    addToBill: false,
    billItemUnitPrice: 0,
  });
  assert.equal(birthResult.success, false);
});

test("vaccination record schema rejects a missing patient and vaccine", () => {
  const result = vaccinationRecordSchema.safeParse({
    patientId: "",
    vaccineId: "",
    doseNumber: 1,
    givenDate: "2026-06-20",
    patientDateOfBirth: "",
    nextDueDate: "",
    batchNumber: "",
    notes: "",
    administeredByUserId: "",
    billId: "",
    addToBill: false,
    billItemUnitPrice: 0,
  });
  assert.equal(result.success, false);
});

test("doctor availability schema rejects inverted time ranges", () => {
  const result = doctorAvailabilitySchema.safeParse({
    dayOfWeek: "MONDAY",
    startTime: "12:00",
    endTime: "11:00",
    breakStartTime: null,
    breakEndTime: null,
    consultationDurationMinutes: 30,
    maxPatientsPerSlot: 2,
    active: true,
  });
  assert.equal(result.success, false);
});

test("doctor unavailability schema rejects inverted date ranges", () => {
  const result = doctorUnavailabilitySchema.safeParse({
    startAt: "2026-06-20T10:00:00",
    endAt: "2026-06-20T09:00:00",
    type: "LEAVE",
    reason: "Annual leave",
    active: true,
  });
  assert.equal(result.success, false);
});

test("notification filter schema rejects inverted date ranges", () => {
  const result = notificationsFilterSchema.safeParse({
    search: " reminder ",
    status: "PENDING",
    eventType: "APPOINTMENT_REMINDER",
    channel: "SMS",
    patientId: "",
    from: "2026-06-20",
    to: "2026-06-10",
  });
  assert.equal(result.success, false);
});

test("dashboard filter schema accepts a valid range", () => {
  const result = dashboardFilterSchema.safeParse({
    startDate: "2026-06-01",
    endDate: "2026-06-20",
    doctorUserId: "",
  });
  assert.equal(result.success, true);
});
