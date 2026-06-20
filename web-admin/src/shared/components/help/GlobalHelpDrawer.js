import React from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Drawer,
  IconButton,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import HelpCenterRoundedIcon from "@mui/icons-material/HelpCenterRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import HistoryRoundedIcon from "@mui/icons-material/HistoryRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import QuizRoundedIcon from "@mui/icons-material/QuizRounded";
import SmartToyRoundedIcon from "@mui/icons-material/SmartToyRounded";

import HelpSearch from "./HelpSearch.js";

function sectionContainer(children) {
  return React.createElement(
    Paper,
    { variant: "outlined", sx: { borderRadius: 2, p: 2, boxShadow: 1 } },
    children,
  );
}

function panelTitle(title, subtitle, icon) {
  return React.createElement(
    Stack,
    { direction: "row", spacing: 1.25, alignItems: "flex-start" },
    icon ? React.createElement(Box, { sx: { mt: 0.25 } }, icon) : null,
    React.createElement(
      Box,
      null,
      React.createElement(Typography, { variant: "subtitle1", sx: { fontWeight: 900 } }, title),
      subtitle ? React.createElement(Typography, { variant: "body2", color: "text.secondary" }, subtitle) : null,
    ),
  );
}

export default function GlobalHelpDrawer({ open, pageKey, pageTitle, onClose }) {
  if (!open) return null;
  const disablePortal = typeof window === "undefined";
  const title = pageKey === "UNKNOWN_PAGE" ? "No page-specific help is available for this page yet." : (pageTitle || "Help");

  return React.createElement(
    Drawer,
    {
      anchor: "right",
      open: Boolean(open),
      onClose,
      ModalProps: { keepMounted: true, disablePortal },
      PaperProps: {
        "data-testid": "global-help-drawer",
        role: "dialog",
        "aria-label": "Help drawer",
        sx: (theme) => ({
          width: { xs: "100vw", sm: 520, md: 560 },
          maxWidth: "100vw",
          position: "fixed",
          right: 0,
          top: 0,
          height: "100vh",
          zIndex: theme.zIndex.modal + 1,
          display: "flex",
          flexDirection: "column",
        }),
      },
    },
    React.createElement(
      Box,
      { sx: { display: "flex", flexDirection: "column", height: "100%" } },
      React.createElement(
        Box,
        { sx: { px: 2.25, py: 2, borderBottom: "1px solid", borderColor: "divider" } },
        React.createElement(
          Stack,
          { direction: "row", alignItems: "flex-start", justifyContent: "space-between", spacing: 1 },
          React.createElement(
            Stack,
            { spacing: 0.5 },
            React.createElement(
              Stack,
              { direction: "row", spacing: 1, alignItems: "center" },
              React.createElement(HelpCenterRoundedIcon, { fontSize: "small", color: "primary" }),
              React.createElement(Typography, { variant: "h6", sx: { fontWeight: 900, lineHeight: 1.15 } }, "Help Center"),
            ),
            React.createElement(Typography, { variant: "body2", color: "text.secondary" }, title),
            pageKey === "UNKNOWN_PAGE"
              ? React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No page-specific help is available for this page yet.")
              : null,
          ),
          React.createElement(
            IconButton,
            { "aria-label": "Close help", onClick: onClose, size: "small" },
            React.createElement(CloseRoundedIcon, { fontSize: "small" }),
          ),
        ),
      ),
      React.createElement(
        Box,
        { sx: { px: 2.25, pt: 2, pb: 1.5 } },
        React.createElement(HelpSearch, {
          value: "",
          onChange: () => undefined,
          placeholder: "Search help, workflows, common errors, and FAQs...",
        }),
      ),
      React.createElement(Divider, null),
      React.createElement(
        Box,
        { sx: { flex: 1, overflow: "auto", px: 2.25, py: 2.25 } },
        React.createElement(
          Stack,
          { spacing: 2 },
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("Current Page Help", pageKey === "UNKNOWN_PAGE" ? "No page-specific help available." : "Workflows, validations, errors, and guidance for this page.", React.createElement(HelpCenterRoundedIcon, { fontSize: "small", color: "action" })),
              React.createElement(Alert, { severity: "info", variant: "outlined" }, "Loading help..."),
            ),
          ),
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("Search All Help", "Search page titles, descriptions, workflows, common errors, and FAQs.", React.createElement(SearchRoundedIcon, { fontSize: "small", color: "action" })),
            ),
          ),
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("Recent Pages", "Quick access to pages you opened recently.", React.createElement(HistoryRoundedIcon, { fontSize: "small", color: "action" })),
              React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No recent pages yet."),
            ),
          ),
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("Related Pages", "Quick links related to the current page.", React.createElement(LinkRoundedIcon, { fontSize: "small", color: "action" })),
              React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No related pages configured."),
              React.createElement(
                Stack,
                { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap" },
                React.createElement(Chip, { label: "Inventory", icon: React.createElement(LinkRoundedIcon, { fontSize: "small" }), variant: "outlined" }),
                React.createElement(Chip, { label: "Dispensing", icon: React.createElement(LinkRoundedIcon, { fontSize: "small" }), variant: "outlined" }),
                React.createElement(Chip, { label: "Stock Movements", icon: React.createElement(LinkRoundedIcon, { fontSize: "small" }), variant: "outlined" }),
                React.createElement(Chip, { label: "Pharmacy POS", icon: React.createElement(LinkRoundedIcon, { fontSize: "small" }), variant: "outlined" }),
              ),
            ),
          ),
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("FAQ", "Common questions and answers for the current page.", React.createElement(QuizRoundedIcon, { fontSize: "small", color: "action" })),
              React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No FAQs have been added yet."),
            ),
          ),
          sectionContainer(
            React.createElement(
              Stack,
              { spacing: 1.25 },
              panelTitle("Ask AIVA", "Ask questions in natural language.", React.createElement(SmartToyRoundedIcon, { fontSize: "small", color: "action" })),
              React.createElement(Alert, { severity: "info", variant: "outlined" }, "Coming soon. Ask AIVA will search Help CMS in a future rollout."),
              React.createElement(
                Stack,
                { spacing: 0.5 },
                React.createElement(Typography, { variant: "caption", color: "text.secondary" }, "Examples"),
                React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "• How do I add a medicine?"),
                React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "• Why is batch number mandatory?"),
                React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "• How do I disable a medicine?"),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}
