import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  const root = fs.existsSync(path.join(process.cwd(), "src")) ? process.cwd() : path.join(process.cwd(), "web-admin");
  return fs.readFileSync(path.join(root, "src", ...relPath.split("/")), "utf8");
}

test("commercial catalog route and legacy plans route remain registered", () => {
  const app = readSource("app/App.tsx");
  const nav = readSource("layout/nav.ts");
  const registry = readSource("modules/moduleRegistry.ts");

  assert.ok(app.includes('path="/platform/commercial-catalog"'));
  assert.ok(app.includes("PlatformAdminGate"));
  assert.ok(app.includes('path="/platform/plans"'));

  assert.ok(nav.includes('key: "platform-commercial-catalog"'));
  assert.ok(nav.includes('path: "/platform/commercial-catalog"'));
  assert.ok(nav.includes('label: "Commercial Catalog"'));
  assert.ok(nav.includes('key: "platform-plans"'));
  assert.ok(nav.includes('path: "/platform/plans"'));

  assert.ok(registry.includes('path === "/platform/commercial-catalog"'));
  assert.ok(registry.includes("PLATFORM_ADMIN"));
  assert.ok(registry.includes('path === "/platform/plans"'));
});

test("relationship dialogs are contextual, searchable, sticky, and business-name first", () => {
  const dialog = readSource("pages/platform/CommercialCatalogRelationshipDialog.tsx");

  assert.ok(dialog.includes('title: "Assign Modules"'));
  assert.ok(dialog.includes('title: "Assign Capabilities"'));
  assert.ok(dialog.includes('title: "Assign Features"'));
  assert.ok(dialog.includes('title: "Configure Limit Increments"'));
  assert.ok(dialog.includes('Select the application modules included in this capability.'));
  assert.ok(dialog.includes('Code: {header.parentCode}'));
  assert.ok(dialog.includes('Code: {item.code}'));
  assert.ok(dialog.includes('autoFocus'));
  assert.ok(dialog.includes('selectedCountLabel'));
  assert.ok(dialog.includes('placeholder={copy.placeholder}'));
  assert.ok(dialog.includes('height: "min(90vh, 920px)"'));
  assert.ok(dialog.includes('borderTop: 1'));
  assert.ok(dialog.includes('Save Changes'));
  assert.ok(dialog.includes('Discard relationship changes?'));
  assert.ok(dialog.includes('Keep Editing'));
  assert.ok(dialog.includes('Discard Changes'));
  assert.ok(dialog.includes('ButtonBase'));
  assert.ok(dialog.includes('onClick={() => {'));
  assert.ok(dialog.includes('onClick={(event) => event.stopPropagation()}'));
  assert.ok(!dialog.includes('window.confirm'));
  assert.ok(!dialog.includes('confirm('));
  assert.ok(!dialog.includes('alert('));
});

test("module grouping and other fallback are implemented for relationship selection", () => {
  const dialog = readSource("pages/platform/CommercialCatalogRelationshipDialog.tsx");

  assert.ok(dialog.includes('Core Clinical'));
  assert.ok(dialog.includes('Operations'));
  assert.ok(dialog.includes('Laboratory'));
  assert.ok(dialog.includes('Pharmacy'));
  assert.ok(dialog.includes('AI'));
  assert.ok(dialog.includes('Engage'));
  assert.ok(dialog.includes('Other'));
  assert.ok(dialog.includes('groupLabel(code: string)'));
  assert.ok(dialog.includes('selectedIdsEqual'));
  assert.ok(dialog.includes('includesSearch'));
  assert.ok(dialog.includes('No modules match your search.'));
  assert.ok(dialog.includes('No capabilities match your search.'));
  assert.ok(dialog.includes('No features match your search.'));
  assert.ok(dialog.includes('No limits match your search.'));
  assert.ok(dialog.includes('+${more} more'));
});

test("main commercial catalog tables show counts, previews, and no uuid primary labels", () => {
  const page = readSource("pages/platform/CommercialCatalogPage.tsx");

  assert.ok(page.includes('moduleCount'));
  assert.ok(page.includes('capabilityCount'));
  assert.ok(page.includes('featureCount'));
  assert.ok(page.includes('limitIncrementCount'));
  assert.ok(page.includes('Assigned relationships'));
  assert.ok(page.includes('Preview'));
  assert.ok(page.includes('Hide Preview'));
  assert.ok(page.includes('Code: {row.code}'));
  assert.ok(page.includes('row.name'));
  assert.ok(page.includes('row.code'));
  assert.ok(!page.includes('row.id}</TableCell>'));
});

test("commercial catalog save and retire feedback uses snackbar and accessible confirmation", () => {
  const page = readSource("pages/platform/CommercialCatalogPage.tsx");

  assert.ok(page.includes('Snackbar'));
  assert.ok(page.includes('Catalog updated.'));
  assert.ok(page.includes('Catalog item retired.'));
  assert.ok(page.includes('Retire catalog item'));
  assert.ok(page.includes('Retiring keeps the record for historical use but prevents it from being newly associated.'));
  assert.ok(!page.includes('window.confirm'));
  assert.ok(!page.includes('confirm('));
  assert.ok(!page.includes('alert('));
});
