export function decodePatientPortalSessionTokenPayload(token: string | null | undefined): Record<string, unknown> | null;
export function isPatientPortalSessionTokenActive(token: string | null | undefined, now?: number): boolean;
