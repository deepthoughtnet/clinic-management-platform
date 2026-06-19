import * as React from "react";
import { Box } from "@mui/material";

const visuallyHiddenSx = {
  border: 0,
  clip: "rect(0 0 0 0)",
  height: 1,
  margin: -1,
  overflow: "hidden",
  padding: 0,
  position: "absolute",
  whiteSpace: "nowrap",
  width: 1,
};

export default function RequiredLabel({ text, required = true }) {
  return React.createElement(
    Box,
    { component: "span", sx: { display: "inline-flex", alignItems: "center", gap: 0.35 } },
    React.createElement(Box, { component: "span" }, text),
    required
      ? React.createElement(
          React.Fragment,
          null,
          React.createElement(Box, { component: "span", "aria-hidden": "true", sx: { color: "error.main" } }, "*"),
          React.createElement(Box, { component: "span", sx: visuallyHiddenSx }, "(required)"),
        )
      : null,
  );
}
