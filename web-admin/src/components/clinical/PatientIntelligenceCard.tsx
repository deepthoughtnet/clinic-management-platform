import * as React from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Divider,
  Drawer,
  IconButton,
  Stack,
  Typography,
} from "@mui/material";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import TimelineRoundedIcon from "@mui/icons-material/TimelineRounded";
import ScienceRoundedIcon from "@mui/icons-material/ScienceRounded";
import DescriptionRoundedIcon from "@mui/icons-material/DescriptionRounded";
import HealingRoundedIcon from "@mui/icons-material/HealingRounded";
import CheckCircleRoundedIcon from "@mui/icons-material/CheckCircleRounded";
import { type ClinicalContextResponse, type Consultation } from "../../api/clinicApi";
import { buildLatestTrustedLabProjection } from "./patientIntelligenceLatestLabs";
import { resolveTrustedLabStatus } from "./patientIntelligenceLabStatus.js";
import {
  buildConsultationHistoryEntries,
  getConsultationHistoryToggleLabel,
  getVisibleConsultationHistoryItems,
  shouldShowConsultationHistoryToggle,
} from "./patientIntelligenceConsultationHistory";
import {
  getSectionCountLabel,
  getSectionToggleLabel,
  getVisibleSectionItems,
  shouldShowSectionToggle,
} from "./patientIntelligenceSectionVisibility";

type LongitudinalConcept = ClinicalContextResponse["longitudinalMemory"]["knownConditions"][number];
type StructuredLabTrend = NonNullable<NonNullable<ClinicalContextResponse["longitudinalClinicalContext"]>["labTrends"]>[number];

function compactText(value: string | null | undefined, max = 110) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
}

function formatDisplayDate(value: string | null | undefined) {
  const normalized = (value || "").trim();
  if (!normalized) return null;
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return normalized;
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function splitCompactList(value: string | null | undefined) {
  return (value || "")
    .split(/[\n,•;|]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatObservedOn(value: string | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const formatter = new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", year: "numeric" });
  const parts = formatter.formatToParts(date).reduce<Record<string, string>>((acc, part) => {
    if (part.type !== "literal") acc[part.type] = part.value;
    return acc;
  }, {});
  return [parts.day, parts.month, parts.year].filter(Boolean).join("-");
}

function formatTrendValue(value: string | null | undefined, unit: string | null | undefined) {
  return [value, unit].filter(Boolean).join(value && unit ? " " : "");
}

function formatTrendVerificationLabel(value: string | null | undefined) {
  const normalized = (value || "").trim().replaceAll("_", " ").toLowerCase();
  if (!normalized) return null;
  return normalized.split(/\s+/).map((part) => `${part.charAt(0).toUpperCase()}${part.slice(1)}`).join(" ");
}

function normalizeConceptLabel(value: string | null | undefined) {
  return (value || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function LongitudinalConceptLine({
  label,
  concept,
  emptyLabel = "Not recorded",
  showStatusBadge = false,
  showMeta = false,
}: {
  label: string;
  concept: {
    label: string;
    valueText: string | null;
    valueUnit: string | null;
    sourceDocumentTitle: string | null;
    observedOn: string | null;
    confidence: number | null;
    verificationStatus: string | null;
  } | null | undefined;
  emptyLabel?: string;
  showStatusBadge?: boolean;
  showMeta?: boolean;
}) {
  const value = concept
    ? [concept.valueText, concept.valueUnit].filter(Boolean).join(concept.valueText && concept.valueUnit ? " " : "")
    : null;
  const isPendingReview = concept?.verificationStatus === "PENDING_REVIEW";
  const meta = concept && showMeta
    ? [
        concept.sourceDocumentTitle ? `Source ${concept.sourceDocumentTitle}` : null,
        concept.observedOn ? formatObservedOn(concept.observedOn) : null,
        concept.confidence != null ? `Confidence ${(concept.confidence * 100).toFixed(0)}%` : null,
      ].filter((part): part is string => Boolean(part)).join(" • ")
    : null;

  return (
    <Stack spacing={0.15}>
      <Stack direction="row" spacing={0.6} alignItems="center" useFlexGap flexWrap="wrap">
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        {showStatusBadge && isPendingReview ? (
          <Chip
            size="small"
            color="warning"
            variant="outlined"
            label="Pending Review"
            sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }}
          />
        ) : null}
      </Stack>
      <Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={value || emptyLabel}>
        {value || emptyLabel}
      </Typography>
      {meta ? (
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
          {meta}
        </Typography>
      ) : null}
    </Stack>
  );
}

function StructuredTrendLine({
  trend,
  onViewSourceDocument,
}: {
  trend: StructuredLabTrend;
  onViewSourceDocument?: (sourceDocumentId: string) => void;
}) {
  const sourceDocumentId = trend.sourceDocumentIds?.[0] || null;
  const sourceCount = trend.sourceDocumentIds?.length || 0;
  const directionLabel = (trend.direction || "").trim().replaceAll("_", " ");
  const verificationLabel = formatTrendVerificationLabel(trend.verificationStatus);

  return (
    <Box
      sx={{
        px: 0.75,
        py: 0.55,
        borderRadius: 1.25,
        border: 1,
        borderColor: "divider",
        bgcolor: "background.paper",
      }}
    >
      <Stack spacing={0.35}>
        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" useFlexGap flexWrap="wrap">
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 800 }}>
            {trend.analyteName || trend.analyteCode || "Trend"}
          </Typography>
          <Stack direction="row" spacing={0.4} useFlexGap flexWrap="wrap">
            {directionLabel ? <Chip size="small" variant="outlined" color="primary" label={directionLabel} sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} /> : null}
            {verificationLabel ? <Chip size="small" variant="outlined" color="warning" label={verificationLabel} sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} /> : null}
            {sourceDocumentId && onViewSourceDocument ? (
              <Chip
                size="small"
                variant="outlined"
                color="default"
                label={sourceCount > 1 ? `${sourceCount} sources` : "Source"}
                onClick={() => onViewSourceDocument(sourceDocumentId)}
                clickable
                sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }}
              />
            ) : null}
          </Stack>
        </Stack>
        <Typography variant="body2" sx={{ fontWeight: 900, lineHeight: 1.2 }}>
          {formatTrendValue(trend.olderValue, trend.olderUnit)}{" "}
          <Box component="span" sx={{ color: "text.secondary", fontWeight: 700 }}>
            →
          </Box>{" "}
          {formatTrendValue(trend.newerValue, trend.newerUnit)}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
          {formatObservedOn(trend.olderDate) || "-"} • {formatObservedOn(trend.newerDate) || "-"}
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
          {trend.direction || "Not recorded"}{trend.absoluteChange || trend.interval ? ` · ${[trend.absoluteChange, trend.interval || null].filter(Boolean).join(" ")}` : ""}
        </Typography>
        {trend.clinicalInterpretation ? (
          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.25 }}>
            {trend.clinicalInterpretation}
          </Typography>
        ) : null}
      </Stack>
    </Box>
  );
}

function confidenceBand(confidence: number | null | undefined) {
  if (confidence == null || Number.isNaN(confidence)) {
    return { label: "Unknown", percent: null, tone: "default" as const };
  }
  const percent = Math.max(0, Math.min(100, Math.round(confidence * 100)));
  if (confidence >= 0.9) return { label: "Very High", percent, tone: "success" as const };
  if (confidence >= 0.75) return { label: "High", percent, tone: "success" as const };
  if (confidence >= 0.6) return { label: "Medium", percent, tone: "warning" as const };
  return { label: "Low", percent, tone: "error" as const };
}

function formatConceptValue(concept: LongitudinalConcept | null | undefined) {
  if (!concept) return null;
  return [concept.valueText, concept.valueUnit].filter(Boolean).join(concept.valueText && concept.valueUnit ? " " : "");
}

function normalizeConditionLabel(label: string | null | undefined) {
  const normalized = normalizeConceptLabel(label);
  if (!normalized) return "";
  if (normalized.includes("diabetes")) return "Diabetes Mellitus";
  if (normalized.includes("dyslipidemia")) return "Dyslipidemia";
  return label?.trim() || "";
}

function normalizeLabLabel(label: string | null | undefined) {
  const normalized = normalizeConceptLabel(label);
  if (!normalized) return "";
  if (normalized.includes("hba1c")) return "HbA1c";
  if (normalized.includes("random blood sugar") || normalized.includes("blood sugar") || normalized.includes("glucose")) return "Blood Sugar";
  if (normalized.includes("total cholesterol")) return "Total Cholesterol";
  if (normalized === "ldl" || normalized.includes("ldl cholesterol")) return "LDL Cholesterol";
  if (normalized === "hdl" || normalized.includes("hdl cholesterol")) return "HDL Cholesterol";
  if (normalized.includes("triglycerides")) return "Triglycerides";
  return label?.trim() || "";
}

function normalizeRiskLabel(label: string | null | undefined) {
  const normalized = normalizeConceptLabel(label);
  if (!normalized) return "";
  if (normalized.includes("diabetes")) return "Diabetes";
  if (normalized.includes("dyslipidemia")) return "Dyslipidemia";
  return label?.trim() || "";
}

function normalizedVerificationStatus(value: string | null | undefined) {
  return (value || "").trim().toUpperCase();
}

function isReviewedLongitudinalConcept(concept: LongitudinalConcept | null | undefined) {
  const status = normalizedVerificationStatus(concept?.verificationStatus);
  return Boolean(status && status !== "PENDING_REVIEW" && status !== "REJECTED");
}

function isPendingLongitudinalConcept(concept: LongitudinalConcept | null | undefined) {
  return normalizedVerificationStatus(concept?.verificationStatus) === "PENDING_REVIEW";
}

function latestClinicalHighlightCandidate(concepts: LongitudinalConcept[]) {
  const available = concepts.filter((concept) => Boolean(concept?.sourceDocumentTitle || concept?.sourceDocumentId || concept?.observedOn || concept?.confidence != null));
  if (!available.length) {
    return null;
  }
  return available.reduce<LongitudinalConcept | null>((winner, candidate) => {
    if (!winner) {
      return candidate;
    }
    const winnerDate = winner.observedOn ? Date.parse(winner.observedOn) : Number.NaN;
    const candidateDate = candidate.observedOn ? Date.parse(candidate.observedOn) : Number.NaN;
    if (!Number.isNaN(candidateDate) && Number.isNaN(winnerDate)) {
      return candidate;
    }
    if (Number.isNaN(candidateDate) && !Number.isNaN(winnerDate)) {
      return winner;
    }
    if (!Number.isNaN(candidateDate) && !Number.isNaN(winnerDate) && candidateDate !== winnerDate) {
      return candidateDate > winnerDate ? candidate : winner;
    }
    return winner;
  }, null);
}

function selectLatestClinicalHighlightSource(trustedConcepts: LongitudinalConcept[], pendingConcepts: LongitudinalConcept[]) {
  return latestClinicalHighlightCandidate(trustedConcepts) || latestClinicalHighlightCandidate(pendingConcepts) || null;
}

function dedupeConcepts(items: LongitudinalConcept[], labelMapper: (label: string | null | undefined) => string) {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = normalizeConceptLabel(labelMapper(item.label));
    if (!key || seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function classifyLabStatus(concept: LongitudinalConcept) {
  return resolveTrustedLabStatus(concept);
}

function conceptKey(concept: LongitudinalConcept) {
  return [concept.conceptFamily, concept.conceptKey, concept.sourceDocumentId || "unknown"].join(":");
}

function SectionHeader({
  title,
  icon,
  action,
}: {
  title: string;
  icon: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="center" justifyContent="space-between">
      <Stack direction="row" spacing={0.75} alignItems="center" sx={{ minWidth: 0 }}>
        <Box sx={{ display: "grid", placeItems: "center", width: 22, height: 22, borderRadius: "50%", bgcolor: "primary.50", color: "primary.main" }}>
          {icon}
        </Box>
        <Typography variant="subtitle2" sx={{ fontWeight: 950, lineHeight: 1.2 }}>
          {title}
        </Typography>
      </Stack>
      {action ? <Box sx={{ flexShrink: 0 }}>{action}</Box> : null}
    </Stack>
  );
}

function SectionBox({
  title,
  icon,
  children,
  action,
}: {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <Box sx={{ p: 0.85, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={0.65}>
        <SectionHeader title={title} icon={icon} action={action} />
        {children}
      </Stack>
    </Box>
  );
}

function CompactRow({
  label,
  value,
}: {
  label: string;
  value: string | null | undefined;
}) {
  return (
    <Stack direction="row" spacing={0.75} alignItems="flex-start">
      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 90, lineHeight: 1.3 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 700, lineHeight: 1.3 }} noWrap title={value || ""}>
        {value || "Not recorded"}
      </Typography>
    </Stack>
  );
}

function CompactChipRow({
  items,
  color = "default",
  limit = 4,
  emptyLabel = "Not recorded",
}: {
  items: string[];
  color?: "default" | "primary" | "secondary" | "warning" | "success";
  limit?: number;
  emptyLabel?: string;
}) {
  const [expanded, setExpanded] = React.useState(false);
  const visible = expanded ? items : items.slice(0, limit);

  return (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
      {visible.length ? (
        visible.map((item) => <Chip key={item} size="small" color={color} variant="outlined" label={item} />)
      ) : (
        <Typography variant="caption" color="text.secondary">
          {emptyLabel}
        </Typography>
      )}
      {items.length > limit ? (
        <Button type="button" size="small" variant="text" onClick={() => setExpanded((current) => !current)}>
          {expanded ? "Show less" : "View more"}
        </Button>
      ) : null}
    </Stack>
  );
}

function TimelineLine({
  date,
  title,
  detail,
}: {
  date: string | null | undefined;
  title: string;
  detail?: string | null;
}) {
  return (
    <Stack spacing={0.2}>
      <Stack direction="row" spacing={0.5} alignItems="center">
        <Chip size="small" variant="outlined" label={date || "-"} sx={{ height: 20, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
        <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.15 }} noWrap>
          {title}
        </Typography>
      </Stack>
      {detail ? (
        <Typography variant="caption" color="text.secondary" sx={{ display: "block", lineHeight: 1.15 }} noWrap>
          {detail}
        </Typography>
      ) : null}
    </Stack>
  );
}

function ConsultationHistoryRow({
  entry,
}: {
  entry: {
    consultationId: string;
    consultationDate: string | null;
    status: string | null;
    doctor: string | null;
    primaryText: string | null;
    followUp: string | null;
    detail: string | null;
    isCurrent: boolean;
  };
}) {
  const statusLabel = (entry.status || "").trim();
  return (
    <Box
      sx={{
        px: 0.75,
        py: 0.55,
        borderRadius: 1.25,
        border: 1,
        borderColor: entry.isCurrent ? "primary.light" : "divider",
        bgcolor: entry.isCurrent ? "primary.50" : "background.paper",
      }}
    >
      <Stack spacing={0.25}>
        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" useFlexGap flexWrap="wrap">
          <Stack direction="row" spacing={0.4} alignItems="center" useFlexGap flexWrap="wrap">
            <Chip size="small" variant="outlined" label={formatDisplayDate(entry.consultationDate) || "Not recorded"} sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
            {entry.isCurrent ? <Chip size="small" color="primary" variant="filled" label="Current" sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} /> : null}
            {statusLabel ? <Chip size="small" variant="outlined" label={statusLabel} sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} /> : null}
          </Stack>
          {entry.doctor ? (
            <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
              Dr. {entry.doctor}
            </Typography>
          ) : null}
        </Stack>
        <Typography variant="body2" sx={{ fontWeight: 900, lineHeight: 1.2 }} noWrap title={entry.primaryText || "Consultation"}>
          {entry.primaryText || "Consultation"}
        </Typography>
        {entry.detail ? (
          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
            {entry.detail}
          </Typography>
        ) : null}
        {entry.followUp ? (
          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
            Follow-up {formatDisplayDate(entry.followUp) || entry.followUp}
          </Typography>
        ) : null}
      </Stack>
    </Box>
  );
}

function SummaryField({
  label,
  value,
}: {
  label: string;
  value: React.ReactNode;
}) {
  return (
    <Stack spacing={0.25}>
      <Typography variant="caption" color="text.secondary" sx={{ textTransform: "uppercase", letterSpacing: 0.4 }}>
        {label}
      </Typography>
      <Box>{value}</Box>
    </Stack>
  );
}

function StatusBadge({ label, tone }: { label: string; tone: "default" | "success" | "warning" | "error" }) {
  return <Chip size="small" label={label} color={tone === "default" ? "default" : tone} variant="filled" sx={{ fontWeight: 800 }} />;
}

function ClinicalCardSection({
  title,
  icon,
  action,
  id,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  action?: React.ReactNode;
  id?: string;
  children: React.ReactNode;
}) {
  return (
    <Box id={id} sx={{ p: 0.85, border: 1, borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={0.65}>
        <SectionHeader title={title} icon={icon} action={action} />
        {children}
      </Stack>
    </Box>
  );
}

function CompactTag({ label, color = "default" }: { label: string; color?: "default" | "primary" | "secondary" | "warning" | "success" }) {
  return <Chip size="small" variant="outlined" color={color} label={label} sx={{ fontWeight: 800, height: 24, "& .MuiChip-label": { px: 1 } }} />;
}

function LabBadge({ tone }: { tone: "default" | "success" | "warning" | "error" }) {
  const mappedColor = tone === "default" ? "default" : tone;
  return <Chip size="small" label={tone === "error" ? "High" : tone === "warning" ? "Low" : tone === "success" ? "Normal" : "Unknown"} color={mappedColor} variant="filled" sx={{ fontWeight: 800, height: 22, "& .MuiChip-label": { px: 0.9, fontSize: 11 } }} />;
}

function LabFindingRow({ concept, highlight }: { concept: LongitudinalConcept; highlight?: boolean }) {
  const value = formatConceptValue(concept);
  const label = normalizeLabLabel(concept.label) || concept.label;
  const status = classifyLabStatus(concept);
  const meta = [
    concept.sourceDocumentTitle ? `Source ${concept.sourceDocumentTitle}` : null,
    concept.observedOn ? `Observed ${formatObservedOn(concept.observedOn)}` : null,
  ].filter((part): part is string => Boolean(part)).join(" • ");
  return (
    <Box
      sx={{
        display: "grid",
        gridTemplateColumns: { xs: "minmax(0, 1fr)", sm: "minmax(0, 1.1fr) minmax(120px, 0.75fr) auto" },
        gap: 0.75,
        alignItems: { sm: "center" },
        px: 0.75,
        py: 0.5,
        borderRadius: 1.25,
        border: 1,
        borderColor: highlight ? "primary.light" : "divider",
        bgcolor: highlight ? "action.hover" : "background.paper",
        outline: highlight ? 2 : 0,
        outlineColor: highlight ? "primary.light" : "transparent",
        outlineStyle: "solid",
        transition: "background-color 180ms ease, outline-color 180ms ease",
      }}
    >
      <Typography variant="body2" sx={{ fontWeight: 900, lineHeight: 1.2 }} noWrap title={label}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.2, textAlign: { xs: "left", sm: "right" } }} noWrap title={value || "Not recorded"}>
        {value || "Not recorded"}
      </Typography>
      <Box sx={{ display: "flex", justifyContent: { xs: "flex-start", sm: "flex-end" } }}>
        <LabBadge tone={status.tone} />
      </Box>
      {meta ? (
        <Typography variant="caption" color="text.secondary" sx={{ gridColumn: { xs: "1", sm: "1 / span 3" }, lineHeight: 1.2 }}>
          {meta}
        </Typography>
      ) : null}
    </Box>
  );
}

function TrustedFindingRow({ concept }: { concept: LongitudinalConcept }) {
  const label = concept.conceptFamily === "LAB_RESULT"
    ? (normalizeLabLabel(concept.label) || concept.label || "Finding")
    : (concept.label?.trim() || "Finding");
  const value = formatConceptValue(concept);
  const meta = [
    concept.sourceDocumentTitle ? `Source ${concept.sourceDocumentTitle}` : null,
    concept.observedOn ? `Observed ${formatObservedOn(concept.observedOn)}` : null,
  ].filter((part): part is string => Boolean(part)).join(" • ");

  return (
    <Box
      sx={{
        px: 0.75,
        py: 0.55,
        borderRadius: 1.25,
        border: 1,
        borderColor: "success.light",
        bgcolor: "success.50",
      }}
    >
      <Stack spacing={0.25}>
        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between" useFlexGap flexWrap="wrap">
          <Typography variant="body2" sx={{ fontWeight: 900, lineHeight: 1.2 }} noWrap title={label}>
            {label}
          </Typography>
          <Chip size="small" color="success" variant="outlined" label="Verified" sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
        </Stack>
        <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap title={value || "Not recorded"}>
          {value || "Not recorded"}
        </Typography>
        {meta ? (
          <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
            {meta}
          </Typography>
        ) : null}
      </Stack>
    </Box>
  );
}

function highlightLabelMatches(conceptLabel: string, highlightLabel?: string | null) {
  if (!highlightLabel) return false;
  return normalizeLabLabel(highlightLabel) === conceptLabel;
}

function ConceptChipGroup({
  concepts,
  labelMapper,
  emptyLabel,
  color,
  onViewSource,
}: {
  concepts: LongitudinalConcept[];
  labelMapper: (label: string | null | undefined) => string;
  emptyLabel: string;
  color: "default" | "primary" | "secondary" | "warning" | "success";
  onViewSource?: (sourceDocumentId: string) => void;
}) {
  const visible = concepts.map((concept) => ({ concept, label: labelMapper(concept.label) || concept.label, sourceDocumentId: concept.sourceDocumentId })).filter((item) => item.label.trim());
  return visible.length ? (
    <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
      {visible.map((item) => (
        <Chip
          key={conceptKey(item.concept)}
          size="small"
          color={color}
          variant="outlined"
          label={item.label}
          onClick={item.sourceDocumentId && onViewSource ? () => onViewSource(item.sourceDocumentId!) : undefined}
          clickable={Boolean(item.sourceDocumentId && onViewSource)}
        />
      ))}
    </Stack>
  ) : (
    <Typography variant="caption" color="text.secondary">
      {emptyLabel}
    </Typography>
  );
}

function hasLongitudinalData(conditions: LongitudinalConcept[], labs: LongitudinalConcept[], riskFlags: LongitudinalConcept[]) {
  return conditions.length > 0 || labs.length > 0 || riskFlags.length > 0;
}

export function PatientIntelligenceCard({
  context,
  loading = false,
  error = null,
  onViewSourceDocument,
  highlightLabLabel = null,
  aiEnabled = true,
  currentConsultation = null,
  previousConsultations = null,
  patientSnapshotFallback = null,
  onReviewFindings,
}: {
  context: ClinicalContextResponse | null;
  loading?: boolean;
  error?: string | null;
  onViewSourceDocument?: (documentId: string) => void;
  highlightLabLabel?: string | null;
  aiEnabled?: boolean;
  currentConsultation?: Consultation | null;
  previousConsultations?: Consultation[] | null;
  patientSnapshotFallback?: {
    patientName?: string | null;
    ageYears?: number | null;
    gender?: string | null;
    bloodGroup?: string | null;
    allergies?: string | null;
    existingConditions?: string | null;
    longTermMedications?: string | null;
    lastConsultationDate?: string | null;
  } | null;
  onReviewFindings?: (documentId: string) => void;
}) {
  const [detailsOpen, setDetailsOpen] = React.useState(false);
  const [latestLabsExpanded, setLatestLabsExpanded] = React.useState(false);
  const [verifiedFindingsExpanded, setVerifiedFindingsExpanded] = React.useState(false);
  const [reportTrendsExpanded, setReportTrendsExpanded] = React.useState(false);
  const [consultationHistoryExpanded, setConsultationHistoryExpanded] = React.useState(false);
  const [drawerConsultationHistoryExpanded, setDrawerConsultationHistoryExpanded] = React.useState(false);
  const [drawerDocumentsExpanded, setDrawerDocumentsExpanded] = React.useState(false);

  const snapshot = context?.patientSummary;
  const intakeSummary = context?.intakeSummary;
  const timelineEvents = context?.timelineSummary.events || [];
  const longitudinalMemory = context?.longitudinalMemory;
  const trustedMemoryConcepts = longitudinalMemory?.history || [];
  const pendingMemoryConcepts = longitudinalMemory?.pendingReviewHistory || [];
  const verifiedHistoryConcepts = React.useMemo(
    () => trustedMemoryConcepts.filter((concept) => Boolean(concept?.conceptKey || concept?.label || concept?.valueText)),
    [trustedMemoryConcepts],
  );
  const allergies = splitCompactList(snapshot?.allergies);
  const chronicConditions = splitCompactList(snapshot?.chronicConditions);
  const currentMedications = snapshot?.currentMedications || [];
  const recentReports = context?.documentIntelligence.recentReports || [];
  const radiologyReports = context?.documentIntelligence.radiology || [];
  const referrals = context?.documentIntelligence.referrals || [];
  const dischargeSummaries = context?.documentIntelligence.dischargeSummaries || [];
  const structuredLabTrends = context?.longitudinalClinicalContext?.labTrends || [];

  const conditions = React.useMemo(
    () =>
      dedupeConcepts(longitudinalMemory?.knownConditions || [], normalizeConditionLabel).map((concept) => ({
        ...concept,
        label: normalizeConditionLabel(concept.label),
      })),
    [longitudinalMemory?.knownConditions],
  );
  const medications = React.useMemo(
    () => dedupeConcepts(longitudinalMemory?.longTermMedications || [], (label) => label?.trim() || ""),
    [longitudinalMemory?.longTermMedications],
  );
  const labItems = React.useMemo(
    () =>
      buildLatestTrustedLabProjection(longitudinalMemory?.history || [])
        .map((concept) => ({
          ...concept,
          label: normalizeLabLabel(concept.label),
        })),
    [longitudinalMemory?.history],
  );
  const riskFlags = React.useMemo(
    () =>
      dedupeConcepts(longitudinalMemory?.riskFlags || [], normalizeRiskLabel).map((concept) => ({
        ...concept,
        label: normalizeRiskLabel(concept.label),
      })),
    [longitudinalMemory?.riskFlags],
  );
  const consultationHistoryEntries = React.useMemo(
    () => buildConsultationHistoryEntries(previousConsultations || context?.previousVisits || [], currentConsultation || null, context?.consultationId || null),
    [context?.consultationId, context?.previousVisits, previousConsultations, currentConsultation],
  );
  const visibleLatestLabs = React.useMemo(
    () => getVisibleSectionItems(labItems, latestLabsExpanded, 6),
    [labItems, latestLabsExpanded],
  );
  const visibleVerifiedFindings = React.useMemo(
    () => getVisibleSectionItems(verifiedHistoryConcepts, verifiedFindingsExpanded, 5),
    [verifiedHistoryConcepts, verifiedFindingsExpanded],
  );
  const visibleReportTrends = React.useMemo(
    () => getVisibleSectionItems(structuredLabTrends, reportTrendsExpanded, 3),
    [reportTrendsExpanded, structuredLabTrends],
  );
  const visibleConsultationHistory = React.useMemo(
    () => getVisibleConsultationHistoryItems(consultationHistoryEntries, consultationHistoryExpanded, 5),
    [consultationHistoryEntries, consultationHistoryExpanded],
  );
  const drawerConsultationHistory = React.useMemo(
    () => getVisibleConsultationHistoryItems(consultationHistoryEntries, drawerConsultationHistoryExpanded, 3),
    [consultationHistoryEntries, drawerConsultationHistoryExpanded],
  );
  const drawerDocumentItems = React.useMemo(
    () => [
      ...recentReports.map((title) => ({ title, category: "Recent report" })),
      ...radiologyReports.map((title) => ({ title, category: "Radiology" })),
      ...referrals.map((title) => ({ title, category: "Referral" })),
      ...dischargeSummaries.map((title) => ({ title, category: "Discharge" })),
    ].filter((item) => item.title.trim()),
    [dischargeSummaries, radiologyReports, recentReports, referrals],
  );
  const drawerVisibleDocumentItems = React.useMemo(
    () => (drawerDocumentsExpanded ? drawerDocumentItems : drawerDocumentItems.slice(0, 3)),
    [drawerDocumentItems, drawerDocumentsExpanded],
  );

  const sourceConcept = React.useMemo(
    () => selectLatestClinicalHighlightSource(trustedMemoryConcepts, pendingMemoryConcepts),
    [pendingMemoryConcepts, trustedMemoryConcepts],
  );

  const sourceConceptStatus = normalizedVerificationStatus(sourceConcept?.verificationStatus);
  const sourceConceptLabel = sourceConcept
    ? (sourceConceptStatus === "PENDING_REVIEW" ? "Pending Review" : "Verified")
    : "Not recorded";
  const sourceConceptTone: "default" | "success" | "warning" = sourceConcept
    ? (sourceConceptStatus === "PENDING_REVIEW" ? "warning" : "success")
    : "default";
  const sourceConceptIsPending = sourceConceptStatus === "PENDING_REVIEW";

  const intakeLabel = intakeSummary?.complete ? "Complete" : intakeSummary ? "Pending" : "Not recorded";
  const confidence = confidenceBand(sourceConcept?.confidence);
  const sourceDocumentTitle = sourceConcept?.sourceDocumentTitle || null;
  const sourceDocumentId = sourceConcept?.sourceDocumentId || null;
  const sourceDocumentAction = sourceDocumentId && onViewSourceDocument
    ? () => onViewSourceDocument(sourceDocumentId)
    : undefined;
  const pendingSourceDocumentId = React.useMemo(() => {
    const pending = latestClinicalHighlightCandidate(pendingMemoryConcepts);
    return pending?.sourceDocumentId || null;
  }, [pendingMemoryConcepts]);
  const pendingFindingCount = React.useMemo(
    () => pendingMemoryConcepts.length,
    [pendingMemoryConcepts],
  );
  const pendingSources = React.useMemo(() => {
    const all = pendingMemoryConcepts
      .filter((concept) => concept.sourceDocumentId)
      .map((concept) => ({ id: concept.sourceDocumentId as string, title: concept.sourceDocumentTitle || concept.sourceDocumentId as string }));
    const counts = new Map<string, { id: string; title: string; count: number }>();
    all.forEach((source) => {
      const current = counts.get(source.id);
      counts.set(source.id, current ? { ...current, count: current.count + 1 } : { ...source, count: 1 });
    });
    return Array.from(counts.values());
  }, [pendingMemoryConcepts]);
  const pendingReportCount = pendingSources.length;
  const latestLabCount = labItems.length;
  const verifiedFindingCount = verifiedHistoryConcepts.length;
  const reportTrendCount = structuredLabTrends.length;
  const consultationHistoryCount = consultationHistoryEntries.length;
  const latestReportCount = recentReports.length;
  const pendingInvestigationCount = context?.labIntelligence.pendingInvestigations.length || 0;
  const abnormalVerifiedResultCount = context?.labIntelligence.abnormalValues.length || 0;
  const hasData = React.useMemo(
    () => hasLongitudinalData(conditions, labItems, riskFlags) || verifiedHistoryConcepts.length > 0,
    [conditions, labItems, riskFlags, verifiedHistoryConcepts.length],
  );
  const emptyLongitudinalMessage = aiEnabled
    ? "No longitudinal findings yet. Upload a report or complete intake to build patient intelligence."
    : "Patient intelligence becomes available after clinical documents or investigation results are added. AI-assisted summaries are currently unavailable.";
  const fallbackAgeYears = patientSnapshotFallback?.ageYears ?? null;
  const fallbackGender = patientSnapshotFallback?.gender || null;
  const snapshotAgeGender = snapshot?.ageYears != null || snapshot?.gender || fallbackAgeYears != null || fallbackGender
    ? `${snapshot?.ageYears ?? fallbackAgeYears ?? ""}${(snapshot?.ageYears ?? fallbackAgeYears) != null ? "y" : ""}${(snapshot?.ageYears ?? fallbackAgeYears) != null && (snapshot?.gender || fallbackGender) ? " • " : ""}${snapshot?.gender || fallbackGender || ""}`
    : null;
  const snapshotPatientName = snapshot?.patientName || patientSnapshotFallback?.patientName || null;
  const snapshotAllergies = splitCompactList(snapshot?.allergies || patientSnapshotFallback?.allergies);
  const snapshotChronicConditions = splitCompactList(snapshot?.chronicConditions || patientSnapshotFallback?.existingConditions);
  const snapshotCurrentMedications = snapshot?.currentMedications?.length
    ? snapshot.currentMedications
    : splitCompactList(patientSnapshotFallback?.longTermMedications);
  const snapshotLastVisitDate = formatDisplayDate(snapshot?.lastConsultationDate || patientSnapshotFallback?.lastConsultationDate || null);
  const snapshotBloodGroup = patientSnapshotFallback?.bloodGroup || null;
  const scrollToMainSection = React.useCallback((sectionId: string, expand?: () => void) => {
    expand?.();
    setDetailsOpen(false);
    window.setTimeout(() => {
      document.getElementById(sectionId)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 0);
  }, []);

  const highlightSections = (
    <>
      {aiEnabled ? (
        <>
          <ClinicalCardSection
            title="AI Extracted Summary"
            icon={<AutoAwesomeRoundedIcon fontSize="inherit" />}
            action={sourceDocumentAction ? (
              <Button type="button" size="small" variant="text" onClick={sourceDocumentAction}>
                View Source
              </Button>
            ) : null}
          >
            <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 0.85 }}>
              <SummaryField label="Source" value={<Typography variant="body2" sx={{ fontWeight: 800 }} noWrap title={sourceDocumentTitle || "Not recorded"}>{sourceDocumentTitle || "Not recorded"}</Typography>} />
              <SummaryField
                label="AI Status"
                value={<StatusBadge label={sourceConceptLabel} tone={sourceConceptTone} />}
              />
              <SummaryField
                label="Observation Date"
                value={<Typography variant="body2" sx={{ fontWeight: 800 }}>{formatObservedOn(sourceConcept?.observedOn) || "Not recorded"}</Typography>}
              />
              <SummaryField
                label="Confidence"
                value={
                  <Stack direction="row" spacing={0.5} alignItems="baseline" useFlexGap flexWrap="wrap">
                    <Typography variant="body2" sx={{ fontWeight: 900 }}>
                      {confidence.label}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {confidence.percent != null ? `${confidence.percent}%` : "Not recorded"}
                    </Typography>
                  </Stack>
                }
              />
            </Box>
          </ClinicalCardSection>

          {sourceConceptIsPending ? (
            <Alert severity="info" sx={{ py: 0.45, "& .MuiAlert-message": { py: 0.1 } }}>
              <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
                AI extracted information
              </Typography>
              <Typography variant="caption" color="text.secondary">
                Doctor verification required before becoming permanent patient history.
              </Typography>
            </Alert>
          ) : null}
        </>
      ) : null}

      <ClinicalCardSection
        title="Conditions"
        icon={<HealingRoundedIcon fontSize="inherit" />}
      >
        <ConceptChipGroup
          concepts={conditions}
          labelMapper={normalizeConditionLabel}
          emptyLabel="Not recorded"
          color="primary"
          onViewSource={onViewSourceDocument}
        />
      </ClinicalCardSection>

      <ClinicalCardSection
        title={getSectionCountLabel("Latest Labs", latestLabCount)}
        icon={<ScienceRoundedIcon fontSize="inherit" />}
        id="patient-intelligence-latest-labs"
        action={shouldShowSectionToggle(latestLabCount, 6) ? (
          <Button type="button" size="small" variant="text" onClick={() => setLatestLabsExpanded((current) => !current)}>
            {getSectionToggleLabel(latestLabsExpanded)}
          </Button>
        ) : null}
      >
        <Stack spacing={0.45}>
          {visibleLatestLabs.length ? (
            visibleLatestLabs.map((concept) => {
              const conceptLabel = normalizeLabLabel(concept.label) || concept.label;
              return <LabFindingRow key={conceptKey(concept)} concept={concept} highlight={highlightLabelMatches(conceptLabel, highlightLabLabel)} />;
            })
          ) : (
            <Typography variant="caption" color="text.secondary">
              Not recorded
            </Typography>
          )}
        </Stack>
      </ClinicalCardSection>

      <ClinicalCardSection
        title="Risk Flags"
        icon={<TimelineRoundedIcon fontSize="inherit" />}
      >
        <ConceptChipGroup
          concepts={riskFlags}
          labelMapper={normalizeRiskLabel}
          emptyLabel="Not recorded"
          color="warning"
          onViewSource={onViewSourceDocument}
        />
      </ClinicalCardSection>

      {verifiedHistoryConcepts.length ? (
        <ClinicalCardSection
          title={getSectionCountLabel("Verified Findings", verifiedFindingCount)}
          icon={<CheckCircleRoundedIcon fontSize="inherit" />}
          id="patient-intelligence-verified-findings"
          action={shouldShowSectionToggle(verifiedFindingCount, 5) ? (
            <Button type="button" size="small" variant="text" onClick={() => setVerifiedFindingsExpanded((current) => !current)}>
              {getSectionToggleLabel(verifiedFindingsExpanded)}
            </Button>
          ) : null}
        >
          <Stack spacing={0.5}>
            {visibleVerifiedFindings.map((concept) => (
              <TrustedFindingRow key={conceptKey(concept)} concept={concept} />
            ))}
          </Stack>
        </ClinicalCardSection>
      ) : null}

      {visibleReportTrends.length ? (
        <ClinicalCardSection
          title={getSectionCountLabel("Report Trends", reportTrendCount)}
          icon={<TimelineRoundedIcon fontSize="inherit" />}
          id="patient-intelligence-report-trends"
          action={shouldShowSectionToggle(reportTrendCount, 3) ? (
            <Button type="button" size="small" variant="text" onClick={() => setReportTrendsExpanded((current) => !current)}>
              {getSectionToggleLabel(reportTrendsExpanded)}
            </Button>
          ) : null}
        >
          <Stack spacing={0.45}>
            {visibleReportTrends.map((trend) => (
              <StructuredTrendLine
                key={`${trend.analyteCode || trend.analyteName || "trend"}-${trend.olderDate || "older"}-${trend.newerDate || "newer"}-${trend.olderValue || "old"}-${trend.newerValue || "new"}`}
                trend={trend}
                onViewSourceDocument={onViewSourceDocument}
              />
            ))}
          </Stack>
        </ClinicalCardSection>
      ) : null}
    </>
  );
  return (
    <>
      <Card variant="outlined" sx={{ boxShadow: "none", overflow: "visible", height: "auto" }}>
        <CardContent sx={{ p: 0.85, "&:last-child": { pb: 0.85 } }}>
          <Stack spacing={0.75}>
            <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={0.75} alignItems="center" useFlexGap flexWrap="wrap">
                  <AutoAwesomeRoundedIcon fontSize="small" color="primary" />
                  <Typography variant="subtitle1" sx={{ fontWeight: 950 }}>
                    Patient Intelligence
                  </Typography>
                  <Chip size="small" variant="outlined" label={loading ? "Loading" : `${timelineEvents.length} events`} />
                </Stack>
                <Typography variant="caption" color="text.secondary">
                  Longitudinal patient context. Not AI generated.
                </Typography>
              </Box>
              <Button type="button" size="small" variant="outlined" onClick={() => setDetailsOpen(true)}>
                Review
              </Button>
            </Stack>

            {error ? <Alert severity="warning" sx={{ py: 0.45 }}>{error}</Alert> : null}

            {pendingFindingCount > 0 && pendingSourceDocumentId && onReviewFindings ? (
              <Card variant="outlined" sx={{ boxShadow: "none", borderColor: "warning.light", bgcolor: "warning.50" }}>
                <CardContent sx={{ p: 0.8, "&:last-child": { pb: 0.8 } }}>
                  <Stack direction={{ xs: "column", sm: "row" }} spacing={0.8} alignItems={{ xs: "stretch", sm: "center" }} justifyContent="space-between">
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 900 }}>AI-extracted findings</Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        {pendingFindingCount} findings pending verification
                      </Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        Across {pendingReportCount} reports
                      </Typography>
                      <Typography variant="caption" color="text.secondary" display="block">
                        Doctor verification required before becoming permanent patient history.
                      </Typography>
                    </Box>
                    <Button size="small" variant="contained" color="warning" onClick={() => onReviewFindings(pendingSourceDocumentId)}>
                      Review
                    </Button>
                  </Stack>
                </CardContent>
              </Card>
            ) : null}

            <SectionBox title="Clinical Highlights" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.55}>
                {hasData ? highlightSections : (
                  <Alert severity="info" sx={{ py: 0.45 }}>
                    {emptyLongitudinalMessage}
                  </Alert>
                )}
              </Stack>
            </SectionBox>

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 0.7 }}>
                <CompactRow label="Patient" value={snapshotPatientName} />
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <CompactRow label="Blood group" value={snapshotBloodGroup} />
                <Stack spacing={0.2}>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                    Existing Conditions
                  </Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                    {conditions.length ? (
                      conditions.slice(0, 3).map((concept) => <CompactTag key={conceptKey(concept)} label={normalizeConditionLabel(concept.label)} color="primary" />)
                    ) : snapshotChronicConditions.length ? (
                      snapshotChronicConditions.slice(0, 3).map((label) => <CompactTag key={label} label={label} color="primary" />)
                    ) : (
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>Not recorded</Typography>
                    )}
                  </Stack>
                </Stack>
                <CompactRow label="Allergies" value={compactText(allergies.slice(0, 3).join(", "), 72) || compactText(snapshotAllergies.slice(0, 3).join(", "), 72)} />
                <CompactRow
                  label="Long-term Medications"
                  value={compactText(medications.map((item) => item.label).join(", "), 72) || "Not recorded"}
                />
                <CompactRow label="Last visit" value={snapshotLastVisitDate} />
                <CompactRow label="Intake status" value={intakeLabel} />
              </Box>
            </SectionBox>
          </Stack>
        </CardContent>
      </Card>

      <Drawer anchor="right" open={detailsOpen} onClose={() => setDetailsOpen(false)}>
        <Box sx={{ width: { xs: "88vw", sm: 460, md: 520 }, p: 2, maxWidth: "100vw" }}>
          <Stack spacing={1.2}>
            <Stack direction="row" spacing={1} alignItems="flex-start" justifyContent="space-between">
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="h6" sx={{ fontWeight: 950 }}>
                  Patient Context
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Quick patient context and operational details.
                </Typography>
              </Box>
              <IconButton aria-label="Close patient intelligence drawer" onClick={() => setDetailsOpen(false)} size="small">
                <CloseRoundedIcon fontSize="small" />
              </IconButton>
            </Stack>

            {error ? <Alert severity="warning">{error}</Alert> : null}

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 0.7 }}>
                <CompactRow label="Patient" value={snapshotPatientName} />
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <CompactRow label="Blood group" value={snapshotBloodGroup} />
                <Stack spacing={0.2}>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                    Existing Conditions
                  </Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                    {conditions.length ? (
                      conditions.slice(0, 3).map((concept) => <CompactTag key={conceptKey(concept)} label={normalizeConditionLabel(concept.label)} color="primary" />)
                    ) : snapshotChronicConditions.length ? (
                      snapshotChronicConditions.slice(0, 3).map((label) => <CompactTag key={label} label={label} color="primary" />)
                    ) : (
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>Not recorded</Typography>
                    )}
                  </Stack>
                </Stack>
                <CompactRow label="Allergies" value={allergies.join(", ") || snapshotAllergies.join(", ") || null} />
                <CompactRow label="Long-term Medications" value={currentMedications.join(", ") || compactText(medications.map((item) => item.label).join(", "), 72) || snapshotCurrentMedications.join(", ") || null} />
                <CompactRow label="Last visit" value={snapshotLastVisitDate} />
              </Box>
            </SectionBox>

            <SectionBox title="Intake status" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.4}>
                <CompactRow label="Status" value={intakeLabel} />
                <CompactRow label="Recorded by" value={intakeSummary?.recordedByName || null} />
                <CompactRow label="Recorded at" value={intakeSummary?.recordedAt ? new Date(intakeSummary.recordedAt).toLocaleString() : null} />
                {intakeSummary?.latestVitals ? (
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Latest vitals
                    </Typography>
                    <CompactChipRow
                      items={[
                        intakeSummary.latestVitals.bloodPressureSystolic != null && intakeSummary.latestVitals.bloodPressureDiastolic != null ? `BP ${intakeSummary.latestVitals.bloodPressureSystolic}/${intakeSummary.latestVitals.bloodPressureDiastolic}` : null,
                        intakeSummary.latestVitals.pulseRate != null ? `Pulse ${intakeSummary.latestVitals.pulseRate}` : null,
                        intakeSummary.latestVitals.spo2 != null ? `SpO2 ${intakeSummary.latestVitals.spo2}%` : null,
                        intakeSummary.latestVitals.temperature != null ? `Temp ${intakeSummary.latestVitals.temperature}${intakeSummary.latestVitals.temperatureUnit === "FAHRENHEIT" ? "F" : "C"}` : null,
                        intakeSummary.vitalsTrendSummary ? compactText(intakeSummary.vitalsTrendSummary, 48) : null,
                      ].filter((item): item is string => Boolean(item))}
                      color="primary"
                      limit={4}
                      emptyLabel="No latest vitals yet"
                    />
                  </Stack>
                ) : null}
                {intakeSummary?.abnormalVitalsAlerts?.length ? (
                  <CompactChipRow items={intakeSummary.abnormalVitalsAlerts.map((item) => compactText(item, 42))} color="warning" limit={3} emptyLabel="No abnormal vitals" />
                ) : null}
              </Stack>
            </SectionBox>

            <SectionBox
              title="Recent Consultations"
              icon={<CheckCircleRoundedIcon fontSize="inherit" />}
              action={shouldShowConsultationHistoryToggle(consultationHistoryCount, 3) ? (
                <Button type="button" size="small" variant="text" onClick={() => setConsultationHistoryExpanded((current) => !current)}>
                  {getConsultationHistoryToggleLabel(consultationHistoryExpanded)}
                </Button>
              ) : null}
            >
              <Stack spacing={0.35}>
                {drawerConsultationHistory.length ? (
                  <Stack spacing={0.45}>
                    {drawerConsultationHistory.map((entry) => (
                      <ConsultationHistoryRow
                        key={`${entry.consultationId}-${entry.consultationDate || "unknown"}-${entry.isCurrent ? "current" : "history"}`}
                        entry={entry}
                      />
                    ))}
                  </Stack>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    No previous consultations available.
                  </Typography>
                )}
              </Stack>
            </SectionBox>

            <Divider />

            <SectionBox title="Uploaded documents" icon={<DescriptionRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                {drawerVisibleDocumentItems.length ? (
                  <>
                    {drawerVisibleDocumentItems.map((item) => (
                      <Box key={`${item.category}-${item.title}`} sx={{ px: 0.75, py: 0.55, borderRadius: 1.25, border: 1, borderColor: "divider", bgcolor: "background.paper" }}>
                        <Stack direction="row" spacing={0.6} alignItems="center" useFlexGap flexWrap="wrap">
                          <Chip size="small" variant="outlined" label={item.category} sx={{ height: 18, "& .MuiChip-label": { px: 0.6, fontSize: 10.5 } }} />
                          <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.2 }} noWrap title={item.title}>
                            {item.title}
                          </Typography>
                        </Stack>
                      </Box>
                    ))}
                    {drawerDocumentItems.length > 3 ? (
                      <Button type="button" size="small" variant="text" onClick={() => setDrawerDocumentsExpanded((current) => !current)}>
                        {drawerDocumentsExpanded ? "Show less" : "View more"}
                      </Button>
                    ) : null}
                  </>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    No uploaded documents.
                  </Typography>
                )}
              </Stack>
            </SectionBox>

            <SectionBox title="Lab Intelligence" icon={<ScienceRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                <CompactRow label="Pending results" value={String(pendingInvestigationCount)} />
                <CompactRow label="Abnormal verified results" value={String(abnormalVerifiedResultCount)} />
                {context?.labIntelligence.latestLabReport ? (
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.25 }}>
                    Latest report: {compactText(context.labIntelligence.latestLabReport, 72)}
                  </Typography>
                ) : null}
                <Button type="button" size="small" variant="text" onClick={() => scrollToMainSection("patient-intelligence-latest-labs", () => setLatestLabsExpanded(true))}>
                  View lab details
                </Button>
              </Stack>
            </SectionBox>
          </Stack>
        </Box>
      </Drawer>
    </>
  );
}
