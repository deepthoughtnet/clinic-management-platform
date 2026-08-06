import * as React from "react";
import {
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Divider,
  FormControl,
  FormControlLabel,
  FormHelperText,
  InputAdornment,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import AddRoundedIcon from "@mui/icons-material/AddRounded";
import ArrowDownwardRoundedIcon from "@mui/icons-material/ArrowDownwardRounded";
import ArrowUpwardRoundedIcon from "@mui/icons-material/ArrowUpwardRounded";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import SearchRoundedIcon from "@mui/icons-material/SearchRounded";
import CheckRoundedIcon from "@mui/icons-material/CheckRounded";
import type { PublicAddressView } from "../../utils/publicProfileFormatting";

const WEEKDAY_ORDER = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

export type WeeklyTimingInterval = {
  dayOfWeek: (typeof WEEKDAY_ORDER)[number];
  startTime: string;
  endTime: string;
};

export type WeeklyScheduleValue = {
  timezone?: string | null;
  intervals?: WeeklyTimingInterval[];
  weekly?: Array<{
    day?: string | null;
    dayOfWeek?: string | null;
    start?: string | null;
    end?: string | null;
    open?: string | null;
    close?: string | null;
  }>;
};

export type ProviderEditorSectionCardProps = {
  title: string;
  description?: string | null;
  children: React.ReactNode;
  action?: React.ReactNode;
};

export type ProviderTagListEditorProps = {
  title: string;
  description?: string | null;
  helperText?: string | null;
  values: string[];
  suggestions?: string[];
  allowCustomEntry?: boolean;
  primaryValue?: string | null;
  onPrimaryValueChange?: (nextPrimary: string | null) => void;
  onChange: (nextValues: string[]) => void;
  emptyState: string;
  addLabel?: string;
  placeholder?: string;
  selectedLabel?: string;
  maxSelectionCount?: number | null;
};

export type ProviderFeeEditorRow = {
  key: string;
  label: string;
  amount: string;
  enabled: boolean;
  helperText?: string | null;
};

export type ProviderFeeEditorValue = {
  currency: string;
  rows: ProviderFeeEditorRow[];
};

export type ProviderFeeEditorProps = {
  value: ProviderFeeEditorValue;
  onChange: (nextValue: ProviderFeeEditorValue) => void;
};

function cleanText(value: unknown) {
  return typeof value === "string" ? value.replace(/\s+/g, " ").trim() : "";
}

function normalizeCaseInsensitiveList(values: unknown) {
  const result: string[] = [];
  if (!Array.isArray(values)) {
    return result;
  }
  for (const value of values) {
    const text = cleanText(value);
    if (!text) {
      continue;
    }
    if (result.some((item) => item.toLowerCase() === text.toLowerCase())) {
      continue;
    }
    result.push(text);
  }
  return result;
}

function stripDuplicateText(values: Array<string | null | undefined>) {
  return normalizeCaseInsensitiveList(values.filter((value): value is string => typeof value === "string"));
}

function normalizeDay(value: string | null | undefined) {
  const text = cleanText(value).toUpperCase();
  return WEEKDAY_ORDER.find((day) => day === text) ?? null;
}

function normalizeTime(value: string | null | undefined) {
  const text = cleanText(value);
  if (!text) {
    return "";
  }
  const match = text.match(/^([01]?\d|2[0-3]):([0-5]\d)$/);
  if (!match) {
    return text;
  }
  return `${match[1].padStart(2, "0")}:${match[2]}`;
}

function minutes(value: string) {
  const match = value.match(/^(\d{2}):(\d{2})$/);
  if (!match) {
    return -1;
  }
  return Number(match[1]) * 60 + Number(match[2]);
}

function normalizeWeeklyIntervals(value: WeeklyScheduleValue | null | undefined): WeeklyTimingInterval[] {
  const source = value?.intervals?.length ? value.intervals : value?.weekly?.length ? value.weekly.map((entry) => ({
    dayOfWeek: normalizeDay(entry.dayOfWeek ?? entry.day) ?? "MONDAY",
    startTime: normalizeTime(entry.start ?? entry.open),
    endTime: normalizeTime(entry.end ?? entry.close),
  })) : [];
  const rows: WeeklyTimingInterval[] = [];
  source.forEach((entry) => {
    const dayOfWeek = normalizeDay(entry.dayOfWeek);
    const startTime = normalizeTime(entry.startTime);
    const endTime = normalizeTime(entry.endTime);
    if (!dayOfWeek || !startTime || !endTime) {
      return;
    }
    const startMinutes = minutes(startTime);
    const endMinutes = minutes(endTime);
    if (startMinutes < 0 || endMinutes <= startMinutes) {
      return;
    }
    if (rows.some((current) => current.dayOfWeek === dayOfWeek && current.startTime === startTime && current.endTime === endTime)) {
      return;
    }
    rows.push({ dayOfWeek, startTime, endTime });
  });
  return rows.sort((left, right) => {
    const dayOrder = WEEKDAY_ORDER.indexOf(left.dayOfWeek) - WEEKDAY_ORDER.indexOf(right.dayOfWeek);
    if (dayOrder !== 0) {
      return dayOrder;
    }
    return minutes(left.startTime) - minutes(right.startTime);
  });
}

function dayLabel(day: string) {
  return day.charAt(0) + day.slice(1).toLowerCase();
}

function formatInterval(interval: WeeklyTimingInterval) {
  return `${interval.startTime}–${interval.endTime}`;
}

function groupWeeklyIntervals(value: WeeklyTimingInterval[]) {
  return WEEKDAY_ORDER.map((day) => ({
    dayOfWeek: day,
    sessions: value.filter((interval) => interval.dayOfWeek === day).sort((left, right) => minutes(left.startTime) - minutes(right.startTime)),
  }));
}

function normalizeFeeValue(value: ProviderFeeEditorValue | Record<string, unknown> | null | undefined): ProviderFeeEditorValue {
  function feeAmount(raw: unknown) {
    if (raw === null || raw === undefined) {
      return "";
    }
    if (typeof raw === "string") {
      return cleanText(raw);
    }
    return cleanText(String(raw));
  }

  if (!value) {
    return {
      currency: "INR",
      rows: [
        { key: "inClinic", label: "In-clinic consultation", amount: "", enabled: false, helperText: "Shown publicly when enabled." },
        { key: "video", label: "Video consultation", amount: "", enabled: false, helperText: "Shown publicly when enabled." },
        { key: "homeVisit", label: "Home visit", amount: "", enabled: false, helperText: "Shown publicly when enabled." },
        { key: "emergency", label: "Emergency consultation", amount: "", enabled: false, helperText: "Shown publicly when enabled." },
      ],
    };
  }
  if ("rows" in value && Array.isArray(value.rows)) {
    return {
      currency: cleanText((value as ProviderFeeEditorValue).currency) || "INR",
      rows: (value as ProviderFeeEditorValue).rows.map((row) => ({
        ...row,
        amount: feeAmount(row.amount),
        enabled: Boolean(row.enabled),
      })),
    };
  }
  const legacy = value as Record<string, unknown>;
  const currency = cleanText(legacy.currency) || "INR";
  const rows: ProviderFeeEditorRow[] = [
    { key: "inClinic", label: "In-clinic consultation", amount: feeAmount(legacy.inClinic), enabled: feeAmount(legacy.inClinic).length > 0, helperText: "Shown publicly when enabled." },
    { key: "video", label: "Video consultation", amount: feeAmount(legacy.video), enabled: feeAmount(legacy.video).length > 0, helperText: "Shown publicly when enabled." },
    { key: "homeVisit", label: "Home visit", amount: feeAmount(legacy.homeVisit), enabled: feeAmount(legacy.homeVisit).length > 0, helperText: "Shown publicly when enabled." },
    { key: "emergency", label: "Emergency consultation", amount: feeAmount(legacy.emergency), enabled: feeAmount(legacy.emergency).length > 0, helperText: "Shown publicly when enabled." },
  ];
  return {
    currency,
    rows,
  };
}

function updateTagValues(current: string[], nextValue: string) {
  const combined = [...current, nextValue];
  return normalizeCaseInsensitiveList(combined);
}

export function ProviderEditorSectionCard({ title, description, children, action }: ProviderEditorSectionCardProps) {
  return (
    <Card variant="outlined" className="provider-editor-section-card" sx={{ borderRadius: 4 }}>
      <CardContent sx={{ p: { xs: 2, sm: 3 } }}>
        <Stack spacing={2}>
          <Stack direction="row" justifyContent="space-between" spacing={2} alignItems="flex-start" flexWrap="wrap">
            <Box>
              <Typography variant="h5" sx={{ fontWeight: 900 }}>{title}</Typography>
              {description ? <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{description}</Typography> : null}
            </Box>
            {action ? <Box>{action}</Box> : null}
          </Stack>
          {children}
        </Stack>
      </CardContent>
    </Card>
  );
}

export function ProviderEditorFooter({ children }: { children: React.ReactNode }) {
  return (
    <Paper
      variant="outlined"
      className="provider-editor-footer"
      sx={{
        position: "sticky",
        bottom: 16,
        zIndex: 4,
        borderRadius: 4,
        px: 2,
        py: 1.5,
        backdropFilter: "blur(8px)",
        backgroundColor: "rgba(255,255,255,0.94)",
      }}
    >
      {children}
    </Paper>
  );
}

export function PublicAddressPreview({ address }: { address: PublicAddressView }) {
  return (
    <Stack spacing={0.75}>
      <Typography variant="body2" color="text.secondary">Compact location</Typography>
      <Typography variant="subtitle2">{address.compact || "Location not pinned."}</Typography>
      {address.lines.length ? (
        <Box sx={{ whiteSpace: "pre-line", color: "text.secondary", fontSize: "0.95rem" }}>
          {address.lines.join("\n")}
        </Box>
      ) : null}
    </Stack>
  );
}

export function ProviderTagListEditor({
  title,
  description,
  helperText,
  values,
  suggestions = [],
  allowCustomEntry = true,
  primaryValue,
  onPrimaryValueChange,
  onChange,
  emptyState,
  addLabel = "Add item",
  placeholder = "Type to add an item",
  selectedLabel = "Selected",
  maxSelectionCount = null,
}: ProviderTagListEditorProps) {
  const [draftValue, setDraftValue] = React.useState("");
  const selected = normalizeCaseInsensitiveList(values);
  const suggestionPool = stripDuplicateText(suggestions).filter((item) => !selected.some((current) => current.toLowerCase() === item.toLowerCase()));
  const visibleSuggestions = draftValue.trim()
    ? suggestionPool.filter((item) => item.toLowerCase().includes(draftValue.trim().toLowerCase())).slice(0, 8)
    : suggestionPool.slice(0, 8);

  function commitValue(nextValue: string) {
    const trimmed = cleanText(nextValue);
    if (!trimmed) {
      return;
    }
    if (!allowCustomEntry && !suggestions.some((item) => item.toLowerCase() === trimmed.toLowerCase())) {
      return;
    }
    const next = updateTagValues(selected, trimmed);
    if (maxSelectionCount != null && next.length > maxSelectionCount) {
      return;
    }
    onChange(next);
    setDraftValue("");
  }

  return (
    <Stack spacing={1.5}>
      <Stack spacing={0.5}>
        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>{title}</Typography>
        {description ? <Typography variant="body2" color="text.secondary">{description}</Typography> : null}
      </Stack>
      <Stack spacing={1} direction={{ xs: "column", sm: "row" }} alignItems={{ xs: "stretch", sm: "flex-start" }}>
        <TextField
          label={placeholder}
          value={draftValue}
          onChange={(event) => setDraftValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              commitValue(draftValue);
            }
          }}
          fullWidth
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">
                <SearchRoundedIcon fontSize="small" aria-hidden="true" />
              </InputAdornment>
            ),
          }}
        />
        <Button variant="contained" onClick={() => commitValue(draftValue)} startIcon={<AddRoundedIcon />}>
          {addLabel}
        </Button>
      </Stack>
      {visibleSuggestions.length ? (
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
          {visibleSuggestions.map((item) => (
            <Chip
              key={item}
              label={item}
              clickable
              variant="outlined"
              onClick={() => commitValue(item)}
              icon={<CheckRoundedIcon fontSize="small" aria-hidden="true" />}
            />
          ))}
        </Stack>
      ) : null}
      {selected.length ? (
        <Stack spacing={1}>
          <Typography variant="caption" color="text.secondary">{selectedLabel}</Typography>
          <Stack spacing={1}>
            {selected.map((item, index) => {
              const isPrimary = primaryValue != null && primaryValue.trim().toLowerCase() === item.toLowerCase();
              return (
                <Paper key={item} variant="outlined" sx={{ p: 1.25, borderRadius: 3 }}>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Chip label={item} color={isPrimary ? "primary" : "default"} variant={isPrimary ? "filled" : "outlined"} />
                    {onPrimaryValueChange ? (
                      <Button
                        size="small"
                        variant="text"
                        onClick={() => onPrimaryValueChange(item)}
                      >
                        {isPrimary ? "Primary speciality" : "Set as primary"}
                      </Button>
                    ) : null}
                    <Box sx={{ flex: 1 }} />
                    <IconButton
                      size="small"
                      aria-label={`Move ${item} up`}
                      disabled={index === 0}
                      onClick={() => {
                        if (index <= 0) {
                          return;
                        }
                        const next = [...selected];
                        [next[index - 1], next[index]] = [next[index], next[index - 1]];
                        onChange(next);
                      }}
                    >
                      <ArrowUpwardRoundedIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      aria-label={`Move ${item} down`}
                      disabled={index === selected.length - 1}
                      onClick={() => {
                        if (index >= selected.length - 1) {
                          return;
                        }
                        const next = [...selected];
                        [next[index + 1], next[index]] = [next[index], next[index + 1]];
                        onChange(next);
                      }}
                    >
                      <ArrowDownwardRoundedIcon fontSize="small" />
                    </IconButton>
                    <IconButton
                      size="small"
                      color="error"
                      aria-label={`Remove ${item}`}
                      onClick={() => {
                        const next = selected.filter((value) => value.toLowerCase() !== item.toLowerCase());
                        onChange(next);
                        if (primaryValue && primaryValue.trim().toLowerCase() === item.toLowerCase()) {
                          onPrimaryValueChange?.(next[0] ?? null);
                        }
                      }}
                    >
                      <DeleteOutlineRoundedIcon fontSize="small" />
                    </IconButton>
                  </Stack>
                </Paper>
              );
            })}
          </Stack>
        </Stack>
      ) : (
        <Typography variant="body2" color="text.secondary">{emptyState}</Typography>
      )}
      {helperText ? <FormHelperText>{helperText}</FormHelperText> : null}
    </Stack>
  );
}

export function ProviderFeeEditor({ value, onChange }: ProviderFeeEditorProps) {
  const normalized = normalizeFeeValue(value);
  const updateRow = (key: string, nextRow: Partial<ProviderFeeEditorRow>) => {
    onChange({
      ...normalized,
      rows: normalized.rows.map((row) => row.key === key ? { ...row, ...nextRow } : row),
    });
  };

  return (
    <Stack spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Consultation fees</Typography>
        <Typography variant="body2" color="text.secondary">Informational fees only. These values do not change billing configuration.</Typography>
      </Stack>
      <TextField
        label="Currency"
        value={normalized.currency}
        onChange={(event) => onChange({ ...normalized, currency: event.target.value.trim() || "INR" })}
        helperText="Currency is shown with the fee amounts."
        sx={{ maxWidth: 220 }}
      />
      <Stack spacing={1.5}>
        {normalized.rows.map((row) => (
          <Paper key={row.key} variant="outlined" sx={{ p: 1.5, borderRadius: 3 }}>
            <Stack spacing={1.5}>
              <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between" flexWrap="wrap">
                <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{row.label}</Typography>
                <FormControlLabel
                  control={<Checkbox checked={row.enabled} onChange={(event) => updateRow(row.key, { enabled: event.target.checked })} />}
                  label="Enabled"
                />
              </Stack>
              <TextField
                label={`${row.label} amount`}
                type="number"
                value={row.amount}
                onChange={(event) => updateRow(row.key, { amount: event.target.value })}
                disabled={!row.enabled}
                helperText={row.helperText ?? "Enter a non-negative amount."}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Typography component="span" sx={{ color: "text.secondary" }}>
                        {normalized.currency} ·
                      </Typography>
                    </InputAdornment>
                  ),
                }}
              />
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Stack>
  );
}

function buildDayMap(intervals: WeeklyTimingInterval[]) {
  const map = new Map<(typeof WEEKDAY_ORDER)[number], WeeklyTimingInterval[]>();
  WEEKDAY_ORDER.forEach((day) => map.set(day, []));
  for (const interval of intervals) {
    const day = normalizeDay(interval.dayOfWeek);
    if (!day) {
      continue;
    }
    const row = map.get(day) ?? [];
    row.push({
      dayOfWeek: day,
      startTime: normalizeTime(interval.startTime),
      endTime: normalizeTime(interval.endTime),
    });
    map.set(day, row);
  }
  for (const day of WEEKDAY_ORDER) {
    const rows = map.get(day) ?? [];
    rows.sort((left, right) => minutes(left.startTime) - minutes(right.startTime));
    map.set(day, rows.filter((item, index) => rows.findIndex((candidate) => candidate.startTime === item.startTime && candidate.endTime === item.endTime) === index));
  }
  return map;
}

function timingValidation(rows: WeeklyTimingInterval[]) {
  const grouped = buildDayMap(rows);
  const warnings = new Map<string, string>();
  grouped.forEach((items, day) => {
    for (let index = 0; index < items.length; index += 1) {
      const item = items[index];
      if (!item.startTime || !item.endTime) {
        warnings.set(day, "Please enter valid start and end times.");
        continue;
      }
      const start = minutes(item.startTime);
      const end = minutes(item.endTime);
      if (start < 0 || end <= start) {
        warnings.set(day, "Start time must be earlier than end time.");
        continue;
      }
      const next = items[index + 1];
      if (next) {
        const nextStart = minutes(next.startTime);
        if (nextStart < end) {
          warnings.set(day, "Sessions for this day must not overlap.");
        }
      }
    }
  });
  return warnings;
}

export function normalizeWeeklyScheduleContent(value: WeeklyScheduleValue | null | undefined) {
  return {
    timezone: cleanText(value?.timezone) || "Asia/Kolkata",
    intervals: normalizeWeeklyIntervals(value),
  };
}

export function ProviderWeeklyScheduleEditor({
  value,
  onChange,
}: {
  value: WeeklyScheduleValue | null | undefined;
  onChange: (nextValue: WeeklyScheduleValue) => void;
}) {
  const normalized = React.useMemo(() => normalizeWeeklyScheduleContent(value), [value]);
  const grouped = React.useMemo(() => buildDayMap(normalized.intervals), [normalized.intervals]);
  const warnings = React.useMemo(() => timingValidation(normalized.intervals), [normalized.intervals]);
  const [copyTargets, setCopyTargets] = React.useState<string[]>([]);

  function updateIntervals(nextIntervals: WeeklyTimingInterval[]) {
    onChange({ timezone: normalized.timezone, intervals: nextIntervals });
  }

  function updateDay(day: (typeof WEEKDAY_ORDER)[number], updater: (current: WeeklyTimingInterval[]) => WeeklyTimingInterval[]) {
    const next = [...normalized.intervals.filter((interval) => interval.dayOfWeek !== day), ...updater(grouped.get(day) ?? [])];
    updateIntervals(next);
  }

  function addSession(day: (typeof WEEKDAY_ORDER)[number]) {
    const current = grouped.get(day) ?? [];
    const last = current[current.length - 1];
    const startTime = last ? last.endTime : "09:00";
    const lastHour = last ? Number(last.endTime.slice(0, 2)) : 13;
    const endTime = last
      ? `${String(Math.min(23, lastHour + 1)).padStart(2, "0")}:${last.endTime.slice(3)}`
      : "13:00";
    updateDay(day, (sessions) => [...sessions, { dayOfWeek: day, startTime, endTime }]);
  }

  function replaceDay(day: (typeof WEEKDAY_ORDER)[number], nextSessions: WeeklyTimingInterval[]) {
    const preserved = normalized.intervals.filter((interval) => interval.dayOfWeek !== day);
    updateIntervals([...preserved, ...nextSessions.map((session) => ({ ...session, dayOfWeek: day }))]);
  }

  return (
    <Stack spacing={2}>
      <Stack spacing={0.5}>
        <Typography variant="subtitle1" sx={{ fontWeight: 800 }}>Weekly schedule</Typography>
        <Typography variant="body2" color="text.secondary">Visual weekly schedule editor. All times are local to the selected timezone.</Typography>
      </Stack>
      <TextField
        label="Timezone"
        value={normalized.timezone}
        onChange={(event) => onChange({ timezone: event.target.value, intervals: normalized.intervals })}
        helperText="Asia/Kolkata is the default for Green Valley."
        sx={{ maxWidth: 280 }}
      />
      <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 3 }}>
        <Stack spacing={1.25}>
          <Typography variant="body2" color="text.secondary">Copy Monday hours to</Typography>
          <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
            {WEEKDAY_ORDER.filter((day) => day !== "MONDAY").map((day) => (
              <FormControlLabel
                key={day}
                control={<Checkbox checked={copyTargets.includes(day)} onChange={(event) => {
                  setCopyTargets((current) => event.target.checked ? [...current, day] : current.filter((item) => item !== day));
                }} />}
                label={dayLabel(day)}
              />
            ))}
          </Stack>
          <Button
            variant="outlined"
            onClick={() => {
              const monday = grouped.get("MONDAY") ?? [];
              if (!monday.length || !copyTargets.length) {
                return;
              }
              const nextIntervals = normalized.intervals.filter((interval) => !copyTargets.includes(interval.dayOfWeek));
              for (const day of copyTargets) {
                nextIntervals.push(...monday.map((session) => ({ ...session, dayOfWeek: day as (typeof WEEKDAY_ORDER)[number] })));
              }
              updateIntervals(nextIntervals);
            }}
          >
            Copy Monday hours
          </Button>
        </Stack>
      </Paper>
      <Stack spacing={1.5}>
        {WEEKDAY_ORDER.map((day) => {
          const sessions = grouped.get(day) ?? [];
          const dayWarning = warnings.get(day) ?? null;
          return (
            <Paper key={day} variant="outlined" sx={{ p: 1.5, borderRadius: 3 }}>
              <Stack spacing={1.25}>
                <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1} flexWrap="wrap">
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>{dayLabel(day)}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap">
                    <Button size="small" variant="outlined" onClick={() => addSession(day)}>+ Add session</Button>
                    <Button
                      size="small"
                      variant="text"
                      color="inherit"
                      onClick={() => replaceDay(day, [])}
                      disabled={!sessions.length}
                    >
                      Mark closed
                    </Button>
                  </Stack>
                </Stack>
                {sessions.length ? (
                  <Stack spacing={1}>
                    {sessions.map((session, index) => (
                      <Stack key={`${day}-${index}`} direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                        <TextField
                          label="Start"
                          type="time"
                          value={session.startTime}
                          onChange={(event) => {
                            const next = sessions.map((item, itemIndex) => itemIndex === index ? { ...item, startTime: event.target.value } : item);
                            replaceDay(day, next);
                          }}
                          InputLabelProps={{ shrink: true }}
                        />
                        <Typography variant="body2" color="text.secondary">to</Typography>
                        <TextField
                          label="End"
                          type="time"
                          value={session.endTime}
                          onChange={(event) => {
                            const next = sessions.map((item, itemIndex) => itemIndex === index ? { ...item, endTime: event.target.value } : item);
                            replaceDay(day, next);
                          }}
                          InputLabelProps={{ shrink: true }}
                        />
                        <IconButton
                          aria-label={`Remove ${dayLabel(day)} session ${index + 1}`}
                          color="error"
                          onClick={() => replaceDay(day, sessions.filter((_, itemIndex) => itemIndex !== index))}
                        >
                          <DeleteOutlineRoundedIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                    ))}
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">Closed</Typography>
                )}
                {dayWarning ? <Typography variant="caption" color="warning.main">{dayWarning}</Typography> : null}
              </Stack>
            </Paper>
          );
        })}
      </Stack>
    </Stack>
  );
}

export {
  normalizeCaseInsensitiveList as normalizeEditorList,
  normalizeWeeklyIntervals,
  normalizeFeeValue,
};
