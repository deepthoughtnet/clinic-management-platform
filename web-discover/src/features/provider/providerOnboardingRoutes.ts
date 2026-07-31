export function providerOnboardingStepRoute(step: string) {
  const normalized = step.toUpperCase();
  if (normalized.includes("ACCOUNT")) return "account";
  if (normalized.includes("PROFILE")) return "organisation";
  if (normalized.includes("ORGANISATION")) return "organisation";
  if (normalized.includes("PROFESSIONAL")) return "professional";
  if (normalized.includes("DETAILS")) return "professional";
  if (normalized.includes("SERVICES")) return "services";
  if (normalized.includes("LOCATION")) return "locations";
  if (normalized.includes("BRANDING")) return "branding";
  if (normalized.includes("DOCUMENT")) return "branding";
  if (normalized.includes("PREVIEW")) return "preview";
  return "submit";
}
