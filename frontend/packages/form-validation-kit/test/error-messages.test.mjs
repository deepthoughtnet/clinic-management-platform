import assert from "node:assert/strict";
import { z } from "zod";

import { requiredString, firstZodError, getFieldError, mapZodErrors, validationMessages } from "../dist/index.js";

const schema = z.object({
  name: requiredString("Name is required."),
  age: z.number().min(1, validationMessages.invalidPositiveNumber),
});

const result = schema.safeParse({ name: "", age: 0 });
assert.equal(result.success, false);

const mapped = mapZodErrors(result.error);
assert.equal(mapped.name, "Name is required.");
assert.equal(mapped.age, validationMessages.invalidPositiveNumber);
assert.equal(firstZodError(result.error), "Name is required.");
assert.equal(getFieldError(result.error, "name"), "Name is required.");
