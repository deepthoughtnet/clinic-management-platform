import test from "node:test";
import assert from "node:assert/strict";

import { createRequiredFields } from "../dist/index.js";

test("required fields helper exposes required names", () => {
  const helper = createRequiredFields([
    "name",
    { name: "email", label: "Email" },
  ]);

  assert.deepEqual(helper.requiredFieldNames.sort(), ["email", "name"]);
  assert.equal(helper.isRequiredField("name"), true);
  assert.equal(helper.isRequiredField("mobile"), false);
});
