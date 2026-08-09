import { type FormEvent, type ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowForwardRounded,
  HealthAndSafetyOutlined,
  LocalHospitalOutlined,
  LocationOnOutlined,
  MedicalServicesOutlined,
  ScienceOutlined,
  SearchOutlined,
  VerifiedOutlined,
} from "@mui/icons-material";
import { Link, Navigate, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import type {
  PublicClinicDetailResponse,
  PublicClinicSummaryResponse,
  PublicDoctorDetailResponse,
  PublicDoctorSummaryResponse,
  PublicHospitalDetailResponse,
  PublicHospitalSummaryResponse,
  PublicPageResponse,
  PublicProviderLocationResponse,
  PublicSpecialityDetailResponse,
  PublicSpecialitySummaryResponse,
} from "../../api/publicCatalog";
import { fetchPublicJson, normalizePublicPageResponse } from "../../api/publicCatalog";
import { discoverConfig } from "../../config";
import {
  AivaDiscoveryAssistantCard,
  ClinicCard,
  DirectoryState,
  DoctorCard,
  InlineDirectoryState,
  HospitalCard,
  PaginationBar,
  QueryToolbar,
  careBookingUrl,
  emptyClinicsPage,
  emptyDoctorsPage,
  emptyHospitalsPage,
  formatConsultationFee,
} from "../../components/DiscoveryComponents";
import {
  AvailabilityTimeline,
  BookingPanel,
  DoctorBreadcrumb,
  RatingSummary,
  RelatedDoctorCard,
  ReviewCard,
  VerificationBadge,
  SpecialtyCard,
  StickyBookingCTA,
  doctorSampleReviews,
  doctorSampleServiceCards,
  doctorSampleSpecialties,
  doctorSampleVerificationBadges,
} from "../../components/discovery/DoctorProfileExperiences";
import {
  AivaComingSoonPanel,
  AlphabetNavigation,
  buildDirectoryResultLabel,
  countActiveDirectoryFilters,
  DirectoryFiltersDrawer,
  DirectoryHero,
  DirectoryPageShell,
  DirectoryPageStickyPanel,
  DirectoryResultList,
  DirectoryResultsToolbar,
  DirectorySearchPanel,
  DirectoryFilterChips,
  DirectoryToggleFilters,
  DoctorDirectoryCard,
  ClinicDirectoryCard,
  HospitalDirectoryCard,
  PopularLinkChipRow,
  PopularSpecialityGrid,
  SpecialityCard,
  pageAccentClass,
  pageAccentTone,
  pageSearchButtonLabel,
  pageSearchPlaceholder,
  splitFilterValues,
  joinFilterValues,
  toggleFilterValue,
} from "../../components/directory/DirectoryComponents";
import { providerBookingPrimaryLabel, normalizeBookingMode } from "../../components/discovery/BookingCapability";
import {
  PublicProviderProfile,
  type PublicProviderProfileDefinitionItem,
  type PublicProviderProfileGalleryItem,
} from "../../components/discovery/PublicProviderProfile";
import { demoClinics, demoDoctors, demoHospitals } from "../../features/home/homeDemoProviders";
import {
  PUBLIC_CURRENT_LOCATION_LABEL,
  PUBLIC_DEFAULT_LOCATION,
  PUBLIC_LOCATION_OPTIONS,
  normalizePublicLocation,
  readStoredPublicLocation,
  type PublicLocationCoordinates,
  usePublicLocation,
} from "../../context/PublicLocationContext";
import { DISCOVER_DETAIL_PATHS, DISCOVER_ROUTES } from "../../routes";
import {
  buildPublicAddressView,
  normalizeDisplayList,
  parseFiniteExperienceYears,
} from "../../utils/publicProfileFormatting";
import {
  discoveryEmptyMessage,
  matchesDiscoveryQuery,
  normalizeDiscoveryText,
  scoreDiscoveryLocation,
  slugify,
} from "../../utils/publicDiscovery";

const discoverHeroIllustrationUrl = "/hero_img.png";

type FetchState<T> = {
  data: T;
  loading: boolean;
  error: string | null;
};

type HomeDemoCard<T> = T & { demo?: boolean };

function mergeHomeCards<T extends object>(realCards: T[], demoCards: T[], targetCount: number, enabled: boolean): HomeDemoCard<T>[] {
  const visible = realCards.slice(0, targetCount).map((card) => ({ ...card, demo: false as const }));
  if (!enabled || visible.length >= targetCount) {
    return visible;
  }
  const remaining = targetCount - visible.length;
  return [...visible, ...demoCards.slice(0, remaining).map((card) => ({ ...card, demo: true as const }))];
}

const FALLBACK_POPULAR_SEARCHES = [
  "General Physician",
  "Pediatrician",
  "Dentist",
  "Dermatologist",
  "Cardiologist",
  "Gynecologist",
  "Orthopedic",
  "Eye Specialist",
] as const;

type HomeCategoryCard = {
  title: string;
  body: string;
  to: string;
  action: string;
  icon: ReactNode;
};

const DISCOVERY_CATEGORY_CARDS: HomeCategoryCard[] = [
  {
    title: "Doctors",
    body: "Find expert doctors.",
    to: DISCOVER_ROUTES.doctors.path,
    action: "View doctors",
    icon: <MedicalServicesOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    title: "Clinics",
    body: "Local clinics & centers.",
    to: DISCOVER_ROUTES.clinics.path,
    action: "View clinics",
    icon: <LocalHospitalOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    title: "Hospitals",
    body: "Multi-speciality hospitals.",
    to: DISCOVER_ROUTES.hospitals.path,
    action: "View hospitals",
    icon: <HealthAndSafetyOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    title: "Specialities",
    body: "Explore by speciality.",
    to: DISCOVER_ROUTES.specialities.path,
    action: "Browse specialities",
    icon: <ScienceOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    title: "Health Packages",
    body: "Preventive health checkups.",
    to: DISCOVER_ROUTES.services.path,
    action: "Explore services",
    icon: <HealthAndSafetyOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    title: "Tests & Diagnostics",
    body: "Lab tests & imaging.",
    to: DISCOVER_ROUTES.services.path,
    action: "Explore services",
    icon: <SearchOutlined fontSize="small" aria-hidden="true" />,
  },
];

const VALUE_PANEL_POINTS = [
  {
    label: "Verified public information",
    body: "Public profiles, services and locations use the published discover data.",
    icon: <VerifiedOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    label: "Location-aware search",
    body: "Search by city, area or current location without forcing a location choice.",
    icon: <LocationOnOutlined fontSize="small" aria-hidden="true" />,
  },
  {
    label: "Appointment handoff",
    body: "Move into booking or continue exploring the right care option.",
    icon: <ArrowForwardRounded fontSize="small" aria-hidden="true" />,
  },
] as const;

function homepageParams(selectedLocation: string, size: number) {
  return {
    city: selectedLocation === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : selectedLocation,
    page: 0,
    size,
  };
}

function usePublicResource<T>(path: string, params: Record<string, string | number | undefined | null>, initialValue: T): FetchState<T> {
  const [state, setState] = useState<FetchState<T>>({
    data: initialValue,
    loading: true,
    error: null,
  });

  useEffect(() => {
    const abortController = new AbortController();
    setState((current) => ({
      data: current.data,
      loading: true,
      error: null,
    }));

    fetchPublicJson<T>(path, params, abortController.signal)
      .then((result) => {
        setState({
          data: result,
          loading: false,
          error: null,
        });
      })
      .catch((error: unknown) => {
        if (abortController.signal.aborted) {
          return;
        }
        setState({
          data: initialValue,
          loading: false,
          error: error instanceof Error ? error.message : "Unable to load public directory data.",
        });
      });

    return () => abortController.abort();
  }, [path, JSON.stringify(params)]);

  return state;
}

type LoadMoreDirectoryPageState<T> = {
  items: T[];
  totalItems: number;
  totalPages: number;
  loadingInitial: boolean;
  loadingMore: boolean;
  initialError: string | null;
  loadMoreError: string | null;
  hasMore: boolean;
  loadMore: () => void;
  retryLoadMore: () => void;
};

function useLoadMoreDirectoryResults<T>({
  path,
  params,
  pageSize = 7,
}: {
  path: string;
  params: Record<string, string | number | undefined | null>;
  pageSize?: number;
}): LoadMoreDirectoryPageState<T> {
  const [pages, setPages] = useState<Record<number, PublicPageResponse<T> | undefined>>({});
  const [pageStatus, setPageStatus] = useState<Record<number, "loading" | "loaded" | "failed">>({});
  const [visiblePageCount, setVisiblePageCount] = useState(1);
  const [retryNonce, setRetryNonce] = useState(0);
  const requestedPagesRef = useRef<Set<number>>(new Set());
  const paramsKey = useMemo(() => JSON.stringify(params), [params]);
  const resolvedParams = useMemo(() => params, [paramsKey]);
  const [initialError, setInitialError] = useState<string | null>(null);
  const [loadMoreError, setLoadMoreError] = useState<string | null>(null);
  const currentPages = useMemo(() => Array.from({ length: visiblePageCount }, (_, page) => pages[page]).filter(Boolean) as PublicPageResponse<T>[], [pages, visiblePageCount]);
  const totalPages = currentPages[0]?.totalPages ?? 0;
  const totalItems = currentPages[0]?.totalItems ?? currentPages.reduce((count, page) => count + page.items.length, 0);
  const loadingInitial = !pages[0] && !initialError;
  const loadingMore = Object.entries(pageStatus).some(([page, status]) => Number(page) > 0 && status === "loading");
  const hasMore = totalPages > visiblePageCount;

  useEffect(() => {
    setPages({});
    setPageStatus({});
    setVisiblePageCount(1);
    setRetryNonce(0);
    requestedPagesRef.current = new Set();
    setInitialError(null);
    setLoadMoreError(null);
  }, [path, pageSize, paramsKey]);

  useEffect(() => {
    const missingPages = Array.from({ length: visiblePageCount }, (_, page) => page).filter(
      (page) => !pages[page] && !requestedPagesRef.current.has(page),
    );
    if (!missingPages.length) {
      return;
    }

    let cancelled = false;
    missingPages.forEach((page) => {
      requestedPagesRef.current.add(page);
      setPageStatus((current) => ({ ...current, [page]: "loading" }));
      fetchPublicJson<PublicPageResponse<T>>(path, { ...resolvedParams, page, size: pageSize })
        .then((result) => {
          if (cancelled) {
            return;
          }
          setPages((current) => ({ ...current, [page]: normalizePublicPageResponse(result) }));
          setPageStatus((current) => ({ ...current, [page]: "loaded" }));
          if (page === 0) {
            setInitialError(null);
          } else {
            setLoadMoreError(null);
          }
          requestedPagesRef.current.delete(page);
        })
        .catch((error: unknown) => {
          if (cancelled) {
            return;
          }
          const message = error instanceof Error ? error.message : "Unable to load directory results.";
          setPageStatus((current) => ({ ...current, [page]: "failed" }));
          requestedPagesRef.current.delete(page);
          if (page === 0) {
            setInitialError(message);
          } else {
            setLoadMoreError(message);
          }
        });
    });

    return () => {
      cancelled = true;
    };
  }, [pageSize, paramsKey, path, resolvedParams, retryNonce, visiblePageCount]);

  function loadMore() {
    setVisiblePageCount((current) => current + 1);
  }

  function retryLoadMore() {
    setRetryNonce((current) => current + 1);
    setLoadMoreError(null);
    setPageStatus((current) => {
      const next = { ...current };
      Object.keys(next).forEach((value) => {
        const page = Number(value);
        if (page > 0 && next[page] === "failed") {
          delete next[page];
        }
      });
      requestedPagesRef.current = new Set(Array.from(requestedPagesRef.current).filter((page) => next[page] !== "failed"));
      return next;
    });
  }

  return {
    items: currentPages.flatMap((page) => page.items),
    totalItems,
    totalPages,
    loadingInitial,
    loadingMore,
    initialError,
    loadMoreError,
    hasMore,
    loadMore,
    retryLoadMore,
  };
}

function filterAndSortDoctorResults(doctors: PublicDoctorSummaryResponse[], query: string, selectedLocation: string) {
  return [...doctors]
    .filter((doctor) =>
      matchesDiscoveryQuery(
        [
          doctor.doctorDisplayName,
          doctor.speciality,
          doctor.clinicDisplayName,
          doctor.area,
          doctor.city,
          doctor.nextAvailableSlotSummary,
          doctor.languages.join(" "),
        ],
        query,
      ),
    )
    .sort((left, right) => {
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.doctorDisplayName).localeCompare(normalizeDiscoveryText(right.doctorDisplayName));
    });
}

function filterAndSortClinicResults(clinics: PublicClinicSummaryResponse[], query: string, selectedLocation: string) {
  return [...clinics]
    .filter((clinic) =>
      matchesDiscoveryQuery(
        [
          clinic.clinicDisplayName,
          clinic.address,
          clinic.area,
          clinic.city,
          clinic.specialities.join(" "),
          clinic.doctorsCount,
        ],
        query,
      ),
    )
    .sort((left, right) => {
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.clinicDisplayName).localeCompare(normalizeDiscoveryText(right.clinicDisplayName));
    });
}

function filterAndSortSpecialities(specialities: PublicSpecialitySummaryResponse[], query: string) {
  return [...specialities]
    .filter((speciality) =>
      matchesDiscoveryQuery([speciality.speciality, speciality.doctorsCount, speciality.clinicsCount], query),
    )
    .sort((left, right) => normalizeDiscoveryText(left.speciality).localeCompare(normalizeDiscoveryText(right.speciality)));
}

function buildDirectorySearchParams({
  query,
  city,
  area,
  page,
  size,
  extra,
}: {
  query?: string | null;
  city?: string | null;
  area?: string | null;
  page: number;
  size: number;
  extra?: Record<string, string | undefined | null>;
}) {
  const params = new URLSearchParams();
  if (query?.trim()) params.set("q", query.trim());
  if (city?.trim()) params.set("city", city.trim());
  if (area?.trim()) params.set("area", area.trim());
  Object.entries(extra ?? {}).forEach(([key, value]) => {
    if (value?.trim()) params.set(key, value.trim());
  });
  params.set("page", `${Math.max(page, 0)}`);
  params.set("size", `${size}`);
  return params;
}

function useDirectoryFilters(defaultSize = 12) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { locationState } = usePublicLocation();
  const selectedLocation =
    locationState.location === PUBLIC_CURRENT_LOCATION_LABEL
      ? PUBLIC_DEFAULT_LOCATION
      : locationState.location || PUBLIC_DEFAULT_LOCATION;
  const queryParam = searchParams.get("q") ?? "";
  const cityParam = searchParams.get("city") ?? "";
  const [query, setQuery] = useState(queryParam);
  const [city, setCity] = useState(cityParam || selectedLocation);
  const [area, setArea] = useState(searchParams.get("area") ?? "");
  const radiusKm = searchParams.get("radiusKm") ?? "";
  const latitude = searchParams.get("lat") ?? "";
  const longitude = searchParams.get("lng") ?? "";
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const size = Number(searchParams.get("size") ?? `${defaultSize}`) || defaultSize;

  useEffect(() => {
    setQuery(searchParams.get("q") ?? "");
    setCity(searchParams.get("city") ?? selectedLocation);
    setArea(searchParams.get("area") ?? "");
  }, [searchParams, selectedLocation]);

  function submit(basePath: string, extra?: Record<string, string | undefined | null>) {
    const params = buildDirectorySearchParams({
      query,
      city: city || selectedLocation,
      area,
      page: 0,
      size: defaultSize,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  function changePage(basePath: string, nextPage: number, extra?: Record<string, string | undefined | null>) {
    const params = buildDirectorySearchParams({
      query: searchParams.get("q"),
      city: searchParams.get("city") || selectedLocation,
      area: searchParams.get("area"),
      page: nextPage,
      size,
      extra,
    });
    navigate(`${basePath}?${params.toString()}`);
  }

  return { searchParams, query, setQuery, city, setCity, area, setArea, radiusKm, latitude, longitude, page, size, submit, changePage };
}

type DirectoryPageKey = "doctors" | "clinics" | "hospitals" | "specialities";

type DirectoryPageState = {
  searchParams: URLSearchParams;
  searchKey: string;
  queryDraft: string;
  setQueryDraft: (value: string) => void;
  sort: string;
  setSort: (value: string) => void;
  radiusKm: string;
  setRadiusKm: (value: string) => void;
  page: number;
  size: number;
  selectedLocation: string;
  selectedCoordinates: PublicLocationCoordinates | null;
  selectedFilterCount: number;
  commitSearch: (basePath: string, extra?: Record<string, string | number | boolean | null | undefined>) => void;
  commitLocation: (basePath: string, nextLocation: string, nextCoordinates?: PublicLocationCoordinates | null) => void;
  updateParams: (basePath: string, updates: Record<string, string | number | boolean | null | undefined>) => void;
  clearParams: (basePath: string, clearQuery?: boolean) => void;
};

function stringifyDirectoryParam(value: string | number | boolean | null | undefined) {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "boolean") {
    return value ? "1" : null;
  }
  const trimmed = `${value}`.trim();
  return trimmed ? trimmed : null;
}

function useDirectoryPageState(defaultSize = 12): DirectoryPageState {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { locationState, setSelectedLocation } = usePublicLocation();
  const searchKey = searchParams.toString();
  const [queryDraft, setQueryDraft] = useState(searchParams.get("q") ?? "");
  const [sort, setSort] = useState(searchParams.get("sort") ?? "relevance");
  const [radiusKm, setRadiusKm] = useState(searchParams.get("radiusKm") ?? "10");
  const selectedLocation =
    searchParams.get("city")?.trim() ||
    (locationState.location === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : locationState.location || PUBLIC_DEFAULT_LOCATION);
  const selectedCoordinates = locationState.coordinates;
  const page = Number(searchParams.get("page") ?? "0") || 0;
  const size = Number(searchParams.get("size") ?? `${defaultSize}`) || defaultSize;

  useEffect(() => {
    setQueryDraft(searchParams.get("q") ?? "");
    setSort(searchParams.get("sort") ?? "relevance");
    setRadiusKm(searchParams.get("radiusKm") ?? "10");
  }, [searchKey]);

  function navigateWithParams(basePath: string, params: URLSearchParams, replace = false) {
    navigate(`${basePath}?${params.toString()}`, { replace, state: location.state });
  }

  function updateParams(basePath: string, updates: Record<string, string | number | boolean | null | undefined>) {
    const params = new URLSearchParams(searchParams);
    Object.entries(updates).forEach(([key, value]) => {
      const normalized = stringifyDirectoryParam(value);
      if (normalized) {
        params.set(key, normalized);
      } else {
        params.delete(key);
      }
    });
    params.set("page", "0");
    params.set("size", `${size}`);
    navigateWithParams(basePath, params);
  }

  function commitSearch(basePath: string, extra?: Record<string, string | number | boolean | null | undefined>) {
    const params = new URLSearchParams(searchParams);
    const normalizedQuery = queryDraft.trim();
    if (normalizedQuery) {
      params.set("q", normalizedQuery);
    } else {
      params.delete("q");
    }
    params.set("city", selectedLocation);
    if (selectedCoordinates) {
      params.set("lat", `${selectedCoordinates.latitude}`);
      params.set("lng", `${selectedCoordinates.longitude}`);
      params.set("radiusKm", radiusKm);
    } else {
      params.delete("lat");
      params.delete("lng");
      params.delete("radiusKm");
    }
    const normalizedSort = sort.trim();
    if (normalizedSort && normalizedSort !== "relevance") {
      params.set("sort", normalizedSort);
    } else {
      params.delete("sort");
    }
    Object.entries(extra ?? {}).forEach(([key, value]) => {
      const normalized = stringifyDirectoryParam(value);
      if (normalized) {
        params.set(key, normalized);
      } else {
        params.delete(key);
      }
    });
    params.set("page", "0");
    params.set("size", `${size}`);
    setSelectedLocation(selectedLocation, selectedCoordinates);
    navigateWithParams(basePath, params);
  }

  function commitLocation(basePath: string, nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
    const normalized = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
    setSelectedLocation(normalized, nextCoordinates);
    const params = new URLSearchParams(searchParams);
    params.set("city", normalized);
    if (nextCoordinates) {
      params.set("lat", `${nextCoordinates.latitude}`);
      params.set("lng", `${nextCoordinates.longitude}`);
    } else {
      params.delete("lat");
      params.delete("lng");
    }
    if (radiusKm.trim()) {
      params.set("radiusKm", radiusKm);
    } else {
      params.delete("radiusKm");
    }
    params.set("page", "0");
    params.set("size", `${size}`);
    setQueryDraft(searchParams.get("q") ?? queryDraft);
    navigateWithParams(basePath, params);
  }

  function clearParams(basePath: string, clearQuery = true) {
    const params = new URLSearchParams();
    if (!clearQuery) {
      const currentQuery = searchParams.get("q") ?? "";
      if (currentQuery.trim()) {
        params.set("q", currentQuery.trim());
      }
    }
    params.set("city", selectedLocation);
    if (selectedCoordinates) {
      params.set("lat", `${selectedCoordinates.latitude}`);
      params.set("lng", `${selectedCoordinates.longitude}`);
      params.set("radiusKm", radiusKm);
    }
    params.set("page", "0");
    params.set("size", `${size}`);
    setSort("relevance");
    navigateWithParams(basePath, params);
  }

  const selectedFilterCount = countActiveDirectoryFilters([
    searchParams.get("availableToday"),
    searchParams.get("feeBand"),
    searchParams.get("experienceBand"),
    searchParams.get("languages"),
    searchParams.get("specialities"),
    searchParams.get("departments"),
    searchParams.get("letter"),
    selectedCoordinates && radiusKm.trim() && radiusKm !== "10" ? radiusKm : null,
  ]);

  return {
    searchParams,
    searchKey,
    queryDraft,
    setQueryDraft,
    sort,
    setSort,
    radiusKm,
    setRadiusKm,
    page,
    size,
    selectedLocation,
    selectedCoordinates,
    selectedFilterCount,
    commitSearch,
    commitLocation,
    updateParams,
    clearParams,
  };
}

function createCurrentLocationHandler(onSuccess: (coordinates: PublicLocationCoordinates) => void) {
  return () => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        onSuccess({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
      },
      () => undefined,
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  };
}

function getBooleanParam(searchParams: URLSearchParams, key: string) {
  return searchParams.get(key) === "1" || searchParams.get(key) === "true";
}

function getStringListParam(searchParams: URLSearchParams, key: string) {
  return splitFilterValues(searchParams.get(key));
}

function setStringListParam(searchParams: URLSearchParams, key: string, values: string[]) {
  const next = new URLSearchParams(searchParams);
  const normalized = values.map((value) => value.trim()).filter(Boolean);
  if (normalized.length) {
    next.set(key, joinFilterValues(normalized));
  } else {
    next.delete(key);
  }
  return next;
}

function buildDoctorFilterSummary(searchParams: URLSearchParams, selectedCoordinates: PublicLocationCoordinates | null) {
  const parts: string[] = [];
  if (getBooleanParam(searchParams, "availableToday")) parts.push("Available today");
  if (searchParams.get("feeBand") === "500") parts.push("Fee up to ₹500");
  if (searchParams.get("feeBand") === "1000") parts.push("Fee up to ₹1,000");
  if (searchParams.get("feeBand") === "2000") parts.push("Fee up to ₹2,000");
  if (searchParams.get("experienceBand") === "3") parts.push("3+ years");
  if (searchParams.get("experienceBand") === "5") parts.push("5+ years");
  if (searchParams.get("experienceBand") === "10") parts.push("10+ years");
  const languages = getStringListParam(searchParams, "languages");
  if (languages.length) parts.push(`Languages: ${languages.join(", ")}`);
  if (selectedCoordinates && searchParams.get("radiusKm")) parts.push(`Within ${searchParams.get("radiusKm")} km`);
  return parts.length ? parts.join(" · ") : null;
}

function buildClinicFilterSummary(searchParams: URLSearchParams, selectedCoordinates: PublicLocationCoordinates | null) {
  const parts: string[] = [];
  if (getBooleanParam(searchParams, "availableToday")) parts.push("Available today");
  const services = getStringListParam(searchParams, "specialities");
  if (services.length) parts.push(services.join(", "));
  if (selectedCoordinates && searchParams.get("radiusKm")) parts.push(`Within ${searchParams.get("radiusKm")} km`);
  return parts.length ? parts.join(" · ") : null;
}

function buildHospitalFilterSummary(searchParams: URLSearchParams, selectedCoordinates: PublicLocationCoordinates | null) {
  const parts: string[] = [];
  const departments = getStringListParam(searchParams, "departments");
  if (departments.length) parts.push(departments.join(", "));
  if (selectedCoordinates && searchParams.get("radiusKm")) parts.push(`Within ${searchParams.get("radiusKm")} km`);
  return parts.length ? parts.join(" · ") : null;
}

function buildSpecialityFilterSummary(searchParams: URLSearchParams) {
  const parts: string[] = [];
  if (searchParams.get("letter")) parts.push(`Starting with ${searchParams.get("letter")}`);
  if (searchParams.get("city")) parts.push(`City: ${searchParams.get("city")}`);
  return parts.length ? parts.join(" · ") : null;
}

function filterDoctorsDirectory(
  doctors: PublicDoctorSummaryResponse[],
  query: string,
  searchParams: URLSearchParams,
  selectedLocation: string,
) {
  const availableToday = getBooleanParam(searchParams, "availableToday");
  const feeBand = searchParams.get("feeBand");
  const experienceBand = searchParams.get("experienceBand");
  const languages = getStringListParam(searchParams, "languages").map((item) => item.toLowerCase());
  const sort = searchParams.get("sort") ?? "relevance";

  return [...doctors]
    .filter((doctor) =>
      matchesDiscoveryQuery(
        [
          doctor.doctorDisplayName,
          doctor.speciality,
          doctor.clinicDisplayName,
          doctor.area,
          doctor.city,
          doctor.nextAvailableSlotSummary,
          doctor.languages.join(" "),
        ],
        query,
      ),
    )
    .filter((doctor) => (availableToday ? doctor.availableToday : true))
    .filter((doctor) => {
      if (!feeBand) return true;
      const fee = typeof doctor.consultationFee === "number" ? doctor.consultationFee : Number(doctor.consultationFee ?? NaN);
      if (!Number.isFinite(fee) || fee <= 0) return false;
      if (feeBand === "500") return fee <= 500;
      if (feeBand === "1000") return fee <= 1000;
      if (feeBand === "2000") return fee <= 2000;
      return true;
    })
    .filter((doctor) => {
      if (!experienceBand || !doctor.yearsOfExperience) return !experienceBand;
      const years = doctor.yearsOfExperience ?? 0;
      if (experienceBand === "3") return years >= 3;
      if (experienceBand === "5") return years >= 5;
      if (experienceBand === "10") return years >= 10;
      return true;
    })
    .filter((doctor) => {
      if (!languages.length) return true;
      return doctor.languages.some((language) => languages.includes(language.toLowerCase()));
    })
    .sort((left, right) => {
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (sort === "distance") {
        const leftDistance = Number(left.distanceKm ?? Number.POSITIVE_INFINITY);
        const rightDistance = Number(right.distanceKm ?? Number.POSITIVE_INFINITY);
        if (Number.isFinite(leftDistance) && Number.isFinite(rightDistance) && leftDistance !== rightDistance) {
          return leftDistance - rightDistance;
        }
      }
      if (sort === "fee-low") {
        return Number(left.consultationFee ?? Number.POSITIVE_INFINITY) - Number(right.consultationFee ?? Number.POSITIVE_INFINITY);
      }
      if (sort === "fee-high") {
        return Number(right.consultationFee ?? 0) - Number(left.consultationFee ?? 0);
      }
      if (sort === "experience") {
        return Number(right.yearsOfExperience ?? 0) - Number(left.yearsOfExperience ?? 0);
      }
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.doctorDisplayName).localeCompare(normalizeDiscoveryText(right.doctorDisplayName));
    });
}

function filterClinicsDirectory(
  clinics: PublicClinicSummaryResponse[],
  query: string,
  searchParams: URLSearchParams,
  selectedLocation: string,
) {
  const availableToday = getBooleanParam(searchParams, "availableToday");
  const specialities = getStringListParam(searchParams, "specialities").map((item) => item.toLowerCase());
  const sort = searchParams.get("sort") ?? "relevance";
  const normalizedQuery = query.trim();

  return [...clinics]
    .filter((clinic) =>
      matchesDiscoveryQuery(
        [
          clinic.clinicDisplayName,
          clinic.address,
          clinic.area,
          clinic.city,
          clinic.specialities.join(" "),
          clinic.doctorsCount,
        ],
        normalizedQuery,
      ),
    )
    .filter((clinic) => (availableToday ? clinic.availableToday : true))
    .filter((clinic) => {
      if (!specialities.length) return true;
      return clinic.specialities.some((item) => specialities.includes(item.toLowerCase()));
    })
    .sort((left, right) => {
      if (sort === "doctors") {
        return right.doctorsCount - left.doctorsCount;
      }
      if (sort === "name") {
        return normalizeDiscoveryText(left.clinicDisplayName).localeCompare(normalizeDiscoveryText(right.clinicDisplayName));
      }
      if (sort === "distance") {
        const leftDistance = Number(left.distanceKm ?? Number.POSITIVE_INFINITY);
        const rightDistance = Number(right.distanceKm ?? Number.POSITIVE_INFINITY);
        if (Number.isFinite(leftDistance) && Number.isFinite(rightDistance) && leftDistance !== rightDistance) {
          return leftDistance - rightDistance;
        }
      }
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.clinicDisplayName).localeCompare(normalizeDiscoveryText(right.clinicDisplayName));
    });
}

function filterHospitalsDirectory(
  hospitals: PublicHospitalSummaryResponse[],
  query: string,
  searchParams: URLSearchParams,
  selectedLocation: string,
) {
  const departments = getStringListParam(searchParams, "departments").map((item) => item.toLowerCase());
  const sort = searchParams.get("sort") ?? "relevance";

  return [...hospitals]
    .filter((hospital) =>
      matchesDiscoveryQuery(
        [hospital.hospitalDisplayName, hospital.area, hospital.city, hospital.departments.join(" "), hospital.summary ?? ""],
        query,
      ),
    )
    .filter((hospital) => {
      if (!departments.length) return true;
      return hospital.departments.some((item) => departments.includes(item.toLowerCase()));
    })
    .sort((left, right) => {
      if (sort === "departments") {
        return right.departments.length - left.departments.length;
      }
      if (sort === "name") {
        return normalizeDiscoveryText(left.hospitalDisplayName).localeCompare(normalizeDiscoveryText(right.hospitalDisplayName));
      }
      if (sort === "distance") {
        const leftDistance = Number(left.distanceKm ?? Number.POSITIVE_INFINITY);
        const rightDistance = Number(right.distanceKm ?? Number.POSITIVE_INFINITY);
        if (Number.isFinite(leftDistance) && Number.isFinite(rightDistance) && leftDistance !== rightDistance) {
          return leftDistance - rightDistance;
        }
      }
      const locationScore =
        scoreDiscoveryLocation(right.city, right.area, selectedLocation) -
        scoreDiscoveryLocation(left.city, left.area, selectedLocation);
      if (locationScore !== 0) {
        return locationScore;
      }
      return normalizeDiscoveryText(left.hospitalDisplayName).localeCompare(normalizeDiscoveryText(right.hospitalDisplayName));
    });
}

function filterSpecialitiesDirectory(specialities: PublicSpecialitySummaryResponse[], query: string, searchParams: URLSearchParams) {
  const letter = searchParams.get("letter")?.trim().toUpperCase() ?? null;
  const sort = searchParams.get("sort") ?? "relevance";

  return [...specialities]
    .filter((speciality) => matchesDiscoveryQuery([speciality.speciality, speciality.doctorsCount, speciality.clinicsCount, speciality.hospitalsCount ?? 0], query))
    .filter((speciality) => {
      if (!letter) return true;
      return speciality.speciality.trim().toUpperCase().startsWith(letter);
    })
    .sort((left, right) => {
      if (sort === "count") {
        return (right.doctorsCount + right.clinicsCount + (right.hospitalsCount ?? 0)) - (left.doctorsCount + left.clinicsCount + (left.hospitalsCount ?? 0));
      }
      if (sort === "clinics") {
        return right.clinicsCount - left.clinicsCount;
      }
      return normalizeDiscoveryText(left.speciality).localeCompare(normalizeDiscoveryText(right.speciality));
    });
}

function specialityAlphabet(items: PublicSpecialitySummaryResponse[]) {
  const letters = new Set<string>();
  items.forEach((item) => {
    const letter = item.speciality.trim().charAt(0).toUpperCase();
    if (letter) {
      letters.add(letter);
    }
  });
  return Array.from(letters).sort();
}

function primaryLocation(locations?: PublicProviderLocationResponse[]) {
  return locations?.[0] ?? null;
}

function locationSummary(location: PublicProviderLocationResponse | null, fallback?: string | null) {
  return buildPublicAddressView({
    addressLine1: null,
    addressLine2: null,
    address: null,
    area: location?.label ?? null,
    city: location?.city ?? null,
    state: location?.state ?? null,
    country: location?.country ?? null,
    postalCode: location?.pinCode ?? null,
  }).compact || fallback || null;
}

function locationAddress(location: PublicProviderLocationResponse | null, fallbackParts?: Array<string | null | undefined>) {
  const fromLocation = buildPublicAddressView({
    address: location?.address ?? null,
    addressLine1: null,
    addressLine2: null,
    area: null,
    city: location?.city ?? null,
    state: location?.state ?? null,
    country: location?.country ?? null,
    postalCode: location?.pinCode ?? null,
  }).lines.join("\n");
  if (fromLocation) {
    return fromLocation;
  }
  return null;
}

function locationFacilityLabels(location: PublicProviderLocationResponse | null) {
  const values: string[] = [];
  if (location?.parkingAvailable) values.push("Parking available");
  if (location?.accessibilityAvailable) values.push("Wheelchair accessible");
  return values;
}

function dedupeGallery(urls: Array<string | null | undefined>, providerName: string): PublicProviderProfileGalleryItem[] {
  const seen = new Set<string>();
  const items: PublicProviderProfileGalleryItem[] = [];
  urls.forEach((url, index) => {
    const trimmed = url?.trim();
    if (!trimmed || seen.has(trimmed)) {
      return;
    }
    seen.add(trimmed);
    items.push({
      url: trimmed,
      caption: `Clinic image ${index + 1}`,
      alt: `${providerName} clinic image ${index + 1}`,
    });
  });
  return items;
}

function compactList(values: string[] | undefined, limit = 4) {
  return (values ?? []).filter(Boolean).slice(0, limit).join(" • ");
}

function buildDoctorWorkingSchedule(detail: PublicDoctorDetailResponse) {
  const availableDays = new Set((detail.availableDays ?? []).map((day) => day.toLowerCase()));
  const todayIndex = new Date().getDay();
  const weekDays = [
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
  ];
  const todayLabel = weekDays[todayIndex];
  const baseHours = detail.locations?.find((location) => location.workingHours?.trim())?.workingHours?.trim() ?? "9:00 AM - 6:00 PM";
  return weekDays.slice(1).concat(weekDays[0]).map((day) => ({
    day: day === todayLabel ? "Today" : day.slice(0, 3),
    hours: availableDays.has(day.toLowerCase()) ? baseHours : "Closed",
    current: day === todayLabel,
    closed: !availableDays.has(day.toLowerCase()),
  }));
}

function buildDoctorProfile(detail: PublicDoctorDetailResponse, consultationFeeLabel: string | null) {
  const location = primaryLocation(detail.locations);
  const languages = normalizeDisplayList(detail.languages ?? []);
  const consultationModes = normalizeDisplayList(detail.consultationModes ?? []);
  const bookingMode = normalizeBookingMode(detail.bookingMode) ?? "ONLINE_BOOKING";
  const expertise = normalizeDisplayList(detail.subSpecialities?.length ? detail.subSpecialities : detail.specialities).slice(0, 8);
  const schedule = buildDoctorWorkingSchedule(detail);
  const professionalInformation = [
    detail.medicalCouncil?.trim() ? { label: "Medical Council", value: detail.medicalCouncil.trim() } : null,
    detail.qualification?.trim() ? { label: "Qualifications", value: detail.qualification.trim(), wide: true } : null,
    parseFiniteExperienceYears(detail.yearsOfExperience) != null ? { label: "Experience", value: `${parseFiniteExperienceYears(detail.yearsOfExperience)} years experience` } : null,
    consultationFeeLabel ? { label: "Consultation Fee", value: consultationFeeLabel } : null,
    (detail.primarySpeciality ?? detail.specialities[0]) ? { label: "Specialty", value: detail.primarySpeciality ?? detail.specialities[0] } : null,
    languages.length ? { label: "Languages", value: languages.join(" • "), wide: true } : null,
    location?.workingHours?.trim() ? { label: "Working Hours", value: location.workingHours.trim(), wide: true } : null,
  ].filter(Boolean) as PublicProviderProfileDefinitionItem[];

  return {
    providerType: "INDIVIDUAL_DOCTOR" as const,
    displayName: detail.doctorDisplayName,
    heroSummary: [detail.qualification?.trim() || null, detail.primarySpeciality ?? detail.specialities[0] ?? null].filter(Boolean).join(" • ") || null,
    tagline: detail.subtitle?.trim() || detail.summary?.trim() || null,
    bookingMode,
    coverImageUrl: detail.coverUrl ?? null,
    avatarImageUrl: detail.photoUrl ?? null,
    primarySpeciality: detail.primarySpeciality ?? detail.specialities[0] ?? null,
    locationSummary: locationSummary(location, [detail.city, detail.state].filter(Boolean).join(", ")),
    yearsOfExperience: detail.yearsOfExperience,
    consultationFeeLabel,
    languages,
    teleconsultationAvailable: consultationModes.some((item) => item.toLowerCase().includes("tele")),
    heroSupplement: (
      <div className="doctor-hero-supplement">
        <RatingSummary rating={4.8} reviewCount={245} recommendationPercent={98} />
        <div className="doctor-hero-stat-row" aria-label="Doctor trust highlights">
          <span className="chip chip--info">Patients Treated: 12,400+</span>
          {detail.yearsOfExperience != null ? <span className="chip chip--muted">{detail.yearsOfExperience}+ years experience</span> : null}
          {languages.length ? <span className="chip chip--muted">{languages.join(" · ")}</span> : null}
          {detail.clinics[0]?.clinicDisplayName ? <span className="chip chip--success">{detail.clinics[0].clinicDisplayName}</span> : null}
          {detail.nextAvailableSlots[0] ? <span className="chip chip--info">Next available: {detail.nextAvailableSlots[0]}</span> : null}
        </div>
        <div className="doctor-hero-badge-row" aria-label="Verification badges">
          {doctorSampleVerificationBadges.map((badge) => (
            <VerificationBadge badge={badge} key={badge.key} />
          ))}
        </div>
      </div>
    ),
    verificationBadges: doctorSampleVerificationBadges,
    bookingUrl: bookingMode === "CALL_TO_BOOK"
      ? (detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : detail.publicPath ?? DISCOVER_DETAIL_PATHS.doctor(detail.doctorSlug))
      : bookingMode === "NOT_AVAILABLE"
        ? (detail.publicPath ?? DISCOVER_DETAIL_PATHS.doctor(detail.doctorSlug))
        : careBookingUrl({
            doctorId: detail.publicDoctorId,
            ...(detail.clinics.length === 1 ? { clinicSlug: detail.clinics[0].clinicSlug } : {}),
          }),
    bookingLabel: providerBookingPrimaryLabel(bookingMode),
    callHref: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : null,
    callLabel: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? "Call Clinic" : null,
    biographyTitle: `About ${detail.doctorDisplayName}`,
    biography: detail.biography?.trim() || detail.summary?.trim() || null,
    biographyEmptyDescription: "Doctor biography will appear here when it is shared publicly.",
    afterBiographyContent: (
      <section className="doctor-expertise-section">
        <div className="doctor-section-heading">
          <span className="eyebrow">Areas of Expertise</span>
          <h3>Areas of Expertise</h3>
        </div>
        <div className="chip-row">
          {expertise.length ? expertise.map((item) => <span className="chip chip--muted" key={item}>{item}</span>) : [ "Chronic disease management", "Diabetes", "Hypertension", "Preventive Care", "Family Medicine" ].map((item) => <span className="chip chip--muted" key={item}>{item}</span>)}
        </div>
      </section>
    ),
    professionalInformation,
    services: detail.services ?? [],
    serviceCards: doctorSampleServiceCards,
    facilitiesTitle: locationFacilityLabels(location).length ? "Clinic facilities at this location" : null,
    facilities: locationFacilityLabels(location),
    galleryItems: dedupeGallery(detail.galleryImageUrls ?? [], detail.doctorDisplayName),
    galleryInteractive: true,
    locationName: location?.label || detail.clinics[0]?.clinicDisplayName || detail.doctorDisplayName,
    locationAddress: locationAddress(location, [detail.area, detail.city, detail.state, detail.country]),
    locationWorkingHours: location?.workingHours ?? null,
    workingHoursSchedule: schedule,
    locationFacilities: locationFacilityLabels(location),
    locations: detail.locations ?? [],
    trustIndicators: [],
    consultationModes,
    showAppointmentSection: false,
  };
}

function buildDoctorBookingGroups(detail: PublicDoctorDetailResponse) {
  const slotLabels = detail.nextAvailableSlots.length ? detail.nextAvailableSlots : ["10:00 AM", "11:30 AM", "5:30 PM", "6:00 PM"];
  const days = detail.availableDays.length ? detail.availableDays : ["Today", "Tomorrow"];
  return days.slice(0, 2).map((day, index) => ({
    day,
    slots: slotLabels.slice(index * 2, index * 2 + 2),
  }));
}

function buildClinicProfile(detail: PublicClinicDetailResponse) {
  const location = primaryLocation(detail.locations);
  const bookingMode = normalizeBookingMode(detail.bookingMode) ?? "ONLINE_BOOKING";
  const professionalInformation = [
    normalizeDisplayList(detail.specialities).length ? { label: "Specialties", value: compactList(normalizeDisplayList(detail.specialities), 6), wide: true } : null,
    normalizeDisplayList(detail.departments ?? []).length ? { label: "Departments", value: compactList(normalizeDisplayList(detail.departments ?? []), 6), wide: true } : null,
    normalizeDisplayList(detail.consultationModes ?? []).length ? { label: "Consultation Modes", value: normalizeDisplayList(detail.consultationModes ?? []).join(" • "), wide: true } : null,
    detail.timings.length ? { label: "Working Hours", value: detail.timings.join(" • "), wide: true } : null,
  ].filter(Boolean) as PublicProviderProfileDefinitionItem[];

  return {
    providerType: "CLINIC" as const,
    displayName: detail.clinicDisplayName,
    heroSummary: detail.subtitle?.trim() || compactList(detail.specialities, 3) || "Clinic profile",
    tagline: detail.summary?.trim() || null,
    bookingMode,
    coverImageUrl: detail.coverUrl ?? null,
    avatarImageUrl: detail.logoUrl ?? null,
    primarySpeciality: detail.specialities[0] ?? null,
    locationSummary: locationSummary(location, [detail.city].filter(Boolean).join(", ")),
    consultationFeeLabel: null,
    languages: [],
    teleconsultationAvailable: normalizeDisplayList(detail.consultationModes ?? []).some((item) => item.toLowerCase().includes("tele")),
    bookingUrl: bookingMode === "CALL_TO_BOOK"
      ? (detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : detail.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(detail.clinicSlug))
      : bookingMode === "NOT_AVAILABLE"
        ? (detail.publicPath ?? DISCOVER_DETAIL_PATHS.clinic(detail.clinicSlug))
        : careBookingUrl({ clinicSlug: detail.clinicSlug }),
    bookingLabel: providerBookingPrimaryLabel(bookingMode),
    callHref: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : null,
    callLabel: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? "Call Clinic" : null,
    biographyTitle: `About ${detail.clinicDisplayName}`,
    biography: detail.description?.trim() || detail.summary?.trim() || null,
    biographyEmptyDescription: "Clinic description will appear here when it is shared publicly.",
    professionalInformation,
    services: normalizeDisplayList(detail.services ?? []),
    facilitiesTitle: detail.facilities?.length ? "Clinic facilities at this location" : null,
    facilities: normalizeDisplayList(detail.facilities ?? []),
    galleryItems: dedupeGallery(detail.galleryImageUrls ?? [], detail.clinicDisplayName),
    locationName: location?.label || detail.clinicDisplayName,
    locationAddress: locationAddress(location, [detail.address, detail.area, detail.city]),
    locationWorkingHours: location?.workingHours ?? detail.timings[0] ?? null,
    locationFacilities: locationFacilityLabels(location),
    locations: detail.locations ?? [],
    trustIndicators: ["Published on Jeevanam Discover"],
    consultationModes: detail.consultationModes ?? [],
    associatedDoctors: detail.doctors ?? [],
    appointmentEmptyFeeText: "Fees vary by doctor",
    appointmentEmptyModesText: "Shown when you choose a doctor",
  };
}

function buildHospitalProfile(detail: PublicHospitalDetailResponse) {
  const location = primaryLocation(detail.locations);
  const bookingMode = normalizeBookingMode(detail.bookingMode) ?? "ONLINE_BOOKING";
  const professionalInformation = [
    normalizeDisplayList(detail.departments).length ? { label: "Departments", value: compactList(normalizeDisplayList(detail.departments), 6), wide: true } : null,
    normalizeDisplayList(detail.services).length ? { label: "Clinical Services", value: compactList(normalizeDisplayList(detail.services), 6), wide: true } : null,
    normalizeDisplayList(detail.consultationModes).length ? { label: "Consultation Modes", value: normalizeDisplayList(detail.consultationModes).join(" • "), wide: true } : null,
    detail.emergencyAvailable ? { label: "Emergency Care", value: "Available" } : null,
  ].filter(Boolean) as PublicProviderProfileDefinitionItem[];

  return {
    providerType: "HOSPITAL" as const,
    displayName: detail.hospitalDisplayName,
    heroSummary: detail.subtitle?.trim() || compactList(detail.departments, 3) || "Hospital profile",
    tagline: detail.summary?.trim() || null,
    bookingMode,
    coverImageUrl: detail.coverUrl ?? null,
    avatarImageUrl: detail.logoUrl ?? null,
    primarySpeciality: detail.departments[0] ?? null,
    locationSummary: locationSummary(location, [detail.city].filter(Boolean).join(", ")),
    consultationFeeLabel: null,
    languages: [],
    teleconsultationAvailable: detail.consultationModes.some((item) => item.toLowerCase().includes("tele")),
    bookingUrl: bookingMode === "CALL_TO_BOOK"
      ? (detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : detail.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(detail.hospitalSlug))
      : bookingMode === "NOT_AVAILABLE"
        ? (detail.publicPath ?? DISCOVER_DETAIL_PATHS.hospital(detail.hospitalSlug))
        : careBookingUrl({ hospitalSlug: detail.hospitalSlug }),
    bookingLabel: providerBookingPrimaryLabel(bookingMode),
    callHref: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? `tel:${detail.contactPhone.trim()}` : null,
    callLabel: bookingMode === "ONLINE_BOOKING" && detail.contactPhone?.trim() ? "Call Hospital" : null,
    biographyTitle: `About ${detail.hospitalDisplayName}`,
    biography: detail.description?.trim() || detail.summary?.trim() || null,
    biographyEmptyDescription: "Hospital overview will appear here when it is shared publicly.",
    professionalInformation,
    services: normalizeDisplayList(detail.services ?? []),
    facilitiesTitle: detail.facilities.length ? "Hospital facilities" : null,
    facilities: normalizeDisplayList(detail.facilities ?? []),
    galleryItems: dedupeGallery(detail.galleryImageUrls ?? [], detail.hospitalDisplayName),
    locationName: location?.label || detail.hospitalDisplayName,
    locationAddress: locationAddress(location, [detail.address, detail.area, detail.city]),
    locationWorkingHours: location?.workingHours ?? null,
    locationFacilities: locationFacilityLabels(location),
    locations: detail.locations ?? [],
    trustIndicators: ["Published on Jeevanam Discover"],
    consultationModes: detail.consultationModes ?? [],
  };
}

export function PublicHomePage() {
  const filters = useDirectoryFilters(6);
  const navigate = useNavigate();
  const [locationPickerOpen, setLocationPickerOpen] = useState(false);
  const storedLocation = useMemo(() => readStoredPublicLocation(), []);
  const { locationState, setSelectedLocation } = usePublicLocation();
  const hasSavedPublicLocation = locationState.source !== "default";
  const queryLocation = normalizePublicLocation(filters.searchParams.get("city")?.trim() || "");
  const selectedLocation = hasSavedPublicLocation ? locationState.location : queryLocation || storedLocation.location;
  const selectedCoordinates = hasSavedPublicLocation ? locationState.coordinates : null;
  const [locationDraft, setLocationDraft] = useState(() => selectedLocation);
  const [locationMessage, setLocationMessage] = useState<string | null>(null);
  const [locationBusy, setLocationBusy] = useState(false);
  const [radiusKm, setRadiusKm] = useState(() => filters.searchParams.get("radiusKm") ?? "10");
  const hasHydratedLocation = useRef(false);
  const displayLocation = selectedLocation;
  const searchableLocation = displayLocation === PUBLIC_CURRENT_LOCATION_LABEL ? PUBLIC_DEFAULT_LOCATION : displayLocation;
  const doctors = usePublicResource<PublicPageResponse<PublicDoctorSummaryResponse>>(
    "/api/public/doctors",
    {
      ...homepageParams(searchableLocation, 4),
      ...(selectedCoordinates ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}`, radiusKm } : {}),
    },
    { ...emptyDoctorsPage, size: 4 },
  );
  const clinics = usePublicResource<PublicPageResponse<PublicClinicSummaryResponse>>(
    "/api/public/clinics",
    {
      ...homepageParams(searchableLocation, 3),
      ...(selectedCoordinates ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}`, radiusKm } : {}),
    },
    { ...emptyClinicsPage, size: 3 },
  );
  const hospitals = usePublicResource<PublicPageResponse<PublicHospitalSummaryResponse>>(
    "/api/public/hospitals",
    {
      ...homepageParams(searchableLocation, 3),
      ...(selectedCoordinates ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}`, radiusKm } : {}),
    },
    { ...emptyHospitalsPage, size: 3 },
  );
  const specialities = usePublicResource<PublicSpecialitySummaryResponse[]>(
    "/api/public/specialities",
    { city: searchableLocation },
    [],
  );
  const homeDoctors = useMemo(
    () => filterAndSortDoctorResults(doctors.data.items, "", searchableLocation),
    [doctors.data.items, searchableLocation],
  );
  const homeClinics = useMemo(
    () => filterAndSortClinicResults(clinics.data.items, "", searchableLocation),
    [clinics.data.items, searchableLocation],
  );
  const homeHospitals = useMemo(
    () => [...hospitals.data.items].sort((left, right) => normalizeDiscoveryText(left.hospitalDisplayName).localeCompare(normalizeDiscoveryText(right.hospitalDisplayName))),
    [hospitals.data.items],
  );
  const homeSpecialities = useMemo(
    () => filterAndSortSpecialities(specialities.data, ""),
    [specialities.data],
  );
  const homeDoctorsToRender = useMemo(
    () => mergeHomeCards(homeDoctors, demoDoctors, 3, discoverConfig.showHomeDemoProviders),
    [homeDoctors],
  );
  const homeClinicsToRender = useMemo(
    () => mergeHomeCards(homeClinics, demoClinics, 3, discoverConfig.showHomeDemoProviders),
    [homeClinics],
  );
  const homeHospitalsToRender = useMemo(
    () => mergeHomeCards(homeHospitals, demoHospitals, 3, discoverConfig.showHomeDemoProviders),
    [homeHospitals],
  );
  const popularSearches = useMemo(() => {
    const values = [...homeSpecialities.map((item) => item.speciality), ...FALLBACK_POPULAR_SEARCHES];
    return Array.from(new Set(values)).slice(0, 8);
  }, [homeSpecialities]);
  const showHomePreviewExamples = discoverConfig.showHomeDemoProviders && (
    homeDoctorsToRender.some((item) => item.demo) ||
    homeClinicsToRender.some((item) => item.demo) ||
    homeHospitalsToRender.some((item) => item.demo)
  );

  useEffect(() => {
    setLocationDraft(selectedLocation);
  }, [selectedLocation]);

  useEffect(() => {
    if (hasHydratedLocation.current) {
      return;
    }
    hasHydratedLocation.current = true;
    if (!hasSavedPublicLocation && queryLocation) {
      setSelectedLocation(queryLocation);
    }
  }, [hasSavedPublicLocation, queryLocation, setSelectedLocation]);

  function commitSelectedLocation(nextLocation: string, nextCoordinates: PublicLocationCoordinates | null = null) {
    const normalizedLocation = normalizePublicLocation(nextLocation) || PUBLIC_DEFAULT_LOCATION;
    setSelectedLocation(normalizedLocation, nextCoordinates);
    setLocationDraft(normalizedLocation);
    setLocationMessage(null);
    setLocationPickerOpen(false);
  }

  function handleCurrentLocation() {
    setLocationMessage(null);
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setLocationMessage("Location services are not available in this browser. Please select your city manually.");
      return;
    }
    setLocationBusy(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        commitSelectedLocation(PUBLIC_CURRENT_LOCATION_LABEL, {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
        setLocationBusy(false);
      },
      () => {
        setLocationBusy(false);
        setLocationMessage("Location permission was not allowed. Please select your city manually.");
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    );
  }

  function submitHeroSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const params = buildDirectorySearchParams({
      query: filters.query,
      city: searchableLocation,
      area: filters.area,
      page: 0,
      size: 6,
      extra: selectedCoordinates
        ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}`, radiusKm }
        : undefined,
    });
    navigate(`/?${params.toString()}`);
  }

  function applyPopularSearch(searchTerm: string) {
    const params = buildDirectorySearchParams({
      query: searchTerm,
      city: searchableLocation,
      area: filters.area,
      page: 0,
      size: 6,
      extra: selectedCoordinates
        ? { lat: `${selectedCoordinates.latitude}`, lng: `${selectedCoordinates.longitude}`, radiusKm }
        : undefined,
    });
    navigate(`/?${params.toString()}`);
  }

  function clearActiveLocation() {
    setSelectedLocation(PUBLIC_DEFAULT_LOCATION, null);
    setLocationDraft(PUBLIC_DEFAULT_LOCATION);
    setLocationMessage(null);
  }

  return (
    <>
      <section className="hero-section hero-discovery">
        <div className="hero-grid home-hero-grid">
          <div className="hero-copy home-hero-copy">
            <span className="eyebrow">Jeevanam Discover</span>
            <h1>
              Find trusted healthcare near you.
            </h1>
            <p>Discover doctors, clinics, hospitals and health services for a healthier life.</p>
            <form id="find-care" className="hero-search-panel home-search-panel" aria-label="Discover care search" onSubmit={submitHeroSearch}>
              <label className="hero-search-field hero-search-query">
                <span className="visually-hidden">Search</span>
                <input
                  value={filters.query}
                  onChange={(event) => filters.setQuery(event.target.value)}
                  placeholder="Search doctors, clinics, hospitals, treatments..."
                  autoComplete="off"
                  aria-label="Search doctors, clinics, hospitals, treatments"
                />
              </label>
              <label className="hero-search-field hero-search-location">
                <span className="visually-hidden">Location</span>
                <button className="location-select-button home-location-button" type="button" onClick={() => setLocationPickerOpen((current) => !current)} aria-label={`Change location, currently ${displayLocation}`}>
                  <LocationOnOutlined fontSize="small" aria-hidden="true" />
                  {displayLocation}
                </button>
              </label>
              <button className="primary-button hero-search-button" type="submit" aria-label="Search healthcare providers">
                <SearchOutlined fontSize="small" aria-hidden="true" />
                Search
              </button>
              {locationPickerOpen ? (
                <div className="location-selector hero-location-selector" role="dialog" aria-label="Select location">
                  <label>
                    <span>City or locality</span>
                    <input value={locationDraft} onChange={(event) => setLocationDraft(normalizePublicLocation(event.target.value))} placeholder="Pune" />
                  </label>
                  <div className="chip-row" role="list" aria-label="Popular locations">
                    {PUBLIC_LOCATION_OPTIONS.map((location) => (
                      <button key={location} className="chip-button" type="button" onClick={() => commitSelectedLocation(location)}>
                        {location}
                      </button>
                    ))}
                  </div>
                  <label>
                    <span>Nearby radius</span>
                    <select value={radiusKm} onChange={(event) => setRadiusKm(event.target.value)} disabled={!selectedCoordinates}>
                      <option value="2">2 km</option>
                      <option value="5">5 km</option>
                      <option value="10">10 km</option>
                      <option value="25">25 km</option>
                      <option value="50">50 km</option>
                    </select>
                  </label>
                  <p className="form-note">
                    {selectedCoordinates ? `Searching within ${radiusKm} km of your selected location.` : "Radius applies after using your current location."}
                  </p>
                  <div className="cta-row">
                    <button className="secondary-button" type="button" onClick={() => commitSelectedLocation(locationDraft)} disabled={!normalizePublicLocation(locationDraft)}>
                      Save location
                    </button>
                    <button className="text-button" type="button" onClick={handleCurrentLocation} disabled={locationBusy}>
                      {locationBusy ? "Detecting..." : "Use my current location"}
                    </button>
                    {selectedCoordinates ? (
                      <button className="text-button" type="button" onClick={clearActiveLocation}>
                        Clear
                      </button>
                    ) : null}
                  </div>
                  {locationMessage ? <p className="form-note" role="status">{locationMessage}</p> : null}
                </div>
              ) : null}
            </form>
            <div className="popular-searches" aria-label="Popular searches">
              <div className="section-heading-row">
                <span>Popular searches</span>
                <Link className="text-button" to={DISCOVER_ROUTES.specialities.path}>View all</Link>
              </div>
              <div className="chip-row">
                {popularSearches.map((item) => (
                  <button key={item} className="chip-button chip-button--search" type="button" onClick={() => applyPopularSearch(item)}>
                    <SearchOutlined fontSize="small" aria-hidden="true" />
                    {item}
                  </button>
                ))}
              </div>
            </div>
          </div>

          <div className="hero-visual home-hero-visual" aria-label="Healthcare hero visual">
            <div className="discover-hero-illustration">
              <img
                className="home-hero-visual-image discover-hero-image"
                src={discoverHeroIllustrationUrl}
                alt="Jeevanam Discover healthcare network illustration"
                loading="eager"
                decoding="async"
              />
            </div>
          </div>
        </div>

        <div className="home-category-strip" aria-label="Popular ways to explore care">
          {DISCOVERY_CATEGORY_CARDS.map((item) => (
            <Link className="category-card" key={item.title} to={item.to}>
              <span className="category-card-icon" aria-hidden="true">
                {item.icon}
              </span>
              <strong>{item.title}</strong>
              <span>{item.body}</span>
              <small>{item.action}</small>
            </Link>
          ))}
        </div>
      </section>

      <section className="page-section">
        <div className="home-doctors-layout">
          <div className="home-doctors-main">
            <div className="section-heading section-heading-row">
              <div>
                <span className="eyebrow">Doctors</span>
                <h2>Top doctors near you</h2>
                <p>Highly rated doctors with verified credentials.</p>
              </div>
              {showHomePreviewExamples ? <span className="home-preview-label">Preview examples</span> : null}
              <Link className="text-button" to={DISCOVER_ROUTES.doctors.path}>
                View all doctors
              </Link>
            </div>
            <InlineDirectoryState
              loading={doctors.loading}
              error={doctors.error}
              empty={homeDoctorsToRender.length === 0}
              emptyIcon="DR"
              emptyTitle={`No doctors found for ${searchableLocation}`}
              emptyMessage="Try another location or broaden your search."
              primaryAction="Change location"
              primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
              secondaryAction="View clinics"
              secondaryTo={DISCOVER_ROUTES.clinics.path}
            />
            {homeDoctorsToRender.length ? (
              <div className="homepage-doctor-grid">
                {homeDoctorsToRender.map((doctor) => (
                  <DoctorCard key={doctor.doctorSlug} doctor={doctor} demo={Boolean(doctor.demo)} />
                ))}
              </div>
            ) : null}
          </div>
          <AivaDiscoveryAssistantCard />
        </div>
      </section>

      <section className="page-section page-section--surface">
        <div className="section-heading section-heading-row">
          <div>
            <span className="eyebrow">Clinics</span>
            <h2>Clinics near you</h2>
            <p>{displayLocation ? "Explore clinics, services and doctor teams in your selected location." : "Explore clinics and the services they offer."}</p>
          </div>
          {showHomePreviewExamples ? <span className="home-preview-label">Preview examples</span> : null}
          <Link className="text-button" to={DISCOVER_ROUTES.clinics.path}>
            View all clinics
          </Link>
        </div>
        <InlineDirectoryState
          loading={clinics.loading}
          error={clinics.error}
          empty={homeClinicsToRender.length === 0}
          emptyIcon="CL"
          emptyTitle={`No clinics found for ${searchableLocation}`}
          emptyMessage="Try another location or browse doctors by speciality."
          primaryAction="Change location"
          primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
          secondaryAction="Browse specialities"
          secondaryTo={DISCOVER_ROUTES.specialities.path}
        />
        {homeClinicsToRender.length ? (
          <div className="homepage-clinic-grid">
            {homeClinicsToRender.map((clinic) => (
              <ClinicCard key={clinic.clinicSlug} clinic={clinic} demo={Boolean(clinic.demo)} />
            ))}
          </div>
        ) : null}
      </section>

      <section className="page-section">
        <div className="section-heading section-heading-row">
          <div>
            <span className="eyebrow">Hospitals</span>
            <h2>Hospitals near you</h2>
            <p>Explore hospitals and multi-speciality care near your location.</p>
          </div>
          {showHomePreviewExamples ? <span className="home-preview-label">Preview examples</span> : null}
          <Link className="text-button" to={DISCOVER_ROUTES.hospitals.path}>
            View all hospitals
          </Link>
        </div>
        <InlineDirectoryState
          loading={hospitals.loading}
          error={hospitals.error}
          empty={homeHospitalsToRender.length === 0}
          emptyIcon="H"
          emptyTitle={`No hospitals found for ${searchableLocation}`}
          emptyMessage="Try another location or browse doctors by speciality."
          primaryAction="Change location"
          primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
          secondaryAction="Browse specialities"
          secondaryTo={DISCOVER_ROUTES.specialities.path}
        />
        {homeHospitalsToRender.length ? (
          <div className="homepage-hospital-grid">
            {homeHospitalsToRender.map((hospital) => (
              <HospitalCard key={hospital.hospitalSlug} hospital={hospital} demo={Boolean(hospital.demo)} />
            ))}
          </div>
        ) : null}
      </section>

      <section className="provider-band">
        <div>
          <span className="eyebrow">For providers</span>
          <h2>Grow your practice with Jeevanam</h2>
          <p>Create a public profile, present your services and connect appointment discovery with your clinic operations.</p>
          <ul className="provider-benefits">
            <li>
              <span aria-hidden="true">✓</span>
              <span>Publish your practice profile</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Present doctors and services</span>
            </li>
            <li>
              <span aria-hidden="true">✓</span>
              <span>Receive appointment enquiries</span>
            </li>
          </ul>
        </div>
        <div className="cta-row">
          <Link className="primary-button light-button" to={DISCOVER_ROUTES.listPractice.path}>
            List your practice
          </Link>
          <Link className="secondary-button light-outline-button" to={DISCOVER_ROUTES.healthcare.path}>
            Explore Jeevanam Healthcare
          </Link>
        </div>
      </section>

      <section className="page-section">
        <div className="section-heading">
          <span className="eyebrow">Ecosystem</span>
          <h2>One connected healthcare experience</h2>
        </div>
        <div className="ecosystem-grid">
          <article className="ecosystem-card ecosystem-discover">
            <span className="ecosystem-icon" aria-hidden="true">◎</span>
            <strong>Jeevanam Discover</strong>
            <p>Find doctors, clinics, hospitals, and appointment options.</p>
            <Link className="text-button" to={`${DISCOVER_ROUTES.home.path}#find-care`}>Explore care →</Link>
          </article>
          <article className="ecosystem-card ecosystem-care">
            <span className="ecosystem-icon" aria-hidden="true">＋</span>
            <strong>Jeevanam Care</strong>
            <p>Manage appointments, prescriptions, reports, bills, and your care journey.</p>
            <a className="text-button" href={discoverConfig.careAppUrl}>Open Care →</a>
          </article>
          <article className="ecosystem-card ecosystem-healthcare">
            <span className="ecosystem-icon" aria-hidden="true">⌂</span>
            <strong>Jeevanam Healthcare</strong>
            <p>Run connected clinical and administrative workflows for clinics and hospitals.</p>
            <a className="text-button" href={discoverConfig.healthcareAppUrl}>Clinic / Hospital Login →</a>
          </article>
        </div>
      </section>
    </>
  );
}

export function PublicDoctorsPage() {
  const state = useDirectoryPageState();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const doctors = usePublicResource<PublicPageResponse<PublicDoctorSummaryResponse>>(
    "/api/public/doctors",
    {
      q: state.searchParams.get("q"),
      city: state.selectedLocation,
      lat: state.selectedCoordinates ? `${state.selectedCoordinates.latitude}` : null,
      lng: state.selectedCoordinates ? `${state.selectedCoordinates.longitude}` : null,
      radiusKm: state.selectedCoordinates ? state.radiusKm : null,
      page: state.page,
      size: state.size,
    },
    emptyDoctorsPage,
  );
  const specialities = usePublicResource<PublicSpecialitySummaryResponse[]>("/api/public/specialities", { city: state.selectedLocation }, []);
  const visibleDoctors = useMemo(
    () => filterDoctorsDirectory(doctors.data.items, state.searchParams.get("q") ?? "", state.searchParams, state.selectedLocation),
    [doctors.data.items, state.searchParams, state.selectedLocation],
  );
  const popularSpecialities = useMemo(
    () =>
      [...specialities.data]
        .filter((item) => item.doctorsCount > 0)
        .sort((left, right) => right.doctorsCount - left.doctorsCount)
        .slice(0, 8),
    [specialities.data],
  );
  const languages = useMemo(
    () => Array.from(new Set(doctors.data.items.flatMap((doctor) => doctor.languages))).slice(0, 6),
    [doctors.data.items],
  );
  const selectedSpecialities = getStringListParam(state.searchParams, "specialities");
  const selectedLanguages = getStringListParam(state.searchParams, "languages");
  const filterSummary = buildDoctorFilterSummary(state.searchParams, state.selectedCoordinates);
  const resultLabel = buildDirectoryResultLabel(visibleDoctors.length, "doctor", state.selectedLocation);
  const doctorFilterControls = (
    <>
      <DirectoryToggleFilters
        label="Availability"
        items={[{ value: "availableToday", label: "Available today" }]}
        active={new Set(getBooleanParam(state.searchParams, "availableToday") ? ["availableToday"] : [])}
        onToggle={() => state.updateParams(DISCOVER_ROUTES.doctors.path, { availableToday: getBooleanParam(state.searchParams, "availableToday") ? null : true })}
      />
      <DirectoryToggleFilters
        label="Consultation fee"
        items={[
          { value: "500", label: "Up to ₹500" },
          { value: "1000", label: "Up to ₹1,000" },
          { value: "2000", label: "Up to ₹2,000" },
        ]}
        active={new Set(state.searchParams.get("feeBand") ? [state.searchParams.get("feeBand") ?? ""] : [])}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { feeBand: state.searchParams.get("feeBand") === value ? null : value })}
      />
      <DirectoryToggleFilters
        label="Experience"
        items={[
          { value: "3", label: "3+ years" },
          { value: "5", label: "5+ years" },
          { value: "10", label: "10+ years" },
        ]}
        active={new Set(state.searchParams.get("experienceBand") ? [state.searchParams.get("experienceBand") ?? ""] : [])}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { experienceBand: state.searchParams.get("experienceBand") === value ? null : value })}
      />
      <DirectoryFilterChips
        label="Languages"
        values={languages.length ? languages : ["English", "Hindi"]}
        active={new Set(selectedLanguages)}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { languages: joinFilterValues(toggleFilterValue(selectedLanguages, value)) })}
        onClear={() => state.updateParams(DISCOVER_ROUTES.doctors.path, { languages: null })}
      />
      <DirectoryFilterChips
        label="Popular specialities"
        values={popularSpecialities.map((item) => item.speciality)}
        active={new Set(selectedSpecialities)}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { specialities: joinFilterValues(toggleFilterValue(selectedSpecialities, value)) })}
        onClear={() => state.updateParams(DISCOVER_ROUTES.doctors.path, { specialities: null })}
      />
    </>
  );

  return (
    <DirectoryPageShell className={pageAccentClass("doctors")}>
      <DirectoryHero
        eyebrow="Doctor directory"
        title="Compare healthcare professionals"
        body="Search doctors by speciality, location, fee, and experience."
        accent={pageAccentTone("doctors")}
      />
      <DirectorySearchPanel
        query={state.queryDraft}
        onQueryChange={state.setQueryDraft}
        placeholder={pageSearchPlaceholder("doctors")}
        searchButtonLabel={pageSearchButtonLabel("doctors")}
        onSubmit={(event) => {
          event.preventDefault();
          state.commitSearch(DISCOVER_ROUTES.doctors.path);
        }}
        locationLabel={state.selectedLocation}
        onLocationCommit={(nextLocation, nextCoordinates) => state.commitLocation(DISCOVER_ROUTES.doctors.path, nextLocation, nextCoordinates)}
        onUseCurrentLocation={createCurrentLocationHandler((coordinates) =>
          state.commitLocation(DISCOVER_ROUTES.doctors.path, PUBLIC_CURRENT_LOCATION_LABEL, coordinates)
        )}
        selectedCoordinates={state.selectedCoordinates}
        radiusKm={state.radiusKm}
        onRadiusChange={state.setRadiusKm}
        note="Use the location control for city, current location, and nearby radius."
      />
      <PopularLinkChipRow
        title="Popular specialities"
        items={popularSpecialities.map((item) => item.speciality)}
        onSelect={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { q: value })}
        secondaryAction={{ label: "Browse all specialities", to: DISCOVER_ROUTES.specialities.path }}
      />

      <DirectoryResultsToolbar
        resultLabel={resultLabel}
        locationLabel={state.selectedLocation}
        filterSummary={filterSummary}
        selectedFilterCount={state.selectedFilterCount}
        sortValue={state.sort}
        sortOptions={[
          { value: "relevance", label: "Relevance" },
          { value: "distance", label: "Distance" },
          { value: "experience", label: "Experience" },
          { value: "fee-low", label: "Fee: Low to high" },
          { value: "fee-high", label: "Fee: High to low" },
        ]}
        onSortChange={(value) => state.updateParams(DISCOVER_ROUTES.doctors.path, { sort: value === "relevance" ? null : value })}
        onOpenFilters={() => setFiltersOpen(true)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.doctors.path)}
      />

      <div className="directory-layout">
        <DirectoryPageStickyPanel title="Doctor filters">
          {doctorFilterControls}
        </DirectoryPageStickyPanel>
        <div className="directory-results-column">
          <DirectoryState
            loading={doctors.loading || specialities.loading}
            error={doctors.error || specialities.error}
            empty={visibleDoctors.length === 0}
            emptyIcon="DR"
            emptyTitle={`No doctors found for ${state.selectedLocation}`}
            emptyMessage="Try another location, clear filters, or broaden the speciality search."
            primaryAction="Change search"
            primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
            secondaryAction="Browse specialities"
            secondaryTo={DISCOVER_ROUTES.specialities.path}
            errorTitle="We could not load doctors right now."
          >
            <div className="directory-card-grid directory-card-grid--doctors">
              {visibleDoctors.map((doctor) => (
                <DoctorDirectoryCard key={doctor.doctorSlug} doctor={doctor} />
              ))}
            </div>
            <PaginationBar
              page={doctors.data.page}
              totalPages={doctors.data.totalPages}
              onPageChange={(nextPage) => state.updateParams(DISCOVER_ROUTES.doctors.path, { page: nextPage })}
            />
          </DirectoryState>
        </div>
      </div>

      <DirectoryFiltersDrawer
        open={filtersOpen}
        title="Doctor filters"
        selectedCount={state.selectedFilterCount}
        onClose={() => setFiltersOpen(false)}
        onApply={() => setFiltersOpen(false)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.doctors.path)}
      >
        {doctorFilterControls}
      </DirectoryFiltersDrawer>
    </DirectoryPageShell>
  );
}

export function PublicDoctorDetailPage() {
  const { doctorSlug = "" } = useParams();
  const location = useLocation();
  const detail = usePublicResource<PublicDoctorDetailResponse | null>(`/api/public/doctors/${doctorSlug}`, {}, null);
  const doctorSummarySearch = usePublicResource<PublicPageResponse<PublicDoctorSummaryResponse>>(
    "/api/public/doctors",
    {
      q: doctorSlug.replaceAll("-", " "),
      page: 0,
      size: 100,
    },
    emptyDoctorsPage,
  );
  const matchedDoctor = useMemo(() => {
    const detailName = detail.data?.doctorDisplayName?.trim().toLowerCase() ?? "";
    return (
      doctorSummarySearch.data.items.find((item) => item.doctorSlug === doctorSlug) ??
      doctorSummarySearch.data.items.find((item) => item.doctorDisplayName.trim().toLowerCase() === detailName) ??
      null
    );
  }, [detail.data?.doctorDisplayName, doctorSlug, doctorSummarySearch.data.items]);
  const consultationFeeLabel = useMemo(() => formatConsultationFee(matchedDoctor?.consultationFee ?? null), [matchedDoctor?.consultationFee]);
  const profile = useMemo(() => (detail.data ? buildDoctorProfile(detail.data, consultationFeeLabel) : null), [detail.data, consultationFeeLabel]);
  const [visibleReviews, setVisibleReviews] = useState(3);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  const loading = detail.loading || doctorSummarySearch.loading;
  const error = detail.error || doctorSummarySearch.error;
  const relatedDoctors = doctorSummarySearch.data.items
    .filter((doctor) => doctor.doctorSlug !== doctorSlug)
    .filter((doctor) => {
      if (!detail.data) {
        return true;
      }
      const primarySpeciality = detail.data.primarySpeciality ?? detail.data.specialities[0] ?? null;
      return primarySpeciality ? doctor.speciality?.toLowerCase() === primarySpeciality.toLowerCase() : true;
    })
    .slice(0, 6);
  const bookingGroups = detail.data ? buildDoctorBookingGroups(detail.data) : [];
  const stickyBookingUrl = profile?.bookingUrl ?? careBookingUrl({ doctorId: doctorSlug });
  const breadcrumbItems = [
    { label: "Home", to: DISCOVER_ROUTES.home.path },
    { label: "Doctors", to: DISCOVER_ROUTES.doctors.path },
    { label: detail.data?.primarySpeciality ?? detail.data?.specialities[0] ?? "General Medicine", to: undefined },
    { label: detail.data?.doctorDisplayName ?? "Doctor profile", current: true },
  ];

  return (
    <section className="page-section">
      <DirectoryState
        loading={loading}
        error={error}
        empty={!detail.data}
        emptyIcon="DR"
        emptyTitle="Doctor profile unavailable"
        emptyMessage="This doctor profile is not available in Discover right now."
        primaryAction="Browse doctors"
        primaryTo={DISCOVER_ROUTES.doctors.path}
        secondaryAction="View clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
      >
        {profile ? (
          <>
            <DoctorBreadcrumb items={breadcrumbItems} />
            <div className="doctor-profile-layout">
              <div className="doctor-profile-layout__main">
                <div className="provider-preview-page-body provider-preview-page-body--public">
                  <PublicProviderProfile {...profile} />
                </div>

                <section className="doctor-profile-section doctor-profile-section--reviews">
                  <div className="doctor-section-heading">
                    <span className="eyebrow">Patient Reviews</span>
                    <h2>Patient Reviews</h2>
                  </div>
                  <RatingSummary rating={4.8} reviewCount={245} recommendationPercent={98} />
                  <div className="doctor-review-grid">
                    {doctorSampleReviews.slice(0, visibleReviews).map((review) => (
                      <ReviewCard key={review.id} review={review} />
                    ))}
                  </div>
                  {visibleReviews < doctorSampleReviews.length ? (
                    <button className="secondary-button" type="button" onClick={() => setVisibleReviews((current) => Math.min(current + 3, doctorSampleReviews.length))}>
                      Load More
                    </button>
                  ) : null}
                </section>

                <section className="doctor-profile-section doctor-profile-section--related">
                  <div className="doctor-section-heading">
                    <span className="eyebrow">Similar Doctors Near You</span>
                    <h2>Similar Doctors Near You</h2>
                  </div>
                  <div className="doctor-related-grid">
                    {relatedDoctors.length ? relatedDoctors.map((doctor) => (
                      <RelatedDoctorCard
                        key={doctor.doctorSlug}
                        doctor={{
                          doctorDisplayName: doctor.doctorDisplayName,
                          doctorSlug: doctor.doctorSlug,
                          photoUrl: doctor.photoUrl,
                          speciality: doctor.speciality,
                          yearsOfExperience: doctor.yearsOfExperience,
                          consultationFee: doctor.consultationFee ?? null,
                          clinicDisplayName: doctor.clinicDisplayName,
                          clinicSlug: doctor.clinicSlug,
                          contactPhone: doctor.contactPhone ?? null,
                          bookingMode: doctor.bookingMode ?? null,
                          availableToday: doctor.availableToday,
                          publicDoctorId: doctor.publicDoctorId,
                        }}
                      />
                    )) : (
                      <div className="doctor-empty-state">
                        <p>No related doctors were found yet. Please explore other doctors nearby.</p>
                      </div>
                    )}
                  </div>
                </section>

                <section className="doctor-profile-section doctor-profile-section--specialties">
                  <div className="doctor-section-heading">
                    <span className="eyebrow">Explore Other Specialties</span>
                    <h2>Explore Other Specialties</h2>
                  </div>
                  <div className="doctor-specialty-grid">
                    {doctorSampleSpecialties.map((specialty) => (
                      <SpecialtyCard key={specialty.slug} specialty={specialty} />
                    ))}
                  </div>
                </section>
              </div>

              <aside className="doctor-profile-layout__sidebar">
                <BookingPanel
                  consultationFee={consultationFeeLabel ?? "Fee shared after booking"}
                  nextAvailableDays={bookingGroups}
                  consultationModes={profile.consultationModes ?? []}
                  clinicName={detail.data?.clinics[0]?.clinicDisplayName ?? detail.data?.doctorDisplayName ?? "Clinic"}
                  averageWaitTime="15 min"
                  appointmentDuration="20 min"
                  bookingUrl={profile.bookingUrl}
                  callHref={profile.callHref}
                  callLabel="Call Clinic"
                />
                <AvailabilityTimeline
                  days={
                    profile.workingHoursSchedule ?? [
                      { day: "Mon", hours: "9:00 AM - 6:00 PM", current: true },
                      { day: "Tue", hours: "9:00 AM - 6:00 PM" },
                      { day: "Wed", hours: "9:00 AM - 6:00 PM" },
                      { day: "Thu", hours: "9:00 AM - 6:00 PM" },
                      { day: "Fri", hours: "9:00 AM - 6:00 PM" },
                      { day: "Sat", hours: "10:00 AM - 2:00 PM" },
                      { day: "Sun", hours: "Closed", closed: true },
                    ]
                  }
                />
              </aside>
            </div>
            <StickyBookingCTA bookingUrl={stickyBookingUrl} />
          </>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicClinicsPage() {
  const state = useDirectoryPageState();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const clinicDirectory = useLoadMoreDirectoryResults<PublicClinicSummaryResponse>({
    path: "/api/public/clinics",
    params: {
      q: state.searchParams.get("q"),
      city: state.selectedLocation,
      lat: state.selectedCoordinates ? `${state.selectedCoordinates.latitude}` : null,
      lng: state.selectedCoordinates ? `${state.selectedCoordinates.longitude}` : null,
      radiusKm: state.selectedCoordinates ? state.radiusKm : null,
      availableToday: state.searchParams.get("availableToday"),
      specialities: state.searchParams.get("specialities"),
    },
    pageSize: 7,
  });
  const visibleClinics = useMemo(
    () => filterClinicsDirectory(clinicDirectory.items, state.searchParams.get("q") ?? "", state.searchParams, state.selectedLocation),
    [clinicDirectory.items, state.searchParams, state.selectedLocation],
  );
  const popularAreas = useMemo(() => {
    const values = clinicDirectory.items.map((clinic) => clinic.area ?? clinic.city ?? "").filter(Boolean);
    return Array.from(new Set(values)).slice(0, 8);
  }, [clinicDirectory.items]);
  const popularServices = useMemo(
    () => Array.from(new Set(clinicDirectory.items.flatMap((clinic) => clinic.specialities))).filter(Boolean).slice(0, 8),
    [clinicDirectory.items],
  );
  const selectedServices = getStringListParam(state.searchParams, "specialities");
  const filterSummary = buildClinicFilterSummary(state.searchParams, state.selectedCoordinates);
  const resultLabel = buildDirectoryResultLabel(clinicDirectory.totalItems, "clinic", state.selectedLocation);
  const loadMoreSummary = clinicDirectory.totalItems > visibleClinics.length ? `Showing ${visibleClinics.length} of ${clinicDirectory.totalItems} clinics` : null;
  const clinicFilterControls = (
    <>
      <DirectoryToggleFilters
        label="Availability"
        items={[{ value: "availableToday", label: "Available today" }]}
        active={new Set(getBooleanParam(state.searchParams, "availableToday") ? ["availableToday"] : [])}
        onToggle={() => state.updateParams(DISCOVER_ROUTES.clinics.path, { availableToday: getBooleanParam(state.searchParams, "availableToday") ? null : true })}
      />
      <DirectoryFilterChips
        label="Clinic services"
        values={popularServices.length ? popularServices : ["General Medicine", "Dermatology", "Pediatrics"]}
        active={new Set(selectedServices)}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.clinics.path, { specialities: joinFilterValues(toggleFilterValue(selectedServices, value)) })}
        onClear={() => state.updateParams(DISCOVER_ROUTES.clinics.path, { specialities: null })}
      />
      <DirectoryFilterChips
        label="Popular areas"
        values={popularAreas.length ? popularAreas : [state.selectedLocation]}
        active={new Set()}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.clinics.path, { q: value })}
      />
    </>
  );

  return (
    <DirectoryPageShell className={pageAccentClass("clinics")}>
      <DirectoryHero
        eyebrow="Clinic directory"
        title="Find clinics near you"
        body="Explore clinics by area, service, and location."
        accent={pageAccentTone("clinics")}
      />
      <DirectorySearchPanel
        query={state.queryDraft}
        onQueryChange={state.setQueryDraft}
        placeholder={pageSearchPlaceholder("clinics")}
        searchButtonLabel={pageSearchButtonLabel("clinics")}
        onSubmit={(event) => {
          event.preventDefault();
          state.commitSearch(DISCOVER_ROUTES.clinics.path);
        }}
        locationLabel={state.selectedLocation}
        onLocationCommit={(nextLocation, nextCoordinates) => state.commitLocation(DISCOVER_ROUTES.clinics.path, nextLocation, nextCoordinates)}
        onUseCurrentLocation={createCurrentLocationHandler((coordinates) =>
          state.commitLocation(DISCOVER_ROUTES.clinics.path, PUBLIC_CURRENT_LOCATION_LABEL, coordinates)
        )}
        selectedCoordinates={state.selectedCoordinates}
        radiusKm={state.radiusKm}
        onRadiusChange={state.setRadiusKm}
        note="Search clinics, services, and areas using real published data."
      />
      <PopularLinkChipRow
        title="Popular areas"
        items={popularAreas.length ? popularAreas : [state.selectedLocation]}
        onSelect={(value) => state.updateParams(DISCOVER_ROUTES.clinics.path, { q: value })}
        secondaryAction={{ label: "Browse doctors", to: DISCOVER_ROUTES.doctors.path }}
      />
      <DirectoryResultsToolbar
        resultLabel={resultLabel}
        locationLabel={state.selectedLocation}
        filterSummary={filterSummary}
        selectedFilterCount={state.selectedFilterCount}
        sortValue={state.sort}
        sortOptions={[
          { value: "relevance", label: "Relevance" },
          { value: "distance", label: "Distance" },
          { value: "doctors", label: "Doctor count" },
          { value: "name", label: "Name" },
        ]}
        onSortChange={(value) => state.updateParams(DISCOVER_ROUTES.clinics.path, { sort: value === "relevance" ? null : value })}
        onOpenFilters={() => setFiltersOpen(true)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.clinics.path)}
      />
      <div className="directory-layout">
        <DirectoryPageStickyPanel title="Clinic filters">
          {clinicFilterControls}
        </DirectoryPageStickyPanel>
        <div className="directory-results-column">
          <DirectoryState
            loading={clinicDirectory.loadingInitial}
            error={clinicDirectory.initialError}
            empty={!clinicDirectory.loadingInitial && !clinicDirectory.initialError && visibleClinics.length === 0}
            emptyIcon="CL"
            emptyTitle={`No clinics found for ${state.selectedLocation}`}
            emptyMessage="Try a different location, clear filters, or browse doctors and specialities."
            primaryAction="Change search"
            primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
            secondaryAction="Browse doctors"
            secondaryTo={DISCOVER_ROUTES.doctors.path}
            errorTitle="We could not load clinics right now."
          >
            <DirectoryResultList
              className="directory-card-grid directory-card-grid--clinics"
              hasMore={clinicDirectory.hasMore}
              loadMoreLabel="Load more clinics"
              loadMoreSummary={loadMoreSummary}
              loadMoreError={clinicDirectory.loadMoreError}
              loadingMore={clinicDirectory.loadingMore}
              onLoadMore={clinicDirectory.loadMore}
              onRetryLoadMore={clinicDirectory.retryLoadMore}
            >
              {visibleClinics.map((clinic) => (
                <ClinicDirectoryCard key={clinic.clinicSlug} clinic={clinic} />
              ))}
            </DirectoryResultList>
          </DirectoryState>
        </div>
      </div>
      <DirectoryFiltersDrawer
        open={filtersOpen}
        title="Clinic filters"
        selectedCount={state.selectedFilterCount}
        onClose={() => setFiltersOpen(false)}
        onApply={() => setFiltersOpen(false)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.clinics.path)}
      >
        {clinicFilterControls}
      </DirectoryFiltersDrawer>
    </DirectoryPageShell>
  );
}

export function PublicClinicDetailPage() {
  const { clinicSlug = "" } = useParams();
  const location = useLocation();
  const detail = usePublicResource<PublicClinicDetailResponse | null>(`/api/public/clinics/${clinicSlug}`, {}, null);
  const profile = useMemo(() => (detail.data ? buildClinicProfile(detail.data) : null), [detail.data]);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyIcon="CL"
        emptyTitle="Clinic profile unavailable"
        emptyMessage="This clinic profile is not available in Discover right now."
        primaryAction="Browse clinics"
        primaryTo={DISCOVER_ROUTES.clinics.path}
        secondaryAction="Find doctors"
        secondaryTo={DISCOVER_ROUTES.doctors.path}
      >
        {profile ? (
          <div className="provider-preview-page-body provider-preview-page-body--public">
            <PublicProviderProfile {...profile} />
          </div>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicHospitalsPage() {
  const state = useDirectoryPageState();
  const [filtersOpen, setFiltersOpen] = useState(false);
  const hospitalDirectory = useLoadMoreDirectoryResults<PublicHospitalSummaryResponse>({
    path: "/api/public/hospitals",
    params: {
      q: state.searchParams.get("q"),
      city: state.selectedLocation,
      lat: state.selectedCoordinates ? `${state.selectedCoordinates.latitude}` : null,
      lng: state.selectedCoordinates ? `${state.selectedCoordinates.longitude}` : null,
      radiusKm: state.selectedCoordinates ? state.radiusKm : null,
      departments: state.searchParams.get("departments"),
    },
    pageSize: 7,
  });
  const visibleHospitals = useMemo(
    () => filterHospitalsDirectory(hospitalDirectory.items, state.searchParams.get("q") ?? "", state.searchParams, state.selectedLocation),
    [hospitalDirectory.items, state.searchParams, state.selectedLocation],
  );
  const popularDepartments = useMemo(() => {
    const values = hospitalDirectory.items.flatMap((hospital) => hospital.departments).filter(Boolean);
    return Array.from(new Set(values)).slice(0, 8);
  }, [hospitalDirectory.items]);
  const selectedDepartments = getStringListParam(state.searchParams, "departments");
  const filterSummary = buildHospitalFilterSummary(state.searchParams, state.selectedCoordinates);
  const resultLabel = buildDirectoryResultLabel(hospitalDirectory.totalItems, "hospital", state.selectedLocation);
  const loadMoreSummary = hospitalDirectory.totalItems > visibleHospitals.length ? `Showing ${visibleHospitals.length} of ${hospitalDirectory.totalItems} hospitals` : null;
  const hospitalFilterControls = (
    <>
      <DirectoryFilterChips
        label="Departments"
        values={popularDepartments.length ? popularDepartments : ["General Medicine", "Cardiology", "Orthopedics"]}
        active={new Set(selectedDepartments)}
        onToggle={(value) => state.updateParams(DISCOVER_ROUTES.hospitals.path, { departments: joinFilterValues(toggleFilterValue(selectedDepartments, value)) })}
        onClear={() => state.updateParams(DISCOVER_ROUTES.hospitals.path, { departments: null })}
      />
    </>
  );

  return (
    <DirectoryPageShell className={pageAccentClass("hospitals")}>
      <DirectoryHero
        eyebrow="Hospital directory"
        title="Explore hospitals and specialty care"
        body="Browse hospitals by area, department, and public capability."
        accent={pageAccentTone("hospitals")}
      />
      <DirectorySearchPanel
        query={state.queryDraft}
        onQueryChange={state.setQueryDraft}
        placeholder={pageSearchPlaceholder("hospitals")}
        searchButtonLabel={pageSearchButtonLabel("hospitals")}
        onSubmit={(event) => {
          event.preventDefault();
          state.commitSearch(DISCOVER_ROUTES.hospitals.path);
        }}
        locationLabel={state.selectedLocation}
        onLocationCommit={(nextLocation, nextCoordinates) => state.commitLocation(DISCOVER_ROUTES.hospitals.path, nextLocation, nextCoordinates)}
        onUseCurrentLocation={createCurrentLocationHandler((coordinates) =>
          state.commitLocation(DISCOVER_ROUTES.hospitals.path, PUBLIC_CURRENT_LOCATION_LABEL, coordinates)
        )}
        selectedCoordinates={state.selectedCoordinates}
        radiusKm={state.radiusKm}
        onRadiusChange={state.setRadiusKm}
        note="Use location and department filters to explore public hospital profiles."
      />
      <PopularLinkChipRow
        title="Popular departments"
        items={popularDepartments.length ? popularDepartments : ["General Medicine", "Cardiology", "Orthopedics"]}
        onSelect={(value) => state.updateParams(DISCOVER_ROUTES.hospitals.path, { q: value })}
        secondaryAction={{ label: "Browse specialities", to: DISCOVER_ROUTES.specialities.path }}
      />
      <DirectoryResultsToolbar
        resultLabel={resultLabel}
        locationLabel={state.selectedLocation}
        filterSummary={filterSummary}
        selectedFilterCount={state.selectedFilterCount}
        sortValue={state.sort}
        sortOptions={[
          { value: "relevance", label: "Relevance" },
          { value: "distance", label: "Distance" },
          { value: "departments", label: "Department count" },
          { value: "name", label: "Name" },
        ]}
        onSortChange={(value) => state.updateParams(DISCOVER_ROUTES.hospitals.path, { sort: value === "relevance" ? null : value })}
        onOpenFilters={() => setFiltersOpen(true)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.hospitals.path)}
      />
      <div className="directory-layout">
        <DirectoryPageStickyPanel title="Hospital filters">
          {hospitalFilterControls}
        </DirectoryPageStickyPanel>
        <div className="directory-results-column">
          <DirectoryState
            loading={hospitalDirectory.loadingInitial}
            error={hospitalDirectory.initialError}
            empty={!hospitalDirectory.loadingInitial && !hospitalDirectory.initialError && visibleHospitals.length === 0}
            emptyIcon="H"
            emptyTitle={`No hospitals found for ${state.selectedLocation}`}
            emptyMessage="Try changing the location, clearing filters, or browsing clinics and specialities."
            primaryAction="Change search"
            primaryTo={`${DISCOVER_ROUTES.home.path}#find-care`}
            secondaryAction="Browse clinics"
            secondaryTo={DISCOVER_ROUTES.clinics.path}
            errorTitle="We could not load hospitals right now."
          >
            <DirectoryResultList
              className="directory-card-grid directory-card-grid--hospitals"
              hasMore={hospitalDirectory.hasMore}
              loadMoreLabel="Load more hospitals"
              loadMoreSummary={loadMoreSummary}
              loadMoreError={hospitalDirectory.loadMoreError}
              loadingMore={hospitalDirectory.loadingMore}
              onLoadMore={hospitalDirectory.loadMore}
              onRetryLoadMore={hospitalDirectory.retryLoadMore}
            >
              {visibleHospitals.map((hospital) => (
                <HospitalDirectoryCard key={hospital.hospitalSlug} hospital={hospital} />
              ))}
            </DirectoryResultList>
          </DirectoryState>
        </div>
      </div>
      <DirectoryFiltersDrawer
        open={filtersOpen}
        title="Hospital filters"
        selectedCount={state.selectedFilterCount}
        onClose={() => setFiltersOpen(false)}
        onApply={() => setFiltersOpen(false)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.hospitals.path)}
      >
        {hospitalFilterControls}
      </DirectoryFiltersDrawer>
    </DirectoryPageShell>
  );
}

export function PublicHospitalDetailPage() {
  const { hospitalSlug = "" } = useParams();
  const location = useLocation();
  const detail = usePublicResource<PublicHospitalDetailResponse | null>(`/api/public/hospitals/${hospitalSlug}`, {}, null);
  const profile = useMemo(() => (detail.data ? buildHospitalProfile(detail.data) : null), [detail.data]);

  if (detail.data?.publicPath && detail.data.publicPath !== location.pathname) {
    return <Navigate replace to={`${detail.data.publicPath}${location.search}`} />;
  }

  return (
    <section className="page-section">
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data}
        emptyIcon="H"
        emptyTitle="Hospital profile unavailable"
        emptyMessage="This hospital profile is not available in Discover right now."
        primaryAction="Browse hospitals"
        primaryTo={DISCOVER_ROUTES.hospitals.path}
        secondaryAction="Find clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
      >
        {profile ? (
          <div className="provider-preview-page-body provider-preview-page-body--public">
            <PublicProviderProfile {...profile} />
          </div>
        ) : null}
      </DirectoryState>
    </section>
  );
}

export function PublicSpecialitiesPage() {
  const state = useDirectoryPageState(24);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const specialities = usePublicResource<PublicSpecialitySummaryResponse[]>(
    "/api/public/specialities",
    { q: state.searchParams.get("q"), city: state.selectedLocation },
    [],
  );
  const visibleSpecialities = useMemo(
    () => filterSpecialitiesDirectory(specialities.data, state.searchParams.get("q") ?? "", state.searchParams),
    [specialities.data, state.searchParams],
  );
  const popularSpecialities = useMemo(
    () =>
      [...specialities.data]
        .filter((item) => item.doctorsCount > 0 || item.clinicsCount > 0)
        .sort((left, right) => (right.doctorsCount + right.clinicsCount + (right.hospitalsCount ?? 0)) - (left.doctorsCount + left.clinicsCount + (left.hospitalsCount ?? 0)))
        .slice(0, 8),
    [specialities.data],
  );
  const alphabet = useMemo(() => Array.from("ABCDEFGHIJKLMNOPQRSTUVWXYZ"), []);
  const availableLetters = useMemo(() => new Set(specialityAlphabet(specialities.data)), [specialities.data]);
  const activeLetter = state.searchParams.get("letter")?.trim().toUpperCase() || null;
  const filterSummary = buildSpecialityFilterSummary(state.searchParams);
  const resultLabel = buildDirectoryResultLabel(visibleSpecialities.length, "speciality", state.selectedLocation);

  return (
    <DirectoryPageShell className={pageAccentClass("specialities")}>
      <DirectoryHero
        eyebrow="Speciality directory"
        title="Explore specialities"
        body="Move from a broad category into the right doctors and clinics."
        accent={pageAccentTone("specialities")}
      />
      <DirectorySearchPanel
        query={state.queryDraft}
        onQueryChange={state.setQueryDraft}
        placeholder={pageSearchPlaceholder("specialities")}
        searchButtonLabel={pageSearchButtonLabel("specialities")}
        onSubmit={(event) => {
          event.preventDefault();
          state.commitSearch(DISCOVER_ROUTES.specialities.path);
        }}
        locationLabel={state.selectedLocation}
        onLocationCommit={(nextLocation, nextCoordinates) => state.commitLocation(DISCOVER_ROUTES.specialities.path, nextLocation, nextCoordinates)}
        onUseCurrentLocation={createCurrentLocationHandler((coordinates) =>
          state.commitLocation(DISCOVER_ROUTES.specialities.path, PUBLIC_CURRENT_LOCATION_LABEL, coordinates)
        )}
        selectedCoordinates={state.selectedCoordinates}
        radiusKm={state.radiusKm}
        onRadiusChange={state.setRadiusKm}
        note="Search specialities, conditions, and care categories using published taxonomy only."
      />
      <PopularSpecialityGrid items={popularSpecialities} />
      {visibleSpecialities.length > 10 ? (
        <AlphabetNavigation
          letters={alphabet}
          activeLetter={activeLetter}
          disabledLetters={new Set(alphabet.filter((letter) => !availableLetters.has(letter)))}
          onSelect={(letter) => state.updateParams(DISCOVER_ROUTES.specialities.path, { letter })}
        />
      ) : null}
      <DirectoryResultsToolbar
        resultLabel={resultLabel}
        locationLabel={state.selectedLocation}
        filterSummary={filterSummary}
        selectedFilterCount={state.selectedFilterCount}
        sortValue={state.sort}
        sortOptions={[
          { value: "relevance", label: "Relevance" },
          { value: "count", label: "Provider count" },
          { value: "clinics", label: "Clinic count" },
          { value: "name", label: "Name" },
        ]}
        onSortChange={(value) => state.updateParams(DISCOVER_ROUTES.specialities.path, { sort: value === "relevance" ? null : value })}
        onOpenFilters={() => setFiltersOpen(true)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.specialities.path)}
      />
      <div className="directory-layout directory-layout--stacked">
        <div className="directory-results-column">
          <DirectoryState
            loading={specialities.loading}
            error={specialities.error}
            empty={visibleSpecialities.length === 0}
            emptyIcon="＋"
            emptyTitle="Specialities are being prepared"
            emptyMessage="Published provider specialities will appear here as the directory grows."
            primaryAction="Browse doctors"
            primaryTo={DISCOVER_ROUTES.doctors.path}
            secondaryAction="Browse clinics"
            secondaryTo={DISCOVER_ROUTES.clinics.path}
            errorTitle="We could not load specialities right now."
          >
            <div className="directory-speciality-results">
              {visibleSpecialities.map((speciality) => (
                <SpecialityCard
                  key={speciality.specialitySlug}
                  speciality={speciality}
                  onSearchDoctors={`${DISCOVER_ROUTES.doctors.path}?speciality=${encodeURIComponent(speciality.speciality)}`}
                  onSearchClinics={`${DISCOVER_ROUTES.clinics.path}?speciality=${encodeURIComponent(speciality.speciality)}`}
                />
              ))}
            </div>
          </DirectoryState>
        </div>
        <AivaComingSoonPanel />
      </div>
      <DirectoryFiltersDrawer
        open={filtersOpen}
        title="Speciality filters"
        selectedCount={state.selectedFilterCount}
        onClose={() => setFiltersOpen(false)}
        onApply={() => setFiltersOpen(false)}
        onClear={() => state.clearParams(DISCOVER_ROUTES.specialities.path)}
      >
        <DirectoryFilterChips
          label="Browse A-Z"
          values={alphabet}
          active={new Set(activeLetter ? [activeLetter] : [])}
          onToggle={(value) => state.updateParams(DISCOVER_ROUTES.specialities.path, { letter: activeLetter === value ? null : value })}
          onClear={() => state.updateParams(DISCOVER_ROUTES.specialities.path, { letter: null })}
        />
      </DirectoryFiltersDrawer>
    </DirectoryPageShell>
  );
}

export function PublicSpecialityDetailPage() {
  const { specialitySlug = "" } = useParams();
  const filters = useDirectoryFilters();
  const detail = usePublicResource<PublicSpecialityDetailResponse | null>(
    `/api/public/specialities/${specialitySlug}`,
    {
      q: filters.searchParams.get("q"),
      city: filters.searchParams.get("city"),
      area: filters.searchParams.get("area"),
      clinic: filters.searchParams.get("clinic"),
      page: filters.page,
      size: filters.size,
    },
    null,
  );
  const visibleDoctors = useMemo(
    () => filterAndSortDoctorResults(detail.data?.doctors.items ?? [], filters.query, filters.city || PUBLIC_DEFAULT_LOCATION),
    [detail.data?.doctors.items, filters.city, filters.query],
  );

  return (
    <section className="page-section">
      <div className="section-heading">
        <span className="eyebrow">Speciality detail</span>
        <h1>{detail.data?.speciality ?? "Speciality"}</h1>
        <p>Browse doctor profiles for this speciality, then continue to Jeevanam Care when you are ready to book.</p>
      </div>
      <QueryToolbar
        actionLabel="Filter doctors"
        query={filters.query}
        setQuery={filters.setQuery}
        city={filters.city}
        setCity={filters.setCity}
        area={filters.area}
        setArea={filters.setArea}
        onSubmit={(event) => {
          event.preventDefault();
          filters.submit(DISCOVER_DETAIL_PATHS.speciality(specialitySlug));
        }}
      />
      <DirectoryState
        loading={detail.loading}
        error={detail.error}
        empty={!detail.data || visibleDoctors.length === 0}
        emptyMessage={discoveryEmptyMessage({
          query: filters.query || filters.city || filters.area,
          selectedLocation: filters.city,
          defaultMessage: "No doctors matched this speciality filter.",
        })}
        emptyIcon="DR"
        emptyTitle="No doctors found for this speciality"
        primaryAction="Browse all specialities"
        primaryTo={DISCOVER_ROUTES.specialities.path}
        secondaryAction="View clinics"
        secondaryTo={DISCOVER_ROUTES.clinics.path}
      >
        {detail.data ? (
          <>
            <div className="speciality-summary-card">
              <strong>{detail.data.speciality}</strong>
              <p>{visibleDoctors.length} doctor{visibleDoctors.length === 1 ? "" : "s"} across visible clinics.</p>
              <span>Search by doctor, clinic, city, or symptom to narrow the list.</span>
            </div>
            <div className="public-directory-grid">
              {visibleDoctors.map((doctor) => <DoctorCard key={doctor.doctorSlug} doctor={doctor} />)}
            </div>
            <PaginationBar page={detail.data.doctors.page} totalPages={detail.data.doctors.totalPages} onPageChange={(nextPage) => filters.changePage(DISCOVER_DETAIL_PATHS.speciality(specialitySlug), nextPage)} />
          </>
        ) : null}
      </DirectoryState>
    </section>
  );
}
