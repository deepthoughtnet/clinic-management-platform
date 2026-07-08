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
import { type ClinicalContextResponse } from "../../api/clinicApi";

type LongitudinalConcept = ClinicalContextResponse["longitudinalMemory"]["knownConditions"][number];

function compactText(value: string | null | undefined, max = 110) {
  const normalized = (value || "").trim();
  if (!normalized) return "";
  return normalized.length > max ? `${normalized.slice(0, max - 1)}…` : normalized;
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
  const label = normalizeLabLabel(concept.label);
  const valueText = (concept.valueText || "").trim().toLowerCase();
  const explicitHigh = valueText.includes("high");
  const explicitLow = valueText.includes("low");
  const explicitNormal = valueText.includes("normal");
  const numericValue = Number.parseFloat((concept.valueText || "").replace(/[^0-9.]+/g, ""));

  if (explicitHigh) return { label: "HIGH", tone: "error" as const };
  if (explicitLow) return { label: "LOW", tone: "warning" as const };
  if (explicitNormal) return { label: "NORMAL", tone: "success" as const };

  if (!Number.isNaN(numericValue)) {
    if (label === "HDL Cholesterol") {
      return numericValue < 40 ? { label: "LOW", tone: "warning" as const } : { label: "NORMAL", tone: "success" as const };
    }
    if (label === "HbA1c") {
      return numericValue >= 5.7 ? { label: "HIGH", tone: "error" as const } : { label: "NORMAL", tone: "success" as const };
    }
    if (label === "Blood Sugar") {
      return numericValue >= 140 ? { label: "HIGH", tone: "error" as const } : { label: "NORMAL", tone: "success" as const };
    }
    if (label === "Total Cholesterol") {
      return numericValue >= 200 ? { label: "HIGH", tone: "error" as const } : { label: "NORMAL", tone: "success" as const };
    }
    if (label === "LDL Cholesterol") {
      return numericValue >= 130 ? { label: "HIGH", tone: "error" as const } : { label: "NORMAL", tone: "success" as const };
    }
    if (label === "Triglycerides") {
      return numericValue >= 150 ? { label: "HIGH", tone: "error" as const } : { label: "NORMAL", tone: "success" as const };
    }
  }

  return { label: "UNKNOWN", tone: "default" as const };
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
  children,
}: {
  title: string;
  icon: React.ReactNode;
  action?: React.ReactNode;
  children: React.ReactNode;
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

function CompactTag({ label, color = "default" }: { label: string; color?: "default" | "primary" | "secondary" | "warning" | "success" }) {
  return <Chip size="small" variant="outlined" color={color} label={label} sx={{ fontWeight: 800, height: 24, "& .MuiChip-label": { px: 1 } }} />;
}

function LabBadge({ tone }: { tone: "default" | "success" | "warning" | "error" }) {
  const mappedColor = tone === "default" ? "default" : tone;
  return <Chip size="small" label={tone === "error" ? "High" : tone === "warning" ? "Low" : tone === "success" ? "Normal" : "Unknown"} color={mappedColor} variant="filled" sx={{ fontWeight: 800, height: 22, "& .MuiChip-label": { px: 0.9, fontSize: 11 } }} />;
}

function LabFindingRow({ concept }: { concept: LongitudinalConcept }) {
  const value = formatConceptValue(concept);
  const label = normalizeLabLabel(concept.label) || concept.label;
  const status = classifyLabStatus(concept);
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
        borderColor: "divider",
        bgcolor: "background.paper",
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
    </Box>
  );
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
}: {
  context: ClinicalContextResponse | null;
  loading?: boolean;
  error?: string | null;
  onViewSourceDocument?: (documentId: string) => void;
}) {
  const [detailsOpen, setDetailsOpen] = React.useState(false);

  const snapshot = context?.patientSummary;
  const intakeSummary = context?.intakeSummary;
  const timelineEvents = context?.timelineSummary.events || [];
  const longitudinalMemory = context?.longitudinalMemory;
  const allergies = splitCompactList(snapshot?.allergies);
  const chronicConditions = splitCompactList(snapshot?.chronicConditions);
  const currentMedications = snapshot?.currentMedications || [];
  const recentReports = context?.documentIntelligence.recentReports || [];
  const radiologyReports = context?.documentIntelligence.radiology || [];
  const referrals = context?.documentIntelligence.referrals || [];
  const dischargeSummaries = context?.documentIntelligence.dischargeSummaries || [];

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
      [longitudinalMemory?.latestHbA1c || null, longitudinalMemory?.latestBloodSugar || null, ...(longitudinalMemory?.latestLipidSummary || [])]
        .filter((concept): concept is LongitudinalConcept => Boolean(concept))
        .map((concept) => ({
          ...concept,
          label: normalizeLabLabel(concept.label),
        })),
    [longitudinalMemory?.latestBloodSugar, longitudinalMemory?.latestHbA1c, longitudinalMemory?.latestLipidSummary],
  );
  const riskFlags = React.useMemo(
    () =>
      dedupeConcepts(longitudinalMemory?.riskFlags || [], normalizeRiskLabel).map((concept) => ({
        ...concept,
        label: normalizeRiskLabel(concept.label),
      })),
    [longitudinalMemory?.riskFlags],
  );

  const sourceConcept = React.useMemo(() => {
    const candidates = [
      longitudinalMemory?.latestHbA1c,
      longitudinalMemory?.latestBloodSugar,
      ...(longitudinalMemory?.latestLipidSummary || []),
      ...(conditions as LongitudinalConcept[]),
      ...(riskFlags as LongitudinalConcept[]),
    ].filter((concept): concept is LongitudinalConcept => Boolean(concept));
    return candidates.find((concept) => Boolean(concept.sourceDocumentTitle || concept.sourceDocumentId || concept.observedOn || concept.confidence != null)) || null;
  }, [conditions, longitudinalMemory?.latestBloodSugar, longitudinalMemory?.latestHbA1c, longitudinalMemory?.latestLipidSummary, riskFlags]);

  const hasPendingReview = React.useMemo(
    () =>
      [...conditions, ...labItems, ...riskFlags].some((concept) => concept.verificationStatus === "PENDING_REVIEW"),
    [conditions, labItems, riskFlags],
  );

  const intakeLabel = intakeSummary?.complete ? "Complete" : intakeSummary ? "Pending" : "Not recorded";
  const snapshotAgeGender = snapshot?.ageYears != null || snapshot?.gender
    ? `${snapshot?.ageYears != null ? `${snapshot.ageYears}y` : ""}${snapshot?.ageYears != null && snapshot?.gender ? " • " : ""}${snapshot?.gender || ""}`
    : null;
  const confidence = confidenceBand(sourceConcept?.confidence);
  const sourceDocumentTitle = sourceConcept?.sourceDocumentTitle || recentReports[0] || null;
  const sourceDocumentId = sourceConcept?.sourceDocumentId || null;
  const sourceDocumentAction = sourceDocumentId && onViewSourceDocument
    ? () => onViewSourceDocument(sourceDocumentId)
    : undefined;
  const hasData = React.useMemo(() => hasLongitudinalData(conditions, labItems, riskFlags), [conditions, labItems, riskFlags]);

  const highlightSections = (
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
            value={<StatusBadge label={hasPendingReview ? "Pending Review" : "Verified"} tone={hasPendingReview ? "warning" : "success"} />}
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

      {hasPendingReview ? (
        <Alert severity="info" sx={{ py: 0.45, "& .MuiAlert-message": { py: 0.1 } }}>
          <Typography variant="body2" sx={{ fontWeight: 800, lineHeight: 1.35 }}>
            AI extracted information
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Doctor verification required before becoming permanent patient history.
          </Typography>
        </Alert>
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
        title="Latest Labs"
        icon={<ScienceRoundedIcon fontSize="inherit" />}
      >
        <Stack spacing={0.45}>
          {labItems.length ? (
            labItems.map((concept) => <LabFindingRow key={conceptKey(concept)} concept={concept} />)
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

            <SectionBox title="Clinical Highlights" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.55}>
                {hasData ? highlightSections : (
                  <Alert severity="info" sx={{ py: 0.45 }}>
                    No longitudinal findings yet. Upload a report or complete intake to build patient intelligence.
                  </Alert>
                )}
              </Stack>
            </SectionBox>

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 0.7 }}>
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <Stack spacing={0.2}>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                    Existing Conditions
                  </Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                    {conditions.length ? (
                      conditions.slice(0, 3).map((concept) => <CompactTag key={conceptKey(concept)} label={normalizeConditionLabel(concept.label)} color="primary" />)
                    ) : (
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>Not recorded</Typography>
                    )}
                  </Stack>
                </Stack>
                <CompactRow label="Allergies" value={compactText(allergies.slice(0, 3).join(", "), 72)} />
                <CompactRow label="Long-term Medications" value={compactText(currentMedications.slice(0, 3).join(", "), 72) || compactText(medications.map((item) => item.label).join(", "), 72)} />
                <CompactRow label="Last visit" value={snapshot?.lastConsultationDate || null} />
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
                  Patient Intelligence
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  Expanded longitudinal context and supporting details.
                </Typography>
              </Box>
              <IconButton aria-label="Close patient intelligence drawer" onClick={() => setDetailsOpen(false)} size="small">
                <CloseRoundedIcon fontSize="small" />
              </IconButton>
            </Stack>

            {error ? <Alert severity="warning">{error}</Alert> : null}

            <SectionBox title="Clinical Highlights" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.55}>
                {hasData ? highlightSections : (
                  <Alert severity="info" sx={{ py: 0.45 }}>
                    No longitudinal findings yet. Upload a report or complete intake to build patient intelligence.
                  </Alert>
                )}
              </Stack>
            </SectionBox>

            <SectionBox title="Patient Snapshot" icon={<HealingRoundedIcon fontSize="inherit" />}>
              <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", sm: "repeat(2, minmax(0, 1fr))" }, gap: 0.7 }}>
                <CompactRow label="Patient" value={snapshot?.patientName || null} />
                <CompactRow label="Age / Gender" value={snapshotAgeGender} />
                <Stack spacing={0.2}>
                  <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.3 }}>
                    Existing Conditions
                  </Typography>
                  <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap" alignItems="center">
                    {conditions.length ? (
                      conditions.slice(0, 3).map((concept) => <CompactTag key={conceptKey(concept)} label={normalizeConditionLabel(concept.label)} color="primary" />)
                    ) : (
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>Not recorded</Typography>
                    )}
                  </Stack>
                </Stack>
                <CompactRow label="Allergies" value={allergies.join(", ") || null} />
                <CompactRow label="Long-term Medications" value={currentMedications.join(", ") || compactText(medications.map((item) => item.label).join(", "), 72) || null} />
                <CompactRow label="Last visit" value={snapshot?.lastConsultationDate || null} />
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

            <SectionBox title="Timeline summary" icon={<TimelineRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                {context?.timelineSummary.recentImportantEvents ? (
                  <Typography variant="caption" color="text.secondary">
                    {context.timelineSummary.recentImportantEvents}
                  </Typography>
                ) : null}
                {timelineEvents.length ? (
                  <Stack spacing={0.45}>
                    {timelineEvents.slice(0, 6).map((event) => (
                      <TimelineLine
                        key={`${event.occurredOn || "unknown"}-${event.title}-${event.type}`}
                        date={event.occurredOn || "-"}
                        title={event.title}
                        detail={event.detail ? compactText(event.detail, 72) : null}
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
                <CompactChipRow items={recentReports.slice(0, 6)} color="default" limit={3} emptyLabel="No uploaded reports available." />
                <CompactChipRow items={radiologyReports.slice(0, 6)} color="secondary" limit={3} emptyLabel="No radiology reports available." />
                <CompactChipRow items={referrals.slice(0, 6)} color="primary" limit={3} emptyLabel="No referral letters available." />
                <CompactChipRow items={dischargeSummaries.slice(0, 6)} color="default" limit={3} emptyLabel="No discharge summaries available." />
              </Stack>
            </SectionBox>

            <SectionBox title="Lab intelligence" icon={<ScienceRoundedIcon fontSize="inherit" />}>
              <Stack spacing={0.45}>
                {context?.labIntelligence.latestLabReport ? (
                  <Typography variant="body2" sx={{ fontWeight: 800 }}>
                    {context.labIntelligence.latestLabReport}
                  </Typography>
                ) : (
                  <Typography variant="caption" color="text.secondary">
                    No recent lab report found. Order investigations or upload a report to track trends.
                  </Typography>
                )}
                <CompactChipRow items={context?.labIntelligence.abnormalValues?.slice(0, 6).map((item) => compactText(item, 42)) || []} color="warning" limit={3} emptyLabel="No abnormal values detected yet." />
                <CompactChipRow
                  items={[
                    context?.labIntelligence.lastHbA1c ? `HbA1c ${compactText(context.labIntelligence.lastHbA1c, 24)}` : null,
                    context?.labIntelligence.lastCbc ? `CBC ${compactText(context.labIntelligence.lastCbc, 24)}` : null,
                    context?.labIntelligence.lastCreatinine ? `Creatinine ${compactText(context.labIntelligence.lastCreatinine, 24)}` : null,
                    context?.labIntelligence.latestBloodSugar ? `Blood sugar ${compactText(context.labIntelligence.latestBloodSugar, 24)}` : null,
                    context?.labIntelligence.latestLipidSummary ? `Lipids ${compactText(context.labIntelligence.latestLipidSummary, 24)}` : null,
                  ].filter((item): item is string => Boolean(item))}
                  color="primary"
                  limit={5}
                  emptyLabel="No recent trend markers."
                />
                {context?.labIntelligence.previousTrends?.length ? (
                  <Typography variant="caption" color="text.secondary">
                    Trends: {context.labIntelligence.previousTrends.slice(0, 3).join(" • ")}
                  </Typography>
                ) : null}
                {context?.labIntelligence.pendingInvestigations?.length ? (
                  <Typography variant="caption" color="text.secondary">
                    Pending: {context.labIntelligence.pendingInvestigations.slice(0, 3).join(", ")}
                  </Typography>
                ) : null}
              </Stack>
            </SectionBox>
          </Stack>
        </Box>
      </Drawer>
    </>
  );
}
