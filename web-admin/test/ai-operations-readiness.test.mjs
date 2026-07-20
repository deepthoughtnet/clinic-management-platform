import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

function readSource(relPath) {
  return fs.readFileSync(path.join(process.cwd(), "src", ...relPath.split("/")), "utf8");
}

test("ai operations names the AI Calls dispatch worker precisely", () => {
  const aiOpsSource = readSource("products/carepilot/ai-operations/AiOperationsPage.tsx");
  const aiCallsSource = readSource("products/carepilot/ai-calls/AiCallsPage.tsx");
  const apiSource = readSource("api/clinicApi.ts");

  assert.ok(apiSource.includes("CarePilotAiCallSchedulerHealth"));
  assert.ok(apiSource.includes("workerLabel: string;"));
  assert.ok(aiOpsSource.includes('overview?.schedulerHealth?.workerLabel || "AI Calls Dispatch Worker"'));
  assert.ok(aiOpsSource.includes("Disabled means queued AI calls will not be dispatched automatically."));
  assert.ok(aiOpsSource.includes("Manual calls, live conversations, and receptionist work queues remain available when permitted."));
  assert.ok(aiCallsSource.includes('schedulerHealth?.workerLabel || "AI Calls Dispatch Worker"'));
  assert.ok(aiCallsSource.includes("Disabled means queued AI calls will not be dispatched automatically."));
  assert.ok(aiCallsSource.includes('(schedulerHealth?.workerLabel || "AI Calls Dispatch Worker")} enabled: {String(schedulerHealth?.enabled ?? false)}'));
  assert.ok(!aiOpsSource.includes("Scheduler: "));
});
