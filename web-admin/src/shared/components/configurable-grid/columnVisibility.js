export function createDefaultVisibleColumnIds(columns) {
  const visible = [];
  for (const column of columns) {
    if (column.mandatory || column.defaultVisible !== false) {
      visible.push(column.id);
    }
  }
  return visible;
}

export function normalizeVisibleColumnIds(columns, visibleColumnIds) {
  const visibleSet = new Set(visibleColumnIds && visibleColumnIds.length ? visibleColumnIds : createDefaultVisibleColumnIds(columns));
  const normalized = [];
  for (const column of columns) {
    if (column.mandatory || visibleSet.has(column.id)) {
      normalized.push(column.id);
    }
  }
  return normalized;
}

export function toggleColumnVisibility(columns, visibleColumnIds, columnId) {
  const column = columns.find((item) => item.id === columnId);
  if (!column || column.mandatory) {
    return normalizeVisibleColumnIds(columns, visibleColumnIds);
  }
  const current = new Set(normalizeVisibleColumnIds(columns, visibleColumnIds));
  if (current.has(columnId)) {
    current.delete(columnId);
  } else {
    current.add(columnId);
  }
  return normalizeVisibleColumnIds(columns, [...current]);
}

export function readStoredVisibleColumnIds(storage, storageKey) {
  const raw = storage.getItem(storageKey);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    return parsed.filter((value) => typeof value === "string");
  } catch {
    return null;
  }
}

export function writeStoredVisibleColumnIds(storage, storageKey, visibleColumnIds) {
  storage.setItem(storageKey, JSON.stringify(visibleColumnIds));
}

export function clearStoredVisibleColumnIds(storage, storageKey) {
  storage.removeItem(storageKey);
}

export function getVisibleColumns(columns, visibleColumnIds) {
  const visibleSet = new Set(visibleColumnIds);
  return columns.filter((column) => visibleSet.has(column.id));
}
