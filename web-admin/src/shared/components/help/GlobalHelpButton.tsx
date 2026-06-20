import * as React from "react";
import { Button } from "@mui/material";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";

import { HelpContext } from "./HelpProvider";
import { openGlobalHelp } from "./helpEvents";

type GlobalHelpButtonProps = {
  buttonLabel?: string;
};

function isDevMode() {
  return import.meta.env?.DEV ?? true;
}

export default function GlobalHelpButton({ buttonLabel = "Help" }: GlobalHelpButtonProps) {
  const helpContext = React.useContext(HelpContext);

  return (
    <Button
      type="button"
      size="small"
      variant="outlined"
      startIcon={<HelpCenterRoundedIcon fontSize="small" />}
      onClick={() => {
        if (isDevMode()) {
          console.log("Help clicked");
        }
        helpContext?.openHelp();
        openGlobalHelp({ source: "topbar" });
      }}
      aria-label="Open help"
    >
      {buttonLabel}
    </Button>
  );
}
