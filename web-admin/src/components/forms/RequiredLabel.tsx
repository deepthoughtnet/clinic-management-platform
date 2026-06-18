import * as React from "react";
import { Box } from "@mui/material";

type RequiredLabelProps = {
  text: React.ReactNode;
};

export default function RequiredLabel({ text }: RequiredLabelProps) {
  return (
    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.35 }}>
      <Box component="span">{text}</Box>
      <Box component="span" aria-hidden="true" sx={{ color: "error.main" }}>
        *
      </Box>
    </Box>
  );
}
