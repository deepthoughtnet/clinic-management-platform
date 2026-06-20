import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  IconButton,
  Link,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import QuizRoundedIcon from "@mui/icons-material/QuizRounded";
import SmartToyRoundedIcon from "@mui/icons-material/SmartToyRounded";
import NavigateNextRoundedIcon from "@mui/icons-material/NavigateNextRounded";
import { useNavigate } from "react-router-dom";

import { getHelpPage, searchHelp } from "../../../api/clinicApi";
import { useAuth } from "../../../auth/useAuth";
import { buildDefaultExpandedHelpSections, filterHelpSections, safeParseHelpJson, searchHelpSearchResults } from "./helpContent";
import HelpSearch from "./HelpSearch";
import { HelpSection } from "./PageHelpDrawer";
import type { HelpPageRecord, HelpSearchResult } from "./helpTypes";
import { loadRecentHelpPages, recordRecentHelpPage, type HelpRecentPage } from "./helpRecentPages";
import { resolveHelpRouteByPageKey } from "./helpPageRegistry";
import { getHelpErrorMessage, isHelpNotFoundError } from "./helpErrors";
import { HelpFAQ } from "./sectionRenderers";

type GlobalHelpDrawerProps = {
  open: boolean;
  pageKey: string;
  pageTitle?: string;
  onClose: () => void;
  onNavigate?: (path: string) => void;
};

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = React.useState(value);
  React.useEffect(() => {
    const handle = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(handle);
  }, [delayMs, value]);
  return debounced;
}

function getRelatedPages(page: HelpPageRecord | null): Array<{ title: string; pageKey?: string; description?: string; url?: string }> {
  if (!page) return [];
  const relatedSection = page.sections.find((section) => section.sectionType.toUpperCase() === "RELATED_PAGES");
  const parsed = safeParseHelpJson<Record<string, unknown>>(relatedSection?.contentJson || null);
  const items = Array.isArray(parsed?.items) ? parsed.items : [];
  return items
    .map((item) => ({
      title: String((item as Record<string, unknown>).title || ""),
      pageKey: typeof (item as Record<string, unknown>).pageKey === "string" ? String((item as Record<string, unknown>).pageKey) : undefined,
      description: typeof (item as Record<string, unknown>).description === "string" ? String((item as Record<string, unknown>).description) : undefined,
      url: typeof (item as Record<string, unknown>).url === "string" ? String((item as Record<string, unknown>).url) : undefined,
    }))
    .filter((item) => item.title);
}

function getFaqItems(page: HelpPageRecord | null): Array<{ question: string; answer: string }> {
  if (!page) return [];
  const faqSection = page.sections.find((section) => section.sectionType.toUpperCase() === "FAQ");
  const parsed = safeParseHelpJson<Record<string, unknown>>(faqSection?.contentJson || null);
  const items = Array.isArray(parsed?.items) ? parsed.items : [];
  return items
    .map((item) => ({
      question: String((item as Record<string, unknown>).question || ""),
      answer: String((item as Record<string, unknown>).answer || ""),
    }))
    .filter((item) => item.question && item.answer);
}

function resolveCurrentPage(route: { pageKey: string } | null, page: HelpPageRecord | null): HelpPageRecord | null {
  if (!route) return page;
  if (!page) return null;
  return page;
}

function HelpPanelTitle({ title, subtitle, icon }: { title: string; subtitle?: string; icon?: React.ReactNode }) {
  return (
    <Stack direction="row" spacing={1.25} alignItems="flex-start">
      {icon ? <Box sx={{ mt: 0.25 }}>{icon}</Box> : null}
      <Box>
        <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
          {title}
        </Typography>
        {subtitle ? (
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        ) : null}
      </Box>
    </Stack>
  );
}

function SectionContainer({ children }: { children: React.ReactNode }) {
  return (
    <Paper variant="outlined" sx={(theme) => ({ borderRadius: 2, p: 2, boxShadow: theme.shadows[1] })}>
      {children}
    </Paper>
  );
}

function SearchResultRow({
  result,
  onNavigate,
}: {
  result: HelpSearchResult;
  onNavigate: (pageKey: string) => void;
}) {
  const pageRoute = resolveHelpRouteByPageKey(result.pageKey);
  return (
    <Paper variant="outlined" sx={(theme) => ({ p: 1.5, borderRadius: 2, boxShadow: theme.shadows[1] })}>
      <Stack spacing={0.5}>
        <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
          {result.pageTitle}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {result.snippet}
        </Typography>
        {pageRoute ? (
          <Button size="small" variant="text" onClick={() => onNavigate(pageRoute.pageKey)}>
            Open page
          </Button>
        ) : null}
      </Stack>
    </Paper>
  );
}

function RecentPageChip({
  item,
  onNavigate,
}: {
  item: HelpRecentPage;
  onNavigate: (route: { path: string; pageKey: string; cmsPageKey: string; title: string }) => void;
}) {
  const route = resolveHelpRouteByPageKey(item.pageKey) || resolveHelpRouteByPageKey(item.cmsPageKey);
  if (!route) return null;
  return (
    <Chip
      clickable
      variant="outlined"
      label={item.title}
      onClick={() => onNavigate(route)}
      icon={<NavigateNextRoundedIcon fontSize="small" />}
    />
  );
}

export default function GlobalHelpDrawer({ open, pageKey, pageTitle, onClose, onNavigate }: GlobalHelpDrawerProps) {
  const auth = useAuth();
  const navigate = useNavigate();
  const disablePortal = typeof window === "undefined";
  const route = React.useMemo(() => {
    const resolved = resolveHelpRouteByPageKey(pageKey);
    if (resolved) return resolved;
    return {
      path: pageKey,
      pageKey: pageKey || "UNKNOWN_PAGE",
      cmsPageKey: pageKey || "UNKNOWN_PAGE",
      title: pageTitle || "Help",
    };
  }, [pageKey, pageTitle]);
  const [currentPage, setCurrentPage] = React.useState<HelpPageRecord | null>(null);
  const [currentLoading, setCurrentLoading] = React.useState(false);
  const [currentError, setCurrentError] = React.useState<string | null>(null);
  const [searchResults, setSearchResults] = React.useState<HelpSearchResult[]>([]);
  const [searchLoading, setSearchLoading] = React.useState(false);
  const [searchError, setSearchError] = React.useState<string | null>(null);
  const [currentNotFound, setCurrentNotFound] = React.useState(false);
  const [query, setQuery] = React.useState("");
  const debouncedQuery = useDebouncedValue(query, 300);
  const [recentPages, setRecentPages] = React.useState<HelpRecentPage[]>([]);
  const [expandedSections, setExpandedSections] = React.useState<Record<string, boolean>>({});
  const [currentReloadToken, setCurrentReloadToken] = React.useState(0);
  const [searchReloadToken, setSearchReloadToken] = React.useState(0);
  const headerTitle = route.pageKey === "UNKNOWN_PAGE" ? "No page-specific help is available for this page yet." : route.title;
  const headerSubtitle = route.pageKey === "UNKNOWN_PAGE" ? "No page-specific help is available for this page yet." : "Workflows, validations, errors, and guidance for this page.";

  React.useEffect(() => {
    if (!open) return;
    if (import.meta.env.DEV) {
      console.info("[help] help drawer opened", { pageKey: route.pageKey, cmsPageKey: route.cmsPageKey, title: route.title });
    }
    setRecentPages(loadRecentHelpPages());
    if (route.pageKey !== "UNKNOWN_PAGE") {
      setRecentPages(recordRecentHelpPage(route));
    } else if (import.meta.env?.MODE !== "production") {
      console.warn(`[help] No route mapping found for pageKey: ${pageKey}`);
    }
  }, [open, pageKey, route]);

  React.useEffect(() => {
    if (!open) return;
    let cancelled = false;
    async function loadCurrentPage() {
      if (route.cmsPageKey === "UNKNOWN_PAGE") {
        setCurrentPage(null);
        setCurrentLoading(false);
        setCurrentError(null);
        setCurrentNotFound(true);
        return;
      }
      setCurrentPage(null);
      setCurrentLoading(true);
      setCurrentError(null);
      setCurrentNotFound(false);
      if (import.meta.env.DEV) {
        console.info("[help] loading current page help", {
          resolvedPageKey: route.pageKey,
          backendPageKey: route.cmsPageKey,
          endpoint: `/api/help/page/${route.pageKey}?lang=en`,
        });
      }
      try {
        const response = await getHelpPage(route.pageKey, "en", auth.accessToken ?? undefined, auth.tenantId);
        if (!cancelled) {
          if (import.meta.env.DEV) {
            console.log("[help] getHelpPage success", {
              requestedPageKey: pageKey,
              resolvedPageKey: route.pageKey,
              backendPageKey: route.cmsPageKey,
              sections: response.sections.length,
            });
          }
          setCurrentPage(response);
          setExpandedSections(buildDefaultExpandedHelpSections(response));
        }
      } catch (error) {
        if (!cancelled) {
          if (isHelpNotFoundError(error)) {
            setCurrentPage(null);
            setCurrentError(null);
            setCurrentNotFound(true);
          } else {
            setCurrentError(getHelpErrorMessage(error));
            setCurrentNotFound(false);
          }
        }
      } finally {
        if (!cancelled) setCurrentLoading(false);
      }
    }
    void loadCurrentPage();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, open, route.cmsPageKey, currentReloadToken, pageKey, route.pageKey]);

  React.useEffect(() => {
    if (!open) return;
    const normalizedQuery = debouncedQuery.trim();
    if (normalizedQuery.length < 2) {
      setSearchResults([]);
      setSearchError(null);
      setSearchLoading(false);
      return;
    }
    let cancelled = false;
    async function loadSearch() {
      setSearchLoading(true);
      setSearchError(null);
      if (import.meta.env.DEV) {
        console.info("[help] loading help search", {
          query: normalizedQuery,
          endpoint: `/api/help/search?q=${encodeURIComponent(normalizedQuery)}&lang=en`,
        });
      }
      try {
        const response = await searchHelp(normalizedQuery, "en", auth.accessToken ?? undefined, auth.tenantId);
        if (!cancelled) setSearchResults(searchHelpSearchResults(response, normalizedQuery));
      } catch (error) {
        if (!cancelled) setSearchError(getHelpErrorMessage(error));
      } finally {
        if (!cancelled) setSearchLoading(false);
      }
    }
    void loadSearch();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, debouncedQuery, open, searchReloadToken]);

  React.useEffect(() => {
    if (!open) setQuery("");
  }, [open]);

  React.useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  const currentHelpPage = resolveCurrentPage(route, currentPage);
  const currentSections = React.useMemo(
    () => filterHelpSections(currentHelpPage, query).filter((section) => {
      const type = section.sectionType.toUpperCase();
      return type !== "FAQ" && type !== "RELATED_PAGES";
    }),
    [currentHelpPage, query],
  );
  const relatedPages = React.useMemo(() => getRelatedPages(currentHelpPage), [currentHelpPage]);
  const faqItems = React.useMemo(() => getFaqItems(currentHelpPage), [currentHelpPage]);

  function navigateToPath(path: string) {
    if (onNavigate) {
      onNavigate(path);
    } else {
      navigate(path);
    }
    onClose();
  }

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true, disablePortal }}
      PaperProps={{
        "data-testid": "global-help-drawer",
        role: "dialog",
        "aria-label": "Help drawer",
        sx: (theme) => ({
          width: { xs: "100vw", sm: 520, md: 560 },
          maxWidth: "100vw",
          position: "fixed",
          right: 0,
          top: 0,
          height: "100vh",
          zIndex: theme.zIndex.modal + 1,
          display: "flex",
          flexDirection: "column",
        }),
      }}
    >
      <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
        <Box sx={{ px: 2.25, py: 2, borderBottom: "1px solid", borderColor: "divider" }}>
          <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
            <Stack spacing={0.5}>
              <Stack direction="row" spacing={1} alignItems="center">
                <HelpCenterRoundedIcon fontSize="small" color="primary" />
                <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.15 }}>
                  Help Center
                </Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {headerTitle}
              </Typography>
              {route.pageKey === "UNKNOWN_PAGE" ? (
                <Typography variant="body2" color="text.secondary">
                  No page-specific help is available for this page yet.
                </Typography>
              ) : null}
            </Stack>
            <IconButton aria-label="Close help" onClick={onClose} size="small">
              <CloseRoundedIcon fontSize="small" />
            </IconButton>
          </Stack>
        </Box>

        <Box sx={{ px: 2.25, pt: 2, pb: 1.5 }}>
          <HelpSearch
            value={query}
            onChange={setQuery}
            placeholder="Search help, workflows, common errors, and FAQs..."
            autoFocus
          />
        </Box>
        <Divider />

        <Box sx={{ flex: 1, overflow: "auto", px: 2.25, py: 2.25 }}>
          <Stack spacing={2}>
            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="Current Page Help"
                  subtitle={headerSubtitle}
                  icon={<HelpCenterRoundedIcon fontSize="small" color="action" />}
                />
                {currentLoading ? (
                  <Alert
                    severity="info"
                    variant="outlined"
                    icon={<CircularProgress size={16} />}
                  >
                    Loading help...
                  </Alert>
                ) : null}
                {currentError ? (
                  <Alert
                    severity="error"
                    action={
                      <Button size="small" onClick={() => setCurrentReloadToken((current) => current + 1)}>
                        Retry
                      </Button>
                    }
                  >
                    {currentError}
                  </Alert>
                ) : null}
                {!currentLoading && !currentError && currentNotFound ? (
                  <Alert severity="info" variant="outlined">
                    No page-specific help is available for this page yet.
                  </Alert>
                ) : null}
                {!currentLoading && !currentError && !currentHelpPage && !currentNotFound ? (
                  <Alert severity="info" variant="outlined">
                    No page-specific help is available for this page yet.
                  </Alert>
                ) : null}
                {currentHelpPage && currentSections.length > 0 ? (
                  <Stack spacing={1}>
                    {currentSections.map((section) => (
                      <HelpSection
                        key={section.id}
                        section={section}
                        expanded={Boolean(expandedSections[section.sectionKey])}
                        onToggle={() => setExpandedSections((current) => ({ ...current, [section.sectionKey]: !current[section.sectionKey] }))}
                      />
                    ))}
                  </Stack>
                ) : null}
                {!currentLoading && !currentError && currentHelpPage && currentSections.length === 0 ? (
                  <Alert severity="info" variant="outlined">
                    No help found.
                  </Alert>
                ) : null}
              </Stack>
            </SectionContainer>

            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="Search All Help"
                  subtitle="Search page titles, descriptions, workflows, common errors, and FAQs."
                  icon={<SearchRoundedIcon fontSize="small" color="action" />}
                />
                {searchLoading ? (
                  <Alert severity="info" variant="outlined" icon={<CircularProgress size={16} />}>
                    Searching help...
                  </Alert>
                ) : null}
                {searchError ? (
                  <Alert severity="error" action={<Button size="small" onClick={() => setSearchReloadToken((current) => current + 1)}>Retry</Button>}>
                    Unable to load help.
                  </Alert>
                ) : null}
                {debouncedQuery.trim() ? (
                  <Typography variant="body2" color="text.secondary">
                    Search results for “{debouncedQuery.trim()}”
                  </Typography>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Start typing to search across all help pages.
                  </Typography>
                )}
                <Stack spacing={1}>
                  {searchResults.length === 0 && debouncedQuery.trim() && !searchLoading && !searchError ? (
                    <Alert severity="info" variant="outlined">
                      No help found.
                    </Alert>
                  ) : null}
                  {searchResults.map((result) => (
                    <SearchResultRow
                      key={`${result.pageKey}-${result.sectionKey}-${result.sectionType}`}
                      result={result}
                      onNavigate={(pageKey) => {
                        const routeToOpen = resolveHelpRouteByPageKey(pageKey);
                        if (routeToOpen) {
                          recordRecentHelpPage(routeToOpen);
                          navigateToPath(routeToOpen.path);
                        }
                      }}
                    />
                  ))}
                </Stack>
              </Stack>
            </SectionContainer>

            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="Recent Pages"
                  subtitle="Quick access to pages you opened recently."
                  icon={<HistoryRoundedIcon fontSize="small" color="action" />}
                />
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  {recentPages.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      No recent pages yet.
                    </Typography>
                  ) : recentPages.map((item) => (
                    <RecentPageChip
                      key={`${item.pageKey}-${item.visitedAt}`}
                      item={item}
                      onNavigate={(targetRoute) => {
                        recordRecentHelpPage(targetRoute);
                        navigateToPath(targetRoute.path);
                      }}
                    />
                  ))}
                </Stack>
              </Stack>
            </SectionContainer>

            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="Related Pages"
                  subtitle="Quick links related to the current page."
                  icon={<LinkRoundedIcon fontSize="small" color="action" />}
                />
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                  {relatedPages.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      No related pages configured.
                    </Typography>
                  ) : relatedPages.map((item) => {
                    const routeToOpen = item.pageKey ? resolveHelpRouteByPageKey(item.pageKey) : null;
                    if (routeToOpen) {
                      return (
                        <Chip
                          key={`${item.title}-${item.pageKey || item.url}`}
                          label={routeToOpen.title}
                          icon={<LinkRoundedIcon fontSize="small" />}
                          variant="outlined"
                          clickable
                          onClick={() => navigateToPath(routeToOpen.path)}
                        />
                      );
                    }
                    if (item.url) {
                      return (
                        <Chip
                          key={`${item.title}-${item.pageKey || item.url}`}
                          label={item.title}
                          icon={<LinkRoundedIcon fontSize="small" />}
                          variant="outlined"
                          component={Link}
                          href={item.url}
                          target={item.url.startsWith("http") ? "_blank" : undefined}
                          rel={item.url.startsWith("http") ? "noreferrer" : undefined}
                          clickable
                        />
                      );
                    }
                    return (
                      <Chip
                        key={`${item.title}-${item.pageKey || item.url}`}
                        label={item.title}
                        icon={<LinkRoundedIcon fontSize="small" />}
                        variant="outlined"
                      />
                    );
                  })}
                </Stack>
              </Stack>
            </SectionContainer>

            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="FAQ"
                  subtitle="Common questions and answers for the current page."
                  icon={<QuizRoundedIcon fontSize="small" color="action" />}
                />
                {faqItems.length === 0 ? (
                  <Typography variant="body2" color="text.secondary">
                    No FAQs have been added yet.
                  </Typography>
                ) : (
                  <HelpFAQ items={faqItems} />
                )}
              </Stack>
            </SectionContainer>

            <SectionContainer>
              <Stack spacing={1.25}>
                <HelpPanelTitle
                  title="Ask AIVA"
                  subtitle="Ask questions in natural language."
                  icon={<SmartToyRoundedIcon fontSize="small" color="action" />}
                />
                <Alert severity="info" variant="outlined">
                  Coming soon. Ask AIVA will search Help CMS in a future rollout.
                </Alert>
                <Stack spacing={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    Examples
                  </Typography>
                  {[
                    "How do I add a medicine?",
                    "Why is batch number mandatory?",
                    "How do I disable a medicine?",
                  ].map((example) => (
                    <Typography key={example} variant="body2" color="text.secondary">
                      • {example}
                    </Typography>
                  ))}
                </Stack>
              </Stack>
            </SectionContainer>
          </Stack>
        </Box>
      </Box>
    </Drawer>
  );
}
