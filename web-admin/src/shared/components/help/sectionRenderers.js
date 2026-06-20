import React from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import ExpandMoreRoundedIcon from "@mui/icons-material/ExpandMoreRounded";
import LinkRoundedIcon from "@mui/icons-material/LinkRounded";
import QuizRoundedIcon from "@mui/icons-material/QuizRounded";

function normalizeText(value) {
  if (value == null) return "";
  return String(value).replace(/\s+/g, " ").trim();
}

function renderItemLabel(item) {
  return normalizeText(item?.title ?? item?.question ?? item?.fieldName ?? item?.label ?? item?.name ?? item?.error ?? item?.field ?? "Item");
}

function renderItemDescription(item) {
  return normalizeText(item?.description ?? item?.answer ?? item?.cause ?? item?.resolution ?? item?.rule ?? item?.example ?? "");
}

function renderItemUrl(item) {
  return normalizeText(item?.url ?? item?.link ?? "");
}

export function HelpDescription({ content }) {
  if (!content) return null;
  return React.createElement(
    Stack,
    { spacing: 0.75 },
    content.title ? React.createElement(Typography, { variant: "subtitle1", sx: { fontWeight: 800 } }, content.title) : null,
    content.description ? React.createElement(Typography, { variant: "body2", color: "text.secondary", sx: { whiteSpace: "pre-wrap" } }, content.description) : null,
  );
}

export function HelpWorkflow({ steps }) {
  if (!steps.length) return null;
  return React.createElement(
    Stack,
    { spacing: 1 },
    steps.map((step, index) => React.createElement(
      Card,
      { key: `${step.title ?? "step"}-${index}`, variant: "outlined" },
      React.createElement(
        CardContent,
        { sx: { py: 1.5 } },
        React.createElement(
          Stack,
          { direction: "row", spacing: 1, alignItems: "flex-start" },
          React.createElement(Chip, { size: "small", label: step.icon || index + 1, variant: "outlined", sx: { mt: 0.15 } }),
          React.createElement(
            Box,
            null,
            React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 800 } }, step.title || "Step"),
            step.description ? React.createElement(Typography, { variant: "body2", color: "text.secondary", sx: { whiteSpace: "pre-wrap" } }, step.description) : null,
          ),
        ),
      ),
    )),
  );
}

export function HelpFieldTable({ rows }) {
  if (!rows.length) return null;
  return React.createElement(
    Table,
    { size: "small" },
    React.createElement(
      TableHead,
      null,
      React.createElement(
        TableRow,
        null,
        React.createElement(TableCell, null, "Field"),
        React.createElement(TableCell, null, "Required"),
        React.createElement(TableCell, null, "Rule"),
        React.createElement(TableCell, null, "Max Length"),
        React.createElement(TableCell, null, "Example"),
      ),
    ),
    React.createElement(
      TableBody,
      null,
      rows.map((row, index) => React.createElement(
        TableRow,
        { key: `${row.fieldName ?? "field"}-${index}` },
        React.createElement(TableCell, { sx: { fontWeight: 700 } }, row.fieldName ?? "-"),
        React.createElement(TableCell, null, row.required ? "Yes" : "No"),
        React.createElement(TableCell, null, row.rule ?? row.type ?? row.description ?? "-"),
        React.createElement(TableCell, null, row.maxLength ?? "-"),
        React.createElement(TableCell, null, row.example ?? "-"),
      )),
    ),
  );
}

export function HelpValidationRules({ rules }) {
  if (!rules.length) return null;
  return React.createElement(
    Table,
    { size: "small" },
    React.createElement(
      TableHead,
      null,
      React.createElement(
        TableRow,
        null,
        React.createElement(TableCell, null, "Field"),
        React.createElement(TableCell, null, "Required"),
        React.createElement(TableCell, null, "Rule"),
        React.createElement(TableCell, null, "Max Length"),
        React.createElement(TableCell, null, "Examples"),
      ),
    ),
    React.createElement(
      TableBody,
      null,
      rules.map((rule, index) => React.createElement(
        TableRow,
        { key: `${rule.field ?? "rule"}-${index}` },
        React.createElement(TableCell, { sx: { fontWeight: 700 } }, rule.field ?? "-"),
        React.createElement(TableCell, null, rule.required ? "Yes" : "No"),
        React.createElement(TableCell, null, rule.rule ?? rule.type ?? "-"),
        React.createElement(TableCell, null, rule.maxLength ?? "-"),
        React.createElement(TableCell, null, rule.example ?? "-"),
      )),
    ),
  );
}

export function HelpReportTypes({ items }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No report types configured.");
}

export function HelpReportFilters({ items }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No report filters configured.");
}

export function HelpExportCsv({ items }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No export guidance configured.");
}

function renderRichListItems(items, emptyLabel) {
  if (!items.length) {
    return React.createElement(Typography, { variant: "body2", color: "text.secondary" }, emptyLabel);
  }
  return React.createElement(
    Stack,
    { spacing: 1 },
    items.map((item, index) => {
      const label = renderItemLabel(item);
      const description = renderItemDescription(item);
      const url = renderItemUrl(item);
      return React.createElement(
        Card,
        { key: `${label}-${index}`, variant: "outlined" },
        React.createElement(
          CardContent,
          { sx: { py: 1.5 } },
          React.createElement(
            Stack,
            { spacing: 0.5 },
            React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 800 } }, label),
            description ? React.createElement(Typography, { variant: "body2", color: "text.secondary", sx: { whiteSpace: "pre-wrap" } }, description) : null,
            url ? React.createElement(Link, { href: url, target: "_blank", rel: "noreferrer" }, url) : null,
          ),
        ),
      );
    }),
  );
}

export function HelpPermissions({ permissions }) {
  if (!permissions.length) return null;
  return React.createElement(Stack, { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap" }, permissions.map((permission) => React.createElement(Chip, { key: permission, size: "small", label: permission, variant: "outlined" })));
}

export function HelpRoles({ allowedRoles = [], notIntendedFor = [], description }) {
  if (!(allowedRoles.length || notIntendedFor.length || description)) return null;
  return React.createElement(
    Stack,
    { spacing: 1 },
    description ? React.createElement(Typography, { variant: "body2", color: "text.secondary" }, description) : null,
    allowedRoles.length ? React.createElement(
      Stack,
      { spacing: 0.75 },
      React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 800 } }, "Allowed"),
      React.createElement(Stack, { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap" }, allowedRoles.map((role) => React.createElement(Chip, { key: role, size: "small", color: "primary", label: role, variant: "outlined" }))),
    ) : null,
    notIntendedFor.length ? React.createElement(
      Stack,
      { spacing: 0.75 },
      React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 800 } }, "Not intended for"),
      React.createElement(Stack, { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap" }, notIntendedFor.map((role) => React.createElement(Chip, { key: role, size: "small", label: role, variant: "outlined" }))),
    ) : null,
  );
}

function RenderListItems({ items, emptyLabel }) {
  return renderRichListItems(items, emptyLabel);
}

export function HelpCommonErrors({ items }) {
  if (!items.length) return null;
  return React.createElement(
    Table,
    { size: "small" },
    React.createElement(
      TableHead,
      null,
      React.createElement(
        TableRow,
        null,
        React.createElement(TableCell, null, "Error"),
        React.createElement(TableCell, null, "Cause"),
        React.createElement(TableCell, null, "Resolution"),
      ),
    ),
    React.createElement(
      TableBody,
      null,
      items.map((item, index) => React.createElement(
        TableRow,
        { key: `${item.error ?? item.cause ?? "error"}-${index}` },
        React.createElement(TableCell, { sx: { fontWeight: 700 } }, normalizeText(item.error || item.cause || "Error")),
        React.createElement(TableCell, null, normalizeText(item.cause || item.answer || "-")),
        React.createElement(TableCell, null, normalizeText(item.resolution || item.description || item.answer || "-")),
      )),
    ),
  );
}

export function HelpCommonIssues({ items }) {
  return React.createElement(HelpCommonErrors, { items });
}

export function HelpBestPractices({ items }) {
  if (!items.length) return null;
  return React.createElement(RenderListItems, { items, emptyLabel: "No best practices available." });
}

export function HelpQuickActions({ items }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No quick actions configured.");
}

export function HelpDashboardCards({ items }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No dashboard cards configured.");
}

export function HelpTips({ items }) {
  if (!items.length) return null;
  return React.createElement(RenderListItems, { items, emptyLabel: "No tips available." });
}

export function HelpKnownLimitations({ items }) {
  if (!items.length) return null;
  return React.createElement(RenderListItems, { items, emptyLabel: "No known limitations." });
}

export function HelpAudit({ items }) {
  if (!items.length) return null;
  return React.createElement(RenderListItems, { items, emptyLabel: "No audit notes." });
}

export function HelpFAQ({ items }) {
  if (!items.length) return React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No FAQs have been added yet.");
  return React.createElement(
    Stack,
    { spacing: 1 },
    items.map((item, index) => React.createElement(
      Accordion,
      { key: `${item.question ?? "faq"}-${index}`, variant: "outlined" },
        React.createElement(
        AccordionSummary,
        { expandIcon: React.createElement(ExpandMoreRoundedIcon, null) },
        React.createElement(
          Stack,
          { direction: "row", spacing: 1, alignItems: "center" },
          React.createElement(QuizRoundedIcon, { fontSize: "small", color: "action" }),
          React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 800 } }, item.question || "Question"),
        ),
      ),
      React.createElement(
        AccordionDetails,
        null,
        React.createElement(Typography, { variant: "body2", color: "text.secondary", sx: { whiteSpace: "pre-wrap" } }, item.answer || "-"),
      ),
    )),
  );
}

export function HelpRelatedPages({ pages }) {
  if (!pages.length) return React.createElement(Typography, { variant: "body2", color: "text.secondary" }, "No related pages configured.");
  return React.createElement(
    Stack,
    { direction: "row", spacing: 1, useFlexGap: true, flexWrap: "wrap" },
    pages.map((page, index) => {
      const label = page.title || page.pageKey || "Related page";
      return React.createElement(Chip, {
        key: `${page.pageKey ?? page.title ?? "page"}-${index}`,
        label,
        icon: React.createElement(LinkRoundedIcon, { fontSize: "small" }),
        variant: "outlined",
      });
    }),
  );
}

export function HelpAttachments({ attachments }) {
  if (!attachments.length) return null;
  return React.createElement(
    Stack,
    { spacing: 1 },
    attachments.map((attachment, index) => {
      const href = normalizeText(attachment.url);
      return React.createElement(
        Card,
        { key: `${attachment.url ?? "attachment"}-${index}`, variant: "outlined" },
        React.createElement(
          CardContent,
          { sx: { py: 1.5 } },
          React.createElement(Typography, { variant: "subtitle2", sx: { fontWeight: 700 } }, attachment.title || attachment.type || "Attachment"),
          attachment.description ? React.createElement(Typography, { variant: "body2", color: "text.secondary" }, attachment.description) : null,
          href ? React.createElement(Link, { href, target: "_blank", rel: "noreferrer" }, href) : null,
        ),
      );
    }),
  );
}
