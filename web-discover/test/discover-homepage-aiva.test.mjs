import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

test("homepage uses contained hero imagery and an AIVA discovery assistant card", () => {
  const page = read("src/pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("src/components/DiscoveryComponents.tsx");
  const styles = read("src/styles.css");

  assert.ok(styles.includes("object-fit: contain"));
  assert.ok(styles.includes("object-position: center center"));
  assert.ok(!page.includes("Your Health, Our Priority"));
  assert.ok(page.includes("AivaDiscoveryAssistantCard"));
  assert.ok(components.includes("Your AI care guide"));
  assert.ok(components.includes("Not sure which doctor to visit?"));
  assert.ok(components.includes("Describe your symptoms in natural language."));
  assert.ok(components.includes("AIVA will soon help you:"));
  assert.ok(components.includes("Suggest the right speciality"));
  assert.ok(components.includes("Explain medical terms"));
  assert.ok(components.includes("Help prepare for appointments"));
  assert.ok(components.includes("Coming Soon"));
  assert.ok(components.includes("AIVA assists healthcare discovery. It does not replace medical advice."));
  assert.ok(!components.includes("Ask AIVA"));
  assert.ok(!components.includes("home-aiva-modal"));
});

test("AIVA assistant remains safe and local-first when no external assistant URL is configured", () => {
  const config = read("src/config.ts");
  const components = read("src/components/DiscoveryComponents.tsx");

  assert.ok(config.includes("aivaAppUrl"));
  assert.ok(!components.includes("buildAivaAssistantUrl"));
  assert.ok(!components.includes("type=\"text\""));
  assert.ok(!components.includes("Ask AIVA"));
  assert.ok(!components.includes("home-aiva-field"));
  assert.ok(!components.includes("home-aiva-modal"));
});
