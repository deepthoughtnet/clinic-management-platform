import * as React from "react";
import { Box, Card, CardContent, Chip, Stack, TableContainer, Typography } from "@mui/material";
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
