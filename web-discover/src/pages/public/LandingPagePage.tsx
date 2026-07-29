import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { loadPublicLandingPage, type PublicLandingPageResponse } from "../../api/providerLandingPage";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { LandingPageRenderer } from "../../components/landing/LandingPageRenderer";

export function LandingPagePage({ param }: { param: "clinicSlug" | "hospitalSlug" | "doctorSlug" }) {
  const params = useParams<Record<string, string>>();
  const slug = params[param] || "";
  const [page, setPage] = useState<PublicLandingPageResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!slug) {
      setPage(null);
      return;
    }
    setError(null);
    loadPublicLandingPage(slug)
      .then(setPage)
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load the landing page.");
        setPage(null);
      });
  }, [slug]);

  if (error && !page) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="Landing page unavailable"
          description={error}
          primaryAction="Browse providers"
          primaryTo="/discover/clinics"
        />
      </section>
    );
  }

  if (!page) {
    return (
      <section className="page-section">
        <div className="provider-dashboard-skeleton" role="status" aria-label="Loading landing page">
          <span />
          <span />
          <span />
        </div>
      </section>
    );
  }

  return <LandingPageRenderer page={page} />;
}
