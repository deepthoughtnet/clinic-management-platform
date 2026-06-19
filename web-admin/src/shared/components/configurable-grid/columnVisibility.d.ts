import type * as React from "react";

export type ColumnVisibilityStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export type ColumnDefinitionBase = {
  id: string;
  label?: React.ReactNode;
  defaultVisible?: boolean;
  mandatory?: boolean;
};

export declare function createDefaultVisibleColumnIds(columns: readonly ColumnDefinitionBase[]): string[];
export declare function normalizeVisibleColumnIds(columns: readonly ColumnDefinitionBase[], visibleColumnIds?: readonly string[] | null): string[];
export declare function toggleColumnVisibility(columns: readonly ColumnDefinitionBase[], visibleColumnIds: readonly string[] | null | undefined, columnId: string): string[];
export declare function readStoredVisibleColumnIds(storage: ColumnVisibilityStorage, storageKey: string): string[] | null;
export declare function writeStoredVisibleColumnIds(storage: ColumnVisibilityStorage, storageKey: string, visibleColumnIds: readonly string[]): void;
export declare function clearStoredVisibleColumnIds(storage: ColumnVisibilityStorage, storageKey: string): void;
export declare function getVisibleColumns<T extends ColumnDefinitionBase>(columns: readonly T[], visibleColumnIds: readonly string[]): T[];
