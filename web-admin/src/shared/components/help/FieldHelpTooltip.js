import React from "react";
import { Box, Tooltip, Typography } from "@mui/material";

import RequiredLabel from "../../../components/forms/RequiredLabel.js";

export default function FieldHelpTooltip({ label, helpText, required = false }) {
  return React.createElement(
    Box,
    { component: "span", sx: { display: "inline-flex", alignItems: "center", gap: 0.5 } },
    React.createElement(RequiredLabel, { text: label, required }),
    React.createElement(
      Tooltip,
      { title: React.createElement(Typography, { variant: "caption" }, helpText), arrow: true },
      React.createElement(Box, { component: "span", sx: { display: "inline-flex", alignItems: "center" }, "aria-label": "Help" }, "?"),
    ),
  );
}

