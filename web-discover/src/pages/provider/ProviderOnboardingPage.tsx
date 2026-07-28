import { type ChangeEvent, type FormEvent, useEffect, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  createProviderApplication,
  loadProviderChangeRequests,
  loadProviderApplication,
  loadProviderPreview,
  resubmitProviderApplication,
  submitProviderApplication,
  updateProviderApplication,
  uploadProviderDocument,
  type ProviderApplication,
  type ProviderApplicationPayload,
  type ProviderDocumentType,
  type ProviderPreview,
  type ProviderServicePayload,
  type ProviderServiceType,
  type ProviderType,
} from "../../api/providerOnboarding";
import { DISCOVER_ROUTES } from "../../routes";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

const steps = [
  ["account", "Account"],
  ["organisation", "Organisation"],
  ["professional", "Professional Details"],
  ["services", "Services"],
  ["locations", "Locations"],
  ["branding", "Branding"],
  ["preview", "Preview"],
  ["submit", "Submit"],
] as const;

const serviceOptions: Array<{ type: ProviderServiceType; label: string }> = [
  { type: "CONSULTATIONS", label: "Consultations" },
  { type: "VACCINATION", label: "Vaccination" },
  { type: "LAB", label: "Lab" },
  { type: "RADIOLOGY", label: "Radiology" },
  { type: "TELECONSULTATION", label: "Teleconsultation" },
  { type: "PHARMACY", label: "Pharmacy" },
  { type: "HEALTH_CHECKUPS", label: "Health Checkups" },
  { type: "PROCEDURES", label: "Procedures" },
];

const typeCopy = {
  INDIVIDUAL_DOCTOR: {
    eyebrow: "Doctor onboarding",
    title: "Create your public doctor profile",
    nameLabel: "Doctor name",
    registrationLabel: "Medical registration number",
  },
  CLINIC: {
    eyebrow: "Clinic onboarding",
    title: "Create your clinic profile",
    nameLabel: "Clinic name",
    registrationLabel: "Clinic registration number",
  },
  HOSPITAL: {
    eyebrow: "Hospital onboarding",
    title: "Create your hospital profile",
    nameLabel: "Hospital name",
    registrationLabel: "Hospital registration number",
  },
} satisfies Record<ProviderType, { eyebrow: string; title: string; nameLabel: string; registrationLabel: string }>;

function blankDraft(providerType: ProviderType): ProviderApplicationPayload {
  return {
    displayName: "",
    legalName: "",
    registrationNumber: "",
    languages: [],
    specialities: [],
    subSpecialities: [],
    departments: [],
    facilities: [],
    accreditations: [],
    onlineConsultation: false,
    emergencyAvailable: false,
    appointmentDurationMinutes: 15,
    services: [],
    locations: [],
    branding: { primaryColor: "#0F8B8D", tagline: "" },
    ...(providerType === "INDIVIDUAL_DOCTOR" ? { yearsOfExperience: 0 } : {}),
    ...(providerType === "HOSPITAL" ? { beds: 0 } : {}),
  };
}

function split(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function joined(values?: string[]) {
  return values?.join(", ") ?? "";
}

function providerTypeFromRoute(type: "doctor" | "clinic" | "hospital"): ProviderType {
  if (type === "doctor") return "INDIVIDUAL_DOCTOR";
  if (type === "clinic") return "CLINIC";
  return "HOSPITAL";
}

function readStoredToken() {
  for (const key of TOKEN_KEYS) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

export function ProviderOnboardingPage({ type }: { type?: "doctor" | "clinic" | "hospital" }) {
  const navigate = useNavigate();
  const params = useParams<{ applicationId?: string; step?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [providerType, setProviderType] = useState<ProviderType>(() => (type ? providerTypeFromRoute(type) : "INDIVIDUAL_DOCTOR"));
  const tokenStorageKey = type ? `${TOKEN_KEY}.${providerType}` : TOKEN_KEY;
  const copy = typeCopy[providerType];
  const activeStep = params.step ?? searchParams.get("step") ?? "account";
  const [token, setToken] = useState(() => readStoredToken());
  const [application, setApplication] = useState<ProviderApplication | null>(null);
  const [draft, setDraft] = useState<ProviderApplicationPayload>(() => blankDraft(providerType));
  const [account, setAccount] = useState({ email: "", phone: "", password: "", termsAccepted: false, privacyAccepted: false });
  const [statusMessage, setStatusMessage] = useState("Start with your account details. Progress saves as you go.");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState<ProviderPreview | null>(null);
  const [changeRequests, setChangeRequests] = useState<Array<{ id: string; reviewerMessage: string | null; providerResponseNote: string | null; requestedSections: string[]; resolved: boolean }>>([]);
  const [responseNote, setResponseNote] = useState("");

  useEffect(() => {
    if (!token) return;
    loadProviderApplication(token)
      .then((result) => {
        if (params.applicationId && result.id !== params.applicationId) {
          setError("This onboarding link does not match the stored application.");
          return;
        }
        setProviderType(result.providerType);
        setApplication(result);
        setDraft(result);
        setStatusMessage(`Draft resumed. Last saved ${new Date(result.lastSavedAt).toLocaleString()}.`);
        void loadProviderChangeRequests(result.id, token)
          .then((items) => setChangeRequests(items.map((item) => ({
            id: item.id,
            reviewerMessage: item.reviewerMessage,
            providerResponseNote: item.providerResponseNote,
            requestedSections: item.requestedSections,
            resolved: item.resolved,
          }))))
          .catch(() => setChangeRequests([]));
      })
      .catch(() => {
        localStorage.removeItem(`${TOKEN_KEY}.INDIVIDUAL_DOCTOR`);
        localStorage.removeItem(`${TOKEN_KEY}.CLINIC`);
        localStorage.removeItem(`${TOKEN_KEY}.HOSPITAL`);
        localStorage.removeItem(TOKEN_KEY);
        setToken("");
      });
  }, [token, params.applicationId, tokenStorageKey]);

  useEffect(() => {
    if (!application || !token) return;
    const handle = window.setTimeout(() => {
      void saveDraft(false);
    }, 900);
    return () => window.clearTimeout(handle);
  }, [draft, application?.id, token]);

  const completionPercent = application?.completionPercent ?? estimateCompletion(draft, account, providerType);
  const missingItems = application?.missingItems ?? clientMissingItems(draft, account, providerType);
  const currentStepIndex = Math.max(0, steps.findIndex(([id]) => id === activeStep));

  function setStep(step: string) {
    if (params.applicationId) {
      navigate(`/provider/onboarding/${params.applicationId}/${step}`);
      return;
    }
    setSearchParams({ step });
  }

  function patchDraft(patch: ProviderApplicationPayload) {
    setDraft((current) => ({ ...current, ...patch }));
  }

  async function createDraft(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await createProviderApplication({ providerType, ...account });
      if (created.onboardingToken) {
        localStorage.setItem(tokenStorageKey, created.onboardingToken);
        localStorage.removeItem(TOKEN_KEY);
        setToken(created.onboardingToken);
      }
      setApplication(created);
      setDraft({ ...blankDraft(providerType), ...created });
      setStatusMessage("Draft created. Continue completing your profile.");
      navigate(`/provider/onboarding/${created.id}/organisation`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not create your provider draft.");
    } finally {
      setSaving(false);
    }
  }

  async function saveDraft(showMessage = true) {
    if (!application || !token) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateProviderApplication(application.id, token, draft);
      setApplication(updated);
      setDraft(updated);
      void loadProviderChangeRequests(updated.id, token)
        .then((items) => setChangeRequests(items.map((item) => ({
          id: item.id,
          reviewerMessage: item.reviewerMessage,
          providerResponseNote: item.providerResponseNote,
          requestedSections: item.requestedSections,
          resolved: item.resolved,
        }))))
        .catch(() => setChangeRequests([]));
      if (showMessage) setStatusMessage("Draft saved.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Draft could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  async function uploadDocument(type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file || !application || !token) return;
    if (!["image/png", "image/jpeg", "application/pdf"].includes(file.type)) {
      setError("Upload PNG, JPEG, or PDF files only.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await uploadProviderDocument(application.id, token, type, file);
      const refreshed = await loadProviderApplication(token);
      setApplication(refreshed);
      setDraft(refreshed);
      setProviderType(refreshed.providerType);
      void loadProviderChangeRequests(refreshed.id, token)
        .then((items) => setChangeRequests(items.map((item) => ({
          id: item.id,
          reviewerMessage: item.reviewerMessage,
          providerResponseNote: item.providerResponseNote,
          requestedSections: item.requestedSections,
          resolved: item.resolved,
        }))))
        .catch(() => setChangeRequests([]));
      setStatusMessage(`${file.name} uploaded.`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Document upload failed.");
    } finally {
      setSaving(false);
    }
  }

  async function loadPreviewPanel() {
    if (!application || !token) return;
    await saveDraft(false);
    try {
      const result = await loadProviderPreview(application.id, token);
      setPreview(result);
      setStatusMessage("Preview refreshed.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Preview could not be loaded.");
    }
  }

  async function submit() {
    if (!application || !token) return;
    setSaving(true);
    setError(null);
    try {
      const submitted = application.status === "CHANGES_REQUESTED"
        ? await resubmitProviderApplication(application.id, token, responseNote || undefined)
        : await submitProviderApplication(application.id, token);
      setApplication(submitted);
      setDraft(submitted);
      void loadProviderChangeRequests(submitted.id, token)
        .then((items) => setChangeRequests(items.map((item) => ({
          id: item.id,
          reviewerMessage: item.reviewerMessage,
          providerResponseNote: item.providerResponseNote,
          requestedSections: item.requestedSections,
          resolved: item.resolved,
        }))))
        .catch(() => setChangeRequests([]));
      setStatusMessage("Submitted for verification. You can return here to track review progress.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Submission could not be completed.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="page-section provider-portal-page">
      <div className="provider-portal-hero">
        <div>
          <span className="eyebrow">{copy.eyebrow}</span>
          <h1>{copy.title}</h1>
          <p>Complete your profile step by step, upload required documents, preview your public presence, and submit for Jeevanam verification.</p>
        </div>
        <aside className="resume-card">
          <strong>{application ? application.referenceNumber : "New application"}</strong>
          <span>{application?.status.replaceAll("_", " ") ?? "Draft not created"}</span>
          <div className="progress-track" aria-label={`${completionPercent}% complete`}>
            <span style={{ width: `${completionPercent}%` }} />
          </div>
          <small>{completionPercent}% Complete</small>
        </aside>
      </div>

      <div className="provider-portal-layout">
        <aside className="provider-stepper" aria-label="Provider onboarding steps">
          {steps.map(([id, label], index) => (
            <button key={id} className={id === activeStep ? "is-active" : ""} type="button" onClick={() => setStep(id)}>
              <span>{index + 1}</span>
              {label}
            </button>
          ))}
        </aside>

        <div className="provider-workspace">
          <div className="autosave-row" role="status">
            <span>{saving ? "Saving..." : statusMessage}</span>
            {application ? <button className="secondary-button" type="button" onClick={() => void saveDraft(true)}>Save draft</button> : null}
          </div>
          {error ? <div className="portal-error" role="alert">{error}</div> : null}
          {activeStep === "account" ? (
            !application ? (
              <form className="onboarding-panel" onSubmit={createDraft}>
                <h2>Account and consent</h2>
                <div className="form-grid">
                  <label>Email<input type="email" value={account.email} onChange={(event) => setAccount({ ...account, email: event.target.value })} required /></label>
                  <label>Phone<input value={account.phone} onChange={(event) => setAccount({ ...account, phone: event.target.value })} required /></label>
                  <label>Password<input type="password" value={account.password} onChange={(event) => setAccount({ ...account, password: event.target.value })} required minLength={8} /></label>
                  <div className="verification-note" role="note">
                    Contact verification is recorded before review. Delivery integration will use backend notification events.
                  </div>
                </div>
                <label className="checkbox-row"><input type="checkbox" checked={account.termsAccepted} onChange={(event) => setAccount({ ...account, termsAccepted: event.target.checked })} /> I accept the Terms.</label>
                <label className="checkbox-row"><input type="checkbox" checked={account.privacyAccepted} onChange={(event) => setAccount({ ...account, privacyAccepted: event.target.checked })} /> I accept the Privacy policy.</label>
                <div className="cta-row">
                  <button className="primary-button" type="submit" disabled={saving}>Create draft</button>
                  <Link className="text-button" to={DISCOVER_ROUTES.login.path}>Already have account?</Link>
                </div>
              </form>
            ) : (
              <Panel title="Account and consent">
                <div className="status-messages">
                  <strong>{application.referenceNumber}</strong>
                  <span>{application.email}</span>
                  <span>{application.phone}</span>
                  <span>{application.termsAccepted ? "Terms accepted" : "Terms pending"}</span>
                  <span>{application.privacyAccepted ? "Privacy accepted" : "Privacy pending"}</span>
                </div>
                <div className="cta-row">
                  <button className="secondary-button" type="button" onClick={() => setStep("organisation")}>Continue</button>
                </div>
              </Panel>
            )
          ) : null}

          {activeStep === "organisation" ? (
            <Panel title="Organisation">
              <div className="form-grid">
                <label>{copy.nameLabel}<input value={draft.displayName ?? ""} onChange={(event) => patchDraft({ displayName: event.target.value, legalName: event.target.value })} /></label>
                <label>{copy.registrationLabel}<input value={draft.registrationNumber ?? ""} onChange={(event) => patchDraft({ registrationNumber: event.target.value })} /></label>
                {providerType !== "INDIVIDUAL_DOCTOR" ? <label>Organisation type<input value={draft.organisationType ?? ""} onChange={(event) => patchDraft({ organisationType: event.target.value })} /></label> : null}
                <label>Website<input value={draft.website ?? ""} onChange={(event) => patchDraft({ website: event.target.value })} /></label>
                <label>GST optional<input value={draft.gstNumber ?? ""} onChange={(event) => patchDraft({ gstNumber: event.target.value })} /></label>
              </div>
            </Panel>
          ) : null}

          {activeStep === "professional" ? (
            <Panel title="Professional details">
              <div className="form-grid">
                {providerType === "INDIVIDUAL_DOCTOR" ? <>
                  <label>Gender<input value={draft.gender ?? ""} onChange={(event) => patchDraft({ gender: event.target.value })} /></label>
                  <label>Date of birth<input type="date" value={draft.dateOfBirth ?? ""} onChange={(event) => patchDraft({ dateOfBirth: event.target.value })} /></label>
                  <label>Qualification<input value={draft.qualification ?? ""} onChange={(event) => patchDraft({ qualification: event.target.value })} /></label>
                  <label>Medical council<input value={draft.medicalCouncil ?? ""} onChange={(event) => patchDraft({ medicalCouncil: event.target.value })} /></label>
                  <label>Experience<input type="number" value={draft.yearsOfExperience ?? 0} onChange={(event) => patchDraft({ yearsOfExperience: Number(event.target.value) })} /></label>
                  <label>Consultation fee<input type="number" value={draft.consultationFee ?? ""} onChange={(event) => patchDraft({ consultationFee: Number(event.target.value) })} /></label>
                </> : <>
                  <label>{providerType === "HOSPITAL" ? "Hospital type" : "Ownership"}<input value={providerType === "HOSPITAL" ? draft.hospitalType ?? "" : draft.ownership ?? ""} onChange={(event) => providerType === "HOSPITAL" ? patchDraft({ hospitalType: event.target.value }) : patchDraft({ ownership: event.target.value })} /></label>
                  {providerType === "HOSPITAL" ? <label>Beds<input type="number" value={draft.beds ?? 0} onChange={(event) => patchDraft({ beds: Number(event.target.value) })} /></label> : null}
                  {providerType === "HOSPITAL" ? <label>Medical director<input value={draft.medicalDirector ?? ""} onChange={(event) => patchDraft({ medicalDirector: event.target.value })} /></label> : null}
                </>}
                <label>Languages<input value={joined(draft.languages)} onChange={(event) => patchDraft({ languages: split(event.target.value) })} /></label>
                <label>Specialities<input value={joined(draft.specialities)} onChange={(event) => patchDraft({ specialities: split(event.target.value), departments: split(event.target.value) })} /></label>
                <label>Facilities<input value={joined(draft.facilities)} onChange={(event) => patchDraft({ facilities: split(event.target.value) })} /></label>
              </div>
              <label>Biography<textarea value={draft.biography ?? ""} onChange={(event) => patchDraft({ biography: event.target.value })} /></label>
            </Panel>
          ) : null}

          {activeStep === "services" ? <Panel title="Services">{serviceOptions.map((service) => <ServiceToggle key={service.type} service={service} draft={draft} patchDraft={patchDraft} />)}</Panel> : null}

          {activeStep === "locations" ? (
            <Panel title="Locations">
              <div className="form-grid">
                <label>Address<input value={draft.locations?.[0]?.address ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { address: event.target.value })} /></label>
                <label>City<input value={draft.locations?.[0]?.city ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { city: event.target.value })} /></label>
                <label>State<input value={draft.locations?.[0]?.state ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { state: event.target.value })} /></label>
                <label>Country<input value={draft.locations?.[0]?.country ?? "India"} onChange={(event) => patchLocation(draft, patchDraft, { country: event.target.value })} /></label>
                <label>PIN<input value={draft.locations?.[0]?.pinCode ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { pinCode: event.target.value })} /></label>
                <label>Working hours<input value={draft.locations?.[0]?.workingHours ?? ""} onChange={(event) => patchLocation(draft, patchDraft, { workingHours: event.target.value })} /></label>
              </div>
              <label className="checkbox-row"><input type="checkbox" checked={draft.locations?.[0]?.parkingAvailable ?? false} onChange={(event) => patchLocation(draft, patchDraft, { parkingAvailable: event.target.checked })} /> Parking available</label>
              <label className="checkbox-row"><input type="checkbox" checked={draft.locations?.[0]?.accessibilityAvailable ?? false} onChange={(event) => patchLocation(draft, patchDraft, { accessibilityAvailable: event.target.checked })} /> Accessibility support available</label>
            </Panel>
          ) : null}

          {activeStep === "branding" ? (
            <Panel title="Branding and documents">
              <div className="upload-grid">
                <UploadBox label="Logo" type="LOGO" uploadDocument={uploadDocument} />
                <UploadBox label="Cover image" type="COVER_IMAGE" uploadDocument={uploadDocument} />
                <UploadBox label={providerType === "INDIVIDUAL_DOCTOR" ? "Doctor photo" : "Gallery image"} type={providerType === "INDIVIDUAL_DOCTOR" ? "DOCTOR_PHOTO" : "GALLERY_IMAGE"} uploadDocument={uploadDocument} />
                <UploadBox label="Registration document" type="REGISTRATION_CERTIFICATE" uploadDocument={uploadDocument} />
              </div>
              <label>Tagline<input value={draft.branding?.tagline ?? ""} onChange={(event) => patchDraft({ branding: { ...draft.branding, tagline: event.target.value } })} /></label>
              <div className="document-list">{application?.documents.map((document) => <span key={document.id}>{document.documentType.replaceAll("_", " ")} · {document.originalFilename}</span>)}</div>
            </Panel>
          ) : null}

          {activeStep === "preview" ? (
            <Panel title="Public profile preview">
              <button className="secondary-button" type="button" onClick={() => void loadPreviewPanel()}>Refresh preview</button>
              <article className="profile-preview-card">
                <span className="onboarding-icon" aria-hidden="true">{providerType === "INDIVIDUAL_DOCTOR" ? "DR" : providerType === "CLINIC" ? "CL" : "H"}</span>
                <h2>{preview?.displayName ?? draft.displayName ?? "Profile name pending"}</h2>
                <p>{preview?.subtitle ?? draft.qualification ?? draft.organisationType ?? draft.hospitalType ?? "Profile details pending"}</p>
                <p>{preview?.locationSummary ?? draft.locations?.[0]?.city ?? "Location pending"}</p>
                <div className="directory-badge-row">{(preview?.services ?? draft.services?.map((item) => item.label) ?? []).map((item) => <span className="chip" key={item}>{item}</span>)}</div>
              </article>
            </Panel>
          ) : null}

          {activeStep === "submit" ? (
            <Panel title="Submit for verification">
              <StatusTimeline status={application?.status ?? "DRAFT"} />
              {changeRequests.length ? (
                <div className="change-request-panel">
                  <strong>Requested changes</strong>
                  {changeRequests.map((request) => (
                    <div key={request.id} className="change-request-item">
                      <span>{request.reviewerMessage ?? "Review team feedback"}</span>
                      {request.requestedSections.length ? <small>{request.requestedSections.join(", ")}</small> : null}
                      {request.providerResponseNote ? <small>Response: {request.providerResponseNote}</small> : null}
                    </div>
                  ))}
                  {application?.status === "CHANGES_REQUESTED" ? (
                    <label>
                      Response note
                      <textarea value={responseNote} onChange={(event) => setResponseNote(event.target.value)} placeholder="Describe the updates you made" />
                    </label>
                  ) : null}
                </div>
              ) : null}
              {application?.statusHistory.length ? (
                <div className="status-messages">
                  <strong>Status messages</strong>
                  {application.statusHistory.slice(-3).map((item) => (
                    <span key={item.id}>{item.reason}</span>
                  ))}
                </div>
              ) : null}
              {missingItems.length ? <div className="missing-list"><strong>Missing information</strong>{missingItems.map((item) => <span key={item}>{item}</span>)}</div> : <p>Your application is ready for review.</p>}
              <button className="primary-button" type="button" onClick={() => void submit()} disabled={saving || missingItems.length > 0}>
                {application?.status === "CHANGES_REQUESTED" ? "Resubmit for verification" : "Submit for verification"}
              </button>
            </Panel>
          ) : null}

          <div className="wizard-footer">
            <button className="secondary-button" type="button" onClick={() => setStep(steps[Math.max(0, currentStepIndex - 1)][0])}>Back</button>
            <button className="primary-button" type="button" onClick={() => setStep(steps[Math.min(steps.length - 1, currentStepIndex + 1)][0])}>Continue</button>
          </div>
        </div>
      </div>
    </section>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="onboarding-panel"><h2>{title}</h2>{children}</div>;
}

function ServiceToggle({ service, draft, patchDraft }: { service: { type: ProviderServiceType; label: string }; draft: ProviderApplicationPayload; patchDraft: (patch: ProviderApplicationPayload) => void }) {
  const enabled = draft.services?.some((item) => item.serviceType === service.type && item.enabled !== false) ?? false;
  return (
    <label className="service-toggle">
      <input
        type="checkbox"
        checked={enabled}
        onChange={(event) => {
          const others = draft.services?.filter((item) => item.serviceType !== service.type) ?? [];
          const next: ProviderServicePayload[] = event.target.checked ? [...others, { serviceType: service.type, label: service.label, enabled: true }] : others;
          patchDraft({ services: next });
        }}
      />
      <span>{service.label}</span>
    </label>
  );
}

function patchLocation(draft: ProviderApplicationPayload, patchDraft: (patch: ProviderApplicationPayload) => void, patch: Partial<NonNullable<ProviderApplicationPayload["locations"]>[number]>) {
  const current = draft.locations?.[0] ?? { label: "Primary", address: "", city: "", state: "", country: "India", pinCode: "" };
  patchDraft({ locations: [{ ...current, ...patch }] });
}

function UploadBox({ label, type, uploadDocument }: { label: string; type: ProviderDocumentType; uploadDocument: (type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) => void }) {
  return <label className="upload-box"><span>{label}</span><input type="file" accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf" onChange={(event) => uploadDocument(type, event)} /></label>;
}

function StatusTimeline({ status }: { status: string }) {
  const labels = ["Draft", "Profile Complete", "Submitted", "Under Review", "Approved", "Published"];
  return <div className="status-timeline">{labels.map((item) => <span className={status.replaceAll("_", " ").toLowerCase().includes(item.toLowerCase().split(" ")[0]) ? "is-active" : ""} key={item}>{item}</span>)}</div>;
}

function clientMissingItems(draft: ProviderApplicationPayload, account: { email: string; phone: string; termsAccepted: boolean; privacyAccepted: boolean }, providerType: ProviderType) {
  const missing = [];
  if (!account.email && !draft.email) missing.push("email");
  if (!account.phone && !draft.phone) missing.push("phone");
  if (!account.termsAccepted && !draft.termsAccepted) missing.push("terms");
  if (!account.privacyAccepted && !draft.privacyAccepted) missing.push("privacy");
  if (!draft.displayName && !draft.legalName) missing.push("displayName");
  if (!draft.registrationNumber) missing.push("registrationNumber");
  if (!draft.services?.length) missing.push("services");
  if (!draft.locations?.length) missing.push("locations");
  if (providerType === "INDIVIDUAL_DOCTOR" && !draft.qualification) missing.push("qualification");
  if (providerType === "HOSPITAL" && !draft.beds) missing.push("beds");
  return missing;
}

function estimateCompletion(draft: ProviderApplicationPayload, account: { email: string; phone: string; termsAccepted: boolean; privacyAccepted: boolean }, providerType: ProviderType) {
  const missing = clientMissingItems(draft, account, providerType).length;
  const total = providerType === "HOSPITAL" ? 10 : 9;
  return Math.max(0, Math.min(100, ((total - missing) * 100) / total));
}
