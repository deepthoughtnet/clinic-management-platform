import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();

function read(relPath) {
  return fs.readFileSync(path.join(root, relPath), "utf8");
}

const discovery = read("src/components/DiscoveryComponents.tsx");
const shared = read("src/components/discovery/ProviderCardMedia.tsx");
const styles = read("src/styles.css");

assert.ok(discovery.includes("HomeProviderCard"));
assert.ok(discovery.includes('providerType="clinic"'));
assert.ok(discovery.includes('providerType="hospital"'));
assert.ok(discovery.includes('context="HOME_BANNER"'));
assert.ok(shared.includes("resolvedCoverUrl"));
assert.ok(shared.includes("`${displayName} cover`"));
assert.ok(discovery.includes("clinic.clinicDisplayName"));
assert.ok(discovery.includes("clinic.coverUrl"));
assert.ok(discovery.includes("clinic.logoUrl"));
assert.ok(discovery.includes('clinic.summary?.trim() || clinic.subtitle?.trim() || "Clinic profile published for Discover"'));
assert.ok(discovery.includes('clinic.availableToday ? "Available today" : "Appointment entry available"'));
assert.ok(discovery.includes('hospital.summary?.trim() || "Hospital profile published for Discover"'));
assert.ok(discovery.includes("hospital.departments.slice(0, 3)"));
assert.ok(discovery.includes('hospital.emergencyAvailable ? "Emergency available" : "Review services"'));
assert.ok(discovery.includes("Book appointment"));
assert.ok(discovery.includes("View clinic"));
assert.ok(discovery.includes("View hospital"));
assert.ok(discovery.includes("Demo clinic"));
assert.ok(discovery.includes("Demo hospital"));
assert.ok(discovery.includes("line-clamp-3"));
assert.ok(!discovery.includes("clinic-home-card__header"));
assert.ok(!discovery.includes("clinic-home-card__copy"));
assert.ok(!discovery.includes("clinic-home-card__chips"));
assert.ok(!discovery.includes("ArrowForwardRounded"));

assert.ok(styles.includes(".home-provider-card,"));
assert.ok(styles.includes(".home-provider-card__body"));
assert.ok(styles.includes(".home-provider-card__heading"));
assert.ok(styles.includes(".home-provider-card__summary"));
assert.ok(styles.includes(".home-provider-card__chip-row"));
assert.ok(styles.includes(".home-provider-card__chip-row--status"));
assert.ok(styles.includes("min-height: 100%"));
assert.ok(styles.includes("padding: 18px 20px 16px"));
assert.ok(styles.includes("min-height: 34px"));
assert.ok(styles.includes(".directory-action-row"));
