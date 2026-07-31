import { type ReactNode } from "react";
import { useEffect, useState } from "react";
import { useAuthenticatedImage } from "../../hooks/useAuthenticatedImage";

export function PublicMediaImage({
  src,
  alt,
  className,
  objectFit,
  fallback,
  loading = "lazy",
  token,
}: {
  src: string | null | undefined;
  alt: string;
  className: string;
  objectFit: "cover" | "contain";
  fallback: ReactNode;
  loading?: "lazy" | "eager";
  token?: string | null;
}) {
  const { objectUrl, loading: isLoading, error } = useAuthenticatedImage(src, { token });
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    setImageError(false);
  }, [src, token]);

  if (!objectUrl || error || imageError) {
    return <>{fallback}</>;
  }

  return (
    <img
      className={className}
      src={objectUrl}
      alt={alt}
      loading={loading}
      decoding="async"
      draggable={false}
      data-object-fit={objectFit}
      aria-busy={isLoading ? "true" : undefined}
      onError={() => setImageError(true)}
    />
  );
}
