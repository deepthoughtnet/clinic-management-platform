import * as React from "react";
import { alpha } from "@mui/material/styles";
import { Box, Stack, Tooltip, Typography } from "@mui/material";
import MedicalServicesRoundedIcon from "@mui/icons-material/MedicalServicesRounded";
import AutoAwesomeRoundedIcon from "@mui/icons-material/AutoAwesomeRounded";

import { branding } from "../../../branding";

export type BrandMarkProps = {
  compact?: boolean;
  size?: number;
  showCopy?: boolean;
  title?: string;
  subtitle?: string;
};

export default function BrandMark({
  compact = false,
  size = 40,
  showCopy = true,
  title = branding.productName,
  subtitle = branding.tagline,
}: BrandMarkProps) {
  const iconBlock = (
    <Box
      sx={(theme) => ({
        width: size,
        height: size,
        borderRadius: compact ? 2 : 3,
        display: "grid",
        placeItems: "center",
        position: "relative",
        flexShrink: 0,
        color: "common.white",
        background: `linear-gradient(145deg, ${theme.palette.primary.dark}, ${theme.palette.primary.main})`,
        boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.24)}`,
        overflow: "hidden",
      })}
    >
      <MedicalServicesRoundedIcon sx={{ fontSize: Math.max(18, Math.round(size * 0.58)) }} />
      <AutoAwesomeRoundedIcon
        sx={{
          position: "absolute",
          top: 5,
          right: 5,
          fontSize: Math.max(10, Math.round(size * 0.24)),
          opacity: 0.95,
        }}
      />
    </Box>
  );

  if (compact || !showCopy) {
    return <Tooltip title={title}>{iconBlock}</Tooltip>;
  }

  return (
    <Stack direction="row" alignItems="center" spacing={1.5} sx={{ minWidth: 0 }}>
      {iconBlock}
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="subtitle1"
          sx={{
            fontWeight: 900,
            lineHeight: 1.1,
            letterSpacing: -0.2,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {title}
        </Typography>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            display: "block",
            lineHeight: 1.15,
            whiteSpace: "nowrap",
            overflow: "hidden",
            textOverflow: "ellipsis",
          }}
        >
          {subtitle}
        </Typography>
      </Box>
    </Stack>
  );
}
