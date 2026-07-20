package com.deepthoughtnet.clinic.carepilot.lead.model;

import com.deepthoughtnet.clinic.carepilot.lead.activity.model.LeadActivityType;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Shared business-facing labels for lead lifecycle values. */
public final class LeadPresentationLabels {
    private static final Map<LeadSource, String> SOURCE_LABELS = Map.ofEntries(
            Map.entry(LeadSource.WEBSITE, "Website"),
            Map.entry(LeadSource.WEBINAR, "Webinar"),
            Map.entry(LeadSource.WALK_IN, "Walk-in"),
            Map.entry(LeadSource.PHONE_CALL, "Phone Call"),
            Map.entry(LeadSource.WHATSAPP, "WhatsApp"),
            Map.entry(LeadSource.FACEBOOK, "Facebook"),
            Map.entry(LeadSource.GOOGLE_ADS, "Google Ads"),
            Map.entry(LeadSource.REFERRAL, "Referral"),
            Map.entry(LeadSource.CAMPAIGN, "Campaign"),
            Map.entry(LeadSource.MANUAL, "Manual"),
            Map.entry(LeadSource.AI_RECEPTIONIST, "AI Receptionist"),
            Map.entry(LeadSource.OTHER, "Other")
    );

    private static final Map<LeadStatus, String> STATUS_LABELS = Map.of(
            LeadStatus.NEW, "New",
            LeadStatus.CONTACTED, "Contacted",
            LeadStatus.QUALIFIED, "Qualified",
            LeadStatus.FOLLOW_UP_REQUIRED, "Follow-up Required",
            LeadStatus.APPOINTMENT_BOOKED, "Appointment Booked",
            LeadStatus.CONVERTED, "Converted",
            LeadStatus.LOST, "Lost",
            LeadStatus.SPAM, "Spam"
    );

    private static final Map<LeadPriority, String> PRIORITY_LABELS = Map.of(
            LeadPriority.LOW, "Low",
            LeadPriority.MEDIUM, "Medium",
            LeadPriority.HIGH, "High"
    );

    private static final Map<LeadActivityType, String> ACTIVITY_LABELS = Map.ofEntries(
            Map.entry(LeadActivityType.CREATED, "Lead Created"),
            Map.entry(LeadActivityType.UPDATED, "Lead Updated"),
            Map.entry(LeadActivityType.STATUS_CHANGED, "Status Changed"),
            Map.entry(LeadActivityType.NOTE_ADDED, "Note Added"),
            Map.entry(LeadActivityType.FOLLOW_UP_SCHEDULED, "Follow-up Scheduled"),
            Map.entry(LeadActivityType.FOLLOW_UP_COMPLETED, "Follow-up Completed"),
            Map.entry(LeadActivityType.CONVERTED_TO_PATIENT, "Lead Converted"),
            Map.entry(LeadActivityType.APPOINTMENT_BOOKED, "Appointment Booked"),
            Map.entry(LeadActivityType.CAMPAIGN_LINKED, "Campaign Linked"),
            Map.entry(LeadActivityType.LOST, "Lead Marked Lost"),
            Map.entry(LeadActivityType.SPAM_MARKED, "Lead Marked Spam")
    );

    private LeadPresentationLabels() {}

    public static String sourceLabel(LeadSource value) {
        return value == null ? "-" : SOURCE_LABELS.getOrDefault(value, humanizeEnum(value.name()));
    }

    public static String statusLabel(LeadStatus value) {
        return value == null ? "-" : STATUS_LABELS.getOrDefault(value, humanizeEnum(value.name()));
    }

    public static String priorityLabel(LeadPriority value) {
        return value == null ? "-" : PRIORITY_LABELS.getOrDefault(value, humanizeEnum(value.name()));
    }

    public static String activityLabel(LeadActivityType value) {
        return value == null ? "-" : ACTIVITY_LABELS.getOrDefault(value, humanizeEnum(value.name()));
    }

    public static String statusTransitionLabel(LeadStatus oldStatus, LeadStatus newStatus) {
        return statusLabel(oldStatus) + " -> " + statusLabel(newStatus);
    }

    public static String formatDateTime(OffsetDateTime value, ZoneId zoneId) {
        if (value == null) {
            return "";
        }
        ZoneId safeZone = zoneId == null ? ZoneId.of("UTC") : zoneId;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH).withZone(safeZone);
        String formatted = formatter.format(value.atZoneSameInstant(safeZone));
        return formatted + " " + formatZoneLabel(safeZone, value);
    }

    private static String humanizeEnum(String value) {
        StringBuilder builder = new StringBuilder(value.replace('_', ' ').toLowerCase());
        boolean capitalizeNext = true;
        for (int i = 0; i < builder.length(); i++) {
            char current = builder.charAt(i);
            if (Character.isWhitespace(current)) {
                capitalizeNext = true;
                continue;
            }
            if (capitalizeNext) {
                builder.setCharAt(i, Character.toUpperCase(current));
                capitalizeNext = false;
            }
        }
        return builder.toString();
    }

    private static String formatZoneLabel(ZoneId zoneId, OffsetDateTime value) {
        if ("Asia/Kolkata".equals(zoneId.getId())) {
            return "IST (UTC+05:30)";
        }
        try {
            String shortName = DateTimeFormatter.ofPattern("z", Locale.ENGLISH).withZone(zoneId).format(value.atZoneSameInstant(zoneId));
            if (shortName == null || shortName.isBlank() || shortName.startsWith("GMT") || shortName.startsWith("UTC")) {
                return zoneId.getId() + " (" + offsetLabel(zoneId, value) + ")";
            }
            return shortName + " (" + offsetLabel(zoneId, value) + ")";
        } catch (Exception ignored) {
            return zoneId.getId() + " (" + offsetLabel(zoneId, value) + ")";
        }
    }

    private static String offsetLabel(ZoneId zoneId, OffsetDateTime value) {
        try {
            java.time.ZoneOffset offset = zoneId.getRules().getOffset(value.toInstant());
            int totalSeconds = offset.getTotalSeconds();
            int absoluteSeconds = Math.abs(totalSeconds);
            int hours = absoluteSeconds / 3600;
            int minutes = (absoluteSeconds % 3600) / 60;
            return String.format(Locale.ENGLISH, "UTC%s%02d:%02d", totalSeconds >= 0 ? "+" : "-", hours, minutes);
        } catch (Exception ignored) {
            return "UTC+00:00";
        }
    }
}
