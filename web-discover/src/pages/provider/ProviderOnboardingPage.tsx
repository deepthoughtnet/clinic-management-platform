import { type ChangeEvent, type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  createProviderApplication,
  loadProviderChangeRequests,
  loadProviderApplication,
  loadProviderPreview,
  requestProviderEmailVerification,
  requestProviderPhoneVerification,
  resubmitProviderApplication,
  submitProviderApplication,
  verifyProviderEmail,
  verifyProviderPhone,
  updateProviderApplication,
  uploadProviderDocument,
  type ProviderApplication,
  type ProviderApplicationPayload,
  type ProviderDocumentType,
  type ContactVerificationStatus,
  type ProviderPreview,
  type ProviderServicePayload,
  type ProviderServiceType,
  type ProviderStatus,
  type ProviderType,
} from "../../api/providerOnboarding";
import { fetchPublicJson, type PublicSpecialitySummaryResponse } from "../../api/publicCatalog";
import { DISCOVER_ROUTES, REGISTRATION_PROVIDER_TYPE_BY_ROUTE } from "../../routes";
import { careBookingUrl } from "../../components/DiscoveryComponents";
import { PublicMediaImage } from "../../components/landing/PublicMediaImage";
import { LocationDisplayMap, LocationPicker } from "../../components/location";
import { ProviderDropdownField, ProviderMultiSelectField } from "../../components/provider-onboarding/ProviderOnboardingFields";
import { ProviderOnboardingStepper, ProviderSaveStatus, type ProviderOnboardingStepState } from "../../components/provider-onboarding/ProviderOnboardingStepper";
import { providerDocumentContentPath } from "../../api/providerOnboarding";

const TOKEN_KEY = "jeevanam.discover.providerOnboardingToken";
const TOKEN_KEYS = [
  TOKEN_KEY,
  `${TOKEN_KEY}.INDIVIDUAL_DOCTOR`,
  `${TOKEN_KEY}.CLINIC`,
  `${TOKEN_KEY}.HOSPITAL`,
];

type SaveReason = "autosave" | "manual" | "navigation" | "preview" | "route-transition";

type AccountStepValues = {
  email: string;
  phone: string;
  password: string;
  termsAccepted: boolean;
  privacyAccepted: boolean;
};

type VerificationCodeState = {
  code: string;
  requestMessage: string;
  resendAfterSeconds: number | null;
  devCode: string | null;
};

const ownershipOptions = [
  "Private",
  "Government",
  "Trust",
  "NGO",
  "Corporate",
  "Teaching Hospital",
  "Community Health Centre",
  "Clinic Chain",
];

const languageOptions = [
  "English",
  "Hindi",
  "Marathi",
  "Gujarati",
  "Bengali",
  "Tamil",
  "Telugu",
  "Kannada",
  "Malayalam",
  "Punjabi",
  "Urdu",
];

const facilityOptions = [
  "Parking",
  "Pharmacy",
  "Wheelchair Access",
  "Vaccination",
  "Sample Collection",
  "Emergency",
  "Digital Payments",
  "Waiting Area",
  "WiFi",
  "Lift",
  "Air Conditioning",
];

const specialityCache = new Map<string, string[]>();

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

const EDITABLE_PROVIDER_STATUSES: ProviderStatus[] = ["DRAFT", "CHANGES_REQUESTED"];

function isApplicationEditable(status: ProviderStatus | undefined) {
  return Boolean(status && EDITABLE_PROVIDER_STATUSES.includes(status));
}

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

function providerTypeFromRoute(type: "doctor" | "clinic" | "hospital"): ProviderType {
  return REGISTRATION_PROVIDER_TYPE_BY_ROUTE[type];
}

function readStoredToken(keys: string[]) {
  for (const key of keys) {
    const token = localStorage.getItem(key);
    if (token) return token;
  }
  return "";
}

function toDraftPayload(source: ProviderApplication | ProviderApplicationPayload): ProviderApplicationPayload {
  return {
    version: source.version,
    email: source.email,
    phone: source.phone,
    contactVerified: source.contactVerified,
    termsAccepted: source.termsAccepted,
    privacyAccepted: source.privacyAccepted,
    displayName: source.displayName,
    legalName: source.legalName,
    organisationType: source.organisationType,
    registrationNumber: source.registrationNumber,
    gstNumber: source.gstNumber,
    website: source.website,
    gender: source.gender,
    dateOfBirth: source.dateOfBirth,
    languages: source.languages,
    biography: source.biography,
    medicalCouncil: source.medicalCouncil,
    qualification: source.qualification,
    yearsOfExperience: source.yearsOfExperience,
    specialities: source.specialities,
    subSpecialities: source.subSpecialities,
    consultationFee: source.consultationFee,
    onlineConsultation: source.onlineConsultation,
    appointmentDurationMinutes: source.appointmentDurationMinutes,
    ownership: source.ownership,
    hospitalType: source.hospitalType,
    beds: source.beds,
    emergencyAvailable: source.emergencyAvailable,
    medicalDirector: source.medicalDirector,
    departments: source.departments,
    facilities: source.facilities,
    accreditations: source.accreditations,
    locations: source.locations,
    services: source.services,
    branding: source.branding,
  };
}

function blankAccountStepValues(): AccountStepValues {
  return {
    email: "",
    phone: "",
    password: "",
    termsAccepted: false,
    privacyAccepted: false,
  };
}

function mapAccountStepValues(source: Pick<ProviderApplicationPayload, "email" | "phone" | "termsAccepted" | "privacyAccepted"> | null | undefined): AccountStepValues {
  return {
    email: source?.email ?? "",
    phone: source?.phone ?? "",
    password: "",
    termsAccepted: Boolean(source?.termsAccepted),
    privacyAccepted: Boolean(source?.privacyAccepted),
  };
}

function draftSnapshot(source: ProviderApplication | ProviderApplicationPayload) {
  const { version: _version, ...rest } = toDraftPayload(source);
  void _version;
  return JSON.stringify(rest);
}

type ProviderFieldErrors = Partial<Record<
  | "displayName"
  | "registrationNumber"
  | "website"
  | "gstNumber"
  | "ownership"
  | "languages"
  | "specialities"
  | "facilities"
  | "biography"
  | "organisationType"
  | "qualification"
  | "medicalCouncil"
  | "yearsOfExperience"
  | "beds"
  | "medicalDirector"
  | "locations"
  | "services"
  | "documents"
  | "account"
  | "terms"
  | "privacy",
  string
>>;

function providerTypeLabel(providerType: ProviderType) {
  return providerType === "INDIVIDUAL_DOCTOR" ? "Doctor" : providerType === "CLINIC" ? "Clinic" : "Hospital";
}

function providerReferencePrefix(providerType: ProviderType) {
  return providerType === "INDIVIDUAL_DOCTOR" ? "JDR" : providerType === "CLINIC" ? "JCL" : "JHS";
}

function normalizeSearchList(items: string[]) {
  return items.map((item) => item.trim()).filter(Boolean).filter((item, index, list) => list.findIndex((candidate) => candidate.toLowerCase() === item.toLowerCase()) === index);
}

function validateDraft(
  draft: ProviderApplicationPayload,
  account: AccountStepValues,
  providerType: ProviderType,
  options: { specialityNames: string[] },
) {
  const errors: ProviderFieldErrors = {};
  const displayName = draft.displayName?.trim() || draft.legalName?.trim();
  const website = draft.website?.trim();
  const gst = draft.gstNumber?.trim();
  const biography = draft.biography?.trim() ?? "";
  const specialitySet = new Set(options.specialityNames.map((item) => item.toLowerCase()));
  const specialities = normalizeSearchList(draft.specialities ?? []);
  const languages = normalizeSearchList(draft.languages ?? []);
  const facilities = normalizeSearchList(draft.facilities ?? []);

  if (!account.email.trim()) errors.account = "Email is required.";
  if (!account.phone.trim()) errors.account = errors.account ?? "Phone is required.";
  if (!account.termsAccepted) errors.terms = "Terms acceptance is required.";
  if (!account.privacyAccepted) errors.privacy = "Privacy acceptance is required.";
  if (!displayName) errors.displayName = `${providerTypeLabel(providerType)} name is required.`;
  if (!draft.registrationNumber?.trim()) errors.registrationNumber = "Official registration number is required.";
  if (website && !/^https?:\/\/[^\s/$.?#].[^\s]*$/i.test(website)) errors.website = "Enter a valid public website URL.";
  if (gst && !/^[0-9A-Z]{15}$/i.test(gst)) errors.gstNumber = "GST number must be 15 alphanumeric characters when provided.";
  if (biography.length > 1500) errors.biography = "Biography must be 1500 characters or fewer.";
  if (!draft.ownership?.trim()) errors.ownership = "Ownership type is required.";

  if (providerType === "INDIVIDUAL_DOCTOR") {
    if (!draft.qualification?.trim()) errors.qualification = "Qualification is required.";
    if (!draft.medicalCouncil?.trim()) errors.medicalCouncil = "Medical council registration is required.";
    if (draft.yearsOfExperience == null || Number.isNaN(draft.yearsOfExperience)) errors.yearsOfExperience = "Experience is required.";
  }

  if (providerType === "CLINIC" && !draft.organisationType?.trim()) {
    errors.organisationType = "Clinic organisation type is required.";
  }

  if (providerType === "HOSPITAL") {
    if (!draft.hospitalType?.trim()) errors.ownership = "Hospital type is required.";
    if (!draft.beds || draft.beds <= 0) errors.beds = "Beds must be greater than zero.";
    if (!draft.medicalDirector?.trim()) errors.medicalDirector = "Medical director is required.";
  }

  if (!specialities.length) errors.specialities = "Select at least one speciality.";
  if (!languages.length) errors.languages = "Select at least one language.";
  if (!facilities.length) errors.facilities = "Select at least one facility.";

  if (options.specialityNames.length) {
    const unknownSpecialities = specialities.filter((item) => !specialitySet.has(item.toLowerCase()));
    if (unknownSpecialities.length) {
      errors.specialities = "Choose from the speciality master.";
    }
  }

  if (!draft.locations?.length) {
    errors.locations = "Add at least one location.";
  }

  return {
    errors,
    specialities,
    languages,
    facilities,
    requiredRemaining: Object.keys(errors).length,
  };
}

function contactVerificationSatisfied(application: ProviderApplication | null, contactVerification: ContactVerificationStatus | null) {
  return Boolean(contactVerification?.requirementSatisfied ?? application?.contactVerification?.requirementSatisfied ?? application?.contactVerified);
}

function missingItemLabel(code: string) {
  switch (code) {
    case "CONTACT_VERIFICATION_REQUIRED":
      return "Verify your contact details before submitting your profile.";
    case "EMAIL_REQUIRED":
      return "Email is required.";
    case "PHONE_REQUIRED":
      return "Phone is required.";
    default:
      return code.replaceAll("_", " ").toLowerCase();
  }
}

function contactVerificationSummary(contactVerification: ContactVerificationStatus | null, email: string, phone: string) {
  const emailStatus = contactVerification?.emailStatus ?? (email ? "NOT_VERIFIED" : "NOT_VERIFIED");
  const phoneStatus = contactVerification?.phoneStatus ?? (phone ? "NOT_VERIFIED" : "NOT_VERIFIED");
  const emailLabel = emailStatus === "VERIFIED" ? "Email verified" : emailStatus === "PENDING" ? "Email verification sent" : "Email not verified";
  const phoneLabel = phoneStatus === "VERIFIED" ? "Phone verified" : phoneStatus === "PENDING" ? "Phone OTP sent" : "Phone not verified";
  return `${emailLabel} · ${phoneLabel}`;
}

function stepCompletion(
  stepId: (typeof steps)[number][0],
  validation: ReturnType<typeof validateDraft>,
  application: ProviderApplication | null,
  draft: ProviderApplicationPayload,
  contactSatisfied: boolean,
) {
  switch (stepId) {
    case "account":
      return !validation.errors.account && !validation.errors.terms && !validation.errors.privacy && contactSatisfied;
    case "organisation":
      return !validation.errors.displayName && !validation.errors.registrationNumber && !validation.errors.website && !validation.errors.gstNumber && !validation.errors.ownership && !validation.errors.organisationType;
    case "professional":
      return !validation.errors.qualification && !validation.errors.medicalCouncil && !validation.errors.yearsOfExperience && !validation.errors.biography;
    case "services":
      return Boolean(draft.services?.some((item) => item.enabled !== false));
    case "locations":
      return Boolean(draft.locations?.length);
    case "branding":
      return Boolean(application?.documents.length);
    case "preview":
      return Boolean(application && contactSatisfied);
    case "submit":
      return Boolean(application && application.status !== "DRAFT" && contactSatisfied);
    default:
      return false;
  }
}

export function ProviderOnboardingPage({ type }: { type?: "doctor" | "clinic" | "hospital" }) {
  const navigate = useNavigate();
  const params = useParams<{ applicationId?: string; step?: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const routeProviderType = type ? providerTypeFromRoute(type) : undefined;
  const [providerType, setProviderType] = useState<ProviderType>(() => routeProviderType ?? "INDIVIDUAL_DOCTOR");
  const tokenStorageKey = routeProviderType ? `${TOKEN_KEY}.${routeProviderType}` : TOKEN_KEY;
  const copy = typeCopy[providerType];
  const activeStep = params.step ?? searchParams.get("step") ?? "account";
  const [token, setToken] = useState(() => readStoredToken(routeProviderType ? [tokenStorageKey] : TOKEN_KEYS));
  const [application, setApplication] = useState<ProviderApplication | null>(null);
  const [draft, setDraft] = useState<ProviderApplicationPayload>(() => blankDraft(providerType));
  const [account, setAccount] = useState<AccountStepValues>(() => blankAccountStepValues());
  const [accountHydrated, setAccountHydrated] = useState(false);
  const [contactVerification, setContactVerification] = useState<ContactVerificationStatus | null>(null);
  const [emailVerification, setEmailVerification] = useState<VerificationCodeState>(() => ({ code: "", requestMessage: "", resendAfterSeconds: null, devCode: null }));
  const [phoneVerification, setPhoneVerification] = useState<VerificationCodeState>(() => ({ code: "", requestMessage: "", resendAfterSeconds: null, devCode: null }));
  const [verificationBusy, setVerificationBusy] = useState(false);
  const [statusMessage, setStatusMessage] = useState("Start with your account details. Progress saves as you go.");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState<ProviderPreview | null>(null);
  const [changeRequests, setChangeRequests] = useState<Array<{ id: string; reviewerMessage: string | null; providerResponseNote: string | null; requestedSections: string[]; resolved: boolean }>>([]);
  const [responseNote, setResponseNote] = useState("");
  const [specialityOptions, setSpecialityOptions] = useState<string[]>([]);
  const latestApplicationRef = useRef<ProviderApplication | null>(null);
  const latestDraftRef = useRef<ProviderApplicationPayload>(draft);
  const latestFormStateRef = useRef<ProviderApplicationPayload>(draft);
  const latestVersionRef = useRef<number | null>(null);
  const latestTokenRef = useRef(token);
  const savedDraftSnapshotRef = useRef(draftSnapshot(draft));
  const dirtyRevisionRef = useRef(0);
  const lastPersistedRevisionRef = useRef(0);
  const saveInFlightRef = useRef(false);
  const pendingSaveRef = useRef<SaveReason | null>(null);
  const queuedSaveRef = useRef<SaveReason | null>(null);
  const autosaveTimeoutRef = useRef<number | null>(null);
  const mountedRef = useRef(true);
  const savePromiseRef = useRef<Promise<boolean> | null>(null);

  useEffect(() => {
    if (!routeProviderType) return;
    setProviderType(routeProviderType);
  }, [routeProviderType]);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (autosaveTimeoutRef.current != null) {
        window.clearTimeout(autosaveTimeoutRef.current);
        autosaveTimeoutRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    latestDraftRef.current = draft;
    latestFormStateRef.current = draft;
  }, [draft]);

  useEffect(() => {
    latestApplicationRef.current = application;
    latestVersionRef.current = application?.version ?? null;
  }, [application]);

  useEffect(() => {
    setContactVerification(application?.contactVerification ?? null);
  }, [application]);

  useEffect(() => {
    latestTokenRef.current = token;
  }, [token]);

  useEffect(() => {
    const abortController = new AbortController();
    const cacheKey = "specialities";
    const cached = specialityCache.get(cacheKey);
    if (cached) {
      setSpecialityOptions(cached);
      return () => abortController.abort();
    }
    void fetchPublicJson<PublicSpecialitySummaryResponse[]>("/api/public/specialities", {}, abortController.signal)
      .then((items) => {
        const names = items.map((item) => item.speciality).filter(Boolean).sort((left, right) => left.localeCompare(right));
        specialityCache.set(cacheKey, names);
        setSpecialityOptions(names);
      })
      .catch(() => {
        if (!abortController.signal.aborted) {
          setSpecialityOptions([]);
        }
      });
    return () => abortController.abort();
  }, []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    setAccountHydrated(false);
    loadProviderApplication(token)
      .then((result) => {
        if (cancelled) return;
        if (params.applicationId && result.id !== params.applicationId) {
          setError("This onboarding link does not match the stored application.");
          return;
        }
        if (routeProviderType && result.providerType !== routeProviderType) {
          setError("This registration link does not match the stored application type.");
          return;
        }
        setProviderType(result.providerType);
        setApplication(result);
        const normalizedDraft = toDraftPayload(result);
        const hydratedAccount = mapAccountStepValues(result);
        setDraft(normalizedDraft);
        setAccount(hydratedAccount);
        setAccountHydrated(true);
        latestDraftRef.current = normalizedDraft;
        latestFormStateRef.current = normalizedDraft;
        latestApplicationRef.current = result;
        latestVersionRef.current = result.version;
        savedDraftSnapshotRef.current = draftSnapshot(result);
        dirtyRevisionRef.current = 0;
        lastPersistedRevisionRef.current = 0;
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
        if (cancelled) return;
        if (routeProviderType) {
          localStorage.removeItem(tokenStorageKey);
        } else {
          localStorage.removeItem(`${TOKEN_KEY}.INDIVIDUAL_DOCTOR`);
          localStorage.removeItem(`${TOKEN_KEY}.CLINIC`);
          localStorage.removeItem(`${TOKEN_KEY}.HOSPITAL`);
          localStorage.removeItem(TOKEN_KEY);
        }
        setToken("");
      });
    return () => {
      cancelled = true;
    };
  }, [token, params.applicationId, tokenStorageKey, routeProviderType]);

  useEffect(() => {
    if (!application || !token || !accountHydrated || !isApplicationEditable(application.status)) return;
    if (draftSnapshot(draft) === savedDraftSnapshotRef.current) return;
    if (dirtyRevisionRef.current <= lastPersistedRevisionRef.current) return;
    if (autosaveTimeoutRef.current != null) {
      window.clearTimeout(autosaveTimeoutRef.current);
    }
    autosaveTimeoutRef.current = window.setTimeout(() => {
      autosaveTimeoutRef.current = null;
      void requestSave("autosave", { showMessage: false });
    }, 900);
    return () => {
      if (autosaveTimeoutRef.current != null) {
        window.clearTimeout(autosaveTimeoutRef.current);
        autosaveTimeoutRef.current = null;
      }
    };
  }, [draft, application, token, accountHydrated]);

  const hydratedAccount = useMemo(
    () => (accountHydrated || !application ? account : mapAccountStepValues(application)),
    [account, accountHydrated, application],
  );
  const completionPercent = application?.completionPercent ?? estimateCompletion(draft, hydratedAccount, providerType);
  const missingItems = application?.missingItems ?? clientMissingItems(draft, hydratedAccount, providerType);
  const currentStepIndex = Math.max(0, steps.findIndex(([id]) => id === activeStep));
  const contactSatisfied = contactVerificationSatisfied(application, contactVerification);
  const validation = useMemo(
    () => validateDraft(draft, hydratedAccount, providerType, { specialityNames: specialityOptions }),
    [draft, hydratedAccount, providerType, specialityOptions],
  );
  const requiredRemainingCount = validation.requiredRemaining + (contactSatisfied ? 0 : 1);
  const unsavedChanges = Boolean(application) && draftSnapshot(draft) !== savedDraftSnapshotRef.current;
  const conflict = Boolean(error && error.toLowerCase().includes("another session"));
  const verificationPending = application ? !contactSatisfied : false;
  const applicationEditable = isApplicationEditable(application?.status);
  const readOnlyApplication = Boolean(application && !applicationEditable);
  const lockedStepIndex = useMemo(() => {
    const firstIncomplete = steps.findIndex(([stepId]) => !stepCompletion(stepId, validation, application, draft, contactSatisfied));
    return firstIncomplete === -1 ? steps.length - 1 : Math.max(0, firstIncomplete);
  }, [application, contactSatisfied, draft, validation]);
  const stepStates: ProviderOnboardingStepState[] = useMemo(() => {
    return steps.map(([id, label], index) => {
      const complete = stepCompletion(id, validation, application, draft, contactSatisfied);
      const current = id === activeStep;
      const disabled = !readOnlyApplication && index > lockedStepIndex && !current;
      const summary = id === "account"
        ? contactSatisfied
          ? contactVerificationSummary(contactVerification ?? application?.contactVerification ?? null, hydratedAccount.email, hydratedAccount.phone)
          : "Contact verification required"
        : id === "organisation"
          ? `${requiredRemainingCount} required items remaining`
          : id === "professional"
            ? `${providerTypeLabel(providerType)} details`
            : id === "services"
              ? `${draft.services?.length ?? 0} services selected`
              : id === "locations"
                ? `${draft.locations?.length ?? 0} location${draft.locations?.length === 1 ? "" : "s"}`
                : id === "branding"
                  ? `${application?.documents.length ?? 0} documents uploaded`
                  : id === "preview"
                    ? "Preview only"
                    : application?.status === "CHANGES_REQUESTED"
                      ? "Resubmission ready"
                      : "Submission review";
      return {
        id,
        label,
        completed: complete,
        current,
        disabled: disabled && !current,
        errorCount: id === activeStep ? Object.values(validation.errors).filter(Boolean).length : 0,
        summary,
      };
    });
  }, [activeStep, application, contactSatisfied, contactVerification, draft, hydratedAccount.email, hydratedAccount.phone, lockedStepIndex, providerType, requiredRemainingCount, validation]);
  const stepPosition = `${currentStepIndex + 1} of ${steps.length}`;
  const specialitySelectOptions = useMemo(() => specialityOptions.map((item) => ({ value: item, label: item })), [specialityOptions]);
  const languageSelectOptions = useMemo(() => languageOptions.map((item) => ({ value: item, label: item })), []);
  const facilitySelectOptions = useMemo(() => facilityOptions.map((item) => ({ value: item, label: item })), []);
  const ownershipSelectOptions = useMemo(() => ownershipOptions.map((item) => ({ value: item, label: item })), []);

  function markDraftChanged(nextDraft: ProviderApplicationPayload) {
    latestDraftRef.current = nextDraft;
    latestFormStateRef.current = nextDraft;
    if (draftSnapshot(nextDraft) !== savedDraftSnapshotRef.current) {
      dirtyRevisionRef.current += 1;
    }
  }

  function clearAutosaveTimer() {
    if (autosaveTimeoutRef.current != null) {
      window.clearTimeout(autosaveTimeoutRef.current);
      autosaveTimeoutRef.current = null;
    }
  }

  function traceSave(reason: SaveReason, phase: "requested" | "queued" | "start" | "skipped" | "success" | "failed", details: Record<string, unknown> = {}) {
    if (import.meta.env.DEV) {
      console.debug("[provider-onboarding-save]", phase, reason, details);
    }
  }

  async function refreshChangeRequests(applicationId: string, onboardingToken: string) {
    try {
      const items = await loadProviderChangeRequests(applicationId, onboardingToken);
      if (!mountedRef.current) return;
      setChangeRequests(items.map((item) => ({
        id: item.id,
        reviewerMessage: item.reviewerMessage,
        providerResponseNote: item.providerResponseNote,
        requestedSections: item.requestedSections,
        resolved: item.resolved,
      })));
    } catch {
      if (mountedRef.current) {
        setChangeRequests([]);
      }
    }
  }

  async function requestSave(reason: SaveReason, options: { showMessage?: boolean } = {}) {
    if (!application || !token) return true;
    if (!applicationEditable) {
      traceSave(reason, "skipped", { snapshot: "read-only" });
      return true;
    }
    const showMessage = options.showMessage ?? reason !== "autosave";
    if (reason !== "autosave") {
      clearAutosaveTimer();
    }
    const payloadAtSchedule = latestFormStateRef.current;
    traceSave(reason, "requested", {
      inFlight: Boolean(savePromiseRef.current),
      revision: dirtyRevisionRef.current,
      version: latestVersionRef.current ?? application.version,
    });
    if (reason === "autosave" && !accountHydrated) {
      traceSave(reason, "skipped", { snapshot: "hydration-pending" });
      return true;
    }
    if (draftSnapshot(payloadAtSchedule) === savedDraftSnapshotRef.current && reason === "autosave") {
      traceSave(reason, "skipped", { snapshot: "clean" });
      return true;
    }
    if (savePromiseRef.current) {
      if (reason !== "autosave") {
        queuedSaveRef.current = reason;
      }
      traceSave(reason, "queued", { queued: queuedSaveRef.current ?? null });
      return savePromiseRef.current;
    }

    const run = async (): Promise<boolean> => {
      let nextReason: SaveReason | null = reason;
      let saved = true;
      while (nextReason) {
        const currentReason = nextReason;
        nextReason = null;
        const currentApplication = latestApplicationRef.current;
        const currentToken = latestTokenRef.current;
        if (!currentApplication || !currentToken) {
          break;
        }

        const payload = latestFormStateRef.current;
        if (draftSnapshot(payload) === savedDraftSnapshotRef.current) {
          traceSave(currentReason, "skipped", {
            snapshot: "clean",
            version: latestVersionRef.current ?? currentApplication.version,
          });
          if (showMessage && mountedRef.current && currentReason !== "autosave") {
            setStatusMessage("Draft saved.");
          }
        } else {
          saveInFlightRef.current = true;
          pendingSaveRef.current = currentReason;
          const revisionAtStart = dirtyRevisionRef.current;
          traceSave(currentReason, "start", {
            revision: revisionAtStart,
            version: latestVersionRef.current ?? currentApplication.version,
          });
          if (mountedRef.current) {
            setSaving(true);
            setError(null);
          }
          try {
            const updated = await updateProviderApplication(currentApplication.id, currentToken, {
              ...payload,
              version: latestVersionRef.current ?? currentApplication.version,
            });
            const normalizedDraft = toDraftPayload(updated);
            const hydratedAccount = mapAccountStepValues(updated);
            latestApplicationRef.current = updated;
            latestVersionRef.current = updated.version;
            savedDraftSnapshotRef.current = draftSnapshot(updated);
            setAccount(hydratedAccount);
            setAccountHydrated(true);

            if (dirtyRevisionRef.current === revisionAtStart) {
              latestDraftRef.current = normalizedDraft;
              latestFormStateRef.current = normalizedDraft;
              lastPersistedRevisionRef.current = dirtyRevisionRef.current;
              if (mountedRef.current) {
                setApplication(updated);
                setDraft(normalizedDraft);
              }
            } else {
              const mergedDraft = { ...latestFormStateRef.current, version: updated.version };
              latestDraftRef.current = mergedDraft;
              latestFormStateRef.current = mergedDraft;
              lastPersistedRevisionRef.current = revisionAtStart;
              if (mountedRef.current) {
                setApplication(updated);
                setDraft(mergedDraft);
              }
            }

            void refreshChangeRequests(updated.id, currentToken);
            if (showMessage && mountedRef.current && currentReason !== "autosave") {
              setStatusMessage("Draft saved.");
            }
            traceSave(currentReason, "success", {
              version: updated.version,
              status: updated.status,
            });
          } catch (ex) {
            saved = false;
            traceSave(currentReason, "failed", {
              error: ex instanceof Error ? ex.message : "Draft could not be saved.",
            });
            if (mountedRef.current) {
              setError(ex instanceof Error ? ex.message : "Draft could not be saved.");
            }
            break;
          } finally {
            saveInFlightRef.current = false;
            pendingSaveRef.current = null;
            if (mountedRef.current) {
              setSaving(false);
            }
          }
        }

        if (queuedSaveRef.current) {
          nextReason = queuedSaveRef.current;
          queuedSaveRef.current = null;
        }
      }
      return saved;
    };

    const promise = run().finally(() => {
      if (savePromiseRef.current === promise) {
        savePromiseRef.current = null;
      }
    });
    savePromiseRef.current = promise;
    return promise;
  }

  async function goToStep(step: string, reason: SaveReason = "navigation") {
    const saved = applicationEditable
      ? await requestSave(reason, { showMessage: false })
      : true;
    if (!saved || !mountedRef.current) return;
    if (params.applicationId) {
      navigate(`/provider/onboarding/${params.applicationId}/${step}`);
      return;
    }
    setSearchParams({ step });
  }

  function patchDraft(patch: ProviderApplicationPayload) {
    if (!applicationEditable) return;
    setDraft((current) => {
      const next = { ...current, ...patch };
      markDraftChanged(next);
      return next;
    });
  }

  async function createDraft(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await createProviderApplication({ providerType: routeProviderType ?? providerType, ...account });
      if (created.onboardingToken) {
        localStorage.setItem(tokenStorageKey, created.onboardingToken);
        localStorage.removeItem(TOKEN_KEY);
        setToken(created.onboardingToken);
      }
      setApplication(created);
      const normalizedDraft = toDraftPayload(created);
      const hydratedAccount = mapAccountStepValues(created);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = created;
      latestVersionRef.current = created.version;
      savedDraftSnapshotRef.current = draftSnapshot(created);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      setStatusMessage("Draft created. Continue completing your profile.");
      navigate(`/provider/onboarding/${created.id}/organisation`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Could not create your provider draft.");
    } finally {
      setSaving(false);
    }
  }

  async function uploadDocument(type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file || !application || !token) return;
    if (!applicationEditable) return;
    if (!["image/png", "image/jpeg", "application/pdf"].includes(file.type)) {
      setError("Upload PNG, JPEG, or PDF files only.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await uploadProviderDocument(application.id, token, type, file);
      const refreshed = await loadProviderApplication(token);
      const normalizedDraft = toDraftPayload(refreshed);
      const hydratedAccount = mapAccountStepValues(refreshed);
      setApplication(refreshed);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = refreshed;
      latestVersionRef.current = refreshed.version;
      savedDraftSnapshotRef.current = draftSnapshot(refreshed);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      setProviderType(refreshed.providerType);
      void refreshChangeRequests(refreshed.id, token);
      setStatusMessage(`${file.name} uploaded.`);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Document upload failed.");
    } finally {
      setSaving(false);
    }
  }

  async function loadPreviewPanel() {
    if (!application || !token) return;
    const saved = await requestSave("preview", { showMessage: false });
    if (!saved) return;
    try {
      const result = await loadProviderPreview(application.id, token);
      setPreview(result);
      setStatusMessage("Preview refreshed.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Preview could not be loaded.");
    }
  }

  async function refreshVerificationState(message: string) {
    if (!token) return;
    const refreshed = await loadProviderApplication(token);
    const normalizedDraft = toDraftPayload(refreshed);
    const hydratedAccount = mapAccountStepValues(refreshed);
    setApplication(refreshed);
    setDraft(normalizedDraft);
    setAccount(hydratedAccount);
    setAccountHydrated(true);
    latestDraftRef.current = normalizedDraft;
    latestFormStateRef.current = normalizedDraft;
    latestApplicationRef.current = refreshed;
    latestVersionRef.current = refreshed.version;
    savedDraftSnapshotRef.current = draftSnapshot(refreshed);
    dirtyRevisionRef.current = 0;
    lastPersistedRevisionRef.current = 0;
    setStatusMessage(message);
  }

  async function requestContactVerification(channel: "email" | "phone") {
    if (!application || !token) return;
    if (!applicationEditable) return;
    setVerificationBusy(true);
    setError(null);
    try {
      const result = channel === "email"
        ? await requestProviderEmailVerification(application.id, token)
        : await requestProviderPhoneVerification(application.id, token);
      await refreshVerificationState(result.message);
      if (channel === "email") {
        setEmailVerification((current) => ({ ...current, requestMessage: result.message, devCode: result.devCode, resendAfterSeconds: result.resendAfterSeconds }));
      } else {
        setPhoneVerification((current) => ({ ...current, requestMessage: result.message, devCode: result.devCode, resendAfterSeconds: result.resendAfterSeconds }));
      }
      setStatusMessage(result.message);
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Verification code could not be requested.");
    } finally {
      setVerificationBusy(false);
    }
  }

  async function verifyContact(channel: "email" | "phone") {
    if (!application || !token) return;
    if (!applicationEditable) return;
    const code = (channel === "email" ? emailVerification.code : phoneVerification.code).trim();
    if (!code) {
      setError("Enter the verification code.");
      return;
    }
    setVerificationBusy(true);
    setError(null);
    try {
      const result = channel === "email"
        ? await verifyProviderEmail(application.id, token, code)
        : await verifyProviderPhone(application.id, token, code);
      if (channel === "email") {
        setEmailVerification((current) => ({ ...current, code: "", requestMessage: "", devCode: null }));
      } else {
        setPhoneVerification((current) => ({ ...current, code: "", requestMessage: "", devCode: null }));
      }
      await refreshVerificationState(channel === "email" ? "Email verified." : "Phone verified.");
      setContactVerification(result);
      setStatusMessage(channel === "email" ? "Email verified." : "Phone verified.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Verification could not be completed.");
    } finally {
      setVerificationBusy(false);
    }
  }

  function verificationPanel() {
    const contact = contactVerification ?? application?.contactVerification ?? null;
    const emailStatus = contact?.emailStatus ?? (account.email ? "NOT_VERIFIED" : "NOT_VERIFIED");
    const phoneStatus = contact?.phoneStatus ?? (account.phone ? "NOT_VERIFIED" : "NOT_VERIFIED");
    const emailVerified = emailStatus === "VERIFIED";
    const phoneVerified = phoneStatus === "VERIFIED";
    const emailPending = emailStatus === "PENDING";
    const phonePending = phoneStatus === "PENDING";
    return (
      <div className="verification-panel">
        <div className="verification-row">
          <div>
            <strong>Email</strong>
            <p>{account.email || application?.email || "Email not set"}</p>
            <small>{emailVerified ? "Verified" : emailPending ? "Verification sent" : "Not verified"}</small>
          </div>
          <div className="cta-row">
            <button className="secondary-button" type="button" onClick={() => void requestContactVerification("email")} disabled={verificationBusy || !account.email}>
              {emailPending ? "Resend email" : "Send verification email"}
            </button>
          </div>
        </div>
        <div className="verification-row">
          <div>
            <strong>Phone</strong>
            <p>{account.phone || application?.phone || "Phone not set"}</p>
            <small>{phoneVerified ? "Verified" : phonePending ? "OTP sent" : "Not verified"}</small>
          </div>
          <div className="cta-row">
            <button className="secondary-button" type="button" onClick={() => void requestContactVerification("phone")} disabled={verificationBusy || !account.phone}>
              {phonePending ? "Resend OTP" : "Send phone OTP"}
            </button>
          </div>
        </div>
        {!emailVerified ? (
          <label>
            Email code
            <input value={emailVerification.code} onChange={(event) => setEmailVerification((current) => ({ ...current, code: event.target.value }))} placeholder="Enter verification code" />
          </label>
        ) : null}
        {!phoneVerified ? (
          <label>
            OTP
            <input value={phoneVerification.code} onChange={(event) => setPhoneVerification((current) => ({ ...current, code: event.target.value }))} placeholder="Enter 6-digit OTP" />
          </label>
        ) : null}
        <div className="cta-row">
          {!emailVerified ? <button className="primary-button" type="button" onClick={() => void verifyContact("email")} disabled={verificationBusy || !emailVerification.code.trim()}>Verify email</button> : null}
          {!phoneVerified ? <button className="primary-button" type="button" onClick={() => void verifyContact("phone")} disabled={verificationBusy || !phoneVerification.code.trim()}>Verify phone</button> : null}
        </div>
        <small>{contactSatisfied ? "Contact verification complete." : "Verify at least one contact method before submitting."}</small>
        {emailVerification.devCode ? <small>Dev email code: {emailVerification.devCode}</small> : null}
        {phoneVerification.devCode ? <small>Dev OTP: {phoneVerification.devCode}</small> : null}
      </div>
    );
  }

  async function submit() {
    if (!application || !token) return;
    setSaving(true);
    setError(null);
    try {
      const submitted = application.status === "CHANGES_REQUESTED"
        ? await resubmitProviderApplication(application.id, token, responseNote || undefined)
        : await submitProviderApplication(application.id, token);
      const normalizedDraft = toDraftPayload(submitted);
      const hydratedAccount = mapAccountStepValues(submitted);
      setApplication(submitted);
      setDraft(normalizedDraft);
      setAccount(hydratedAccount);
      setAccountHydrated(true);
      latestDraftRef.current = normalizedDraft;
      latestFormStateRef.current = normalizedDraft;
      latestApplicationRef.current = submitted;
      latestVersionRef.current = submitted.version;
      savedDraftSnapshotRef.current = draftSnapshot(submitted);
      dirtyRevisionRef.current = 0;
      lastPersistedRevisionRef.current = 0;
      void refreshChangeRequests(submitted.id, token);
      setStatusMessage("Submitted for verification. You can return here to track review progress.");
    } catch (ex) {
      setError(ex instanceof Error ? ex.message : "Submission could not be completed.");
    } finally {
      setSaving(false);
    }
  }

  const providerPublicId = application?.id ?? "";
  const previewBranding = preview?.branding;
  const previewLogoPath = previewBranding?.logoDocumentId ? providerDocumentContentPath(providerPublicId, previewBranding.logoDocumentId) : null;
  const previewCoverPath = previewBranding?.coverImageDocumentId ? providerDocumentContentPath(providerPublicId, previewBranding.coverImageDocumentId) : null;
  const previewGalleryPaths = (previewBranding?.galleryDocumentIds ?? []).map((documentId) => providerDocumentContentPath(providerPublicId, documentId));

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
          <span>{application ? `${providerReferencePrefix(application.providerType)} reference` : `${providerReferencePrefix(providerType)} reference prefix`}</span>
          <span>{application?.status.replaceAll("_", " ") ?? "Draft not created"}</span>
          <div className="progress-track" aria-label={`${completionPercent}% complete`}>
            <span style={{ width: `${completionPercent}%` }} />
          </div>
          <small>{completionPercent}% Complete</small>
        </aside>
      </div>

      <div className="provider-portal-layout">
        <ProviderOnboardingStepper
          steps={stepStates}
          onSelect={(stepId) => {
            void goToStep(stepId, "route-transition");
          }}
        />

        <div className="provider-workspace">
          <ProviderSaveStatus
            saving={saving}
            statusMessage={statusMessage}
            unsavedChanges={unsavedChanges}
            conflict={conflict}
            autosaveEnabled={application ? applicationEditable : false}
          />
          {readOnlyApplication ? (
            <div className="provider-readonly-banner" role="status" aria-live="polite">
              <strong>Application submitted</strong>
              <span>Your profile is read-only while Jeevanam reviews it.</span>
              <span>You can edit it again if changes are requested.</span>
            </div>
          ) : null}
          <div className="provider-progress-summary" aria-label="Onboarding progress summary">
            <strong>Step {stepPosition}</strong>
            <span>{completionPercent}% complete</span>
            <span>{validation.requiredRemaining} required items remaining</span>
            <span>{application ? `${application.missingItems.length} missing items` : `${validation.requiredRemaining} missing items`}</span>
            <span>{verificationPending ? "Verification pending" : application?.status === "PUBLISHED" ? "Published" : application?.status ?? "Draft"}</span>
          </div>
          {application && applicationEditable ? <div className="provider-save-actions"><button className="secondary-button" type="button" onClick={() => void requestSave("manual", { showMessage: true })}>Save draft</button></div> : null}
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
                  <span>{contactVerification?.emailStatus === "VERIFIED" ? "Email verified" : contactVerification?.emailStatus === "PENDING" ? "Email verification sent" : "Email not verified"}</span>
                  <span>{contactVerification?.phoneStatus === "VERIFIED" ? "Phone verified" : contactVerification?.phoneStatus === "PENDING" ? "Phone OTP sent" : "Phone not verified"}</span>
                </div>
                <p className="panel-help">Change email or phone here if needed. Saving the draft resets verification for the updated contact details.</p>
                <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
                <div className="form-grid">
                  <FieldShell label="Email" helperText="Used for verification and account recovery." error={validation.errors.account}>
                    <input
                      type="email"
                      aria-invalid={Boolean(validation.errors.account)}
                      value={account.email}
                      onChange={(event) => {
                        const nextEmail = event.target.value;
                        setAccount((current) => ({ ...current, email: nextEmail }));
                        patchDraft({ email: nextEmail });
                      }}
                    />
                  </FieldShell>
                  <FieldShell label="Phone" helperText="Used for OTP delivery and verification." error={validation.errors.account}>
                    <input
                      value={account.phone}
                      aria-invalid={Boolean(validation.errors.account)}
                      onChange={(event) => {
                        const nextPhone = event.target.value;
                        setAccount((current) => ({ ...current, phone: nextPhone }));
                        patchDraft({ phone: nextPhone });
                      }}
                    />
                  </FieldShell>
                </div>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={account.termsAccepted}
                    onChange={(event) => {
                      const nextChecked = event.target.checked;
                      setAccount((current) => ({ ...current, termsAccepted: nextChecked }));
                      patchDraft({ termsAccepted: nextChecked });
                    }}
                  />
                  I accept the Terms.
                </label>
                <label className="checkbox-row">
                  <input
                    type="checkbox"
                    checked={account.privacyAccepted}
                    onChange={(event) => {
                      const nextChecked = event.target.checked;
                      setAccount((current) => ({ ...current, privacyAccepted: nextChecked }));
                      patchDraft({ privacyAccepted: nextChecked });
                    }}
                  />
                  I accept the Privacy policy.
                </label>
                {verificationPanel()}
                </fieldset>
                <div className="cta-row">
                  <button className="secondary-button" type="button" onClick={() => void goToStep("organisation")} disabled={applicationEditable ? !stepCompletion("account", validation, application, draft, contactSatisfied) : false}>
                    Continue
                  </button>
                </div>
              </Panel>
            )
          ) : null}

          {activeStep === "organisation" ? (
            <Panel title="Organisation">
              <p className="panel-help">Use the official provider identity that patients will see on the public profile.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="form-grid">
                <FieldShell label={copy.nameLabel} helperText="Displayed publicly on the provider profile." error={validation.errors.displayName}>
                  <input
                    aria-invalid={Boolean(validation.errors.displayName)}
                    value={draft.displayName ?? ""}
                    onChange={(event) => patchDraft({ displayName: event.target.value, legalName: event.target.value })}
                    placeholder={copy.nameLabel}
                  />
                </FieldShell>
                <FieldShell label={copy.registrationLabel} helperText="Official registration document number." error={validation.errors.registrationNumber}>
                  <input
                    aria-invalid={Boolean(validation.errors.registrationNumber)}
                    value={draft.registrationNumber ?? ""}
                    onChange={(event) => patchDraft({ registrationNumber: event.target.value })}
                    placeholder={copy.registrationLabel}
                  />
                </FieldShell>
                {providerType !== "INDIVIDUAL_DOCTOR" ? (
                  <FieldShell label="Organisation type" helperText="Describe the practice structure." error={validation.errors.organisationType}>
                    <input
                      aria-invalid={Boolean(validation.errors.organisationType)}
                      value={draft.organisationType ?? ""}
                      onChange={(event) => patchDraft({ organisationType: event.target.value })}
                      placeholder="Group practice, standalone, chain, etc."
                    />
                  </FieldShell>
                ) : null}
                <FieldShell label="Website" helperText="Public website. Must include https://." error={validation.errors.website}>
                  <input
                    type="url"
                    aria-invalid={Boolean(validation.errors.website)}
                    value={draft.website ?? ""}
                    onChange={(event) => patchDraft({ website: event.target.value })}
                    placeholder="https://example.com"
                  />
                </FieldShell>
                <FieldShell label="GST" helperText="Optional. Only used for verification." error={validation.errors.gstNumber}>
                  <input
                    aria-invalid={Boolean(validation.errors.gstNumber)}
                    value={draft.gstNumber ?? ""}
                    onChange={(event) => patchDraft({ gstNumber: event.target.value.toUpperCase() })}
                    placeholder="15-character GST number"
                  />
                </FieldShell>
                {providerType !== "INDIVIDUAL_DOCTOR" ? (
                  <ProviderDropdownField
                    label="Ownership"
                    helperText="Select the ownership category."
                    error={validation.errors.ownership}
                    value={draft.ownership ?? ""}
                    options={ownershipSelectOptions}
                    onChange={(value) => patchDraft({ ownership: value })}
                    placeholder="Select ownership"
                    disabled={!applicationEditable}
                  />
                ) : null}
                {providerType === "HOSPITAL" ? (
                  <FieldShell label="Hospital type" helperText="Describe the hospital profile type." error={null}>
                    <input
                      value={draft.hospitalType ?? ""}
                      onChange={(event) => patchDraft({ hospitalType: event.target.value })}
                      placeholder="Multi-speciality, super speciality, etc."
                    />
                  </FieldShell>
                ) : null}
              </div>
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "professional" ? (
            <Panel title="Professional details">
              <p className="panel-help">Add the details that support the public profile and review process.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="form-grid">
                {providerType === "INDIVIDUAL_DOCTOR" ? <>
                  <FieldShell label="Gender" helperText="Optional." error={null}><input value={draft.gender ?? ""} onChange={(event) => patchDraft({ gender: event.target.value })} /></FieldShell>
                  <FieldShell label="Date of birth" helperText="Publicly hidden unless required for verification." error={null}><input type="date" value={draft.dateOfBirth ?? ""} onChange={(event) => patchDraft({ dateOfBirth: event.target.value })} /></FieldShell>
                  <FieldShell label="Qualification" helperText="Displayed publicly on your profile." error={validation.errors.qualification}><input aria-invalid={Boolean(validation.errors.qualification)} value={draft.qualification ?? ""} onChange={(event) => patchDraft({ qualification: event.target.value })} /></FieldShell>
                  <FieldShell label="Medical council" helperText="Registration details used for review." error={validation.errors.medicalCouncil}><input aria-invalid={Boolean(validation.errors.medicalCouncil)} value={draft.medicalCouncil ?? ""} onChange={(event) => patchDraft({ medicalCouncil: event.target.value })} /></FieldShell>
                  <FieldShell label="Experience" helperText="Years of practice." error={validation.errors.yearsOfExperience}><input type="number" min={0} value={draft.yearsOfExperience ?? 0} onChange={(event) => patchDraft({ yearsOfExperience: Number(event.target.value) })} /></FieldShell>
                  <FieldShell label="Consultation fee" helperText="Optional. Shown publicly when enabled." error={null}><input type="number" min={0} value={draft.consultationFee ?? ""} onChange={(event) => patchDraft({ consultationFee: Number(event.target.value) })} /></FieldShell>
                </> : <>
                  {providerType === "HOSPITAL" ? (
                    <FieldShell label="Beds" helperText="Number of licensed beds." error={validation.errors.beds}>
                      <input type="number" min={1} value={draft.beds ?? 0} onChange={(event) => patchDraft({ beds: Number(event.target.value) })} />
                    </FieldShell>
                  ) : null}
                  {providerType === "HOSPITAL" ? (
                    <FieldShell label="Medical director" helperText="Lead physician responsible for the hospital." error={validation.errors.medicalDirector}>
                      <input aria-invalid={Boolean(validation.errors.medicalDirector)} value={draft.medicalDirector ?? ""} onChange={(event) => patchDraft({ medicalDirector: event.target.value })} />
                    </FieldShell>
                  ) : null}
                </>}
                <ProviderMultiSelectField
                  label="Languages"
                  helperText="Patients will see these."
                  error={validation.errors.languages}
                  value={validation.languages}
                  options={languageSelectOptions}
                  onChange={(value) => patchDraft({ languages: normalizeSearchList(value) })}
                  placeholder="Search languages"
                  disabled={!applicationEditable}
                />
                <ProviderMultiSelectField
                  label="Specialities"
                  helperText="Choose from the speciality master."
                  error={validation.errors.specialities}
                  value={validation.specialities}
                  options={specialitySelectOptions}
                  onChange={(value) => patchDraft({ specialities: normalizeSearchList(value), departments: normalizeSearchList(value) })}
                  placeholder="Search specialities"
                  loading={!specialityOptions.length}
                  disabled={!applicationEditable}
                />
                <ProviderMultiSelectField
                  label="Facilities"
                  helperText="Select the facilities available at this location."
                  error={validation.errors.facilities}
                  value={validation.facilities}
                  options={facilitySelectOptions}
                  onChange={(value) => patchDraft({ facilities: normalizeSearchList(value) })}
                  placeholder="Search facilities"
                  disabled={!applicationEditable}
                />
              </div>
              <FieldShell label="Biography" helperText="Displayed publicly on your profile. Maximum 1500 characters." error={validation.errors.biography}>
                <textarea
                  aria-invalid={Boolean(validation.errors.biography)}
                  value={draft.biography ?? ""}
                  onChange={(event) => patchDraft({ biography: event.target.value.slice(0, 1500) })}
                  maxLength={1500}
                  placeholder="Summarise experience, focus areas, and care approach."
                />
              </FieldShell>
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "services" ? <Panel title="Services"><fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>{serviceOptions.map((service) => <ServiceToggle key={service.type} service={service} draft={draft} patchDraft={patchDraft} disabled={!applicationEditable} />)}</fieldset></Panel> : null}

          {activeStep === "locations" ? (
            <Panel title="Locations">
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
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
              <LocationPicker
                providerName={draft.displayName || copy.nameLabel}
                location={draft.locations?.[0] ?? null}
                editable={applicationEditable}
                onCoordinatesChange={(coordinates) => patchLocation(draft, patchDraft, {
                  latitude: coordinates?.latitude ?? null,
                  longitude: coordinates?.longitude ?? null,
                })}
              />
              </fieldset>
            </Panel>
          ) : null}

          {activeStep === "branding" ? (
            <Panel title="Branding and documents">
              <p className="panel-help">Upload the assets patients will recognize on the public page.</p>
              <fieldset className="provider-readonly-fieldset" disabled={!applicationEditable}>
              <div className="upload-grid">
                <UploadBox label="Logo" type="LOGO" uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label="Cover image" type="COVER_IMAGE" uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label={providerType === "INDIVIDUAL_DOCTOR" ? "Doctor photo" : "Gallery image"} type={providerType === "INDIVIDUAL_DOCTOR" ? "DOCTOR_PHOTO" : "GALLERY_IMAGE"} uploadDocument={uploadDocument} disabled={!applicationEditable} />
                <UploadBox label="Registration document" type="REGISTRATION_CERTIFICATE" uploadDocument={uploadDocument} disabled={!applicationEditable} />
              </div>
              <FieldShell label="Tagline" helperText="Short public brand line. Optional." error={null}>
                <input
                  value={draft.branding?.tagline ?? ""}
                  onChange={(event) => patchDraft({ branding: { ...draft.branding, tagline: event.target.value } })}
                  placeholder="One line summary"
                />
              </FieldShell>
              </fieldset>
              <div className="document-list">{application?.documents.map((document) => <span key={document.id}>{document.documentType.replaceAll("_", " ")} · {document.originalFilename}</span>)}</div>
            </Panel>
          ) : null}

          {activeStep === "preview" ? (
            <Panel title="Public profile preview">
              <p className="panel-help">This preview uses the same public profile data that patients will see.</p>
              <button className="secondary-button" type="button" onClick={() => void loadPreviewPanel()}>Refresh preview</button>
              <article className="profile-preview-card provider-public-preview">
                <div className="provider-public-hero">
                  <div className="provider-public-hero-media">
                    <div className="provider-public-cover-frame">
                      <PublicMediaImage
                        src={previewCoverPath}
                        alt={`${preview?.displayName ?? draft.displayName ?? "Provider"} cover image`}
                        className="landing-cover-image"
                        objectFit="cover"
                        fallback={<div className="landing-cover-fallback" aria-hidden="true" />}
                        token={token}
                      />
                    </div>
                    <div className="provider-public-avatar-frame">
                      <PublicMediaImage
                        src={previewLogoPath}
                        alt={`${preview?.displayName ?? draft.displayName ?? "Provider"} logo`}
                        className="landing-avatar-image"
                        objectFit="contain"
                        fallback={(
                          <div className="landing-avatar-fallback" aria-hidden="true">
                            <span>{providerType === "INDIVIDUAL_DOCTOR" ? "DR" : providerType === "CLINIC" ? "CL" : "HS"}</span>
                          </div>
                        )}
                        token={token}
                      />
                    </div>
                  </div>
                  <div>
                    <span className="eyebrow">{providerTypeLabel(providerType)} profile</span>
                    <h2>{preview?.displayName ?? draft.displayName ?? "Profile name pending"}</h2>
                    <p>{preview?.subtitle ?? draft.qualification ?? draft.organisationType ?? draft.hospitalType ?? "Profile details pending"}</p>
                  </div>
                </div>
                <div className="provider-public-section-grid">
                  <section>
                    <strong>Biography</strong>
                    <p>{preview?.biography ?? draft.biography ?? "Biography will appear here once published."}</p>
                  </section>
                  <section>
                    <strong>Services</strong>
                    <div className="directory-badge-row">{(preview?.services ?? draft.services?.map((item) => item.label) ?? []).map((item) => <span className="chip" key={item}>{item}</span>)}</div>
                  </section>
                  <section>
                    <strong>Languages</strong>
                    <div className="directory-badge-row">{validation.languages.map((item) => <span className="chip" key={item}>{item}</span>)}</div>
                  </section>
                  <section>
                    <strong>Facilities</strong>
                    <div className="directory-badge-row">{validation.facilities.map((item) => <span className="chip" key={item}>{item}</span>)}</div>
                  </section>
                  <section>
                    <strong>Locations</strong>
                    <p>{preview?.locationSummary ?? draft.locations?.[0]?.city ?? "Location pending"}</p>
                    <LocationDisplayMap
                      providerName={preview?.displayName ?? draft.displayName ?? copy.nameLabel}
                      locations={draft.locations ?? []}
                      compact
                    />
                  </section>
                  <section>
                    <strong>Branding</strong>
                    <p>{previewBranding?.tagline || draft.branding?.tagline || "Branding tagline pending."}</p>
                    {previewGalleryPaths.length ? (
                      <div className="landing-gallery-grid provider-public-gallery">
                        {previewGalleryPaths.map((url, index) => (
                          <article className="landing-gallery-card" key={url}>
                            <div className="landing-gallery-media">
                              <PublicMediaImage
                                src={url}
                                alt={`${preview?.displayName ?? draft.displayName ?? "Provider"} gallery image ${index + 1}`}
                                className="landing-gallery-image"
                                objectFit="cover"
                                fallback={<div className="landing-gallery-fallback" aria-hidden="true" />}
                                token={token}
                              />
                            </div>
                            <strong>Gallery image {index + 1}</strong>
                          </article>
                        ))}
                      </div>
                    ) : null}
                  </section>
                </div>
                <div className="provider-public-cta">
                  <a className="primary-button" href={careBookingUrl({ provider: preview?.displayName ?? draft.displayName })}>
                    Appointment CTA
                  </a>
                </div>
              </article>
            </Panel>
          ) : null}

          {activeStep === "submit" ? (
            <Panel title="Submit for verification">
              <p className="panel-help">Review the remaining items before submitting for verification.</p>
              <StatusTimeline status={application?.status ?? "DRAFT"} />
              {!contactSatisfied ? (
                <div className="verification-blocking">
                  <strong>Contact verification required</strong>
                  <p>Verify your contact details before submitting your profile.</p>
                  {verificationPanel()}
                </div>
              ) : null}
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
              {missingItems.length ? <div className="missing-list"><strong>Missing information</strong>{missingItems.map((item) => <span key={item}>{missingItemLabel(item)}</span>)}</div> : <p>Your application is ready for review.</p>}
              <button className="primary-button" type="button" onClick={() => void submit()} disabled={!applicationEditable || saving || missingItems.length > 0 || !contactSatisfied}>
                {application?.status === "CHANGES_REQUESTED" ? "Resubmit for verification" : "Submit for verification"}
              </button>
            </Panel>
          ) : null}

          <div className="wizard-footer">
            <button className="secondary-button" type="button" onClick={() => void goToStep(steps[Math.max(0, currentStepIndex - 1)][0])} disabled={currentStepIndex === 0}>Back</button>
            {activeStep !== "submit" ? (
              <button
                className="primary-button"
                type="button"
                onClick={() => void goToStep(steps[Math.min(steps.length - 1, currentStepIndex + 1)][0])}
                disabled={currentStepIndex >= steps.length - 1 || (!readOnlyApplication && stepStates[currentStepIndex + 1]?.disabled)}
              >
                Continue
              </button>
            ) : null}
          </div>
        </div>
      </div>
    </section>
  );
}

function Panel({ title, children }: { title: string; children: React.ReactNode }) {
  return <div className="onboarding-panel"><h2>{title}</h2>{children}</div>;
}

function ServiceToggle({ service, draft, patchDraft, disabled = false }: { service: { type: ProviderServiceType; label: string }; draft: ProviderApplicationPayload; patchDraft: (patch: ProviderApplicationPayload) => void; disabled?: boolean }) {
  const enabled = draft.services?.some((item) => item.serviceType === service.type && item.enabled !== false) ?? false;
  return (
    <label className="service-toggle">
      <input
        type="checkbox"
        checked={enabled}
        disabled={disabled}
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

function UploadBox({ label, type, uploadDocument, disabled = false }: { label: string; type: ProviderDocumentType; uploadDocument: (type: ProviderDocumentType, event: ChangeEvent<HTMLInputElement>) => void; disabled?: boolean }) {
  return <label className="upload-box"><span>{label}</span><input type="file" disabled={disabled} accept=".png,.jpg,.jpeg,.pdf,image/png,image/jpeg,application/pdf" onChange={(event) => uploadDocument(type, event)} /></label>;
}

function StatusTimeline({ status }: { status: string }) {
  const labels = ["Draft", "Profile Complete", "Submitted", "Under Review", "Approved", "Published"];
  return <div className="status-timeline">{labels.map((item) => <span className={status.replaceAll("_", " ").toLowerCase().includes(item.toLowerCase().split(" ")[0]) ? "is-active" : ""} key={item}>{item}</span>)}</div>;
}

function FieldShell({ label, helperText, error, children }: { label: string; helperText?: string; error?: string | null; children: ReactNode }) {
  return (
    <label className="provider-field">
      <span>
        {label}
        {helperText ? <small>{helperText}</small> : null}
      </span>
      {children}
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
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
