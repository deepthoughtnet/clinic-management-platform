import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

function readRepoRootFile(relPath) {
  const candidates = [process.cwd(), path.join(process.cwd(), "..")];
  for (const root of candidates) {
    const file = path.join(root, relPath);
    if (fs.existsSync(file)) {
      return fs.readFileSync(file, "utf8");
    }
  }
  throw new Error(`Unable to locate ${relPath}`);
}

test("product implementation page reflects OPD UAT readiness and current milestone", () => {
  const source = readSource("pages/platform/ProductImplementationPage.tsx");
  assert.ok(source.includes("Product Implementation & Release Readiness"));
  assert.ok(source.includes("Evidence-based view of implementation, integration, UAT, pilot readiness, and production hardening."));
  assert.ok(source.includes('label: "Overview"'));
  assert.ok(source.includes('label: "Modules"'));
  assert.ok(source.includes('label: "Workflows"'));
  assert.ok(source.includes('label: "Feature Matrix"'));
  assert.ok(source.includes('label: "UAT & Regression"'));
  assert.ok(source.includes('label: "Production Readiness"'));
  assert.ok(source.includes('label: "Releases"'));
  assert.ok(source.includes("CONDITIONAL GO"));
  assert.ok(source.includes("Module readiness matrix"));
  assert.ok(source.includes("P0 Pilot Blockers"));
  assert.ok(source.includes("P1 Pilot Preparation"));
  assert.ok(source.includes("P2 Pilot Improvements"));
  assert.ok(source.includes("P3 Post-Pilot Roadmap"));
  assert.ok(source.includes("Doctor Consultation AI final phase"));
  assert.ok(source.includes("Batch 5 is an integration, refinement, and pilot-readiness phase, not a new feature expansion."));
  assert.ok(source.includes("Detailed evidence and release criteria are maintained in `PRODUCT_READINESS.md`"));
});

test("shared readiness model exposes the five scorecards and module matrix values", () => {
  const source = readSource("pages/platform/productImplementation/readinessModel.ts");
  assert.ok(source.includes('label: "Feature Completion"'));
  assert.ok(source.includes('label: "Workflow Integration"'));
  assert.ok(source.includes('label: "UAT Verification"'));
  assert.ok(source.includes('label: "Pilot Readiness"'));
  assert.ok(source.includes('label: "Production Readiness"'));
  assert.ok(source.includes('percentage: 84'));
  assert.ok(source.includes('percentage: 79'));
  assert.ok(source.includes('percentage: 74'));
  assert.ok(source.includes('percentage: 67'));
  assert.ok(source.includes('percentage: 56'));
  assert.ok(source.includes('label: "CONDITIONAL GO"'));
  assert.ok(source.includes('module: "Doctor Consultation"'));
  assert.ok(source.includes('pilotStatus: "READY WITH CONDITIONS"'));
  assert.ok(source.includes('remainingWorkClass: "P0"'));
});

test("product readiness markdown reflects the same readiness model", () => {
  const source = readRepoRootFile("PRODUCT_READINESS.md");
  assert.ok(source.includes("# Jeevanam Healthcare Platform — Product Readiness"));
  assert.ok(source.includes("Controlled pilot for the core OPD clinical flow"));
  assert.ok(source.includes("Functional Feature Completion"));
  assert.ok(source.includes("Workflow Integration Completion"));
  assert.ok(source.includes("UAT Verification Completion"));
  assert.ok(source.includes("Controlled Pilot Readiness"));
  assert.ok(source.includes("Production Readiness"));
  assert.ok(source.includes("CONDITIONAL GO"));
  assert.ok(source.includes("Doctor Consultation AI — Final Integration Phase"));
  assert.ok(source.includes("Remaining consultation-AI sequence"));
  assert.ok(source.includes("Controlled Pilot Entry Criteria"));
  assert.ok(source.includes("Production Release Criteria"));
  assert.ok(source.includes("Readiness history"));
});
