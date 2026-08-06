export type ProviderConsentPresentation = {
  tone: "warning" | "success" | "neutral";
  title: string;
  message: string;
  visible: boolean;
  isBlocked: boolean;
};

export type ProviderConsentPresentationInput = {
  tenantConsentStatus?: string | null;
  submissionEligible?: boolean | null;
  submissionBlockers?: readonly string[] | null;
  contentStatus?: string | null;
  readinessStatus?: string | null;
};

/** Consent copy is intentionally keyed only by the current backend consent state. */
export function getProviderConsentPresentation({
  tenantConsentStatus,
  submissionEligible,
  contentStatus,
  readinessStatus,
}: ProviderConsentPresentationInput): ProviderConsentPresentation {
  const status = tenantConsentStatus?.trim().toUpperCase();

  if (status === "DISABLED") {
    return {
      tone: "warning",
      title: "Discover participation required",
      message: "Healthcare tenant consent is currently disabled. You can continue preparing the draft, but submission remains unavailable.",
      visible: true,
      isBlocked: true,
    };
  }

  if (status === "ENABLED") {
    return {
      tone: "success",
      title: submissionEligible ? "Ready to submit for Platform Review" : "Discover participation enabled",
      message: submissionEligible
        ? "Your profile is ready to submit for Platform Review."
        : contentStatus === "READY_FOR_REVIEW" && readinessStatus === "READY"
          ? "Discover participation is enabled. Submission is not currently available."
          : "Discover participation is enabled. Complete the remaining profile requirements before submitting.",
      visible: false,
      isBlocked: false,
    };
  }

  return {
    tone: "neutral",
    title: "Discover participation status unavailable",
    message: "We are checking whether the clinic has enabled Discover participation.",
    visible: true,
    isBlocked: false,
  };
}
