import { isUuid } from "../../api/patientPortal";

export type PatientPortalClinicContext = {
  clinicId: string | null;
  clinicCode: string;
  clinicName: string | null;
  clinicSlug: string | null;
  locked: boolean;
  source: "query" | "slug" | "tenant" | "manual" | "route" | "storage" | "dev";
  doctorId: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  tenantId: string | null;
  isResolved: boolean;
  needsClinicSelection: boolean;
  nextPath: string | null;
};

export type PatientAuthContext = {
  mobile?: string;
  clinicId?: string;
  clinicSlug?: string;
  clinicCode?: string;
  tenantId?: string;
  clinicName?: string;
  doctorId?: string;
  doctorSlug?: string;
  doctorName?: string;
  selectedSlot?: Record<string, unknown>;
  appointmentIntent?: {
    nextPath?: string;
  };
  source: "direct" | "clinic" | "doctor" | "booking";
};

export type PublicBookingContext = PatientAuthContext & {
  nextPath?: string;
};

export type PatientPortalLinkContext = {
  clinicId?: string | null;
  clinicCode?: string | null;
  clinicSlug?: string | null;
  clinicName?: string | null;
  tenantId?: string | null;
  tenantSlug?: string | null;
  doctorId?: string | null;
  doctorSlug?: string | null;
  doctorName?: string | null;
  nextPath?: string | null;
  mobile?: string | null;
  selectedSlot?: Record<string, unknown> | null;
  appointmentIntent?: {
    nextPath?: string | null;
  } | null;
};

export type PatientPortalRouteSelectedClinicState = {
  clinicId: string | null;
  clinicCode: string;
  clinicName: string | null;
  clinicSlug: string | null;
  doctorId: string | null;
  doctorSlug: string | null;
  doctorName: string | null;
  tenantId: string | null;
  tenantSlug: string | null;
  nextPath: string | null;
  mobile: string | null;
  selectedSlot: Record<string, unknown> | null;
};

export type PatientPortalRouteState = {
  selectedClinic?: PatientPortalRouteSelectedClinicState | null;
};

export const MISSING_CLINIC_CODE_MESSAGE = "Clinic context could not be resolved. Please go back and select the clinic again.";

const DEMO_CLINIC_CODE = "demo-clinic";
const DEMO_CLINIC_NAME = "Demo Clinic";
const PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY = "clinic-web-public-patient-portal-clinic-context";

export function normalizeClinicCode(value: string | null | undefined): string {
  return (value ?? "").trim();
}

export function normalizeClinicCodeFallback(value: string | null | undefined): string {
  const normalized = normalizeClinicCode(value);
  if (!normalized) {
    return "";
  }
  if (/^[a-z0-9][a-z0-9-]{0,59}$/i.test(normalized)) {
    return normalized;
  }
  try {
    const url = new URL(normalized, window.location.origin);
    const directValues = [
      url.searchParams.get("clinicCode"),
      url.searchParams.get("clinic"),
      url.searchParams.get("clinicSlug"),
      url.searchParams.get("tenant"),
      url.searchParams.get("tenantSlug"),
    ];
    for (const candidate of directValues) {
      const trimmed = normalizeClinicCode(candidate);
      if (trimmed && /^[a-z0-9][a-z0-9-]{0,59}$/i.test(trimmed)) {
        return trimmed;
      }
    }
    const pathSlug = normalizeClinicCode(url.pathname.split("/").filter(Boolean).at(-1));
    if (pathSlug && /^[a-z0-9][a-z0-9-]{0,59}$/i.test(pathSlug)) {
      return pathSlug;
    }
  } catch {
    return "";
  }
  return "";
}

function normalizeRouteSelectedClinicState(value: unknown): PatientPortalRouteSelectedClinicState | null {
  if (!value || typeof value !== "object") {
    return null;
  }
  const source = value as Record<string, unknown>;
  const selectedClinic =
    source.selectedClinic && typeof source.selectedClinic === "object"
      ? (source.selectedClinic as Record<string, unknown>)
      : source;
  const clinicIdValue = normalizeClinicCode(typeof selectedClinic.clinicId === "string" ? selectedClinic.clinicId : null);
  const clinicId = isUuid(clinicIdValue) ? clinicIdValue : null;
  const clinicCode = normalizeClinicCode(
    typeof selectedClinic.clinicCode === "string"
      ? selectedClinic.clinicCode
      : typeof selectedClinic.tenantCode === "string"
        ? selectedClinic.tenantCode
        : typeof selectedClinic.clinicSlug === "string"
          ? selectedClinic.clinicSlug
          : clinicIdValue,
  );
  const doctorSlug = normalizeClinicCode(typeof selectedClinic.doctorSlug === "string" ? selectedClinic.doctorSlug : null);
  const doctorId = normalizeClinicCode(typeof selectedClinic.doctorId === "string" ? selectedClinic.doctorId : null);
  const nextPath = normalizeNextPath(typeof selectedClinic.nextPath === "string" ? selectedClinic.nextPath : null) || null;
  const mobile = normalizeClinicCode(typeof selectedClinic.mobile === "string" ? selectedClinic.mobile : null) || null;
  const selectedSlot =
    selectedClinic.selectedSlot && typeof selectedClinic.selectedSlot === "object"
      ? (selectedClinic.selectedSlot as Record<string, unknown>)
      : null;
  if (!clinicCode && !doctorSlug && !doctorId && !nextPath && !mobile) {
    return null;
  }
  return {
    clinicId: clinicId || null,
    clinicCode,
    clinicName: normalizeClinicCode(typeof selectedClinic.clinicName === "string" ? selectedClinic.clinicName : null) || null,
    clinicSlug: normalizeClinicCode(typeof selectedClinic.clinicSlug === "string" ? selectedClinic.clinicSlug : null) || null,
    doctorId: doctorId || null,
    doctorSlug: doctorSlug || null,
    doctorName: normalizeClinicCode(typeof selectedClinic.doctorName === "string" ? selectedClinic.doctorName : null) || null,
    tenantId: normalizeClinicCode(typeof selectedClinic.tenantId === "string" ? selectedClinic.tenantId : null) || null,
    tenantSlug: normalizeClinicCode(typeof selectedClinic.tenantSlug === "string" ? selectedClinic.tenantSlug : null) || null,
    nextPath,
    mobile,
    selectedSlot,
  };
}

function readStoredClinicContext(): PatientPortalRouteSelectedClinicState | null {
  if (typeof window === "undefined") {
    return null;
  }
  for (const storage of [window.sessionStorage, window.localStorage]) {
    try {
      const raw = storage.getItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      if (!raw) {
        continue;
      }
      const parsed = JSON.parse(raw) as unknown;
      const normalized = normalizeRouteSelectedClinicState(parsed);
      if (normalized) {
        return normalized;
      }
    } catch {
      // Ignore malformed stored context.
    }
  }
  return null;
}

export function persistPatientPortalClinicContext(context: PatientPortalLinkContext | PatientPortalRouteSelectedClinicState | null) {
  if (typeof window === "undefined") {
    return;
  }
  try {
    if (!context) {
      window.sessionStorage.removeItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      window.localStorage.removeItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY);
      return;
    }
    const clinicCode = normalizeClinicCode(context.clinicCode || context.clinicSlug);
    const doctorSlug = normalizeClinicCode(context.doctorSlug);
    const doctorId = normalizeClinicCode(context.doctorId);
    const nextPath = normalizeNextPath(context.nextPath) || null;
    const mobile = normalizeClinicCode(context.mobile) || null;
    const selectedSlot = context.selectedSlot && typeof context.selectedSlot === "object"
      ? context.selectedSlot
      : null;
    if (!clinicCode && !doctorSlug && !doctorId && !nextPath && !mobile) {
      return;
    }
    const payload = JSON.stringify({
      clinicId: isUuid(normalizeClinicCode(context.clinicId)) ? normalizeClinicCode(context.clinicId) : null,
      clinicCode,
      clinicSlug: normalizeClinicCode(context.clinicSlug) || null,
      doctorId: doctorId || null,
      tenantId: isUuid(normalizeClinicCode(context.tenantId)) ? normalizeClinicCode(context.tenantId) : null,
      tenantSlug: normalizeClinicCode(context.tenantSlug) || null,
      nextPath,
      mobile,
      selectedSlot,
    });
    window.sessionStorage.setItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY, payload);
    window.localStorage.setItem(PATIENT_PORTAL_CLINIC_CONTEXT_STORAGE_KEY, payload);
  } catch {
    // Ignore storage failures.
  }
}

function normalizeNextPath(value: string | null | undefined): string | null {
  const normalized = normalizeClinicCode(value);
  if (!normalized || !normalized.startsWith("/")) {
    return null;
  }
  return normalized;
}

function resolvePatientPortalDevContext(): PatientPortalClinicContext | null {
  if (typeof window === "undefined") {
    return null;
  }
  const hostname = window.location.hostname.toLowerCase();
  if (hostname !== "localhost" && hostname !== "127.0.0.1") {
    return null;
  }
  return {
    clinicId: null,
    clinicCode: DEMO_CLINIC_CODE,
    clinicName: DEMO_CLINIC_NAME,
    clinicSlug: DEMO_CLINIC_CODE,
    locked: true,
    source: "dev",
    doctorId: null,
    doctorSlug: null,
    doctorName: null,
    tenantId: null,
    isResolved: true,
    needsClinicSelection: false,
    nextPath: null,
  };
}

export function resolvePatientPortalContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientPortalClinicContext {
  const routeSelectedClinic = normalizeRouteSelectedClinicState(routeState);
  const storedClinic = readStoredClinicContext();
  const clinicIdParam = normalizeClinicCode(searchParams.get("clinicId"));
  const clinicId = isUuid(clinicIdParam) ? clinicIdParam : null;
  const queryCode = normalizeClinicCode(searchParams.get("clinicCode"));
  const clinicCodeAlias = normalizeClinicCode(searchParams.get("clinic"));
  const querySlug = normalizeClinicCode(searchParams.get("clinicSlug"));
  const tenantIdParam = normalizeClinicCode(searchParams.get("tenantId"));
  const tenantId = isUuid(tenantIdParam) ? tenantIdParam : null;
  const tenantCode = normalizeClinicCode(searchParams.get("tenant"));
  const tenantSlug = normalizeClinicCode(searchParams.get("tenantSlug"));
  const doctorId = normalizeClinicCode(searchParams.get("doctorId"));
  const doctorSlug = normalizeClinicCode(searchParams.get("doctorSlug"));
  const doctorName = normalizeClinicCode(searchParams.get("doctorName"));
  const clinicIdAlias = !clinicId && clinicIdParam ? clinicIdParam : null;
  const clinicCode =
    routeSelectedClinic?.clinicCode
      || clinicId
      || queryCode
      || clinicCodeAlias
      || querySlug
      || clinicIdAlias
      || tenantCode
      || tenantSlug
      || storedClinic?.clinicCode
      || storedClinic?.clinicSlug
      || "";
  const clinicName = routeSelectedClinic?.clinicName
    || normalizeClinicCode(searchParams.get("clinicName"))
    || storedClinic?.clinicName
    || null;
  const resolvedDoctorId = routeSelectedClinic?.doctorId || doctorId || storedClinic?.doctorId || null;
  const resolvedDoctorSlug = routeSelectedClinic?.doctorSlug
    || doctorSlug
    || storedClinic?.doctorSlug
    || null;
  const resolvedDoctorName = routeSelectedClinic?.doctorName
    || doctorName
    || storedClinic?.doctorName
    || null;
  const nextPath = routeSelectedClinic?.nextPath
    || normalizeNextPath(searchParams.get("next"))
    || storedClinic?.nextPath
    || null;
  const clinicSlug = routeSelectedClinic?.clinicSlug
    || querySlug
    || clinicIdAlias
    || storedClinic?.clinicSlug
    || clinicCode;
  const resolvedTenantId = routeSelectedClinic?.tenantId
    || tenantId
    || storedClinic?.tenantId
    || null;
  const needsClinicSelection = Boolean((resolvedDoctorSlug || resolvedDoctorId) && !clinicCode);
  if (!clinicCode) {
    const devFallback = resolvePatientPortalDevContext();
    if (devFallback) {
      return devFallback;
    }
  }
  return {
    clinicId: routeSelectedClinic?.clinicId || clinicId || storedClinic?.clinicId || null,
    clinicCode,
    clinicName,
    clinicSlug: clinicSlug || null,
    locked: Boolean(clinicCode),
    source: routeSelectedClinic
      ? "route"
      : clinicId || clinicIdAlias || queryCode || clinicCodeAlias
        ? "query"
      : querySlug
        ? "slug"
        : tenantId || tenantCode || tenantSlug
          ? "tenant"
          : storedClinic
            ? "storage"
          : "manual",
    doctorId: resolvedDoctorId,
    doctorSlug: resolvedDoctorSlug,
    doctorName: resolvedDoctorName,
    tenantId: resolvedTenantId,
    isResolved: Boolean(clinicCode),
    needsClinicSelection,
    nextPath,
  };
}

export function resolvePatientAuthContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientAuthContext {
  const portalContext = resolvePatientPortalContext(searchParams, routeState);
  const mobile = normalizeClinicCode(searchParams.get("mobile")) || undefined;
  const appointmentNextPath = portalContext.nextPath || undefined;
  const source: PatientAuthContext["source"] = appointmentNextPath
    ? "booking"
    : portalContext.clinicCode
      ? "clinic"
      : portalContext.doctorSlug || portalContext.doctorId
        ? "doctor"
        : "direct";
  return {
    ...(mobile ? { mobile } : {}),
    ...(portalContext.clinicId ? { clinicId: portalContext.clinicId } : {}),
    ...(portalContext.clinicCode ? { clinicCode: portalContext.clinicCode } : {}),
    ...(portalContext.clinicSlug || portalContext.clinicCode ? { clinicSlug: portalContext.clinicSlug || portalContext.clinicCode } : {}),
    ...(portalContext.tenantId ? { tenantId: portalContext.tenantId } : {}),
    ...(portalContext.clinicName ? { clinicName: portalContext.clinicName } : {}),
    ...(portalContext.doctorId ? { doctorId: portalContext.doctorId } : {}),
    ...(portalContext.doctorSlug ? { doctorSlug: portalContext.doctorSlug } : {}),
    ...(portalContext.doctorName ? { doctorName: portalContext.doctorName } : {}),
    ...(appointmentNextPath ? { appointmentIntent: { nextPath: appointmentNextPath } } : {}),
    source,
  };
}

export function getPublicBookingContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PublicBookingContext {
  const portalContext = resolvePatientPortalContext(searchParams, routeState);
  const authContext = resolvePatientAuthContext(searchParams, routeState);
  return {
    ...authContext,
    ...(portalContext.nextPath ? { nextPath: portalContext.nextPath } : {}),
  };
}

export function savePublicBookingContext(context: PatientPortalLinkContext | PatientPortalRouteSelectedClinicState | null) {
  persistPatientPortalClinicContext(context);
}

export function clearPublicBookingContext() {
  persistPatientPortalClinicContext(null);
}

export function resolvePatientPortalClinicContext(
  searchParams: URLSearchParams,
  routeState?: unknown,
): PatientPortalClinicContext {
  return resolvePatientPortalContext(searchParams, routeState);
}

function appendQuery(path: string, params: Record<string, string | null | undefined>) {
  const url = new URL(path, window.location.origin);
  for (const [key, value] of Object.entries(params)) {
    const normalized = normalizeClinicCode(value);
    if (normalized) {
      url.searchParams.set(key, normalized);
    }
  }
  return `${url.pathname}${url.search}`;
}

export function patientPortalLoginPath(context?: PatientPortalLinkContext) {
  if (!context) {
    return "/patient/login";
  }
  return appendQuery("/patient/login", {
    next: context.nextPath,
  });
}

function createBookingRouteState(context?: PatientPortalLinkContext): PatientPortalRouteState | undefined {
  const clinicCode = normalizeClinicCode(context?.clinicCode || context?.clinicSlug);
  const doctorId = normalizeClinicCode(context?.doctorId);
  const doctorSlug = normalizeClinicCode(context?.doctorSlug);
  const nextPath = normalizeNextPath(context?.nextPath) || null;
  const mobile = normalizeClinicCode(context?.mobile) || null;
  const clinicIdValue = normalizeClinicCode(context?.clinicId);
  const tenantIdValue = normalizeClinicCode(context?.tenantId);
  const clinicId = isUuid(clinicIdValue) ? clinicIdValue : null;
  const tenantId = isUuid(tenantIdValue) ? tenantIdValue : null;
  if (!clinicCode && !clinicId && !tenantId && !doctorId && !doctorSlug && !nextPath && !mobile) {
    return undefined;
  }
  return {
    selectedClinic: {
      clinicId,
      clinicCode,
      clinicName: null,
      clinicSlug: normalizeClinicCode(context?.clinicSlug) || null,
      doctorId: doctorId || null,
      doctorSlug: null,
      doctorName: null,
      tenantId,
      tenantSlug: null,
      nextPath,
      mobile,
      selectedSlot: context?.selectedSlot ?? null,
    },
  };
}

export function withPatientPortalClinicContext(path: string, context?: PatientPortalLinkContext) {
  if (!context) {
    return path;
  }
  const clinicId = isUuid(context.clinicId) ? context.clinicId : null;
  const tenantId = isUuid(context.tenantId) ? context.tenantId : null;
  return appendQuery(path, {
    clinicId: clinicId || undefined,
    tenantId: !clinicId && tenantId ? tenantId : undefined,
    clinicSlug: !clinicId && !tenantId ? context.clinicSlug : undefined,
    doctorId: context.doctorId,
  });
}

export function patientPortalBookingTo(
  session: import("../../api/patientPortal").PatientPortalSession | null,
  context?: PatientPortalLinkContext,
) {
  const nextPath = withPatientPortalClinicContext("/patient/book-appointment", context);
  const bookingState = createBookingRouteState(context);

  if (session?.sessionRole === "patient") {
    return nextPath;
  }
  if (session?.sessionRole === "registration") {
    return `/patient/register?next=${encodeURIComponent(nextPath)}`;
  }
  return {
    pathname: "/patient/login",
    search: patientPortalLoginPath({ nextPath }).replace("/patient/login", ""),
    state: bookingState,
  };
}

export function patientPortalBookingPath(
  session: import("../../api/patientPortal").PatientPortalSession | null,
  context?: PatientPortalLinkContext,
) {
  const nextPath = withPatientPortalClinicContext("/patient/book-appointment", context);
  const registrationPath = `/patient/register?next=${encodeURIComponent(nextPath)}`;

  if (session?.sessionRole === "patient") {
    return nextPath;
  }
  if (session?.sessionRole === "registration") {
    return registrationPath;
  }
  return patientPortalLoginPath({ nextPath });
}
