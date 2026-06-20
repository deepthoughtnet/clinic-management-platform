import React from "react";
import { Button } from "@mui/material";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";

import { HelpContext } from "./HelpProvider.js";
import { openGlobalHelp } from "./helpEvents.js";

function isDevMode() {
  return (import.meta.env && import.meta.env.DEV) ?? true;
}

export default function GlobalHelpButton({ buttonLabel = "Help" }) {
  const helpContext = React.useContext(HelpContext);

  return React.createElement(Button, {
    type: "button",
    size: "small",
    variant: "outlined",
    startIcon: React.createElement(HelpCenterRoundedIcon, { fontSize: "small" }),
    onClick: () => {
      if (isDevMode()) {
        console.log("Help clicked");
      }
      if (helpContext && helpContext.openHelp) {
        helpContext.openHelp();
      }
      openGlobalHelp({ source: "topbar" });
    },
    "aria-label": "Open help",
    children: buttonLabel,
  });
}
