function notificationEventType(notification) {
  return (notification.eventType ?? "").toUpperCase();
}

function isAttentionNotification(notification) {
  const eventType = notificationEventType(notification);
  return (
    eventType.includes("ACTION_REQUIRED") ||
    eventType.includes("ATTENTION_REQUIRED") ||
    eventType.includes("APPOINTMENT_REMINDER") ||
    eventType.includes("APPOINTMENT_SOON") ||
    eventType.includes("APPOINTMENT_DUE") ||
    eventType.includes("BILL_DUE") ||
    eventType.includes("PAYMENT_DUE") ||
    eventType.includes("VACCINATION_DUE") ||
    eventType.includes("VACCINATION_OVERDUE") ||
    eventType.includes("LAB_REVIEW_REQUIRED") ||
    eventType.includes("REPORT_REVIEW_REQUIRED") ||
    eventType.includes("PRESCRIPTION_ACKNOWLEDGEMENT_REQUIRED") ||
    eventType.includes("FOLLOW_UP_REQUIRED") ||
    eventType.includes("CRITICAL")
  );
}

function isRecentActivityNotification(notification) {
  if (isAttentionNotification(notification)) {
    return false;
  }
  const eventType = notificationEventType(notification);
  return (
    eventType.includes("APPOINTMENT_BOOKED") ||
    eventType.includes("APPOINTMENT_CONFIRMED") ||
    eventType.includes("APPOINTMENT_RESCHEDULED") ||
    eventType.includes("BILL_PAID") ||
    eventType.includes("RECEIPT_") ||
    eventType.includes("LAB_REPORT_READY") ||
    eventType.includes("REPORT_PUBLISHED") ||
    eventType.includes("PRESCRIPTION_SHARED") ||
    eventType.includes("PRESCRIPTION_") ||
    eventType.includes("VACCINATION_RECORDED") ||
    eventType.includes("NOTIFICATION")
  );
}

function dashboardNotificationTitle(notification) {
  const eventType = notificationEventType(notification);
  if (eventType.includes("APPOINTMENT_BOOKED")) return "Appointment booked";
  if (eventType.includes("APPOINTMENT_CONFIRMED")) return "Appointment confirmed";
  if (eventType.includes("APPOINTMENT_RESCHEDULED")) return "Appointment rescheduled";
  if (eventType.includes("BILL_PAID") || eventType.includes("RECEIPT_")) return "Bill paid";
  if (eventType.includes("LAB_REPORT_READY") || eventType.includes("REPORT_PUBLISHED")) return "Report published";
  if (eventType.includes("PRESCRIPTION_SHARED")) return "Prescription shared";
  if (eventType.includes("PRESCRIPTION_")) return "Prescription update";
  if (eventType.includes("VACCINATION_RECORDED")) return "Vaccination recorded";
  if (eventType.includes("ACTION_REQUIRED") || eventType.includes("ATTENTION_REQUIRED")) return "Action required";
  if (eventType.includes("PAYMENT_DUE") || eventType.includes("BILL_DUE")) return "Payment due";
  if (eventType.includes("APPOINTMENT_REMINDER") || eventType.includes("APPOINTMENT_SOON")) return "Appointment soon";
  return notification.subject?.trim() || "Notification";
}

function dashboardNotificationDetail(notification) {
  const detail = notification.subject?.trim() || notification.message?.trim() || "";
  return detail.replace(/\s+/g, " ").trim();
}

function notificationSourceKey(notification) {
  const sourceType = notification.sourceType?.trim().toUpperCase() ?? "";
  const sourceId = notification.sourceId?.trim() ?? "";
  if (sourceType && sourceId) {
    return `${sourceType}:${sourceId}`;
  }
  return null;
}

function dashboardActivityTitleFromGroup(notifications) {
  const eventTypes = notifications.map(notificationEventType);
  if (eventTypes.some((eventType) => eventType.includes("APPOINTMENT_RESCHEDULED"))) {
    return "Appointment rescheduled";
  }
  if (eventTypes.some((eventType) => eventType.includes("APPOINTMENT_BOOKED"))) {
    return "Appointment booked";
  }
  if (eventTypes.some((eventType) => eventType.includes("APPOINTMENT_CONFIRMED"))) {
    return "Appointment confirmed";
  }
  if (eventTypes.some((eventType) => eventType.includes("BILL_PAID") || eventType.includes("RECEIPT_"))) {
    return "Bill paid";
  }
  if (eventTypes.some((eventType) => eventType.includes("LAB_REPORT_READY") || eventType.includes("REPORT_PUBLISHED"))) {
    return "Report published";
  }
  if (eventTypes.some((eventType) => eventType.includes("PRESCRIPTION_SHARED"))) {
    return "Prescription shared";
  }
  if (eventTypes.some((eventType) => eventType.includes("PRESCRIPTION_"))) {
    return "Prescription update";
  }
  if (eventTypes.some((eventType) => eventType.includes("VACCINATION_RECORDED"))) {
    return "Vaccination recorded";
  }
  return dashboardNotificationTitle(notifications[0]);
}

export function buildRecentDashboardActivityItems(notifications, limit = 3) {
  const seen = new Map();

  [...notifications]
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
    .forEach((notification) => {
      if (!isRecentActivityNotification(notification)) {
        return;
      }
      const sourceKey = notificationSourceKey(notification);
      if (!sourceKey) {
        seen.set(notification.id, [notification]);
        return;
      }
      if (seen.has(sourceKey)) {
        seen.get(sourceKey).push(notification);
        return;
      }
      seen.set(sourceKey, [notification]);
    });

  const items = [];
  seen.forEach((group, key) => {
    const latestNotification = group[0];
    items.push({
      id: latestNotification.id,
      title: dashboardActivityTitleFromGroup(group),
      detail: dashboardNotificationDetail(latestNotification),
      createdAt: latestNotification.createdAt,
      sourceKey: key.includes(":") ? key : null,
    });
  });

  return items.slice(0, limit);
}

export {
  dashboardNotificationDetail,
  dashboardNotificationTitle,
  dashboardActivityTitleFromGroup,
  isAttentionNotification,
  isRecentActivityNotification,
  notificationSourceKey,
};
