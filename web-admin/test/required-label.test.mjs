import test from "node:test";
import assert from "node:assert/strict";
import { renderToStaticMarkup } from "react-dom/server";
import React from "react";

import RequiredLabel from "../src/components/forms/RequiredLabel.js";

test("required label renders an asterisk for required fields", () => {
  const markup = renderToStaticMarkup(React.createElement(RequiredLabel, { text: "Medicine name" }));
  assert.ok(markup.includes("*"));
  assert.ok(markup.includes("Medicine name"));
  assert.ok(markup.includes("(required)"));
});

test("required label omits the asterisk for optional fields", () => {
  const markup = renderToStaticMarkup(React.createElement(RequiredLabel, { text: "Generic name", required: false }));
  assert.ok(!markup.includes("*"));
  assert.ok(markup.includes("Generic name"));
});
