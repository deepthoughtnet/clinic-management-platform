import { type ChangeEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
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
import { DISCOVER_ROUTES } from "../../routes";

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
          primaryTo={DISCOVER_ROUTES.providerDashboard.path}
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
          secondaryTo={DISCOVER_ROUTES.providerDashboard.path}
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

  return (
    <section className="page-section landing-builder-page">
      <div className="landing-builder-hero">
        <div>
          <span className="eyebrow">Landing page builder</span>
          <h1>{page.displayName}</h1>
          <p>{page.publicPath}</p>
        </div>
        <div className="landing-builder-actions">
          <button className="secondary-button" type="button" onClick={() => void saveDraft()} disabled={saving}>
            Save draft
          </button>
          <button className="primary-button" type="button" onClick={() => void publish()} disabled={saving}>
            Publish landing page
          </button>
          {page.publishedVersionNumber ? (
            <button className="secondary-button" type="button" onClick={() => void revertToPublished()} disabled={saving}>
              Revert to published
            </button>
          ) : null}
        </div>
      </div>

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
              <h2>Sections</h2>
              <span>{draftSections.length} sections</span>
            </div>
            <div className="landing-section-editor-list">
              {draftSections.map((section, index) => (
                <article className="landing-section-editor" key={section.key}>
                  <div className="panel-heading">
                    <strong>{section.key}</strong>
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
              <LandingPageRenderer page={page} snapshot={visibleSnapshot} />
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
