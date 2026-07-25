import type { CommercialSubscriptionSummary, CommercialSubscriptionStatus } from "../../api/clinicApi";

const STATUS_LABELS: Record<CommercialSubscriptionStatus | "UNKNOWN", string> = {
  DRAFT: "Draft assignment",
  ACTIVE: "Active",
  SCHEDULED: "Scheduled",
  PAUSED: "Paused",
  EXPIRED: "Expired",
  CANCELLED: "Cancelled",
  SUPERSEDED: "Superseded",
  UNKNOWN: "Unknown",
};

const STATUS_PRIORITY: Record<CommercialSubscriptionStatus | "UNKNOWN", number> = {
  ACTIVE: 0,
  SCHEDULED: 1,
  PAUSED: 2,
  DRAFT: 3,
  EXPIRED: 4,
  CANCELLED: 5,
  SUPERSEDED: 6,
  UNKNOWN: 7,
};

export function formatCommercialDate(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(parsed);
}

export function formatCommercialDateTime(value: string | null | undefined) {
  if (!value) return "—";
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? "—" : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(parsed);
}

export function subscriptionStatusLabel(status: CommercialSubscriptionStatus | string | null | undefined) {
  if (!status) return STATUS_LABELS.UNKNOWN;
  return STATUS_LABELS[status as CommercialSubscriptionStatus] || String(status).replaceAll("_", " ").toLowerCase().replace(/^\w/, (match) => match.toUpperCase());
}

export function subscriptionSummaryTitle(subscription: CommercialSubscriptionSummary | null | undefined) {
  if (!subscription) return "No commercial subscription";
  return subscription.displayName?.trim() || subscription.planTemplateName;
}

export function subscriptionSummaryLine(subscription: CommercialSubscriptionSummary | null | undefined) {
  if (!subscription) return "No commercial subscription";
  const status = subscriptionStatusLabel(subscription.subscriptionStatus);
  const dateLabel = subscription.subscriptionStatus === "SCHEDULED"
    ? `Effective ${formatCommercialDate(subscription.startDate)}`
    : subscription.subscriptionStatus === "ACTIVE"
      ? `Active since ${formatCommercialDate(subscription.startDate)}`
      : subscription.subscriptionStatus === "PAUSED"
        ? `Paused since ${formatCommercialDate(subscription.startDate)}`
        : `Start ${formatCommercialDate(subscription.startDate)}`;
  return `Version ${subscription.publishedVersionNumber} · ${status} · ${dateLabel}`;
}

export function subscriptionSummaryMeta(subscription: CommercialSubscriptionSummary | null | undefined) {
  if (!subscription) return "No commercial subscription";
  return `${subscription.planTemplateName} · ${subscription.publishedVersionLabel}`;
}

export function pickCurrentCommercialSubscription(subscriptions: CommercialSubscriptionSummary[] | null | undefined, tenantId: string | null | undefined) {
  if (!subscriptions || !tenantId) return null;
  const matching = subscriptions.filter((subscription) => subscription.tenantId === tenantId);
  if (matching.length === 0) return null;
  return [...matching].sort((left, right) => {
    const priorityLeft = STATUS_PRIORITY[left.subscriptionStatus as CommercialSubscriptionStatus | "UNKNOWN"] ?? STATUS_PRIORITY.UNKNOWN;
    const priorityRight = STATUS_PRIORITY[right.subscriptionStatus as CommercialSubscriptionStatus | "UNKNOWN"] ?? STATUS_PRIORITY.UNKNOWN;
    if (priorityLeft !== priorityRight) {
      return priorityLeft - priorityRight;
    }
    return new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
  })[0];
}

export function formatCommercialPlanVersionSummary(version: {
  versionNumber: number;
  publishedAt: string;
  publishedBy: string | null;
  capabilityCount: number;
  moduleCount: number;
  featureCount: number;
  limitCount: number;
  addonCount: number;
}) {
  const parts = [
    `Version ${version.versionNumber}`,
    `Published ${formatCommercialDate(version.publishedAt)}`,
    version.publishedBy ? `Published by ${version.publishedBy}` : null,
    `${version.capabilityCount} capabilities`,
    `${version.moduleCount} modules`,
    `${version.featureCount} features`,
    `${version.limitCount} limits`,
    `${version.addonCount} add-ons`,
  ].filter(Boolean);
  return parts.join(" · ");
}

