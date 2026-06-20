import * as React from "react";
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

import type {
  HelpAttachmentItem,
  HelpCommonErrorItem,
  HelpDescriptionContent,
  HelpFaqItem,
  HelpFieldTableRow,
  HelpRelatedPageItem,
  HelpValidationRule,
  HelpWorkflowStep,
} from "./helpContent";

function sectionTitle(value: string) {
  return value.replaceAll("_", " ").toLowerCase().replace(/(^|\s)\w/g, (m) => m.toUpperCase());
}

function normalizeText(value: unknown): string {
  if (value == null) return "";
  const text = String(value).replace(/\s+/g, " ").trim();
  return text;
}

function renderItemLabel(item: Record<string, unknown>): string {
  return normalizeText(item.title ?? item.question ?? item.fieldName ?? item.label ?? item.name ?? item.error ?? item.field ?? "Item");
}

function renderItemDescription(item: Record<string, unknown>): string {
  return normalizeText(item.description ?? item.answer ?? item.cause ?? item.resolution ?? item.rule ?? item.example ?? "");
}

function renderItemUrl(item: Record<string, unknown>): string {
  return normalizeText(item.url ?? item.link ?? "");
}

export function HelpDescription({ content }: { content: HelpDescriptionContent | null }) {
  if (!content) return null;
  return (
    <Stack spacing={0.75}>
      {content.title ? <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{content.title}</Typography> : null}
      {content.description ? <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{content.description}</Typography> : null}
    </Stack>
  );
}

export function HelpWorkflow({ steps }: { steps: HelpWorkflowStep[] }) {
  if (!steps.length) return null;
  return (
    <Stack spacing={1}>
      {steps.map((step, index) => (
        <Card key={`${step.title ?? "step"}-${index}`} variant="outlined">
          <CardContent sx={{ py: 1.5 }}>
            <Stack direction="row" spacing={1} alignItems="flex-start">
              <Chip size="small" label={step.icon || index + 1} variant="outlined" sx={{ mt: 0.15 }} />
              <Box>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                  {step.title || "Step"}
                </Typography>
                {step.description ? <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{step.description}</Typography> : null}
              </Box>
            </Stack>
          </CardContent>
        </Card>
      ))}
    </Stack>
  );
}

export function HelpFieldTable({ rows }: { rows: HelpFieldTableRow[] }) {
  if (!rows.length) return null;
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Field</TableCell>
          <TableCell>Required</TableCell>
          <TableCell>Rule</TableCell>
          <TableCell>Max Length</TableCell>
          <TableCell>Example</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rows.map((row, index) => (
          <TableRow key={`${row.fieldName ?? "field"}-${index}`}>
            <TableCell sx={{ fontWeight: 700 }}>{row.fieldName ?? "-"}</TableCell>
            <TableCell>{row.required ? "Yes" : "No"}</TableCell>
            <TableCell>{row.rule ?? row.type ?? row.description ?? "-"}</TableCell>
            <TableCell>{row.maxLength ?? "-"}</TableCell>
            <TableCell>{row.example ?? "-"}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function HelpValidationRules({ rules }: { rules: HelpValidationRule[] }) {
  if (!rules.length) return null;
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Field</TableCell>
          <TableCell>Required</TableCell>
          <TableCell>Rule</TableCell>
          <TableCell>Max Length</TableCell>
          <TableCell>Examples</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {rules.map((rule, index) => (
          <TableRow key={`${rule.field ?? "rule"}-${index}`}>
            <TableCell sx={{ fontWeight: 700 }}>{rule.field ?? "-"}</TableCell>
            <TableCell>{rule.required ? "Yes" : "No"}</TableCell>
            <TableCell>{rule.rule ?? rule.type ?? "-"}</TableCell>
            <TableCell>{rule.maxLength ?? "-"}</TableCell>
            <TableCell>{rule.example ?? "-"}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function HelpReportTypes({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No report types configured.");
}

export function HelpReportFilters({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No report filters configured.");
}

export function HelpExportCsv({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No export guidance configured.");
}

function renderRichListItems(items: Array<Record<string, unknown>>, emptyLabel: string) {
  if (!items.length) {
    return <Typography variant="body2" color="text.secondary">{emptyLabel}</Typography>;
  }
  return (
    <Stack spacing={1}>
      {items.map((item, index) => {
        const label = renderItemLabel(item);
        const description = renderItemDescription(item);
        const url = renderItemUrl(item);
        return (
          <Card key={`${label}-${index}`} variant="outlined">
            <CardContent sx={{ py: 1.5 }}>
              <Stack spacing={0.5}>
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{label}</Typography>
                {description ? <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{description}</Typography> : null}
                {url ? <Link href={url} target="_blank" rel="noreferrer">{url}</Link> : null}
              </Stack>
            </CardContent>
          </Card>
        );
      })}
    </Stack>
  );
}

export function HelpPermissions({ permissions }: { permissions: string[] }) {
  if (!permissions.length) return null;
  return <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">{permissions.map((permission) => <Chip key={permission} size="small" label={permission} variant="outlined" />)}</Stack>;
}

export function HelpRoles({ allowedRoles, notIntendedFor, description }: { allowedRoles?: string[]; notIntendedFor?: string[]; description?: string }) {
  if (!(allowedRoles?.length || notIntendedFor?.length || description)) return null;
  return (
    <Stack spacing={1}>
      {description ? <Typography variant="body2" color="text.secondary">{description}</Typography> : null}
      {allowedRoles?.length ? (
        <Stack spacing={0.75}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Allowed</Typography>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {allowedRoles.map((role) => <Chip key={role} size="small" color="primary" label={role} variant="outlined" />)}
          </Stack>
        </Stack>
      ) : null}
      {notIntendedFor?.length ? (
        <Stack spacing={0.75}>
          <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>Not intended for</Typography>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {notIntendedFor.map((role) => <Chip key={role} size="small" label={role} variant="outlined" />)}
          </Stack>
        </Stack>
      ) : null}
    </Stack>
  );
}

function RenderListItems({ items, emptyLabel = "No items." }: { items: Array<Record<string, unknown>>; emptyLabel?: string }) {
  return renderRichListItems(items, emptyLabel);
}

export function HelpCommonErrors({ items }: { items: HelpCommonErrorItem[] }) {
  if (!items.length) return null;
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Error</TableCell>
          <TableCell>Cause</TableCell>
          <TableCell>Resolution</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {items.map((item, index) => (
          <TableRow key={`${item.error ?? item.cause ?? "error"}-${index}`}>
            <TableCell sx={{ fontWeight: 700 }}>{normalizeText(item.error || item.cause || "Error")}</TableCell>
            <TableCell>{normalizeText(item.cause || item.answer || "-")}</TableCell>
            <TableCell>{normalizeText(item.resolution || item.description || item.answer || "-")}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function HelpCommonIssues({ items }: { items: HelpCommonErrorItem[] }) {
  return <HelpCommonErrors items={items} />;
}

export function HelpBestPractices({ items }: { items: HelpFaqItem[] }) {
  if (!items.length) return null;
  return <RenderListItems items={items as Array<Record<string, unknown>>} emptyLabel="No best practices available." />;
}

export function HelpQuickActions({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No quick actions configured.");
}

export function HelpDashboardCards({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return renderRichListItems(items, "No dashboard cards configured.");
}

export function HelpTips({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return <RenderListItems items={items} emptyLabel="No tips available." />;
}

export function HelpKnownLimitations({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return <RenderListItems items={items} emptyLabel="No known limitations." />;
}

export function HelpAudit({ items }: { items: Array<Record<string, unknown>> }) {
  if (!items.length) return null;
  return <RenderListItems items={items} emptyLabel="No audit notes." />;
}

export function HelpFAQ({ items }: { items: HelpFaqItem[] }) {
  if (!items.length) {
    return <Typography variant="body2" color="text.secondary">No FAQs have been added yet.</Typography>;
  }
  return (
    <Stack spacing={1}>
      {items.map((item, index) => (
        <Accordion key={`${item.question ?? "faq"}-${index}`} disableGutters sx={{ borderRadius: 2, overflow: "hidden", "&:before": { display: "none" } }}>
          <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}>
            <Stack direction="row" spacing={1} alignItems="center">
              <QuizRoundedIcon fontSize="small" color="action" />
              <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{item.question || "Question"}</Typography>
            </Stack>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: "pre-wrap" }}>{item.answer || "-"}</Typography>
          </AccordionDetails>
        </Accordion>
      ))}
    </Stack>
  );
}

export function HelpRelatedPages({ pages }: { pages: HelpRelatedPageItem[] }) {
  if (!pages.length) {
    return <Typography variant="body2" color="text.secondary">No related pages configured.</Typography>;
  }
  return (
    <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
      {pages.map((page, index) => {
        const label = page.title || page.pageKey || "Related page";
        if (page.url) {
          const external = page.url.startsWith("http");
          return (
            <Chip
              key={`${page.pageKey ?? label}-${index}`}
              label={label}
              icon={<LinkRoundedIcon fontSize="small" />}
              component={Link}
              href={page.url}
              target={external ? "_blank" : undefined}
              rel={external ? "noreferrer" : undefined}
              clickable
              variant="outlined"
            />
          );
        }
        return (
          <Chip
            key={`${page.pageKey ?? label}-${index}`}
            label={label}
            icon={<LinkRoundedIcon fontSize="small" />}
            variant="outlined"
          />
        );
      })}
    </Stack>
  );
}

export function HelpAttachments({ attachments }: { attachments: HelpAttachmentItem[] }) {
  if (!attachments.length) return null;
  return (
    <Stack spacing={1}>
      {attachments.map((attachment, index) => {
        const href = normalizeText(attachment.url);
        return (
          <Card key={`${attachment.url ?? "attachment"}-${index}`} variant="outlined">
            <CardContent sx={{ py: 1.5 }}>
              <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{attachment.title || attachment.type || "Attachment"}</Typography>
              {attachment.description ? <Typography variant="body2" color="text.secondary">{attachment.description}</Typography> : null}
              {href ? <Link href={href} target="_blank" rel="noreferrer">{href}</Link> : null}
            </CardContent>
          </Card>
        );
      })}
    </Stack>
  );
}
