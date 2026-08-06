import * as React from "react";

import { fetchPlatformPublicProfileReviewMedia } from "../api/clinicApi";
import { fetchAuthenticatedBlob } from "../api/restClient";
import { useAuth } from "../auth/useAuth";

type UseAuthenticatedImageResult = {
  objectUrl: string | null;
  loading: boolean;
  error: string | null;
};

function revokeObjectUrl(url: string | null) {
  if (url?.startsWith("blob:")) {
    URL.revokeObjectURL(url);
  }
}

const PLATFORM_PUBLIC_PROFILE_REVIEW_MEDIA_RE = /^\/api\/platform\/provider-connections\/public-profile-reviews\/([^/]+)\/media\/([^/]+)\/content$/;

function parsePlatformPublicProfileReviewMediaPath(url: string) {
  const match = url.match(PLATFORM_PUBLIC_PROFILE_REVIEW_MEDIA_RE);
  if (!match) {
    return null;
  }
  return {
    submissionReference: decodeURIComponent(match[1]),
    mediaReference: decodeURIComponent(match[2]),
  };
}

export function useAuthenticatedImage(url: string | null | undefined): UseAuthenticatedImageResult {
  const auth = useAuth();
  const [objectUrl, setObjectUrl] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;
    const abortController = new AbortController();

    async function load() {
      const nextUrl = url?.trim() || null;
      setError(null);

      setObjectUrl((current) => {
        if (current && current !== nextUrl) {
          revokeObjectUrl(current);
        }
        return null;
      });

      if (!nextUrl) {
        setLoading(false);
        return;
      }

      if (!nextUrl.startsWith("/api/")) {
        setObjectUrl(nextUrl);
        setLoading(false);
        return;
      }

      const platformReviewMedia = parsePlatformPublicProfileReviewMediaPath(nextUrl);

      if (!auth.accessToken || (!auth.tenantId && !platformReviewMedia)) {
        setLoading(false);
        setError("Missing authentication context for image request.");
        return;
      }

      setLoading(true);
      try {
        const blob = platformReviewMedia
          ? await fetchPlatformPublicProfileReviewMedia(
            auth.accessToken,
            platformReviewMedia.submissionReference,
            platformReviewMedia.mediaReference,
            abortController.signal,
          )
          : await fetchAuthenticatedBlob(nextUrl, {
            token: auth.accessToken,
            tenantId: auth.tenantId,
            signal: abortController.signal,
          });
        if (!blob.size) {
          throw new Error("Image response is empty.");
        }
        const nextObjectUrl = URL.createObjectURL(blob);
        if (cancelled) {
          revokeObjectUrl(nextObjectUrl);
          return;
        }
        setObjectUrl(nextObjectUrl);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Failed to load image.");
          setObjectUrl(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();

    return () => {
      cancelled = true;
      abortController.abort();
      setObjectUrl((current) => {
        revokeObjectUrl(current);
        return null;
      });
    };
  }, [auth.accessToken, auth.tenantId, url]);

  return { objectUrl, loading, error };
}
