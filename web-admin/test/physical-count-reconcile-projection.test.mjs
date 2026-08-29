import test from "node:test";
import assert from "node:assert/strict";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function normalize(value) {
  return typeof value === "string" ? value.trim() : "";
}

function isUuidLike(value) {
  return Boolean(value && UUID_PATTERN.test(value.trim()));
}

function resolvePhysicalCountActorLabel(labels, auth) {
  const normalizedLabels = labels.map((label) => normalize(label)).filter(Boolean);
  const readableLabel = normalizedLabels.find((label) => !isUuidLike(label));
  if (readableLabel) return readableLabel;
  const currentUserId = normalize(auth.appUserId);
  if (currentUserId && normalizedLabels.some((label) => label.toLowerCase() === currentUserId.toLowerCase())) {
    if (normalize(auth.username) && !isUuidLike(auth.username) && normalize(auth.username).toLowerCase() !== "guest") {
      return normalize(auth.username);
    }
    return auth.tenantRole || currentUserId;
  }
  return normalizedLabels[0] || "System";
}

function resolvePhysicalCountCreatedByLabel(session, auth) {
  const createdBy = normalize(session.audit.createdBy);
  if (createdBy) {
    if (!isUuidLike(createdBy)) return createdBy;
    const currentUserId = normalize(auth.appUserId);
    if (currentUserId && createdBy.toLowerCase() === currentUserId.toLowerCase()) {
      if (normalize(auth.username) && !isUuidLike(auth.username) && normalize(auth.username).toLowerCase() !== "guest") {
        return normalize(auth.username);
      }
      return auth.tenantRole || createdBy;
    }
    return createdBy;
  }

  const fallbackLabels = [
    session.audit.startedBy,
    session.audit.submittedBy,
    session.audit.reviewedBy,
    session.audit.postedBy,
  ].map((label) => normalize(label)).filter(Boolean);
  const readableLabel = fallbackLabels.find((label) => !isUuidLike(label));
  if (readableLabel) return readableLabel;
  const currentUserId = normalize(auth.appUserId);
  if (currentUserId && fallbackLabels.some((label) => label.toLowerCase() === currentUserId.toLowerCase())) {
    if (normalize(auth.username) && !isUuidLike(auth.username) && normalize(auth.username).toLowerCase() !== "guest") {
      return normalize(auth.username);
    }
    return auth.tenantRole || currentUserId;
  }
  return fallbackLabels[0] || "System";
}

function countedDifference(line) {
  const trimmed = normalize(line.countedQty);
  if (!trimmed) return null;
  const countedQty = Number(trimmed);
  return Number.isFinite(countedQty) ? countedQty - line.systemQty : null;
}

function collectPhysicalCountArchiveTransactions(session, transactions) {
  return transactions
    .filter((transaction) => transaction.referenceType === "PHYSICAL_COUNT" || (transaction.notes || "").includes("Source: PHYSICAL_COUNT"))
    .filter((transaction) => transaction.referenceId === session.id || transaction.businessReference === session.id)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt));
}

function isPostedPhysicalCountArchiveSession(session, transactions) {
  const normalizedStatus = normalize(session.status).toUpperCase();
  if (normalizedStatus === "POSTED" || Boolean(session.audit.postedAt)) return true;
  if (normalizedStatus !== "APPROVED") return false;
  return collectPhysicalCountArchiveTransactions(session, transactions).length > 0;
}

function projectPhysicalCountArchive(session, transactions, auth) {
  const sessionTransactions = transactions
    .filter((transaction) => transaction.referenceType === "PHYSICAL_COUNT" || (transaction.notes || "").includes("Source: PHYSICAL_COUNT"))
    .filter((transaction) => transaction.referenceId === session.id || transaction.businessReference === session.id)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt));
  const sessionLineDiffs = session.lines.map((line) => countedDifference(line));
  const movementRows = sessionTransactions.map((transaction) => {
    const beforeQty = transaction.beforeQuantity ?? 0;
    const afterQty = transaction.afterQuantity ?? beforeQty + (transaction.transactionType === "ADJUSTMENT_OUT" ? -transaction.quantity : transaction.quantity);
    const difference = afterQty - beforeQty;
    return {
      movementId: transaction.id,
      postedTimestamp: transaction.createdAt,
      postedBy: resolvePhysicalCountActorLabel([session.audit.postedBy, transaction.adjustedByName, transaction.createdBy], auth),
      difference,
    };
  });
  return {
    itemsCounted: session.lines.length,
    matched: sessionLineDiffs.filter((difference) => difference === 0).length,
    short: sessionLineDiffs.filter((difference) => difference != null && difference < 0).length,
    excess: sessionLineDiffs.filter((difference) => difference != null && difference > 0).length,
    varianceItems: sessionLineDiffs.filter((difference) => difference != null && difference !== 0).length,
    movementCount: movementRows.length,
    createdBy: resolvePhysicalCountCreatedByLabel(session, auth),
    postedBy: resolvePhysicalCountActorLabel([session.audit.postedBy, movementRows.at(-1)?.postedBy], auth),
    movementRows,
  };
}

function projectPostedPhysicalCountArchiveRows(sessions, transactions, auth) {
  return sessions
    .filter((session) => isPostedPhysicalCountArchiveSession(session, transactions))
    .map((session) => projectPhysicalCountArchive(session, transactions, auth))
    .sort((left, right) => right.postedDate.localeCompare(left.postedDate));
}

test("physical count archive preserves session totals while movement rows stay variance-only", () => {
  const session = {
    id: "rbac-pc-uat-01",
    status: "APPROVED",
    lines: [
      { countedQty: "55", systemQty: 55 },
      { countedQty: "98", systemQty: 100 },
      { countedQty: "40", systemQty: 40 },
      { countedQty: "100", systemQty: 100 },
      { countedQty: "18", systemQty: 18 },
    ],
    audit: {
      createdBy: "8d9f6b3e-1c7a-4f52-a8d1-6b7e3c9f4201",
      startedBy: null,
      submittedBy: null,
      reviewedBy: null,
      postedBy: "Inventory Supervisor",
    },
  };

  const transactions = [
    { id: "move-1", referenceType: "PHYSICAL_COUNT", referenceId: "rbac-pc-uat-01", businessReference: null, createdAt: "2026-08-29T00:00:00Z", quantity: 2, transactionType: "ADJUSTMENT_OUT", beforeQuantity: 100, afterQuantity: 98, notes: "Source: PHYSICAL_COUNT", adjustedByName: "Inventory Supervisor", createdBy: null },
  ];
  const projected = projectPostedPhysicalCountArchiveRows([session], transactions, { appUserId: "8d9f6b3e-1c7a-4f52-a8d1-6b7e3c9f4201", username: "Pharmacist User", tenantRole: "PHARMACIST" })[0];

  assert.equal(isPostedPhysicalCountArchiveSession(session, transactions), true);
  assert.equal(projected.itemsCounted, 5);
  assert.equal(projected.matched, 4);
  assert.equal(projected.short, 1);
  assert.equal(projected.excess, 0);
  assert.equal(projected.varianceItems, 1);
  assert.equal(projected.movementCount, 1);
  assert.equal(projected.createdBy, "Pharmacist User");
  assert.equal(projected.postedBy, "Inventory Supervisor");
});

test("physical count archive keeps movement count at zero when all lines match", () => {
  const session = {
    id: "session-all-matched",
    status: "POSTED",
    lines: [
      { countedQty: "10", systemQty: 10 },
      { countedQty: "22", systemQty: 22 },
    ],
    audit: {
      createdBy: "Pharmacist User",
      startedBy: "Pharmacist User",
      submittedBy: null,
      reviewedBy: null,
      postedBy: "Inventory Supervisor",
    },
  };

  const projected = projectPostedPhysicalCountArchiveRows([session], [], { appUserId: "user-1", username: "Pharmacist User", tenantRole: "PHARMACIST" })[0];

  assert.equal(projected.itemsCounted, 2);
  assert.equal(projected.matched, 2);
  assert.equal(projected.varianceItems, 0);
  assert.equal(projected.movementCount, 0);
});

test("physical count archive keeps total session size when multiple variance lines post one movement per variance", () => {
  const session = {
    id: "session-multi-variance",
    status: "APPROVED",
    lines: [
      { countedQty: "100", systemQty: 100 },
      { countedQty: "98", systemQty: 100 },
      { countedQty: "18", systemQty: 20 },
      { countedQty: "40", systemQty: 40 },
    ],
    audit: {
      createdBy: "Pharmacist User",
      startedBy: "Pharmacist User",
      submittedBy: null,
      reviewedBy: null,
      postedBy: "Inventory Supervisor",
    },
  };

  const projected = projectPhysicalCountArchive(
    session,
    [
      { id: "move-1", referenceType: "PHYSICAL_COUNT", referenceId: "session-multi-variance", businessReference: null, createdAt: "2026-08-29T00:00:00Z", quantity: 2, transactionType: "ADJUSTMENT_OUT", beforeQuantity: 100, afterQuantity: 98, notes: "Source: PHYSICAL_COUNT", adjustedByName: "Inventory Supervisor", createdBy: null },
      { id: "move-2", referenceType: "PHYSICAL_COUNT", referenceId: "session-multi-variance", businessReference: null, createdAt: "2026-08-29T00:01:00Z", quantity: 2, transactionType: "ADJUSTMENT_OUT", beforeQuantity: 20, afterQuantity: 18, notes: "Source: PHYSICAL_COUNT", adjustedByName: "Inventory Supervisor", createdBy: null },
    ],
    { appUserId: "user-1", username: "Pharmacist User", tenantRole: "PHARMACIST" },
  );

  assert.equal(projected.itemsCounted, 4);
  assert.equal(projected.matched, 2);
  assert.equal(projected.varianceItems, 2);
  assert.equal(projected.movementCount, 2);
});

test("approved but not posted physical count sessions remain excluded from the posted archive", () => {
  const session = {
    id: "session-approved-only",
    status: "APPROVED",
    lines: [
      { countedQty: "10", systemQty: 10 },
      { countedQty: "22", systemQty: 22 },
    ],
    audit: {
      createdBy: "Pharmacist User",
      startedBy: "Pharmacist User",
      submittedBy: "Pharmacist User",
      reviewedBy: "Inventory Supervisor",
      postedBy: null,
      postedAt: null,
    },
  };

  assert.equal(isPostedPhysicalCountArchiveSession(session, []), false);
  assert.equal(projectPostedPhysicalCountArchiveRows([session], [], { appUserId: "user-1", username: "Pharmacist User", tenantRole: "PHARMACIST" }).length, 0);
});
