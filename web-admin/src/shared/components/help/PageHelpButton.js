import React from "react";
import { Button } from "@mui/material";

export default function PageHelpButton({ buttonLabel = "Help" }) {
  return React.createElement(Button, { size: "small", variant: "outlined" }, buttonLabel);
}

