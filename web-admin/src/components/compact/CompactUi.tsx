import * as React from "react";
import { Box, Card, CardContent, Chip, Stack, Typography } from "@mui/material";
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
  p: 1.25,
  "&:last-child": {
    pb: 1.25,
  },
} as const;

export const compactPanelSx = {
  borderRadius: 2,
} as const;

type CompactStatCardProps = {
  label: string;
  value: React.ReactNode;
  tone?: ChipProps["color"];
  helper?: React.ReactNode;
};

export function CompactStatCard({ label, value, tone = "default", helper }: CompactStatCardProps) {
  return (
    <Card variant="outlined" sx={{ height: "100%", ...compactPanelSx }}>
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
        minHeight: 180,
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
