import { type ChangeEvent, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Alert, Box, Button, Chip, Paper, Stack, TextField, Typography } from "@mui/material";
import {
  compareLandingPageVersions,
  loadLandingPage,
  publishLandingPage,
  revertLandingPage,
  updateLandingPage,
  type LandingPageResponse,
  type LandingSection,
  type LandingSnapshot,
  type LandingTheme,
} from "../../api/providerLandingPage";
import { DiscoverEmptyState } from "../../components/DiscoveryComponents";
import { LandingPageRenderer } from "../../components/landing/LandingPageRenderer";
import { DISCOVER_LANDING_PATHS, DISCOVER_ROUTES } from "../../routes";

const TOKEN_KEYS = [
  "jeevanam.discover.providerOnboardingToken.INDIVIDUAL_DOCTOR",
  "jeevanam.discover.providerOnboardingToken.CLINIC",
  "jeevanam.discover.providerOnboardingToken.HOSPITAL",
  "jeevanam.discover.providerOnboardingToken",
];

function readStoredToken() {
  for (const key of TOKEN_KEYS) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

function sortSections(sections: LandingSection[]) {
  return [...sections].sort((left, right) => left.displayOrder - right.displayOrder);
}

function snapshotFrom(templateKey: string, templateVersion: number, theme: LandingTheme, sections: LandingSection[]): LandingSnapshot {
  return { templateKey, templateVersion, theme, sections: sortSections(sections) };
}

function deviceClass(device: string) {
  switch (device) {
    case "tablet":
      return "device-frame is-tablet";
    case "mobile":
      return "device-frame is-mobile";
    default:
      return "device-frame";
  }
}

function readinessLabel(code: string) {
  return code
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function readinessDescription(code: string) {
  switch (code) {
    case "DISPLAY_NAME":
      return "Add the display name shown on Discover.";
    case "PUBLIC_LOCATION":
      return "Add the city and area that identify the practice.";
    case "PUBLIC_CONTACT":
      return "Add a public phone number for patients.";
    case "ABOUT_SECTION":
      return "Write the public about section.";
    case "COVER_PHOTO":
      return "Upload a cover image.";
    case "LOGO":
      return "Upload a logo or profile image.";
    case "AT_LEAST_ONE_SERVICE":
      return "Choose at least one service.";
    case "AT_LEAST_ONE_SPECIALITY":
      return "Choose at least one speciality.";
    case "OPENING_HOURS":
      return "Add opening hours or consultation timing details.";
    case "GALLERY_RECOMMENDED":
      return "Add a gallery image if you want richer visual presentation.";
    case "BRANDING_RECOMMENDED":
      return "Add branded media so the profile looks complete.";
    case "SLUG_PENDING":
      return "Choose an editable public slug.";
    case "NOT_YET_PUBLISHED":
      return "Submit the profile for publication when it is ready.";
    default:
      return "Review this item before publishing.";
  }
}

function readinessChecklistLabel(code: string) {
  switch (code) {
    case "DISPLAY_NAME":
      return "Clinic name";
    case "PUBLIC_LOCATION":
      return "Address";
    case "PUBLIC_CONTACT":
      return "Contact methods";
    case "ABOUT_SECTION":
      return "Description";
    case "COVER_PHOTO":
      return "Cover photo";
    case "LOGO":
      return "Logo";
    case "AT_LEAST_ONE_SERVICE":
      return "Services";
    case "AT_LEAST_ONE_SPECIALITY":
      return "Specialities";
    case "OPENING_HOURS":
      return "Timings";
    case "GALLERY_RECOMMENDED":
      return "Gallery";
    case "BRANDING_RECOMMENDED":
      return "Branding";
    case "SLUG_PENDING":
      return "Public slug";
    case "NOT_YET_PUBLISHED":
      return "Publication";
    default:
      return readinessLabel(code);
  }
}

function readableLifecycleStatus(code: string) {
  switch (code) {
    case "PROFILE_INCOMPLETE":
      return "Profile needs more information";
    case "TENANT_CONSENT_REQUIRED":
      return "Clinic has not enabled Discover publishing yet";
    case "NOT_SUBMITTED":
      return "Draft";
    case "READY":
      return "Content Ready";
    case "SUBMITTED":
      return "Submitted";
    case "UNDER_REVIEW":
      return "Under Review";
    case "CHANGES_REQUESTED":
      return "Changes requested";
    case "APPROVED":
      return "Approved";
    case "PUBLISHED":
      return "Published";
    default:
      return code.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

function publicationSummary(readiness: LandingPageResponse["publicationReadiness"]) {
  if (readiness.ready && readiness.missingFields.length === 0 && readiness.invalidFields.length === 0) {
    return "Content complete";
  }
  return "Profile needs more information";
}

function missingFieldEditorSection(code: string) {
  switch (code) {
    case "DISPLAY_NAME":
    case "ABOUT_SECTION":
      return "about";
    case "PUBLIC_LOCATION":
    case "PUBLIC_CONTACT":
      return "contact";
    case "AT_LEAST_ONE_SERVICE":
      return "services";
    case "AT_LEAST_ONE_SPECIALITY":
      return "specialities";
    case "OPENING_HOURS":
      return "timings";
    case "LOGO":
    case "COVER_PHOTO":
    case "GALLERY_RECOMMENDED":
    case "BRANDING_RECOMMENDED":
      return "media";
    case "SLUG_PENDING":
      return "seo";
    default:
      return "overview";
  }
}

function sectionDisplayLabel(key: string) {
  switch (key) {
    case "HERO":
      return "Overview";
    case "ABOUT":
      return "About";
    case "SERVICES":
      return "Services";
    case "DOCTORS":
      return "Specialities";
    case "DEPARTMENTS":
      return "Specialities";
    case "FACILITIES":
      return "Facilities";
    case "CONSULTATION_MODES":
      return "Timings";
    case "WORKING_HOURS":
      return "Timings";
    case "GALLERY":
      return "Photos";
    case "INSURANCE":
      return "Fees";
    case "AWARDS":
      return "Media";
    case "FAQ":
      return "SEO";
    case "CONTACT":
      return "Contact";
    case "CTA":
      return "Publication";
    default:
      return key.replaceAll("_", " ").replace(/\b\w/g, (char) => char.toUpperCase());
  }
}

export function ProviderLandingPagePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [token] = useState(() => readStoredToken());
  const [page, setPage] = useState<LandingPageResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [compare, setCompare] = useState<null | { addedSections: string[]; removedSections: string[]; changedSections: string[]; templateChanged: boolean; themeChanged: boolean; sectionOrderChanged: boolean }>(null);
  const tab = searchParams.get("tab") || "builder";
  const device = searchParams.get("device") || "desktop";
  const [draftTemplateKey, setDraftTemplateKey] = useState("");
  const [draftTheme, setDraftTheme] = useState<LandingTheme | null>(null);
  const [draftSections, setDraftSections] = useState<LandingSection[]>([]);
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);

  useEffect(() => {
    if (!token) {
      setPage(null);
      return;
    }
    setLoading(true);
    setError(null);
    loadLandingPage(token)
      .then((result) => {
        setPage(result);
        setDraftTemplateKey(result.draft.templateKey);
        setDraftTheme(result.draft.theme);
        setDraftSections(sortSections(result.draft.sections));
        setSelectedVersion(result.publishedVersionNumber ?? result.versions[0]?.versionNumber ?? null);
      })
      .catch((ex) => {
        setError(ex instanceof Error ? ex.message : "Could not load your landing page.");
        setPage(null);
      })
      .finally(() => setLoading(false));
  }, [token]);

  useEffect(() => {
    if (!page || page.versions.length < 2) {
      setCompare(null);
      return;
    }
    const [latest, previous] = page.versions;
    compareLandingPageVersions(token, previous.versionNumber, latest.versionNumber)
      .then(setCompare)
      .catch(() => setCompare(null));
  }, [page, token]);

  const template = useMemo(() => page?.templates.find((item) => item.templateKey === draftTemplateKey) ?? page?.templates[0] ?? null, [page, draftTemplateKey]);
  const draftSnapshot = useMemo(() => {
    if (!template || !draftTheme) {
      return null;
    }
    return snapshotFrom(draftTemplateKey || template.templateKey, template.templateVersion, draftTheme, draftSections);
  }, [draftTemplateKey, draftSections, draftTheme, template]);

  function updateSection(index: number, next: Partial<LandingSection>) {
    setDraftSections((current) => current.map((section, sectionIndex) => (sectionIndex === index ? { ...section, ...next } : section)));
  }

  function moveSection(index: number, delta: number) {
    setDraftSections((current) => {
      const next = [...current];
      const target = index + delta;
      if (target < 0 || target >= next.length) {
        return current;
      }
      const [item] = next.splice(index, 1);
      next.splice(target, 0, item);
      return next.map((section, sectionIndex) => ({ ...section, displayOrder: sectionIndex }));
    });
  }

  function switchTemplate(key: string) {
    const selected = page?.templates.find((item) => item.templateKey === key);
    if (!selected) {
      return;
    }
    setDraftTemplateKey(key);
    setDraftTheme(selected.defaultTheme);
    setDraftSections(sortSections(selected.defaultSections).map((section, index) => ({ ...section, displayOrder: index })));
  }

  async function saveDraft() {
    if (!token || !page || !draftSnapshot) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateLandingPage(token, {
        version: page.draftVersionNumber,
        templateKey: draftTemplateKey,
        theme: draftTheme,
        sections: draftSections,
      });
      setPage(updated);
      setDraftTemplateKey(updated.draft.templateKey);
      setDraftTheme(updated.draft.theme);
      setDraftSections(sortSections(updated.draft.sections));
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not save the landing page draft.");
    } finally {
      setSaving(false);
    }
  }

  async function publish() {
    if (!token) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await publishLandingPage(token);
      setPage(updated);
      setDraftTemplateKey(updated.draft.templateKey);
      setDraftTheme(updated.draft.theme);
      setDraftSections(sortSections(updated.draft.sections));
      setSelectedVersion(updated.publishedVersionNumber ?? updated.versions[0]?.versionNumber ?? null);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not publish the landing page.");
    } finally {
      setSaving(false);
    }
  }

  async function revertToPublished() {
    if (!token || !page?.publishedVersionNumber) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await revertLandingPage(token, { versionNumber: page.publishedVersionNumber });
      setPage(updated);
      setDraftTemplateKey(updated.draft.templateKey);
      setDraftTheme(updated.draft.theme);
      setDraftSections(sortSections(updated.draft.sections));
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not revert the landing page.");
    } finally {
      setSaving(false);
    }
  }

  async function chooseVersion(event: ChangeEvent<HTMLSelectElement>) {
    const value = Number(event.target.value);
    setSelectedVersion(Number.isFinite(value) ? value : null);
    if (!token || !page || !Number.isFinite(value)) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await revertLandingPage(token, { versionNumber: value });
      setPage(updated);
      setDraftTemplateKey(updated.draft.templateKey);
      setDraftTheme(updated.draft.theme);
      setDraftSections(sortSections(updated.draft.sections));
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not load that version.");
    } finally {
      setSaving(false);
    }
  }

  if (!token) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="◌"
          title="Open your provider workspace first"
          description="Landing pages are available after publication for the owning provider."
          primaryAction="Provider dashboard"
          primaryTo={DISCOVER_ROUTES.providerWorkspace.path}
          secondaryAction="List your practice"
          secondaryTo={DISCOVER_ROUTES.listPractice.path}
        />
      </section>
    );
  }

  if (error && !page) {
    return (
      <section className="page-section">
        <DiscoverEmptyState
          icon="!"
          title="We could not load your landing page"
          description={error}
          primaryAction="Try again"
          primaryHref={window.location.href}
          secondaryAction="Provider dashboard"
          secondaryTo={DISCOVER_ROUTES.providerWorkspace.path}
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

  const visibleSnapshot = draftSnapshot ?? page.draft;
  const canEdit = page.allowedActions.includes("EDIT_PUBLIC_PROFILE");
  const canSubmit = page.allowedActions.includes("SUBMIT_FOR_PUBLICATION");
  const readiness = page.publicationReadiness;
  const completionPercent = readiness.completenessPercentage;
  const statusText = page.pageMode === "PUBLIC_PROFILE_PUBLISHED"
    ? "Your public profile is published and visible in Discover."
    : page.pageMode === "PUBLIC_PROFILE_READY"
      ? "Your public profile is ready for publication."
      : "Ownership is verified. Complete the profile before submitting it for review.";
  const futurePublicPath = page.profile?.canonicalSlug
    ? (page.profile.providerType === "INDIVIDUAL_DOCTOR"
      ? DISCOVER_LANDING_PATHS.doctor(page.profile.canonicalSlug)
      : page.profile.providerType === "HOSPITAL"
        ? DISCOVER_LANDING_PATHS.hospital(page.profile.canonicalSlug)
        : DISCOVER_LANDING_PATHS.clinic(page.profile.canonicalSlug))
    : page.publicPath;
  const readinessChecklist = [
    ["OWNERSHIP", "Ownership verified", true],
    ["DRAFT", "Draft saved", Boolean(page.draftVersionNumber ?? page.draft)],
    ["DISPLAY_NAME", "Clinic name", !readiness.missingFields.includes("DISPLAY_NAME")],
    ["ABOUT_SECTION", "Description", !readiness.missingFields.includes("ABOUT_SECTION")],
    ["PUBLIC_LOCATION", "Address", !readiness.missingFields.includes("PUBLIC_LOCATION")],
    ["PUBLIC_CONTACT", "Contact methods", !readiness.missingFields.includes("PUBLIC_CONTACT")],
    ["AT_LEAST_ONE_SPECIALITY", "Specialities", !readiness.missingFields.includes("AT_LEAST_ONE_SPECIALITY")],
    ["AT_LEAST_ONE_SERVICE", "Services", !readiness.missingFields.includes("AT_LEAST_ONE_SERVICE")],
    ["OPENING_HOURS", "Timings", !readiness.missingFields.includes("OPENING_HOURS")],
    ["LOGO", "Logo", !readiness.missingFields.includes("LOGO")],
    ["COVER_PHOTO", "Cover photo", !readiness.missingFields.includes("COVER_PHOTO")],
    ["SLUG", "Public slug", Boolean(page.canonicalSlug && page.canonicalSlug.trim())],
    ["GALLERY", "Gallery", !readiness.warnings.includes("GALLERY_RECOMMENDED")],
    ["BRANDING", "Branding", !readiness.warnings.includes("BRANDING_RECOMMENDED")],
  ] as const;
  const blockingSummary = readiness.missingFields.length
    ? readiness.missingFields.map((item) => readinessDescription(item)).join(" · ")
    : readiness.invalidFields.length
      ? readiness.invalidFields.map((item) => readinessLabel(item)).join(" · ")
      : "Submission becomes available once all required items are completed.";

  return (
    <section className="page-section landing-builder-page">
      <div className="provider-dashboard-panel">
        <span className="eyebrow">Public Profile</span>
        <h1>Ownership verified</h1>
        <p>{statusText}</p>
        <p>{page.displayName}</p>
      </div>

      <div className="landing-builder-hero">
        <div>
          <span className="eyebrow">Publication setup</span>
          <h1>{page.publicPath}</h1>
          <p>{page.profile?.canonicalSlug ?? page.canonicalSlug}</p>
        </div>
        <div className="landing-builder-actions">
          <button className="secondary-button" type="button" onClick={() => void saveDraft()} disabled={saving || !canEdit}>
            Save draft
          </button>
          <Button component={Link} to={page.published ? futurePublicPath : `${DISCOVER_ROUTES.providerLandingPage.path}?tab=preview`} variant="outlined">
            {page.published ? "View Public Profile" : "Preview Draft"}
          </Button>
          <button className="primary-button" type="button" onClick={() => void publish()} disabled={saving || !canSubmit}>
            {page.published ? "Update publication" : "Submit for Review"}
          </button>
          {page.publishedVersionNumber ? (
            <button className="secondary-button" type="button" onClick={() => void revertToPublished()} disabled={saving || !canEdit}>
              Revert to published
            </button>
          ) : null}
        </div>
      </div>

      <section className="provider-dashboard-panel" aria-label="Publication readiness">
        <div className="provider-account-section-heading">
          <div>
            <span className="eyebrow">Profile completeness</span>
            <h2>{completionPercent}% complete</h2>
          </div>
          <span>{publicationSummary(readiness)}</span>
        </div>
        <div className="provider-account-summary-grid">
          <article className="provider-account-summary-card">
            <span>Publication</span>
            <strong>{page.published ? "Published" : "Draft"}</strong>
            <p>{page.published ? "View Public Profile" : "Preview Draft"}</p>
          </article>
          <article className="provider-account-summary-card">
            <span>Last ownership update</span>
            <strong>{readiness.sourceUpdatedAt ? new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(readiness.sourceUpdatedAt)) : "Not yet synchronized"}</strong>
            <p>{readableLifecycleStatus(readiness.currentStatus)}</p>
          </article>
          <article className="provider-account-summary-card">
            <span>Public URL</span>
            <strong>{futurePublicPath}</strong>
            <p>{page.published ? "Public profile" : "Draft preview"}</p>
          </article>
        </div>
        <div className="provider-account-section" style={{ marginTop: "1rem" }}>
          <label className="field-label">Profile readiness</label>
          <ul className="provider-account-detail-list" aria-label="Profile readiness checklist">
            {readinessChecklist
              .filter((item, index, current) => current.findIndex((candidate) => candidate[1] === item[1]) === index)
              .map(([key, label, complete]) => (
                <li key={key}>
                  <strong>{complete ? "✓" : "✗"} {label}</strong>
                  <div>{complete ? "Completed" : readinessDescription(key)}</div>
                </li>
              ))}
          </ul>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1.5 }}>
            {readiness.missingFields.map((item) => (
              <Chip
                key={item}
                label={readinessChecklistLabel(item)}
                component={Link}
                to={`${DISCOVER_ROUTES.providerLandingPage.path}?tab=builder#landing-section-${missingFieldEditorSection(item)}`}
                clickable
                variant="outlined"
              />
            ))}
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {blockingSummary}
          </Typography>
        </div>
      </section>

      <div className="landing-builder-layout">
        <aside className="landing-builder-sidebar">
          <div className="landing-builder-panel">
            <label>
              Template
              <select value={draftTemplateKey} onChange={(event) => switchTemplate(event.target.value)}>
                {page.templates.map((item) => (
                  <option key={item.templateKey} value={item.templateKey}>
                    {item.templateName}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Primary color
              <input type="color" value={draftTheme?.primaryColor ?? "#0F8B8D"} onChange={(event) => setDraftTheme((current) => current ? { ...current, primaryColor: event.target.value } : current)} />
            </label>
            <label>
              Accent color
              <input type="color" value={draftTheme?.accentColor ?? "#1E88E5"} onChange={(event) => setDraftTheme((current) => current ? { ...current, accentColor: event.target.value } : current)} />
            </label>
            <label>
              Typography
              <select value={draftTheme?.typographyPreset ?? "clean"} onChange={(event) => setDraftTheme((current) => current ? { ...current, typographyPreset: event.target.value } : current)}>
                <option value="clean">Clean</option>
                <option value="warm">Warm</option>
                <option value="balanced">Balanced</option>
                <option value="institutional">Institutional</option>
              </select>
            </label>
            <label>
              Button style
              <select value={draftTheme?.buttonStyle ?? "solid"} onChange={(event) => setDraftTheme((current) => current ? { ...current, buttonStyle: event.target.value } : current)}>
                <option value="solid">Solid</option>
                <option value="outline">Outline</option>
                <option value="soft">Soft</option>
              </select>
            </label>
            <label>
              Radius
              <select value={draftTheme?.borderRadiusPreset ?? "medium"} onChange={(event) => setDraftTheme((current) => current ? { ...current, borderRadiusPreset: event.target.value } : current)}>
                <option value="small">Small</option>
                <option value="medium">Medium</option>
                <option value="large">Large</option>
              </select>
            </label>
          </div>

          <div className="landing-builder-panel">
              <div className="panel-heading">
                <h2>Profile sections</h2>
                <span>{draftSections.length} sections</span>
              </div>
              <div className="landing-section-editor-list">
                {draftSections.map((section, index) => (
                  <article className="landing-section-editor" key={section.key} id={`landing-section-${section.key.toLowerCase()}`}>
                    <div className="panel-heading">
                      <strong>{sectionDisplayLabel(section.key)}</strong>
                      <div className="cta-row">
                        <button className="text-button" type="button" onClick={() => moveSection(index, -1)}>Up</button>
                        <button className="text-button" type="button" onClick={() => moveSection(index, 1)}>Down</button>
                      </div>
                    </div>
                  <label className="checkbox-row">
                    <input type="checkbox" checked={section.enabled} onChange={(event) => updateSection(index, { enabled: event.target.checked })} />
                    Enabled
                  </label>
                  <label>
                    Title
                    <input value={section.title} onChange={(event) => updateSection(index, { title: event.target.value })} />
                  </label>
                  <label>
                    Description
                    <textarea value={section.description ?? ""} onChange={(event) => updateSection(index, { description: event.target.value })} />
                  </label>
                  <label>
                    Visibility
                    <select value={section.visibilityRule} onChange={(event) => updateSection(index, { visibilityRule: event.target.value })}>
                      <option value="PUBLIC">Public</option>
                      <option value="DRAFT_ONLY">Draft only</option>
                      <option value="AUTHENTICATED">Authenticated</option>
                    </select>
                  </label>
                  <small>Order {section.displayOrder + 1}</small>
                </article>
              ))}
            </div>
          </div>
        </aside>

        <div className="landing-builder-main">
          <div className="landing-builder-tabs" role="tablist" aria-label="Landing page workspace">
            {["builder", "preview", "versions"].map((item) => (
              <button
                key={item}
                className={`text-button${tab === item ? " is-active" : ""}`}
                type="button"
                onClick={() => setSearchParams({ tab: item, device })}
                role="tab"
                aria-selected={tab === item}
              >
                {item === "builder" ? "Builder" : item === "preview" ? "Preview" : "Versions"}
              </button>
            ))}
            <div className="device-switcher" aria-label="Preview device">
              {["desktop", "tablet", "mobile"].map((item) => (
                <button
                  key={item}
                  className={`text-button${device === item ? " is-active" : ""}`}
                  type="button"
                  onClick={() => setSearchParams({ tab, device: item })}
                >
                  {item}
                </button>
              ))}
            </div>
          </div>

          {tab === "versions" ? (
            <div className="landing-builder-panel">
              <div className="panel-heading">
                <h2>Version history</h2>
                <select value={selectedVersion ?? ""} onChange={(event) => void chooseVersion(event)}>
                  <option value="">Select a version</option>
                  {page.versions.map((version) => (
                    <option key={version.id} value={version.versionNumber}>
                      v{version.versionNumber} · {version.changeSummary}
                    </option>
                  ))}
                </select>
              </div>
              {compare ? (
                <div className="landing-compare-card">
                  <strong>Latest comparison</strong>
                  <span>Template changed: {compare.templateChanged ? "Yes" : "No"}</span>
                  <span>Theme changed: {compare.themeChanged ? "Yes" : "No"}</span>
                  <span>Section order changed: {compare.sectionOrderChanged ? "Yes" : "No"}</span>
                  <span>Added: {compare.addedSections.join(", ") || "None"}</span>
                  <span>Removed: {compare.removedSections.join(", ") || "None"}</span>
                  <span>Changed: {compare.changedSections.join(", ") || "None"}</span>
                </div>
              ) : null}
              <div className="landing-version-list">
                {page.versions.map((version) => (
                  <article className="landing-version-card" key={version.id}>
                    <strong>Version {version.versionNumber}</strong>
                    <span>{version.changeSummary}</span>
                    <small>{version.versionKind}</small>
                    <small>{new Date(version.publishedAt).toLocaleString()}</small>
                  </article>
                ))}
              </div>
            </div>
          ) : (
            <div className={deviceClass(device)}>
              {page.profile ? (
                <LandingPageRenderer page={page} snapshot={visibleSnapshot} />
              ) : (
                <div className="provider-dashboard-panel">
                  <h2>Complete publication setup</h2>
                  <p>Your profile is approved. Configure the public profile and submit it for review when ready.</p>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {error ? <p className="autosave-row" role="status">{error}</p> : null}
      {loading ? <p className="autosave-row" role="status">Loading landing page…</p> : null}
      {saving ? <p className="autosave-row" role="status">Saving changes…</p> : null}
    </section>
  );
}
