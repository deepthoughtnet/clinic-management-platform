import * as React from "react";

import {
  createDefaultVisibleColumnIds,
  normalizeVisibleColumnIds,
  readStoredVisibleColumnIds,
  toggleColumnVisibility,
  writeStoredVisibleColumnIds,
  type ColumnDefinitionBase,
} from "./columnVisibility.js";

export function useColumnVisibility<T extends ColumnDefinitionBase>(
  columns: readonly T[],
  storageKey: string,
) {
  const defaultVisibleColumnIds = React.useMemo(() => createDefaultVisibleColumnIds(columns), [columns]);
  const [visibleColumnIds, setVisibleColumnIds] = React.useState<string[]>(defaultVisibleColumnIds);

  React.useEffect(() => {
    if (typeof window === "undefined") return;
    const stored = readStoredVisibleColumnIds(window.localStorage, storageKey);
    setVisibleColumnIds(normalizeVisibleColumnIds(columns, stored));
  }, [columns, storageKey]);

  React.useEffect(() => {
    if (typeof window === "undefined") return;
    writeStoredVisibleColumnIds(window.localStorage, storageKey, visibleColumnIds);
  }, [storageKey, visibleColumnIds]);

  const updateVisibleColumnIds = React.useCallback((nextVisibleColumnIds: readonly string[]) => {
    setVisibleColumnIds(normalizeVisibleColumnIds(columns, nextVisibleColumnIds));
  }, [columns]);

  const toggleVisibleColumnId = React.useCallback((columnId: string) => {
    setVisibleColumnIds((current) => toggleColumnVisibility(columns, current, columnId));
  }, [columns]);

  const resetVisibleColumnIds = React.useCallback(() => {
    setVisibleColumnIds(defaultVisibleColumnIds);
  }, [defaultVisibleColumnIds]);

  return {
    visibleColumnIds,
    defaultVisibleColumnIds,
    updateVisibleColumnIds,
    toggleVisibleColumnId,
    resetVisibleColumnIds,
  };
}
