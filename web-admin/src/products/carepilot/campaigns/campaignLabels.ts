import type {
  CarePilotAudienceType,
  CarePilotCampaignStatus,
  CarePilotCampaignType,
  CarePilotChannelType,
  CarePilotTriggerType,
} from "../../../api/clinicApi";

const CAMPAIGN_TYPE_LABELS: Record<CarePilotCampaignType, string> = {
  APPOINTMENT_REMINDER: "Appointment Reminder",
  MISSED_APPOINTMENT_FOLLOW_UP: "Missed Appointment Follow-up",
  FOLLOW_UP_REMINDER: "Follow-up Reminder",
  REFILL_REMINDER: "Refill Reminder",
  VACCINATION_REMINDER: "Vaccination Reminder",
  BILLING_REMINDER: "Billing Reminder",
  WELLNESS_MESSAGE: "Wellness Message",
  LEAD_FOLLOW_UP_REMINDER: "Lead Follow-up Reminder",
  WEBINAR_CONFIRMATION: "Webinar Confirmation",
  WEBINAR_REMINDER: "Webinar Reminder",
  WEBINAR_FOLLOW_UP: "Webinar Follow-up",
  CUSTOM: "Custom",
};

const TRIGGER_TYPE_LABELS: Record<CarePilotTriggerType, string> = {
  MANUAL: "Manual",
  SCHEDULED: "Scheduled",
  EVENT_BASED: "Event Based",
};

const AUDIENCE_TYPE_LABELS: Record<CarePilotAudienceType, string> = {
  ALL_PATIENTS: "All Patients",
  SPECIFIC_PATIENTS: "Specific Patients",
  TAG_BASED: "Tag Based",
  RULE_BASED: "Rule Based",
  HIGH_RISK_PATIENTS: "High Risk Patients",
  INACTIVE_PATIENTS: "Inactive Patients",
  REFILL_RISK_PATIENTS: "Refill Risk Patients",
  FOLLOW_UP_OVERDUE_PATIENTS: "Follow-up Overdue Patients",
};

const CHANNEL_TYPE_LABELS: Record<CarePilotChannelType, string> = {
  EMAIL: "Email",
  SMS: "SMS",
  WHATSAPP: "WhatsApp",
  IN_APP: "In App",
  APP_NOTIFICATION: "App Notification",
};

const STATUS_LABELS: Record<CarePilotCampaignStatus, string> = {
  DRAFT: "Draft",
  PENDING_APPROVAL: "Pending Approval",
  CHANGES_REQUESTED: "Changes Requested",
  APPROVED: "Approved",
  ACTIVE: "Active",
  PAUSED: "Paused",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export function campaignTypeLabel(value: CarePilotCampaignType) {
  return CAMPAIGN_TYPE_LABELS[value] || value;
}

export function triggerTypeLabel(value: CarePilotTriggerType) {
  return TRIGGER_TYPE_LABELS[value] || value;
}

export function audienceTypeLabel(value: CarePilotAudienceType) {
  return AUDIENCE_TYPE_LABELS[value] || value;
}

export function channelTypeLabel(value: CarePilotChannelType) {
  return CHANNEL_TYPE_LABELS[value] || value;
}

export function campaignStatusLabel(value: CarePilotCampaignStatus) {
  return STATUS_LABELS[value] || value;
}

export function campaignEventLabel(value: string) {
  const map: Record<string, string> = {
    CREATED: "Created",
    UPDATED: "Updated",
    SUBMITTED: "Submitted for Approval",
    RESUBMITTED_FOR_APPROVAL: "Resubmitted for Approval",
    WITHDRAWN: "Withdrawn",
    APPROVED: "Approved",
    CHANGES_REQUESTED: "Changes Requested",
    APPROVAL_INVALIDATED: "Approval Invalidated",
    ACTIVATED: "Activated",
    RESUMED: "Resumed",
    PAUSED: "Paused",
    CANCELLED: "Cancelled",
    COMPLETED: "Completed",
  };
  return map[value] || value;
}
