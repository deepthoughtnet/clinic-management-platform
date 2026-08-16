import { CheckCircleOutlineRounded, CallOutlined, HelpOutlineOutlined } from "@mui/icons-material";
import type { ReactNode } from "react";

export type BookingMode = "ONLINE_BOOKING" | "CALL_TO_BOOK" | "REQUEST_APPOINTMENT" | "NOT_AVAILABLE" | null | undefined;

export function normalizeBookingMode(mode: string | null | undefined): BookingMode {
  switch (mode) {
    case "ONLINE_BOOKING":
    case "CALL_TO_BOOK":
    case "REQUEST_APPOINTMENT":
    case "NOT_AVAILABLE":
      return mode;
    default:
      return null;
  }
}

export function resolveDoctorBookingMode(
  mode: string | null | undefined,
  canBookOnline: boolean | null | undefined,
  fallbackPhone?: string | null,
): BookingMode {
  const normalized = normalizeBookingMode(mode);
  if (canBookOnline) {
    return normalized ?? "ONLINE_BOOKING";
  }
  if (normalized === "CALL_TO_BOOK" || normalized === "REQUEST_APPOINTMENT" || normalized === "NOT_AVAILABLE") {
    return normalized;
  }
  if (fallbackPhone?.trim()) {
    return "CALL_TO_BOOK";
  }
  return "NOT_AVAILABLE";
}

export function bookingCapabilityLabel(mode: BookingMode) {
  switch (mode) {
    case "ONLINE_BOOKING":
      return "Book online with Jeevanam";
    case "CALL_TO_BOOK":
      return "Call clinic to book";
    case "REQUEST_APPOINTMENT":
      return "Request appointment";
    case "NOT_AVAILABLE":
      return "Booking unavailable";
    default:
      return "Booking availability";
  }
}

export function bookingCapabilitySupportText(mode: BookingMode) {
  switch (mode) {
    case "ONLINE_BOOKING":
      return "Instant booking through Jeevanam Care";
    case "CALL_TO_BOOK":
      return "Call the clinic to schedule your visit";
    case "REQUEST_APPOINTMENT":
      return "Submit a request for the clinic to review";
    case "NOT_AVAILABLE":
      return "No public booking route is available";
    default:
      return "Booking availability is shared by the provider";
  }
}

export function bookingCapabilityTone(mode: BookingMode): "success" | "info" | "warning" | "muted" {
  switch (mode) {
    case "ONLINE_BOOKING":
      return "success";
    case "CALL_TO_BOOK":
      return "info";
    case "REQUEST_APPOINTMENT":
      return "warning";
    case "NOT_AVAILABLE":
      return "muted";
    default:
      return "muted";
  }
}

export function providerBookingPrimaryLabel(mode: BookingMode) {
  switch (mode) {
    case "ONLINE_BOOKING":
      return "Book appointment";
    case "CALL_TO_BOOK":
      return "Call clinic";
    case "REQUEST_APPOINTMENT":
      return "Request appointment";
    case "NOT_AVAILABLE":
      return "View profile";
    default:
      return "Book appointment";
  }
}

export function providerBookingSecondaryLabel(mode: BookingMode) {
  switch (mode) {
    case "ONLINE_BOOKING":
      return "View profile";
    case "CALL_TO_BOOK":
      return "View profile";
    case "REQUEST_APPOINTMENT":
      return "View profile";
    case "NOT_AVAILABLE":
      return null;
    default:
      return "View profile";
  }
}

export function BookingCapabilityBadge({
  mode,
  compact = false,
  className,
}: {
  mode: BookingMode;
  compact?: boolean;
  className?: string;
}) {
  const tone = bookingCapabilityTone(mode);
  const label = bookingCapabilityLabel(mode);
  const icon: ReactNode =
    mode === "ONLINE_BOOKING"
      ? <CheckCircleOutlineRounded fontSize="small" aria-hidden="true" />
      : mode === "CALL_TO_BOOK"
        ? <CallOutlined fontSize="small" aria-hidden="true" />
        : <HelpOutlineOutlined fontSize="small" aria-hidden="true" />;
  return (
    <span className={`booking-capability-badge booking-capability-badge--${tone}${compact ? " is-compact" : ""}${className ? ` ${className}` : ""}`.trim()}>
      {icon}
      <span>{label}</span>
    </span>
  );
}
