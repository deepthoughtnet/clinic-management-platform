import type { MenuProps } from "@mui/material";
import type {
  CarePilotLeadActivity,
  CarePilotLeadPriority,
  CarePilotLeadSource,
  CarePilotLeadStatus,
} from "../../../api/clinicApi";
import { formatCarePilotDateTime } from "./carepilotFormatting";

export const LEAD_SOURCE_OPTIONS: Array<{ value: CarePilotLeadSource; label: string }> = [
  { value: "WEBSITE", label: "Website" },
  { value: "WEBINAR", label: "Webinar" },
  { value: "WALK_IN", label: "Walk-in" },
  { value: "PHONE_CALL", label: "Phone Call" },
  { value: "WHATSAPP", label: "WhatsApp" },
  { value: "FACEBOOK", label: "Facebook" },
  { value: "GOOGLE_ADS", label: "Google Ads" },
  { value: "REFERRAL", label: "Referral" },
  { value: "CAMPAIGN", label: "Campaign" },
  { value: "MANUAL", label: "Manual" },
  { value: "AI_RECEPTIONIST", label: "AI Receptionist" },
  { value: "OTHER", label: "Other" },
];

export const LEAD_STATUS_OPTIONS: Array<{ value: CarePilotLeadStatus; label: string }> = [
  { value: "NEW", label: "New" },
  { value: "CONTACTED", label: "Contacted" },
  { value: "QUALIFIED", label: "Qualified" },
  { value: "FOLLOW_UP_REQUIRED", label: "Follow-up Required" },
  { value: "APPOINTMENT_BOOKED", label: "Appointment Booked" },
  { value: "CONVERTED", label: "Converted" },
  { value: "LOST", label: "Lost" },
  { value: "SPAM", label: "Spam" },
];

export const LEAD_PRIORITY_OPTIONS: Array<{ value: CarePilotLeadPriority; label: string }> = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
];

export const LEAD_SELECT_MENU_PROPS: Partial<MenuProps> = {
  PaperProps: {
    sx: {
      maxHeight: 304,
      maxWidth: "calc(100vw - 32px)",
      overflowX: "hidden",
      "& .MuiMenuItem-root": {
        minWidth: 0,
        whiteSpace: "normal",
        wordBreak: "break-word",
      },
    },
  },
  MenuListProps: {
    dense: true,
  },
};

const ACTIVITY_LABELS: Record<string, string> = {
  CREATED: "Lead Created",
  UPDATED: "Lead Updated",
  STATUS_CHANGED: "Status Changed",
  NOTE_ADDED: "Note Added",
  FOLLOW_UP_SCHEDULED: "Follow-up Scheduled",
  FOLLOW_UP_COMPLETED: "Follow-up Completed",
  CONVERTED_TO_PATIENT: "Lead Converted",
  APPOINTMENT_BOOKED: "Appointment Booked",
  CAMPAIGN_LINKED: "Campaign Linked",
  LOST: "Lead Marked Lost",
  SPAM_MARKED: "Lead Marked Spam",
};

export function leadSourceLabel(value: CarePilotLeadSource | string | null | undefined) {
  if (!value) return "-";
  const match = LEAD_SOURCE_OPTIONS.find((option) => option.value === value);
  return match?.label || humanizeLeadCode(value);
}

export function leadStatusLabel(value: CarePilotLeadStatus | string | null | undefined) {
  if (!value) return "-";
  const match = LEAD_STATUS_OPTIONS.find((option) => option.value === value);
  return match?.label || humanizeLeadCode(value);
}

export function leadPriorityLabel(value: CarePilotLeadPriority | string | null | undefined) {
  if (!value) return "-";
  const match = LEAD_PRIORITY_OPTIONS.find((option) => option.value === value);
  return match?.label || humanizeLeadCode(value);
}

export function leadActivityLabel(value: string | null | undefined) {
  if (!value) return "-";
  return ACTIVITY_LABELS[value] || humanizeLeadCode(value);
}

export function formatLeadTimelineDescription(
  activity: Pick<CarePilotLeadActivity, "activityType" | "description">,
  clinicTimeZone?: string | null,
) {
  const type = activity.activityType;
  const description = activity.description || "";
  if (!type) {
    return description;
  }

  if (type === "CREATED") {
    const source = description.replace(/^Source:\s*/i, "").trim();
    return `Source: ${leadSourceLabel(source)}`;
  }

  if (type === "STATUS_CHANGED" || type === "LOST" || type === "SPAM_MARKED") {
    const [oldStatus, newStatus] = description.split("->").map((part) => part.trim());
    if (oldStatus && newStatus) {
      return `Status: ${leadStatusLabel(oldStatus)} → ${leadStatusLabel(newStatus)}`;
    }
    return description ? `Status: ${leadStatusLabel(description)}` : "";
  }

  if (type === "FOLLOW_UP_SCHEDULED") {
    const rawTimestamp = description.replace(/^Next follow-up at\s*/i, "").trim();
    const formatted = formatCarePilotDateTime(rawTimestamp || null, clinicTimeZone);
    return formatted !== "-" ? `Next follow-up: ${formatted}` : "Follow-up scheduled";
  }

  if (type === "FOLLOW_UP_COMPLETED") {
    return "Follow-up completed";
  }

  if (type === "NOTE_ADDED") {
    return description ? `Note: ${description}` : "Note added";
  }

  return description;
}

function humanizeLeadCode(value: string) {
  return value
    .replace(/[-_]/g, " ")
    .toLowerCase()
    .replace(/(^|\s)\w/g, (match) => match.toUpperCase());
}
