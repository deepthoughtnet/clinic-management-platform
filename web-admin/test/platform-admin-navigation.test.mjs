import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("platform admin navigation groups technical tools by purpose and removes them from clinic administration", () => {
  const nav = readSource("layout/nav.ts");
  const sidebar = readSource("layout/SidebarNav.tsx");
  const app = readSource("app/App.tsx");
  const help = readSource("shared/components/help/helpPageRegistry.ts");
  const registry = readSource("modules/moduleRegistry.ts");

  assert.ok(nav.includes('label: "Platform Administration"'));
  assert.ok(nav.includes('key: "platform-integrations"'));
  assert.ok(nav.includes('label: "Provider & Integrations"'));
  assert.ok(nav.includes('path: "/platform/integrations"'));
  assert.ok(nav.includes('label: "AI Operations"'));
  assert.ok(nav.includes('path: "/platform/ai-ops"'));
  assert.ok(nav.includes('path: "/platform/ai-reasoning-console"'));
  assert.ok(nav.includes('path: "/platform/realtime-ai"'));
  assert.ok(nav.includes('path: "/platform/voice-test"'));
  assert.ok(nav.includes('label: "Platform Operations"'));
  assert.ok(nav.includes('path: "/platform/operations"'));
  assert.ok(nav.includes('label: "Commercial Administration"'));
  assert.ok(!nav.includes('path: "/admin/integrations"'));
  assert.ok(!nav.includes('path: "/admin/ai-ops"'));
  assert.ok(!nav.includes('path: "/admin/ai-reasoning-console"'));
  assert.ok(!nav.includes('path: "/admin/platform-ops"'));
  assert.ok(!nav.includes('path: "/admin/realtime-ai"'));
  assert.ok(!nav.includes('path: "/ai/voice-test"'));

  assert.ok(sidebar.includes('"platform-administration"'));
  assert.ok(sidebar.includes('"platform-ai-operations"'));
  assert.ok(sidebar.includes('"platform-ops"'));
  assert.ok(sidebar.includes('"voice-test"'));
  assert.ok(sidebar.includes('"realtime-ai"'));

  assert.ok(app.includes('path="/platform/integrations"'));
  assert.ok(app.includes('path="/platform/ai-ops"'));
  assert.ok(app.includes('path="/platform/ai-reasoning-console"'));
  assert.ok(app.includes('path="/platform/operations"'));
  assert.ok(app.includes('path="/platform/realtime-ai"'));
  assert.ok(app.includes('path="/platform/voice-test"'));
  assert.ok(app.includes('path="/admin/integrations"'));
  assert.ok(app.includes('to="/platform/integrations"'));
  assert.ok(app.includes('path="/admin/ai-ops"'));
  assert.ok(app.includes('to="/platform/ai-ops"'));
  assert.ok(app.includes('path="/admin/ai-reasoning-console"'));
  assert.ok(app.includes('to="/platform/ai-reasoning-console"'));
  assert.ok(app.includes('path="/admin/platform-ops"'));
  assert.ok(app.includes('to="/platform/operations"'));
  assert.ok(app.includes('path="/admin/realtime-ai"'));
  assert.ok(app.includes('to="/platform/realtime-ai"'));
  assert.ok(app.includes('path="/ai/voice-test"'));
  assert.ok(app.includes('to="/platform/voice-test"'));

  assert.ok(help.includes('path: "/platform/integrations"'));
  assert.ok(help.includes('path: "/platform/ai-ops"'));
  assert.ok(help.includes('path: "/platform/ai-reasoning-console"'));
  assert.ok(help.includes('path: "/platform/operations"'));
  assert.ok(help.includes('path: "/platform/realtime-ai"'));
  assert.ok(help.includes('path: "/platform/voice-test"'));

  assert.ok(registry.includes('defaultLandingPage: "/platform/ai-ops"'));
  assert.ok(registry.includes('routes: ["/platform/ai-ops", "/platform/realtime-ai", "/platform/ai-reasoning-console"]'));
  assert.ok(registry.includes('path === "/admin/integrations" || path === "/admin/ai-ops" || path === "/admin/ai-reasoning-console" || path === "/admin/platform-ops" || path === "/admin/realtime-ai" || path === "/ai/voice-test"'));
});
