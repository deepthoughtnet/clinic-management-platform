import test from "node:test";
import assert from "node:assert/strict";

import { clinicProfileSchema } from "../dist/index.js";

test("clinic profile schema accepts a valid payload", () => {
  const result = clinicProfileSchema.safeParse({
    clinicName: "Jeevanam Healthcare Clinic",
    displayName: "Jeevanam Healthcare",
    phone: "9876543210",
    email: "clinic@example.com",
    addressLine1: "123 Main Road",
    addressLine2: "",
    city: "Pune",
    state: "Maharashtra",
    country: "India",
    postalCode: "411001",
    registrationNumber: "REG-123",
    gstNumber: "GST-123",
    logoDocumentId: "",
    active: true,
    publicListingEnabled: false,
    slug: "jeevanam-healthcare",
  });

  assert.equal(result.success, true);
});

test("clinic profile schema rejects a missing clinic name", () => {
  const result = clinicProfileSchema.safeParse({
    clinicName: "",
    displayName: "Jeevanam Healthcare",
  });

  assert.equal(result.success, false);
  assert.match(result.error.issues[0]?.message || "", /required/i);
});
