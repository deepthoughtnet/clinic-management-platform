export function normalizeDiscoveryText(value: unknown): string;
export function matchesDiscoveryQuery(fields: Array<string | number | null | undefined>, query: string | null | undefined): boolean;
export function scoreDiscoveryLocation(
  city: string | null | undefined,
  area: string | null | undefined,
  selectedLocation: string | null | undefined,
): number;
export function discoveryEmptyMessage(options: {
  query?: string | null | undefined;
  selectedLocation?: string | null | undefined;
  defaultMessage?: string;
}): string;
