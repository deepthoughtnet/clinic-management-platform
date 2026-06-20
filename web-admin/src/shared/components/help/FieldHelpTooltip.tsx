import * as React from "react";
import { Box, Tooltip, Typography } from "@mui/material";
import HelpOutlineRoundedIcon from "@mui/icons-material/HelpOutlineRounded";

import RequiredLabel from "../../../components/forms/RequiredLabel.js";

type FieldHelpTooltipProps = {
  label: React.ReactNode;
  helpText: string;
  required?: boolean;
  tooltipLabel?: string;
};

export default function FieldHelpTooltip({ label, helpText, required = false, tooltipLabel = "Help" }: FieldHelpTooltipProps) {
  return (
    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.5 }}>
      <RequiredLabel text={label} required={required} />
      <Tooltip title={<Typography variant="caption">{helpText}</Typography>} arrow>
        <Box component="span" sx={{ display: "inline-flex", alignItems: "center", color: "text.secondary" }} aria-label={tooltipLabel}>
          <HelpOutlineRoundedIcon fontSize="inherit" />
        </Box>
      </Tooltip>
    </Box>
  );
}
