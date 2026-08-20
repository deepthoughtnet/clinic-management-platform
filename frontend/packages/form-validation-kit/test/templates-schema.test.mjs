import assert from "node:assert/strict";

import { adminTemplateSchema } from "../dist/index.js";

const base = {
  name: "Appointment Reminder",
  description: "System reminder template",
  templateType: "REMINDER",
  channel: "EMAIL",
  category: "APPOINTMENT_REMINDER",
  subject: "Hello {{patientName}}",
  body: "Hi {{patientName}}, your appointment is on {{appointmentDate}}.",
  variablesJson: '{"patientName":"John Doe"}',
  active: true,
};

function parse(values) {
  return adminTemplateSchema.safeParse(values);
}

{
  const result = parse({ ...base, name: "   " });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Template name is required.");
}

{
  const result = parse({ ...base, body: "   " });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Template body is required.");
}

{
  const result = parse({ ...base, subject: "   " });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Subject is required for email templates.");
}

{
  const result = parse({ ...base, channel: "SMS", subject: "   " });
  assert.equal(result.success, true);
}

{
  const result = parse({ ...base, variablesJson: "   " });
  assert.equal(result.success, true);
}

{
  const result = parse({ ...base, variablesJson: '{"patientName":"John Doe"}' });
  assert.equal(result.success, true);
}

{
  const result = parse({ ...base, variablesJson: "{invalid-json" });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Enter valid JSON.");
}

{
  const result = parse({ ...base, variablesJson: '["a"]' });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Variables JSON must be a JSON object.");
}

{
  const result = parse({ ...base, body: "Hello {{patientName" });
  assert.equal(result.success, false);
  assert.equal(result.error.issues[0].message, "Body contains an invalid placeholder.");
}

{
  const result = parse({ ...base, body: "Hello {{unknownVariable}}" });
  assert.equal(result.success, true);
}
