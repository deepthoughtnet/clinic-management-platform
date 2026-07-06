import * as React from "react";
import { Box, Chip, Stack, Typography } from "@mui/material";
import type { Appointment } from "../../api/clinicApi";
import {
  derivePatientJourneyStage,
  getAppointmentTokenLabel,
  getPatientJourneyStageIndex,
  getPatientJourneyStageLabel,
  getPatientJourneyStages,
  getWorkflowStatusLabel,
  getWorkflowStatusTone,
  type PatientJourneyContext,
  type WorkflowStatusTone,
} from "./workflowHelpers";

type TokenAppointment = {
  displayReference?: string | null;
  tokenNumber?: number | null;
  token?: string | null;
};

type AppointmentTokenProps = {
  appointment?: TokenAppointment | null;
  compact?: boolean;
  hidden?: boolean;
};

type WorkflowStatusBadgeProps = {
  status?: string | null;
  label?: string | null;
  tone?: WorkflowStatusTone;
  compact?: boolean;
};

type PatientJourneyTrackerProps = {
  context?: PatientJourneyContext;
  compact?: boolean;
  title?: string;
};

type PatientWorkflowGuideProps = {
  currentWorkflowStage?: string | null;
  compact?: boolean;
  title?: string;
  subtitle?: string;
};

const JOURNEY_STAGES = getPatientJourneyStages();

function workflowCircleSx(state: "completed" | "current" | "future") {
  if (state === "completed") {
    return {
      bgcolor: "success.main",
      color: "success.contrastText",
      borderColor: "success.main",
    } as const;
  }
  if (state === "current") {
    return {
      bgcolor: "primary.main",
      color: "primary.contrastText",
      borderColor: "primary.main",
      boxShadow: "0 0 0 4px rgba(25, 118, 210, 0.12)",
    } as const;
  }
  return {
    bgcolor: "background.paper",
    color: "text.secondary",
    borderColor: "divider",
  } as const;
}

function WorkflowStepper({
  currentIndex,
  compact = false,
  title,
  subtitle,
  renderLabel,
}: {
  currentIndex: number;
  compact?: boolean;
  title?: string;
  subtitle?: string;
  renderLabel: (index: number) => React.ReactNode;
}) {
  const size = compact ? 22 : 30;
  return (
    <Stack spacing={compact ? 1.1 : 1.5}>
      {title || subtitle ? (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: compact ? 2 : 3,
            flexWrap: "wrap",
          }}
        >
          {title ? (
            <Typography variant={compact ? "subtitle2" : "subtitle1"} sx={{ fontWeight: 900, lineHeight: 1.2 }}>
              {title}
            </Typography>
          ) : null}
          {subtitle ? (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ lineHeight: 1.2, fontWeight: 700, textAlign: { xs: "left", sm: "right" } }}
            >
              {subtitle}
            </Typography>
          ) : null}
        </Box>
      ) : null}
      <Box
        sx={{
          width: "100%",
          overflowX: { xs: "auto", md: "visible" },
          overflowY: "hidden",
          scrollbarWidth: "thin",
          pb: 0.25,
          "&::-webkit-scrollbar": { height: 6 },
        }}
      >
        <Box
          sx={{
            minWidth: compact ? 760 : 920,
            width: "100%",
            display: "flex",
            alignItems: "flex-start",
            gap: 0,
            pr: compact ? 0.5 : 0,
          }}
        >
          {JOURNEY_STAGES.map((stage, index) => {
            const state = index < currentIndex ? "completed" : index === currentIndex ? "current" : "future";
            const circle = workflowCircleSx(state);
            const isLast = index === JOURNEY_STAGES.length - 1;
            return (
              <React.Fragment key={stage}>
                <Box
                  sx={{
                    flex: 1,
                    minWidth: compact ? 68 : 84,
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    gap: compact ? 0.55 : 0.75,
                    textAlign: "center",
                  }}
                >
                  <Box
                    sx={{
                      width: state === "current" ? size + 6 : size,
                      height: state === "current" ? size + 6 : size,
                      borderRadius: "50%",
                      border: "2px solid",
                      display: "grid",
                      placeItems: "center",
                      fontSize: compact ? 11 : 12,
                      fontWeight: 900,
                      transition: "all 0.2s ease",
                      ...circle,
                      ...(state === "current"
                        ? {
                            animation: "journeyPulse 1.9s ease-in-out infinite",
                          }
                        : null),
                    }}
                  >
                    {state === "completed" ? "✓" : index + 1}
                  </Box>
                  <Typography
                    variant={compact ? "caption" : "body2"}
                    sx={{
                      fontWeight: state === "current" ? 900 : state === "completed" ? 700 : 600,
                      lineHeight: 1.2,
                      color: state === "current" ? "text.primary" : "text.secondary",
                      maxWidth: compact ? 76 : 96,
                      overflowWrap: "anywhere",
                    }}
                  >
                    {renderLabel(index)}
                  </Typography>
                </Box>
                {!isLast ? (
                  <Box
                    sx={{
                      flex: 1,
                      alignSelf: "flex-start",
                      mt: state === "current" ? `${(size + 6) / 2}px` : `${size / 2}px`,
                      mx: compact ? 0.4 : 0.6,
                      height: 3,
                      borderRadius: 999,
                      bgcolor: index < currentIndex ? "success.main" : index === currentIndex ? "primary.main" : "divider",
                      opacity: index <= currentIndex ? 1 : 0.7,
                    }}
                  />
                ) : null}
              </React.Fragment>
            );
          })}
        </Box>
      </Box>
    </Stack>
  );
}

function WorkflowCard({
  compact = false,
  children,
}: {
  compact?: boolean;
  children: React.ReactNode;
}) {
  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        bgcolor: "background.paper",
        p: compact ? 1.2 : 1.5,
        width: "100%",
        overflow: "hidden",
        "@keyframes journeyPulse": {
          "0%": { boxShadow: "0 0 0 0 rgba(25, 118, 210, 0.18)" },
          "70%": { boxShadow: "0 0 0 6px rgba(25, 118, 210, 0)" },
          "100%": { boxShadow: "0 0 0 0 rgba(25, 118, 210, 0)" },
        },
      }}
    >
      {children}
    </Box>
  );
}

export function AppointmentTokenChip({ appointment, compact = false, hidden = false }: AppointmentTokenProps) {
  const label = getAppointmentTokenLabel(appointment);
  if (hidden && label === "Token not assigned") return null;
  return (
    <Chip
      size="small"
      variant="outlined"
      label={label}
      sx={{
        height: compact ? 20 : 24,
        fontWeight: 700,
        "& .MuiChip-label": { px: compact ? 0.7 : 0.85 },
      }}
    />
  );
}

export function WorkflowStatusBadge({ status, label, tone, compact = false }: WorkflowStatusBadgeProps) {
  const resolvedLabel = label || getWorkflowStatusLabel(status) || "-";
  const resolvedTone = tone || getWorkflowStatusTone(status);
  return (
    <Chip
      size="small"
      label={resolvedLabel}
      color={resolvedTone}
      variant="outlined"
      sx={{
        height: compact ? 20 : 24,
        fontWeight: 700,
        "& .MuiChip-label": { px: compact ? 0.75 : 0.9 },
      }}
    />
  );
}

export function PatientJourneyTracker({ context, compact = false, title = "Patient Journey" }: PatientJourneyTrackerProps) {
  const currentStage = derivePatientJourneyStage(context);
  return <PatientWorkflowGuide currentWorkflowStage={currentStage} compact={compact} title={title} />;
}

export function PatientWorkflowGuide({
  currentWorkflowStage,
  compact = false,
  title = "Patient Workflow",
  subtitle = "Complete OPD journey",
}: PatientWorkflowGuideProps) {
  const currentIndex = getPatientJourneyStageIndex(currentWorkflowStage);
  return (
    <WorkflowCard compact={compact}>
      <WorkflowStepper
        currentIndex={currentIndex}
        compact={compact}
        title={title}
        subtitle={subtitle}
        renderLabel={(index) => getPatientJourneyStageLabel(JOURNEY_STAGES[index])}
      />
    </WorkflowCard>
  );
}

export function AppointmentWorkflowSummary({
  appointment,
  context,
  compact = false,
}: {
  appointment?: TokenAppointment | null;
  context?: PatientJourneyContext;
  compact?: boolean;
}) {
  return (
    <Stack spacing={compact ? 0.45 : 0.6}>
      {appointment ? (
        <Stack direction="row" spacing={0.5} flexWrap="wrap" alignItems="center">
          <AppointmentTokenChip appointment={appointment} compact={compact} />
        </Stack>
      ) : null}
      <PatientJourneyTracker context={context} compact={compact} />
    </Stack>
  );
}

export function WorkflowContextCard({ title, context, appointment, compact = false }: {
  title?: string;
  context?: PatientJourneyContext;
  appointment?: TokenAppointment | null;
  compact?: boolean;
}) {
  return (
    <Box sx={{ p: compact ? 0.75 : 1, border: "1px solid", borderColor: "divider", borderRadius: 2, bgcolor: "background.paper" }}>
      <Stack spacing={compact ? 0.65 : 0.8}>
        {title ? <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>{title}</Typography> : null}
        <AppointmentWorkflowSummary appointment={appointment} context={context} compact={compact} />
      </Stack>
    </Box>
  );
}
