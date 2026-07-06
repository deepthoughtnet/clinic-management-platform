import * as React from "react";
import { Box, Card, CardContent, Chip, Stack, TableContainer, TableRow, Typography } from "@mui/material";
import type { ChipProps } from "@mui/material";

export const compactChipSx = {
  height: 22,
  borderRadius: 999,
  fontSize: "0.68rem",
  "& .MuiChip-label": {
    px: 0.8,
    py: 0,
  },
} as const;

export const compactCardContentSx = {
  p: 1.1,
  "&:last-child": {
    pb: 1.1,
  },
} as const;

export const compactPanelSx = {
  borderRadius: 2,
} as const;

export const compactSectionSx = {
  p: 1,
} as const;

export const compactFormSx = {
  "& .MuiFormControl-root, & .MuiTextField-root": {
    m: 0,
  },
  "& .MuiInputBase-root": {
    minHeight: 38,
  },
  "& .MuiFormHelperText-root": {
    mt: 0.25,
    minHeight: 16,
  },
  "& .MuiAutocomplete-inputRoot": {
    minHeight: 38,
    py: 0.15,
  },
} as const;

export const compactAccordionSx = {
  border: "1px solid",
  borderColor: "divider",
  borderRadius: 2,
  overflow: "hidden",
  "&:before": {
    display: "none",
  },
} as const;

export const compactFormGridSx = {
  mt: 0.25,
} as const;

export type WorkflowStripStep = {
  label: React.ReactNode;
  tone?: ChipProps["color"];
  helper?: React.ReactNode;
  state?: "completed" | "current" | "future";
};

type WorkflowStripProps = {
  steps: readonly WorkflowStripStep[];
  label?: React.ReactNode;
  currentStepIndex?: number;
};

function stepState(index: number, currentStepIndex: number | undefined, explicitState?: WorkflowStripStep["state"]) {
  if (explicitState) return explicitState;
  if (currentStepIndex === undefined) return index === 0 ? "current" : "future";
  if (index < currentStepIndex) return "completed";
  if (index === currentStepIndex) return "current";
  return "future";
}

function stepColors(state: "completed" | "current" | "future", tone?: ChipProps["color"]) {
  if (state === "completed") {
    return { bgcolor: "success.main", color: "success.contrastText", borderColor: "success.main" } as const;
  }
  if (state === "current") {
    return tone === "success"
      ? { bgcolor: "success.main", color: "success.contrastText", borderColor: "success.main" } as const
      : tone === "error"
        ? { bgcolor: "error.main", color: "error.contrastText", borderColor: "error.main" } as const
        : tone === "warning"
          ? { bgcolor: "warning.main", color: "warning.contrastText", borderColor: "warning.main" } as const
          : { bgcolor: "primary.main", color: "primary.contrastText", borderColor: "primary.main" } as const;
  }
  return { bgcolor: "background.paper", color: "text.secondary", borderColor: "divider" } as const;
}

export function WorkflowStrip({ steps, label = "Workflow", currentStepIndex }: WorkflowStripProps) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 0.6 }}>
      <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700, letterSpacing: 0.5, textTransform: "uppercase" }}>
        {label}
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridAutoFlow: "column",
          gridAutoColumns: "minmax(108px, 1fr)",
          gap: 0.8,
          overflowX: "auto",
          scrollbarWidth: "thin",
          pb: 0.35,
          "&::-webkit-scrollbar": { height: 6 },
        }}
      >
        {steps.map((step, index) => {
          const state = stepState(index, currentStepIndex, step.state);
          const colors = stepColors(state, step.tone);
          return (
            <Box
              key={typeof step.label === "string" ? `${step.label}-${index}` : index}
              sx={{
                position: "relative",
                minWidth: 108,
                display: "grid",
                justifyItems: "center",
                gap: 0.45,
                textAlign: "center",
                opacity: state === "future" ? 0.92 : 1,
                alignContent: "start",
              }}
            >
              <Box
                sx={{
                  position: "absolute",
                  top: 14,
                  left: "calc(50% + 14px)",
                  right: "-50%",
                  height: 2,
                  bgcolor: state === "completed" ? "success.main" : "divider",
                  display: index === steps.length - 1 ? "none" : "block",
                }}
              />
              <Box
                sx={{
                  width: 28,
                  height: 28,
                  borderRadius: "50%",
                  border: "2px solid",
                  display: "grid",
                  placeItems: "center",
                  fontSize: 12,
                  fontWeight: 800,
                  lineHeight: 1,
                  zIndex: 1,
                  flexShrink: 0,
                  ...colors,
                }}
              >
                {state === "completed" ? "✓" : index + 1}
              </Box>
              <Typography
                variant="body2"
                sx={{
                  fontSize: "0.74rem",
                  fontWeight: 700,
                  lineHeight: 1.2,
                  minHeight: "2.4em",
                  width: "100%",
                  maxWidth: 112,
                  display: "-webkit-box",
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: "vertical",
                  overflow: "hidden",
                  textAlign: "center",
                  color: state === "future" ? "text.secondary" : "text.primary",
                }}
              >
                {step.label}
              </Typography>
              {step.helper ? (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{
                    lineHeight: 1.15,
                    minHeight: "1.15em",
                    width: "100%",
                    maxWidth: 112,
                    textAlign: "center",
                    display: "-webkit-box",
                    WebkitLineClamp: 1,
                    WebkitBoxOrient: "vertical",
                    overflow: "hidden",
                  }}
                >
                  {step.helper}
                </Typography>
              ) : null}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

export type WorkflowGuideStep = {
  label: React.ReactNode;
  helper?: React.ReactNode;
  tone?: ChipProps["color"];
};

type WorkflowGuideProps = {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  steps: WorkflowGuideStep[];
};

export function WorkflowGuide({ title, subtitle, steps }: WorkflowGuideProps) {
  return (
    <CompactFilterCard title={title} subtitle={subtitle}>
      <WorkflowStrip
        steps={steps.map((step) => ({ label: step.label, tone: step.tone, helper: step.helper }))}
        currentStepIndex={Math.max(0, steps.findIndex((step) => step.tone === "primary"))}
      />
    </CompactFilterCard>
  );
}

export type WorkflowDependency = {
  key: string;
  label: React.ReactNode;
  isMet: boolean;
  cta?: React.ReactNode;
  helper?: React.ReactNode;
};

type WorkflowDependencyGateProps = {
  requirements: WorkflowDependency[];
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  children: React.ReactNode;
};

export function WorkflowDependencyGate({ requirements, title = "Workflow prerequisites", subtitle, children }: WorkflowDependencyGateProps) {
  const unmet = requirements.filter((requirement) => !requirement.isMet);
  if (!unmet.length) {
    return <>{children}</>;
  }

  return (
    <CompactFilterCard title={title} subtitle={subtitle}>
      <Stack spacing={0.75}>
        {unmet.map((requirement) => (
          <Stack key={requirement.key} spacing={0.35}>
            <Typography variant="body2" sx={{ fontWeight: 700 }}>
              {requirement.label}
            </Typography>
            {requirement.helper ? (
              <Typography variant="caption" color="text.secondary">
                {requirement.helper}
              </Typography>
            ) : null}
            {requirement.cta}
          </Stack>
        ))}
      </Stack>
    </CompactFilterCard>
  );
}

type CompactStatCardProps = {
  label: string;
  value: React.ReactNode;
  tone?: ChipProps["color"];
  helper?: React.ReactNode;
  onClick?: () => void;
};

export function CompactStatCard({ label, value, tone = "default", helper, onClick }: CompactStatCardProps) {
  return (
    <Card
      variant="outlined"
      onClick={onClick}
      onKeyDown={onClick ? (event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onClick();
        }
      } : undefined}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      sx={{
        height: "100%",
        cursor: onClick ? "pointer" : "default",
        transition: "box-shadow 120ms ease, transform 120ms ease",
        "&:hover": onClick ? { boxShadow: 2, transform: "translateY(-1px)" } : undefined,
        ...compactPanelSx,
      }}
    >
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={0.35}>
          <Chip size="small" label={label} color={tone} variant="outlined" sx={compactChipSx} />
          <Typography variant="h6" sx={{ fontWeight: 900, lineHeight: 1.1 }}>
            {value}
          </Typography>
          {helper ? (
            <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
              {helper}
            </Typography>
          ) : null}
        </Stack>
      </CardContent>
    </Card>
  );
}

type CompactFilterCardProps = {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
};

export function CompactFilterCard({ title, subtitle, actions, children }: CompactFilterCardProps) {
  return (
    <Card variant="outlined" sx={compactPanelSx}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1}>
          {(title || subtitle || actions) ? (
            <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "flex-start", flexWrap: "wrap" }}>
              <Box>
                {title ? <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{title}</Typography> : null}
                {subtitle ? <Typography variant="body2" color="text.secondary">{subtitle}</Typography> : null}
              </Box>
              {actions}
            </Box>
          ) : null}
          {children}
        </Stack>
      </CardContent>
    </Card>
  );
}

type CompactEmptyStateProps = {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  action?: React.ReactNode;
};

export function CompactEmptyState({ title, subtitle, action }: CompactEmptyStateProps) {
  return (
    <Box
      sx={{
        minHeight: 160,
        display: "grid",
        placeItems: "center",
        textAlign: "center",
        px: 2,
        py: 3,
        border: "1px dashed",
        borderColor: "divider",
        borderRadius: 2,
        bgcolor: "background.default",
      }}
    >
      <Stack spacing={1} alignItems="center" sx={{ maxWidth: 420 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
          {title}
        </Typography>
        {subtitle ? (
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        ) : null}
        {action}
      </Stack>
    </Box>
  );
}

type CompactTableFrameProps = {
  children: React.ReactNode;
  maxHeight?: number | string;
};

export function CompactTableFrame({ children, maxHeight = 560 }: CompactTableFrameProps) {
  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        overflow: "hidden",
        bgcolor: "background.paper",
      }}
    >
      <TableContainer
        sx={{
          maxHeight,
          overflowX: "auto",
          "& .MuiTableCell-stickyHeader": {
            bgcolor: "background.paper",
          },
        }}
      >
        {children}
      </TableContainer>
    </Box>
  );
}

type OperationalTableCardProps = {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  countLabel?: React.ReactNode;
  maxVisibleRows?: number;
  actions?: React.ReactNode;
  emptyState?: React.ReactNode;
  children: React.ReactNode;
};

function countOperationalTableRows(node: React.ReactNode): number {
  let count = 0;
  React.Children.forEach(node, (child) => {
    if (!React.isValidElement(child)) {
      return;
    }
    const element = child as React.ReactElement<{ children?: React.ReactNode }>;
    if (child.type === TableRow) {
      count += 1;
    }
    if (element.props.children) {
      count += countOperationalTableRows(element.props.children);
    }
  });
  return count;
}

export function OperationalTableCard({
  title,
  subtitle,
  countLabel,
  maxVisibleRows = 5,
  actions,
  emptyState,
  children,
}: OperationalTableCardProps) {
  const rowCount = React.useMemo(() => countOperationalTableRows(children), [children]);
  const shouldScroll = rowCount > maxVisibleRows;
  const maxHeight = shouldScroll ? (40 + (maxVisibleRows * 52)) : undefined;
  const accessibleTitle = typeof title === "string" ? title : "Operational table";

  return (
    <Card variant="outlined" sx={compactPanelSx}>
      <CardContent sx={compactCardContentSx}>
        <Stack spacing={1.25}>
          <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1, alignItems: "center", flexWrap: "wrap" }}>
            <Box>
              <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>
                {title}
              </Typography>
              {subtitle ? (
                <Typography variant="body2" color="text.secondary">
                  {subtitle}
                </Typography>
              ) : null}
            </Box>
            <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
              {actions}
              {countLabel ? <Chip size="small" label={countLabel} variant="outlined" /> : null}
            </Stack>
          </Box>
          {emptyState ? (
            emptyState
          ) : (
            <Box
              role="region"
              aria-label={`${accessibleTitle} rows`}
              tabIndex={0}
              sx={{
                outline: "none",
                borderRadius: 2,
                "&:focus-visible": {
                  boxShadow: (theme) => `0 0 0 2px ${theme.palette.primary.main}`,
                },
              }}
            >
              <CompactTableFrame maxHeight={maxHeight}>
                {children}
              </CompactTableFrame>
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
