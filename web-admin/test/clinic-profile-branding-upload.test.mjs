import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function repoRoot() {
  return fs.existsSync(path.join(process.cwd(), "web-admin", "src")) ? process.cwd() : path.join(process.cwd(), "..");
}

function readWebAdminSource(relPath) {
  return fs.readFileSync(path.join(repoRoot(), "web-admin", "src", ...relPath.split("/")), "utf8");
}

test("clinic profile branding logo workflow is wired to the hidden file input", () => {
  const source = readWebAdminSource("pages/settings/ClinicProfilePage.tsx");

  assert.ok(source.includes('const triggerLogoPicker = React.useCallback(() => {'));
  assert.ok(source.includes('logoInputRef.current.value = "";'));
  assert.ok(source.includes('logoInputRef.current.click();'));
  assert.ok(source.includes('const handleLogoPanelKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {'));
  assert.ok(source.includes('if (event.key === "Enter" || event.key === " ")'));
  assert.ok(source.includes('role={canEdit && !logoBusy ? "button" : undefined}'));
  assert.ok(source.includes('tabIndex={canEdit && !logoBusy ? 0 : -1}'));
  assert.ok(source.includes('aria-label={templateForm.clinicLogoDocumentId ? "Replace clinic logo" : "Upload clinic logo"}'));
  assert.ok(source.includes('onClick={triggerLogoPicker}'));
  assert.ok(source.includes('onKeyDown={handleLogoPanelKeyDown}'));
  assert.ok(source.includes('accept="image/png,image/jpeg,image/webp"'));
  assert.ok(source.includes('hidden'));
  assert.ok(source.includes('aria-hidden="true"'));
  assert.ok(source.includes('{templateForm.clinicLogoDocumentId ? "Replace Logo" : "Upload Logo"}'));
  assert.ok(source.includes('<Button type="button" variant="outlined" disabled={!canEdit || saving || logoBusy} onClick={triggerLogoPicker}>'));
  assert.ok(source.includes('useAuthenticatedImage(templateForm.logoUrl)'));
  assert.ok(source.includes('logoUrl: string | null;'));
  assert.ok(source.includes('logoUrl: template.logoUrl || null,'));
  assert.ok(source.includes('Logo preview could not be loaded.'));
  assert.ok(source.includes('event.target.value = "";'));
  assert.ok(!source.includes('label="Logo document ID"'));
});

test("clinic profile branding upload API stays multipart and uses the file field", () => {
  const source = readWebAdminSource("api/clinicApi.ts");

  assert.ok(source.includes('export async function uploadPrescriptionTemplateLogo(token: string, tenantId: string, file: File)'));
  assert.ok(source.includes('const formData = new FormData();'));
  assert.ok(source.includes('formData.append("file", file);'));
  assert.ok(source.includes('httpPostForm<PrescriptionTemplateConfig>("/api/settings/prescription-template/logo", formData, { token, tenantId })'));
  assert.ok(source.includes('logoUrl: string | null;'));
});
