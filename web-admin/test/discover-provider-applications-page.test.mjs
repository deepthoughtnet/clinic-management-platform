import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("provider applications page uses a canonical URL-to-status filter map", () => {
  const source = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(source.includes("const STATUS_FILTER_MAP"));
  assert.ok(source.includes('submitted: ["SUBMITTED"]'));
  assert.ok(source.includes('"under-review": ["UNDER_REVIEW"]'));
  assert.ok(source.includes('"changes-requested": ["CHANGES_REQUESTED"]'));
  assert.ok(source.includes('approved: ["APPROVED"]'));
  assert.ok(source.includes('published: ["PUBLISHED"]'));
  assert.ok(source.includes("all: null"));
  assert.ok(!source.includes('replace("-", "_").toUpperCase()'));
});

test("provider applications page defaults and normalizes the status URL parameter", () => {
  const source = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(source.includes('next.set("status", STATUS_TABS[0].value)'));
  assert.ok(source.includes("isKnownStatusFilter"));
  assert.ok(source.includes("setSearchParams(next, { replace: true })"));
});

test("provider applications page separates queue errors from empty states", () => {
  const source = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(source.includes("setQueueError"));
  assert.ok(source.includes("setDetailError"));
  assert.ok(source.includes("Promise.allSettled"));
  assert.ok(source.includes("No provider applications are waiting for review."));
  assert.ok(source.includes("No provider applications are currently under review."));
  assert.ok(source.includes("Select a provider application to review."));
  assert.ok(!source.includes('{error ? <Alert severity=\"error\">{error}</Alert> : null}'));
});

test("provider applications page uses the existing authenticated review document blob path", () => {
  const source = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(source.includes("getDiscoverProviderReviewDocumentBlob"));
  assert.ok(source.includes("URL.createObjectURL(blob)"));
  assert.ok(source.includes("URL.revokeObjectURL"));
  assert.ok(source.includes("Preview"));
  assert.ok(source.includes("Download"));
});

test("provider applications page renders document metadata and safe scan-state handling", () => {
  const source = readSource("pages/platform/DiscoverProviderApplicationsPage.tsx");

  assert.ok(source.includes("formatFileSize"));
  assert.ok(source.includes("Virus scan:"));
  assert.ok(source.includes("This document was blocked by the security scan."));
  assert.ok(source.includes("Preview is not available for this file type."));
  assert.ok(!source.includes("storageKey"));
});
