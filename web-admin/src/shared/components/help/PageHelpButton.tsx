import * as React from "react";
import { Button } from "@mui/material";
import HelpOutlineRoundedIcon from "@mui/icons-material/HelpOutlineRounded";

import PageHelpDrawer from "./PageHelpDrawer";

type PageHelpButtonProps = {
  pageKey: string;
  title?: string;
  languageCode?: string;
  buttonLabel?: string;
};

export default function PageHelpButton({ pageKey, title, languageCode = "en", buttonLabel = "Help" }: PageHelpButtonProps) {
  const [open, setOpen] = React.useState(false);

  return (
    <>
      <Button
        size="small"
        variant="outlined"
        startIcon={<HelpOutlineRoundedIcon fontSize="small" />}
        onClick={() => setOpen(true)}
      >
        {buttonLabel}
      </Button>
      <PageHelpDrawer
        open={open}
        pageKey={pageKey}
        pageTitle={title}
        languageCode={languageCode}
        onClose={() => setOpen(false)}
      />
    </>
  );
}
