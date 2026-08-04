import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function read(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("clinic and hospital directories normalize public page responses and exit initial loading after the first response", () => {
  const api = read("api/publicCatalog.ts");
  const pages = read("pages/discovery/PublicDiscoveryPages.tsx");
  const components = read("components/directory/DirectoryComponents.tsx");

  assert.ok(api.includes("normalizePublicPageResponse"));
  assert.ok(api.includes("items = Array.isArray(response?.items) ? response.items : []"));
  assert.ok(api.includes("totalItems = Number.isFinite(response?.totalItems) ? Number(response?.totalItems) : items.length"));
  assert.ok(pages.includes("const requestedPagesRef = useRef<Set<number>>(new Set());"));
  assert.ok(pages.includes("const loadingInitial = !pages[0] && !initialError;"));
  assert.ok(pages.includes("!pages[page] && !requestedPagesRef.current.has(page)"));
  assert.ok(pages.includes("[pageSize, paramsKey, path, resolvedParams, retryNonce, visiblePageCount]"));
  assert.ok(pages.includes("loading={clinicDirectory.loadingInitial}"));
  assert.ok(pages.includes("loading={hospitalDirectory.loadingInitial}"));
  assert.ok(pages.includes('active={new Set(getBooleanParam(state.searchParams, "availableToday") ? ["availableToday"] : [])}'));
  assert.ok(components.includes("sanitizeClinicSpecialities"));
  assert.ok(components.includes('filter((value) => value && value.toLowerCase() !== normalizedFallback)'));
  assert.ok(pages.includes('loadMoreLabel="Load more clinics"'));
  assert.ok(pages.includes('loadMoreLabel="Load more hospitals"'));
});
