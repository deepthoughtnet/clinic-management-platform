import type { CarePilotAudienceType, CarePilotCampaignType, CarePilotTriggerType } from "../../../api/clinicApi";

export type PresetImplementationStatus = "READY" | "FOUNDATION_ONLY" | "FUTURE";

export type CampaignPreset = {
  presetKey: string;
  displayName: string;
  description: string;
  campaignType: CarePilotCampaignType;
  triggerType: CarePilotTriggerType;
  audienceType: CarePilotAudienceType;
  defaultChannel: "EMAIL";
  triggerLabel: string;
  defaultTriggerConfig: Record<string, string | number | boolean>;
  defaultTemplateSubject: string;
  defaultTemplateBody: string;
  supportedPlaceholders: string[];
  implementationStatus: PresetImplementationStatus;
};

export const CAMPAIGN_PRESETS: CampaignPreset[] = [
  {
    presetKey: "appointment_24h",
    displayName: "Appointment Reminder - 24 Hours Before",
    description: "Reminds patients one day before scheduled appointments.",
    campaignType: "APPOINTMENT_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "24h before appointment",
    defaultTriggerConfig: { reminderOffset: "PT24H", appointmentStatuses: "BOOKED" },
    defaultTemplateSubject: "Appointment reminder for {{appointmentDate}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nThis is a reminder for your appointment with {{doctorName}} at {{clinicName}} on {{appointmentDate}} at {{appointmentTime}}.\nPlease contact us if you need to reschedule.\nRegards,\n{{clinicName}}",
    supportedPlaceholders: ["{{patientName}}", "{{doctorName}}", "{{clinicName}}", "{{appointmentDate}}", "{{appointmentTime}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "appointment_2h",
    displayName: "Appointment Reminder - 2 Hours Before",
    description: "Sends short-notice reminders before appointment time.",
    campaignType: "APPOINTMENT_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "2h before appointment",
    defaultTriggerConfig: { reminderOffset: "PT2H", appointmentStatuses: "BOOKED" },
    defaultTemplateSubject: "Upcoming appointment at {{clinicName}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nYour appointment with {{doctorName}} starts at {{appointmentTime}} on {{appointmentDate}}.\nIf you need to reschedule, call {{clinicPhone}}.\nRegards,\n{{clinicName}}",
    supportedPlaceholders: ["{{patientName}}", "{{doctorName}}", "{{clinicName}}", "{{clinicPhone}}", "{{appointmentDate}}", "{{appointmentTime}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "missed_appointment_follow_up",
    displayName: "Missed Appointment Follow-up",
    description: "Reconnects with no-show patients and prompts rebooking.",
    campaignType: "MISSED_APPOINTMENT_FOLLOW_UP",
    triggerType: "EVENT_BASED",
    audienceType: "RULE_BASED",
    defaultChannel: "EMAIL",
    triggerLabel: "2h/24h after missed appointment",
    defaultTriggerConfig: { event: "AFTER_MISSED_APPOINTMENT", reminderOffset: "PT2H" },
    defaultTemplateSubject: "We missed you at {{clinicName}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nYou missed your appointment with {{doctorName}} on {{appointmentDate}} at {{appointmentTime}}.\nPlease contact {{clinicName}} to reschedule.",
    supportedPlaceholders: ["{{patientName}}", "{{doctorName}}", "{{appointmentDate}}", "{{appointmentTime}}", "{{clinicName}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "follow_up_visit",
    displayName: "Follow-up Visit Reminder",
    description: "Reminds patients before follow-up dates.",
    campaignType: "FOLLOW_UP_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "24h before follow-up date",
    defaultTriggerConfig: { reminderOffset: "PT24H" },
    defaultTemplateSubject: "Follow-up reminder from {{clinicName}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nThis is a reminder for your follow-up visit with {{doctorName}} on {{followUpDate}}.\nRegards,\n{{clinicName}}",
    supportedPlaceholders: ["{{patientName}}", "{{doctorName}}", "{{followUpDate}}", "{{clinicName}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "pending_bill",
    displayName: "Pending Bill Reminder",
    description: "Sends reminder for unpaid overdue bills.",
    campaignType: "BILLING_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "after bill overdue",
    defaultTriggerConfig: { overdueDays: 3 },
    defaultTemplateSubject: "Pending bill reminder from {{clinicName}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nYour bill {{billNumber}} has an outstanding due amount of {{amountDue}}.\nPlease complete payment at your earliest convenience.\nRegards,\n{{clinicName}}",
    supportedPlaceholders: ["{{patientName}}", "{{billNumber}}", "{{amountDue}}", "{{clinicName}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "refill",
    displayName: "Refill Reminder",
    description: "Reminds patients when refill is expected.",
    campaignType: "REFILL_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "before refill due",
    defaultTriggerConfig: { estimatedRefillDays: 30 },
    defaultTemplateSubject: "Refill reminder for {{patientName}}",
    defaultTemplateBody:
      "Dear {{patientName}},\nYour medication refill is due around {{refillDueDate}} for {{medicineName}}.\nPlease contact {{clinicName}} to continue treatment.",
    supportedPlaceholders: ["{{patientName}}", "{{medicineName}}", "{{refillDueDate}}", "{{clinicName}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "vaccination",
    displayName: "Vaccination Reminder",
    description: "Prompts patients before due vaccination date.",
    campaignType: "VACCINATION_REMINDER",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "1 day before vaccination due",
    defaultTriggerConfig: { reminderOffset: 1, includeOverdue: true },
    defaultTemplateSubject: "Vaccination due reminder",
    defaultTemplateBody:
      "Dear {{patientName}},\n{{vaccineName}} is {{vaccinationStatus}} and due on {{vaccinationDueDate}}.\nPlease book your visit with {{clinicName}}.",
    supportedPlaceholders: ["{{patientName}}", "{{clinicName}}", "{{vaccineName}}", "{{vaccinationDueDate}}", "{{vaccinationStatus}}", "{{doctorName}}"],
    implementationStatus: "READY",
  },
  {
    presetKey: "birthday_wellness",
    displayName: "Birthday / Wellness Message",
    description: "Sends wellness greetings on patient birthdays.",
    campaignType: "WELLNESS_MESSAGE",
    triggerType: "SCHEDULED",
    audienceType: "ALL_PATIENTS",
    defaultChannel: "EMAIL",
    triggerLabel: "on patient birthday",
    defaultTriggerConfig: { event: "ON_PATIENT_BIRTHDAY", daysBeforeBirthday: 0, sendTimeLocal: "09:00" },
    defaultTemplateSubject: "Warm wishes from {{clinicName}}",
    defaultTemplateBody: "Dear {{patientName}},\nWishing you a healthy and happy birthday on {{birthdayDate}} from all of us at {{clinicName}}.",
    supportedPlaceholders: ["{{patientName}}", "{{clinicName}}", "{{birthdayDate}}", "{{age}}", "{{clinicPhone}}"],
    implementationStatus: "READY",
  },
];

export function presetStatusLabel(status: PresetImplementationStatus): string {
  if (status === "READY") return "Ready";
  if (status === "FOUNDATION_ONLY") return "Foundation Only";
  return "Future";
}
