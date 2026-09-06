export const CARE_PROFILE_FALLBACK_PATH = "/patient/doctors";

export function buildCareProfileBackState(currentPath) {
  return { careBackTo: currentPath };
}

export function hasCareProfileBackState(value) {
  return Boolean(
    value
    && typeof value === "object"
    && "careBackTo" in value
    && typeof value.careBackTo === "string"
    && value.careBackTo.trim(),
  );
}

export function resolveCareProfileBackTarget(value) {
  if (!hasCareProfileBackState(value)) {
    return CARE_PROFILE_FALLBACK_PATH;
  }
  const nextTarget = value.careBackTo.trim();
  return nextTarget.startsWith("/patient/") ? nextTarget : CARE_PROFILE_FALLBACK_PATH;
}

export function resolveCareProfileFallbackPath() {
  return CARE_PROFILE_FALLBACK_PATH;
}
