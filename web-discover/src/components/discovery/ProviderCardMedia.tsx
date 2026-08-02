import { type ReactNode } from "react";
import { PublicMediaImage } from "../landing/PublicMediaImage";

export type ProviderCardMediaContext = "HOME_COMPACT" | "HOME_BANNER" | "DIRECTORY_HORIZONTAL" | "PROFILE_HERO";

export type ProviderCardMediaProviderType = "doctor" | "clinic" | "hospital";

type ProviderCardMediaProps = {
  providerType: ProviderCardMediaProviderType;
  displayName: string;
  logoUrl?: string | null;
  coverUrl?: string | null;
  context: ProviderCardMediaContext;
  fallbackInitials?: string;
  className?: string;
  loading?: "lazy" | "eager";
};

type ResolvedProviderCardMedia = {
  src: string | null;
  alt: string;
  objectFit: "contain" | "cover";
  wrapperClassName: string;
  imageClassName: string;
  fallback: ReactNode;
};

function initials(label: string) {
  return label
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

function ProviderMediaFallback({ initialsText, providerType }: { initialsText: string; providerType: ProviderCardMediaProviderType }) {
  return (
    <div className={`provider-card-media__fallback provider-card-media__fallback--${providerType}`} aria-hidden="true">
      <span>{initialsText}</span>
    </div>
  );
}

export function resolveProviderCardMedia({
  providerType,
  displayName,
  logoUrl,
  coverUrl,
  context,
  fallbackInitials,
}: Pick<ProviderCardMediaProps, "providerType" | "displayName" | "logoUrl" | "coverUrl" | "context" | "fallbackInitials">): ResolvedProviderCardMedia {
  const initialsText = fallbackInitials || initials(displayName) || providerType.slice(0, 2).toUpperCase();
  const resolvedLogoUrl = logoUrl?.trim() || null;
  const resolvedCoverUrl = coverUrl?.trim() || null;
  const useLogoFirst = context === "HOME_COMPACT";
  const src = useLogoFirst ? resolvedLogoUrl : resolvedCoverUrl ?? resolvedLogoUrl;
  const alt = useLogoFirst
    ? `${displayName} logo`
    : resolvedCoverUrl
      ? `${displayName} cover`
      : `${displayName} logo`;
  const objectFit = "contain" as const;
  const wrapperClassName = [
    "provider-card-media",
    `provider-card-media--${context.toLowerCase().replaceAll("_", "-")}`,
    `provider-card-media--${providerType}`,
  ].join(" ");
  const imageClassName = [
    "provider-card-media__image",
    `provider-card-media__image--${objectFit}`,
    `provider-card-media__image--${providerType}`,
    `provider-card-media__image--${context.toLowerCase().replaceAll("_", "-")}`,
  ].join(" ");

  return {
    src,
    alt,
    objectFit,
    wrapperClassName,
    imageClassName,
    fallback: <ProviderMediaFallback initialsText={initialsText} providerType={providerType} />,
  };
}

export function ProviderCardMedia({
  providerType,
  displayName,
  logoUrl,
  coverUrl,
  context,
  fallbackInitials,
  className,
  loading = "lazy",
}: ProviderCardMediaProps) {
  const media = resolveProviderCardMedia({ providerType, displayName, logoUrl, coverUrl, context, fallbackInitials });

  return (
    <div className={[media.wrapperClassName, className].filter(Boolean).join(" ")}>
      {media.src ? (
        <PublicMediaImage
          src={media.src}
          alt={media.alt}
          className={media.imageClassName}
          objectFit={media.objectFit}
          fallback={media.fallback}
          loading={loading}
        />
      ) : (
        media.fallback
      )}
    </div>
  );
}
