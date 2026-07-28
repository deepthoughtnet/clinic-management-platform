import { type MouseEvent } from "react";

import type { PublicClinicMiniResponse } from "../../api/publicCatalog";

function clinicSubtitle(clinic: PublicClinicMiniResponse) {
  if (clinic.area && clinic.city) {
    return `${clinic.area} · ${clinic.city}`;
  }
  return clinic.area ?? clinic.city ?? "Clinic profile";
}

export function DoctorClinicSelector({
  doctorName,
  clinics,
  selectedClinicCode,
  nextAvailableSlot,
  onSelect,
}: {
  doctorName: string;
  clinics: PublicClinicMiniResponse[];
  selectedClinicCode: string;
  nextAvailableSlot: string | null;
  onSelect: (clinic: PublicClinicMiniResponse) => void;
}) {
  function handleClick(event: MouseEvent<HTMLButtonElement>, clinic: PublicClinicMiniResponse) {
    event.preventDefault();
    onSelect(clinic);
  }

  return (
    <section className="patient-panel patient-clinic-selector">
      <div className="patient-panel-heading">
        <h2>Select a clinic</h2>
        <span className="panel-caption">{clinics.length} options</span>
      </div>
      <p className="patient-inline-note">
        {doctorName} is listed at more than one public clinic. Choose the right clinic before continuing to phone number and OTP sign-in.
      </p>
      <div className="patient-subcard-list">
        {clinics.map((clinic) => (
          <button
            key={clinic.clinicSlug}
            type="button"
            className={`doctor-choice-card${selectedClinicCode === clinic.clinicSlug ? " is-active" : ""}`}
            onClick={(event) => handleClick(event, clinic)}
          >
            <strong>{clinic.clinicDisplayName}</strong>
            <span>{clinicSubtitle(clinic)}</span>
            <small>{nextAvailableSlot ? `Next slot: ${nextAvailableSlot}` : "Available slot will appear after sign-in"}</small>
          </button>
        ))}
      </div>
    </section>
  );
}
