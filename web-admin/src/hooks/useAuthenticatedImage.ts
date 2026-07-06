import * as React from "react";

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

export function useAuthenticatedImage(url: string | null | undefined): UseAuthenticatedImageResult {
  const auth = useAuth();
  const [objectUrl, setObjectUrl] = React.useState<string | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelled = false;

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

      if (!auth.accessToken || !auth.tenantId) {
        setLoading(false);
        setError("Missing authentication context for image request.");
        return;
      }

      setLoading(true);
      try {
        const blob = await fetchAuthenticatedBlob(nextUrl, {
          token: auth.accessToken,
          tenantId: auth.tenantId,
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
      setObjectUrl((current) => {
        revokeObjectUrl(current);
        return null;
      });
    };
  }, [auth.accessToken, auth.tenantId, url]);

  return { objectUrl, loading, error };
}

