import test from "node:test";
import assert from "node:assert/strict";

import {
  createDefaultVisibleColumnIds,
  getVisibleColumns,
  normalizeVisibleColumnIds,
  readStoredVisibleColumnIds,
  toggleColumnVisibility,
  writeStoredVisibleColumnIds,
} from "../src/shared/components/configurable-grid/columnVisibility.js";

const columns = [
  { id: "name", label: "Name", mandatory: true, defaultVisible: true },
  { id: "code", label: "Code", defaultVisible: false },
  { id: "type", label: "Type", defaultVisible: true },
  { id: "price", label: "Price", defaultVisible: true },
  { id: "actions", label: "Actions", mandatory: true, defaultVisible: true },
];

function createMemoryStorage() {
  const store = new Map();
  return {
    getItem(key) {
      return store.has(key) ? store.get(key) : null;
    },
    setItem(key, value) {
      store.set(key, value);
    },
    removeItem(key) {
      store.delete(key);
    },
  };
}

test("default visible columns exclude optional hidden columns", () => {
  assert.deepEqual(createDefaultVisibleColumnIds(columns), ["name", "type", "price", "actions"]);
});

test("mandatory columns cannot be hidden", () => {
  assert.deepEqual(normalizeVisibleColumnIds(columns, ["code", "type"]), ["name", "code", "type", "actions"]);
  assert.deepEqual(normalizeVisibleColumnIds(columns, ["code"]), ["name", "code", "actions"]);
  assert.deepEqual(toggleColumnVisibility(columns, ["name", "type", "actions"], "name"), ["name", "type", "actions"]);
});

test("optional columns can be toggled on and off", () => {
  assert.deepEqual(toggleColumnVisibility(columns, ["name", "type", "actions"], "code"), ["name", "code", "type", "actions"]);
  assert.deepEqual(toggleColumnVisibility(columns, ["name", "code", "type", "actions"], "code"), ["name", "type", "actions"]);
});

test("selected columns persist in storage", () => {
  const storage = createMemoryStorage();
  writeStoredVisibleColumnIds(storage, "medicine.columns", ["name", "price", "actions"]);
  assert.deepEqual(readStoredVisibleColumnIds(storage, "medicine.columns"), ["name", "price", "actions"]);
});

test("visible columns filter rows without reserving hidden columns", () => {
  assert.deepEqual(getVisibleColumns(columns, ["name", "price", "actions"]).map((column) => column.id), ["name", "price", "actions"]);
});
