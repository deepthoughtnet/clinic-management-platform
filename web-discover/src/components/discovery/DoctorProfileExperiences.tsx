import { type ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  ArrowForwardRounded,
  AccessTimeOutlined,
  CalendarMonthOutlined,
  DirectionsCarOutlined,
  FavoriteBorderOutlined,
  MedicalServicesOutlined,
  NearMeOutlined,
  PsychologyOutlined,
  RateReviewOutlined,
  SchoolOutlined,
  StarRounded,
  VerifiedOutlined,
  WorkHistoryOutlined,
} from "@mui/icons-material";
import type { PublicDoctorSummaryResponse } from "../../api/publicCatalog";
import { DISCOVER_DETAIL_PATHS } from "../../routes";
import { careBookingUrl, formatConsultationFee, initials } from "../DiscoveryComponents";
import { BookingCapabilityBadge, providerBookingPrimaryLabel, providerBookingSecondaryLabel, normalizeBookingMode } from "./BookingCapability";
import { PublicMediaImage } from "../landing/PublicMediaImage";

export type DoctorBreadcrumbItem = {
  label: string;
  to?: string;
  current?: boolean;
};

export type DoctorReview = {
  id: string;
  patientFirstName: string;
  verifiedPatient: boolean;
  visitType: string;
  rating: number;
  reviewDate: string;
  reviewText: string;
};

export type DoctorVerificationBadge = {
  key: string;
  label: string;
  title: string;
  icon: ReactNode;
  tone?: "default" | "success" | "info" | "muted" | "warning";
};

export type DoctorServiceCard = {
  key: string;
  title: string;
  description: string;
  icon: ReactNode;
};

export type DoctorScheduleDay = {
  day: string;
  hours: string;
  current?: boolean;
  closed?: boolean;
};

export type DoctorRelatedSpecialty = {
  title: string;
  slug: string;
  description: string;
  icon: ReactNode;
};

export type DoctorRelatedDoctorCard = {
  publicDoctorId: string;
  doctorDisplayName: string;
  doctorSlug: string;
  photoUrl: string | null;
  speciality: string | null;
  yearsOfExperience: number | null;
  consultationFee: number | string | null;
  clinicDisplayName: string;
  clinicSlug: string;
  contactPhone: string | null;
  bookingMode: string | null;
  availableToday: boolean;
};

export const doctorSampleReviews: DoctorReview[] = [
  {
    id: "review-1",
    patientFirstName: "Asha",
    verifiedPatient: true,
    visitType: "Consultation",
    rating: 5,
    reviewDate: "31 Jul 2026",
    reviewText: "Very clear explanation, calm bedside manner, and the treatment plan was easy to follow.",
  },
  {
    id: "review-2",
    patientFirstName: "Rohan",
    verifiedPatient: true,
    visitType: "Follow-up",
    rating: 5,
    reviewDate: "29 Jul 2026",
    reviewText: "The diagnosis was quick, the fee was clear upfront, and the follow-up instructions were practical.",
  },
  {
    id: "review-3",
    patientFirstName: "Meera",
    verifiedPatient: true,
    visitType: "Video consultation",
    rating: 4,
    reviewDate: "26 Jul 2026",
    reviewText: "Helpful teleconsultation and the prescription was shared promptly after the visit.",
  },
  {
    id: "review-4",
    patientFirstName: "Kabir",
    verifiedPatient: true,
    visitType: "Clinic visit",
    rating: 5,
    reviewDate: "24 Jul 2026",
    reviewText: "Professional and reassuring. The clinic team handled registration and the appointment flow well.",
  },
  {
    id: "review-5",
    patientFirstName: "Priya",
    verifiedPatient: true,
    visitType: "Review",
    rating: 5,
    reviewDate: "21 Jul 2026",
    reviewText: "Great follow-through and the recommendations were tailored to my history.",
  },
  {
    id: "review-6",
    patientFirstName: "Dev",
    verifiedPatient: true,
    visitType: "Consultation",
    rating: 4,
    reviewDate: "18 Jul 2026",
    reviewText: "Good experience overall. Appointment started on time and I understood the next steps clearly.",
  },
];

export const doctorSampleVerificationBadges: DoctorVerificationBadge[] = [
  { key: "verified-doctor", label: "Verified Doctor", title: "The doctor profile has been verified by Jeevanam.", icon: <VerifiedOutlined fontSize="small" aria-hidden="true" />, tone: "success" },
  { key: "registration", label: "Medical Registration Verified", title: "Medical registration details have been checked.", icon: <SchoolOutlined fontSize="small" aria-hidden="true" />, tone: "info" },
  { key: "qualification", label: "Qualification Verified", title: "Qualification details have been checked.", icon: <WorkHistoryOutlined fontSize="small" aria-hidden="true" />, tone: "success" },
  { key: "profile", label: "Profile Verified", title: "Public profile details are verified.", icon: <MedicalServicesOutlined fontSize="small" aria-hidden="true" />, tone: "muted" },
  { key: "updated", label: "Recently Updated", title: "This profile was recently refreshed.", icon: <CalendarMonthOutlined fontSize="small" aria-hidden="true" />, tone: "info" },
];

export const doctorSampleServiceCards: DoctorServiceCard[] = [
  { key: "consultation", title: "Consultation", description: "General and specialist consultations in a calm, private setting.", icon: <MedicalServicesOutlined fontSize="small" aria-hidden="true" /> },
  { key: "teleconsultation", title: "Teleconsultation", description: "Follow-up visits and triage support from home.", icon: <DirectionsCarOutlined fontSize="small" aria-hidden="true" /> },
  { key: "checkup", title: "Health Checkup", description: "Preventive checkups and shared care planning.", icon: <FavoriteBorderOutlined fontSize="small" aria-hidden="true" /> },
  { key: "lifestyle", title: "Lifestyle Counselling", description: "Personalized advice for diet, sleep, and activity.", icon: <PsychologyOutlined fontSize="small" aria-hidden="true" /> },
  { key: "preventive", title: "Preventive Care", description: "Screening and wellness guidance with clear next steps.", icon: <NearMeOutlined fontSize="small" aria-hidden="true" /> },
  { key: "diabetes", title: "Diabetes Care", description: "Routine diabetes management and treatment review.", icon: <AccessTimeOutlined fontSize="small" aria-hidden="true" /> },
];

export const doctorSampleSpecialties: DoctorRelatedSpecialty[] = [
  { title: "Cardiology", slug: "cardiology", description: "Heart and circulation care.", icon: <MedicalServicesOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Dermatology", slug: "dermatology", description: "Skin and hair care.", icon: <FavoriteBorderOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Orthopedics", slug: "orthopedics", description: "Bones, joints, and mobility.", icon: <WorkHistoryOutlined fontSize="small" aria-hidden="true" /> },
  { title: "ENT", slug: "ent", description: "Ear, nose, and throat care.", icon: <MedicalServicesOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Pediatrics", slug: "pediatrics", description: "Child and adolescent care.", icon: <CalendarMonthOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Gynecology", slug: "gynecology", description: "Women’s health support.", icon: <FavoriteBorderOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Neurology", slug: "neurology", description: "Brain and nerve care.", icon: <PsychologyOutlined fontSize="small" aria-hidden="true" /> },
  { title: "Psychiatry", slug: "psychiatry", description: "Mental health support.", icon: <RateReviewOutlined fontSize="small" aria-hidden="true" /> },
];

export function DoctorBreadcrumb({ items }: { items: DoctorBreadcrumbItem[] }) {
  return (
    <nav className="doctor-breadcrumb" aria-label="Breadcrumb">
      <ol>
        {items.map((item, index) => (
          <li key={`${item.label}-${index}`} aria-current={item.current ? "page" : undefined}>
            {item.to && !item.current ? <Link to={item.to}>{item.label}</Link> : <span>{item.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}

export function RatingSummary({
  rating,
  reviewCount,
  recommendationPercent,
  compact = false,
}: {
  rating: number;
  reviewCount: number;
  recommendationPercent?: number | null;
  compact?: boolean;
}) {
  const fullStars = Math.max(0, Math.min(5, Math.round(rating)));
  return (
    <div className={`doctor-rating-summary${compact ? " is-compact" : ""}`} aria-label={`Average rating ${rating.toFixed(1)} out of 5 from ${reviewCount} reviews${recommendationPercent != null ? `, ${recommendationPercent}% recommend` : ""}`}>
      <div className="doctor-rating-summary__score">
        <span className="doctor-rating-summary__stars" aria-hidden="true">
          {Array.from({ length: 5 }, (_, index) => (
            <StarRounded key={index} fontSize="inherit" className={index < fullStars ? "is-filled" : ""} />
          ))}
        </span>
        <strong>{rating.toFixed(1)}</strong>
      </div>
      <span>{reviewCount} Patient Reviews</span>
      {recommendationPercent != null ? <span>{recommendationPercent}% Recommend</span> : null}
    </div>
  );
}

export function VerificationBadge({ badge }: { badge: DoctorVerificationBadge }) {
  return (
    <span className={`doctor-verification-badge doctor-verification-badge--${badge.tone ?? "default"}`} title={badge.title}>
      {badge.icon}
      <span>{badge.label}</span>
    </span>
  );
}

export function ReviewCard({ review }: { review: DoctorReview }) {
  return (
    <article className="doctor-review-card">
      <div className="doctor-review-card__heading">
        <div>
          <strong>{review.patientFirstName}</strong>
          <div className="doctor-review-card__meta">
            {review.verifiedPatient ? <span className="chip chip--success">Verified patient</span> : null}
            <span className="chip chip--muted">{review.visitType}</span>
          </div>
        </div>
        <div className="doctor-review-card__rating" aria-label={`${review.rating} star rating`}>
          <StarRounded fontSize="small" aria-hidden="true" />
          <strong>{review.rating.toFixed(1)}</strong>
        </div>
      </div>
      <p>{review.reviewText}</p>
      <small>{review.reviewDate}</small>
    </article>
  );
}

export function AvailabilityTimeline({ days }: { days: DoctorScheduleDay[] }) {
  return (
    <section className="doctor-availability-timeline" aria-label="Working hours">
      <div className="doctor-section-heading">
        <span className="eyebrow">Working Hours</span>
        <h2>Weekly schedule</h2>
      </div>
      <div className="doctor-availability-timeline__list">
        {days.map((day) => (
          <div
            key={day.day}
            className={`doctor-availability-timeline__row${day.current ? " is-current" : ""}${day.closed ? " is-closed" : ""}`}
          >
            <strong>{day.day}</strong>
            <span>{day.hours}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

export function BookingPanel({
  consultationFee,
  nextAvailableDays,
  consultationModes,
  clinicName,
  averageWaitTime,
  appointmentDuration,
  bookingUrl,
  callHref,
  callLabel = "Call Clinic",
  feeLabel = "Consultation Fee",
}: {
  consultationFee: string;
  nextAvailableDays: Array<{ day: string; slots: string[] }>;
  consultationModes: string[];
  clinicName: string;
  averageWaitTime: string;
  appointmentDuration: string;
  bookingUrl: string;
  callHref?: string | null;
  callLabel?: string;
  feeLabel?: string;
}) {
  return (
    <section className="doctor-booking-panel" aria-label="Appointment booking panel">
      <div className="doctor-section-heading">
        <span className="eyebrow">Appointment</span>
        <h2>Book with confidence</h2>
      </div>
      <div className="doctor-booking-panel__fee">
        <strong>{feeLabel}</strong>
        <span>{consultationFee}</span>
      </div>
      <div className="doctor-booking-panel__slots" aria-label="Available slots">
        {nextAvailableDays.map((day) => (
          <div className="doctor-booking-panel__day" key={day.day}>
            <strong>{day.day}</strong>
            <div className="doctor-booking-panel__slot-row">
              {day.slots.length ? day.slots.map((slot) => <span key={slot} className="chip chip--muted">{slot}</span>) : <span className="doctor-booking-panel__closed">Slots shared after review</span>}
            </div>
          </div>
        ))}
      </div>
      <div className="doctor-booking-panel__meta">
        <div>
          <strong>Consultation Modes</strong>
          <span>{consultationModes.join(" · ")}</span>
        </div>
        <div>
          <strong>Clinic</strong>
          <span>{clinicName}</span>
        </div>
        <div>
          <strong>Average Wait Time</strong>
          <span>{averageWaitTime}</span>
        </div>
        <div>
          <strong>Appointment Duration</strong>
          <span>{appointmentDuration}</span>
        </div>
      </div>
      <div className="doctor-booking-panel__actions">
        <a className="primary-button" href={bookingUrl}>
          Book Appointment
        </a>
        {callHref ? (
          <a className="secondary-button" href={callHref}>
            {callLabel}
          </a>
        ) : null}
      </div>
    </section>
  );
}

export function RelatedDoctorCard({ doctor }: { doctor: DoctorRelatedDoctorCard }) {
  const consultationFee = formatConsultationFee(doctor.consultationFee ?? null);
  const initialsText = initials(doctor.doctorDisplayName);
  const bookingMode = normalizeBookingMode(doctor.bookingMode) ?? "ONLINE_BOOKING";
  const bookingHref = bookingMode === "CALL_TO_BOOK"
    ? (doctor.contactPhone?.trim() ? `tel:${doctor.contactPhone.trim()}` : undefined)
    : careBookingUrl({ doctorId: doctor.publicDoctorId, clinicSlug: doctor.clinicSlug });
  const primaryLabel = providerBookingPrimaryLabel(bookingMode);
  const secondaryLabel = providerBookingSecondaryLabel(bookingMode) ?? "View profile";
  return (
    <article className="doctor-related-card">
      <div className="doctor-related-card__media">
        {doctor.photoUrl ? (
          <PublicMediaImage
            src={doctor.photoUrl}
            alt={`${doctor.doctorDisplayName} photo`}
            className="doctor-related-card__image"
            objectFit="cover"
            fallback={<div className="doctor-related-card__fallback" aria-hidden="true"><span>{initialsText}</span></div>}
          />
        ) : (
          <div className="doctor-related-card__fallback" aria-hidden="true"><span>{initialsText}</span></div>
        )}
      </div>
      <div className="doctor-related-card__body">
        <div className="doctor-related-card__heading">
          <strong>{doctor.doctorDisplayName}</strong>
          {doctor.speciality ? <span>{doctor.speciality}</span> : null}
        </div>
        <div className="doctor-related-card__meta">
          {doctor.yearsOfExperience != null ? <span>{doctor.yearsOfExperience}+ years experience</span> : null}
          {consultationFee ? <span>{consultationFee}</span> : null}
          <span>{doctor.clinicDisplayName}</span>
          <BookingCapabilityBadge mode={bookingMode} compact />
        </div>
        <RatingSummary rating={4.8} reviewCount={245} compact />
        <div className="doctor-related-card__action-row">
          <Link className="secondary-button" to={DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>
            {secondaryLabel}
          </Link>
          {bookingMode === "CALL_TO_BOOK" ? (
            bookingHref ? <a className="primary-button" href={bookingHref}>{primaryLabel}</a> : <Link className="primary-button" to={DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>{primaryLabel}</Link>
          ) : bookingMode === "NOT_AVAILABLE" ? (
            <Link className="primary-button" to={DISCOVER_DETAIL_PATHS.doctor(doctor.doctorSlug)}>
              {primaryLabel}
            </Link>
          ) : (
            <a className="primary-button" href={bookingHref}>
              {primaryLabel}
            </a>
          )}
        </div>
      </div>
    </article>
  );
}

export function SpecialtyCard({ specialty }: { specialty: DoctorRelatedSpecialty }) {
  return (
    <Link className="doctor-specialty-card" to={DISCOVER_DETAIL_PATHS.speciality(specialty.slug)}>
      <span className="doctor-specialty-card__icon" aria-hidden="true">{specialty.icon}</span>
      <strong>{specialty.title}</strong>
      <span>{specialty.description}</span>
      <small>Explore specialty</small>
    </Link>
  );
}

export function StickyBookingCTA({ bookingUrl, label = "Book Appointment" }: { bookingUrl: string; label?: string }) {
  return (
    <div className="doctor-sticky-booking-cta" aria-label="Quick booking">
      <a className="primary-button" href={bookingUrl}>
        <ArrowForwardRounded fontSize="small" aria-hidden="true" />
        {label}
      </a>
    </div>
  );
}
