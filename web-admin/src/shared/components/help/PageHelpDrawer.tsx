import * as React from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Drawer,
  CircularProgress,
  IconButton,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import SyncAltRoundedIcon from "@mui/icons-material/SyncAltRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import QuizRoundedIcon from "@mui/icons-material/QuizRounded";
import LightbulbRoundedIcon from "@mui/icons-material/LightbulbRounded";
import LockRoundedIcon from "@mui/icons-material/LockRounded";
import GroupsRoundedIcon from "@mui/icons-material/GroupsRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import StarRoundedIcon from "@mui/icons-material/StarRounded";
import BoltRoundedIcon from "@mui/icons-material/BoltRounded";
import TableRowsRoundedIcon from "@mui/icons-material/TableRowsRounded";
import DownloadRoundedIcon from "@mui/icons-material/DownloadRounded";
import ImageRoundedIcon from "@mui/icons-material/ImageRounded";
import PlayCircleRoundedIcon from "@mui/icons-material/PlayCircleRounded";
import ArticleRoundedIcon from "@mui/icons-material/ArticleRounded";

import { getHelpPage } from "../../../api/clinicApi";
import { useAuth } from "../../../auth/useAuth";
import HelpSearch from "./HelpSearch";
import {
  buildDefaultExpandedHelpSections,
  filterHelpSections,
  getHelpSectionPresentation,
  safeParseHelpJson,
} from "./helpContent";
import type { HelpPageRecord, HelpSectionRecord } from "./helpTypes";
import { getHelpErrorMessage, isHelpNotFoundError } from "./helpErrors";
import {
  HelpAttachments,
  HelpBestPractices,
  HelpDashboardCards,
  HelpCommonErrors,
  HelpCommonIssues,
  HelpDescription,
  HelpFAQ,
  HelpKnownLimitations,
  HelpRoles,
  HelpPermissions,
  HelpRelatedPages,
  HelpReportFilters,
  HelpReportTypes,
  HelpExportCsv,
  HelpQuickActions,
  HelpValidationRules,
  HelpAudit,
  HelpTips,
  HelpWorkflow,
} from "./sectionRenderers";

function renderSectionIcon(sectionType: string) {
  const iconKey = getHelpSectionPresentation(sectionType).iconKey;
  switch (iconKey) {
    case "description":
      return <DescriptionRoundedIcon fontSize="small" color="action" />;
    case "workflow":
      return <SyncAltRoundedIcon fontSize="small" color="action" />;
    case "validation":
      return <CheckCircleRoundedIcon fontSize="small" color="action" />;
    case "table":
      return <TableRowsRoundedIcon fontSize="small" color="action" />;
    case "download":
      return <DownloadRoundedIcon fontSize="small" color="action" />;
    case "quickActions":
      return <LinkRoundedIcon fontSize="small" color="action" />;
    case "dashboardCards":
      return <TableRowsRoundedIcon fontSize="small" color="action" />;
    case "permissions":
      return <LockRoundedIcon fontSize="small" color="action" />;
    case "roles":
      return <GroupsRoundedIcon fontSize="small" color="action" />;
    case "errors":
      return <WarningAmberRoundedIcon fontSize="small" color="action" />;
    case "faq":
      return <QuizRoundedIcon fontSize="small" color="action" />;
    case "tips":
      return <LightbulbRoundedIcon fontSize="small" color="action" />;
    case "relatedPages":
      return <LinkRoundedIcon fontSize="small" color="action" />;
    case "bestPractices":
      return <StarRoundedIcon fontSize="small" color="action" />;
    case "limitations":
      return <BoltRoundedIcon fontSize="small" color="action" />;
    case "videos":
      return <PlayCircleRoundedIcon fontSize="small" color="action" />;
    case "images":
      return <ImageRoundedIcon fontSize="small" color="action" />;
    case "links":
      return <LinkRoundedIcon fontSize="small" color="action" />;
    case "audit":
      return <ArticleRoundedIcon fontSize="small" color="action" />;
    case "fieldTable":
      return <TableRowsRoundedIcon fontSize="small" color="action" />;
    default:
      return <DescriptionRoundedIcon fontSize="small" color="action" />;
  }
}

type PageHelpDrawerProps = {
  open: boolean;
  pageKey: string;
  pageTitle?: string;
  languageCode?: string;
  onClose: () => void;
};

function renderSectionContent(section: HelpSectionRecord) {
  const sectionType = section.sectionType.toUpperCase();
  const content = safeParseHelpJson<Record<string, unknown>>(section.contentJson);
  switch (sectionType) {
    case "DESCRIPTION":
      return <HelpDescription content={content as any} />;
    case "WORKFLOW":
      return <HelpWorkflow steps={Array.isArray(content?.steps) ? (content.steps as any[]) : []} />;
    case "FIELD_TABLE":
      return null;
    case "VALIDATION_RULES":
      return <HelpValidationRules rules={Array.isArray(content?.rules) ? (content.rules as any[]) : []} />;
    case "REPORT_TYPES":
      return <HelpReportTypes items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "FILTERS":
      return <HelpReportFilters items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "EXPORT_CSV":
      return <HelpExportCsv items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "QUICK_ACTIONS":
      return <HelpQuickActions items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "DASHBOARD_CARDS":
      return <HelpDashboardCards items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "PERMISSIONS": {
      const permissionsSource = Array.isArray(content?.permissions)
        ? (content.permissions as any[])
        : Array.isArray(content?.items)
          ? (content.items as any[])
          : [];
      const permissions = permissionsSource
        .flatMap((item) => String(item?.answer ?? item?.permission ?? item ?? "").split(","))
        .map((value) => value.trim())
        .filter(Boolean);
      return <HelpPermissions permissions={permissions} />;
    }
    case "COMMON_ERRORS":
      return <HelpCommonErrors items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "COMMON_ISSUES":
      return <HelpCommonIssues items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "BEST_PRACTICES":
      return <HelpBestPractices items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "FAQ":
      return <HelpFAQ items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "RELATED_PAGES":
      return <HelpRelatedPages pages={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "VIDEOS":
    case "IMAGES":
      return <HelpAttachments attachments={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "LINKS":
      return <HelpAttachments attachments={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "AUDIT":
      return <HelpAudit items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "ROLES": {
      const roles = content && typeof content === "object" ? content as Record<string, unknown> : {};
      return (
        <HelpRoles
          allowedRoles={Array.isArray(roles.allowedRoles) ? roles.allowedRoles.map((value) => String(value)) : []}
          notIntendedFor={Array.isArray(roles.notIntendedFor) ? roles.notIntendedFor.map((value) => String(value)) : []}
          description={typeof roles.description === "string" ? roles.description : undefined}
        />
      );
    }
    case "TIPS":
      return <HelpTips items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    case "KNOWN_LIMITATIONS":
      return <HelpKnownLimitations items={Array.isArray(content?.items) ? (content.items as any[]) : []} />;
    default:
      return content ? <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{JSON.stringify(content, null, 2)}</Typography> : <Typography variant="body2" color="text.secondary">No help content.</Typography>;
  }
}

export function HelpSection({ section, expanded, onToggle }: { section: HelpSectionRecord; expanded: boolean; onToggle: () => void }) {
  const presentation = getHelpSectionPresentation(section.sectionType);
  const content = renderSectionContent(section);
  return (
    <Accordion
      expanded={expanded}
      onChange={onToggle}
      disableGutters
      sx={(theme) => ({
        "&:before": { display: "none" },
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        overflow: "hidden",
        boxShadow: theme.shadows[1],
      })}
    >
      <AccordionSummary expandIcon={section.collapsible ? <ExpandMoreRoundedIcon /> : null}>
        <Stack direction="row" spacing={1} alignItems="center">
          {renderSectionIcon(section.sectionType)}
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
            {presentation.label}
          </Typography>
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ pt: 0.5, pb: 2 }}>
        {content}
      </AccordionDetails>
    </Accordion>
  );
}

function HelpDrawerContent({ page, query, onQueryChange }: { page: HelpPageRecord; query: string; onQueryChange: (value: string) => void }) {
  const [expandedKeys, setExpandedKeys] = React.useState<Record<string, boolean>>({});

  React.useEffect(() => {
    const next: Record<string, boolean> = {};
    Object.assign(next, buildDefaultExpandedHelpSections(page));
    setExpandedKeys(next);
  }, [page.pageKey]);

  const visibleSections = React.useMemo(() => filterHelpSections(page, query), [page, query]);

  return (
    <Stack spacing={2} sx={{ p: 2, width: { xs: "100vw", sm: 520, md: 640 }, maxWidth: "100vw" }}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={1}>
        <Box>
          <Stack direction="row" spacing={1} alignItems="center">
            <HelpCenterRoundedIcon fontSize="small" color="primary" />
            <Typography variant="h6" sx={{ fontWeight: 900 }}>
              Help Center
            </Typography>
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {page.title}
          </Typography>
        </Box>
      </Stack>
      <HelpSearch value={query} onChange={onQueryChange} placeholder="Search help, workflows, common errors, and FAQs..." />
      {query.trim() ? (
        <Alert severity="info" variant="outlined">
          Search results for “{query.trim()}”
        </Alert>
      ) : null}
      <Stack spacing={1.25}>
        {visibleSections.length === 0 ? (
          <Alert severity="info" variant="outlined">
            No matching help content.
          </Alert>
        ) : visibleSections.map((section) => (
          <HelpSection
            key={section.id}
            section={section}
            expanded={Boolean(expandedKeys[section.sectionKey])}
            onToggle={() => setExpandedKeys((current) => ({ ...current, [section.sectionKey]: !current[section.sectionKey] }))}
          />
        ))}
      </Stack>
    </Stack>
  );
}

export default function PageHelpDrawer({ open, pageKey, pageTitle, languageCode = "en", onClose }: PageHelpDrawerProps) {
  const auth = useAuth();
  const disablePortal = typeof window === "undefined";
  const [page, setPage] = React.useState<HelpPageRecord | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [notFound, setNotFound] = React.useState(false);
  const [query, setQuery] = React.useState("");

  React.useEffect(() => {
    if (!open) return;
    let cancelled = false;
    async function load() {
      setPage(null);
      setLoading(true);
      setError(null);
      setNotFound(false);
      if (import.meta.env.DEV) {
        console.info("[help] loading page help", {
          pageKey,
          endpoint: `/api/help/page/${pageKey}?lang=${encodeURIComponent(languageCode)}`,
        });
      }
      try {
        const response = await getHelpPage(pageKey, languageCode, auth.accessToken ?? undefined, auth.tenantId);
        if (import.meta.env.DEV) {
          console.info("[help] help API status", {
            endpoint: `/api/help/page/${pageKey}?lang=${encodeURIComponent(languageCode)}`,
            status: 200,
            pageKey: response.pageKey,
            sections: response.sections.length,
            sectionKeys: response.sections.map((section) => section.sectionKey),
          });
        }
        if (!cancelled) setPage(response);
      } catch (e) {
        if (!cancelled) {
          if (import.meta.env.DEV) {
            console.warn("[help] help page load failed", {
              pageKey,
              error: e,
            });
          }
          if (isHelpNotFoundError(e)) {
            setPage(null);
            setNotFound(true);
          } else {
            setError(getHelpErrorMessage(e));
          }
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [auth.accessToken, auth.tenantId, languageCode, open, pageKey]);

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

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true, disablePortal }}
      PaperProps={{
        sx: (theme) => ({
          width: { xs: "100vw", sm: 520, md: 680 },
          maxWidth: "100vw",
          position: "fixed",
          right: 0,
          top: 0,
          height: "100vh",
          zIndex: theme.zIndex.modal + 1,
        }),
      }}
    >
      <Stack spacing={1.5} sx={{ height: "100%" }}>
        <Box sx={{ px: 2, py: 1.5, borderBottom: "1px solid", borderColor: "divider", display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
          <Box>
            <Stack direction="row" spacing={1} alignItems="center">
              <HelpCenterRoundedIcon fontSize="small" color="primary" />
              <Typography variant="subtitle1" sx={{ fontWeight: 900 }}>
                Help Center
              </Typography>
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {pageTitle || "Help"}
            </Typography>
          </Box>
          <IconButton aria-label="Close help" onClick={onClose} size="small">
            <CloseRoundedIcon fontSize="small" />
          </IconButton>
        </Box>
        {loading ? (
          <Alert severity="info" variant="outlined" sx={{ mx: 2 }} icon={<CircularProgress size={16} />}>
            Loading help...
          </Alert>
        ) : null}
        {error ? <Alert severity="error" sx={{ mx: 2 }}>{error}</Alert> : null}
        {page ? <HelpDrawerContent page={page} query={query} onQueryChange={setQuery} /> : null}
        {!loading && !error && !page ? (
          <Box sx={{ p: 2 }}>
            <Alert severity="info" variant="outlined">
              {notFound ? "No page-specific help is available for this page yet." : "No page-specific help is available for this page yet."}
            </Alert>
          </Box>
        ) : null}
      </Stack>
    </Drawer>
  );
}
