export declare function normalizeDiscoveryText(value: unknown): string;
export declare function dedupeByExplicitKey<T>(
  items: T[],
  keySelector: (item: T, index: number) => string | null | undefined,
): T[];
export declare function resolveTelHref(phone: string | null | undefined): string | null;
export declare function normalizePublicClinicDisplayName(value: string | null | undefined): string | null;
