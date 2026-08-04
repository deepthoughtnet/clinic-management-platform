import * as React from "react";
import { fetchProviderDocumentBlob } from "../api/providerOnboarding";

type UseAuthenticatedImageResult = {
  objectUrl: string | null;
  loading: boolean;
  error: string | null;
};

type UseAuthenticatedImageOptions = {
  token?: string | null;
};

function revokeObjectUrl(url: string | null) {
  if (url?.startsWith("blob:")) {
    URL.revokeObjectURL(url);
  }
}

function isPublicApiImage(url: string) {
  try {
    const resolved = new URL(url, window.location.origin);
    return resolved.pathname.startsWith("/api/public/");
  } catch {
    return false;
  }
}

export function useAuthenticatedImage(url: string | null | undefined, options: UseAuthenticatedImageOptions = {}): UseAuthenticatedImageResult {
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

      if (isPublicApiImage(nextUrl)) {
        setObjectUrl(nextUrl);
        setLoading(false);
        return;
      }

      if (!options.token) {
        try {
          setLoading(true);
          const response = await fetch(nextUrl, {
            method: "GET",
            signal: abortController.signal,
            credentials: "include",
            headers: {
              Accept: "application/octet-stream,image/*,*/*",
            },
          });
          if (!response.ok) {
            throw new Error(`Request failed with status ${response.status}`);
          }
          const blob = await response.blob();
          if (!blob.size) {
            throw new Error("Image response is empty.");
          }
          const nextObjectUrl = URL.createObjectURL(blob);
          if (cancelled) {
            revokeObjectUrl(nextObjectUrl);
            return;
          }
          setObjectUrl(nextObjectUrl);
          setLoading(false);
          return;
        } catch (err) {
          if (!cancelled) {
            setLoading(false);
            setError(err instanceof Error ? err.message : "Failed to load image.");
            setObjectUrl(null);
          }
          return;
        }
      }

      setLoading(true);
      try {
        const blob = await fetchProviderDocumentBlob(nextUrl, options.token, abortController.signal);
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
  }, [options.token, url]);

  return { objectUrl, loading, error };
}
