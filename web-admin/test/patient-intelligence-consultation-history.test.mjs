import test from "node:test";
import assert from "node:assert/strict";
import {
  buildConsultationHistoryEntries,
  getConsultationHistorySectionTitle,
  getConsultationHistoryToggleLabel,
  getVisibleConsultationHistoryItems,
  shouldShowConsultationHistoryToggle,
} from "../src/components/clinical/patientIntelligenceConsultationHistory.js";

test("history at a glance includes the current consultation and orders by consultation date descending", () => {
  const entries = buildConsultationHistoryEntries(
    [
      {
        consultationId: "older",
        consultationDate: "2025-08-22T10:00:00Z",
        diagnosis: "Viral URI",
        treatmentSummary: "Advice only",
        advice: "Rest",
      },
      {
        consultationId: "newer-history",
        consultationDate: "2026-08-23T10:00:00Z",
        diagnosis: "CBC follow-up",
        treatmentSummary: "Reviewed CBC",
        advice: "Continue care",
      },
    ],
    {
      id: "current",
      createdAt: "2026-09-03T10:00:00Z",
      completedAt: null,
      status: "IN_CONSULTATION",
      doctorName: "Doc UAT Automation Doctor",
      diagnosis: "Fever and cough",
      chiefComplaints: "Fever and cough",
      clinicalNotes: "Current consultation notes",
      advice: "Hydration",
      followUpDate: "2026-09-10",
    },
    "current",
  );

  assert.equal(entries[0].isCurrent, true);
  assert.equal(entries[0].primaryText, "Fever and cough");
  assert.equal(entries[0].doctor, "Doc UAT Automation Doctor");
  assert.equal(entries[0].status, "IN_CONSULTATION");
  assert.equal(entries[0].followUp, "2026-09-10");
  assert.equal(entries[1].consultationId, "newer-history");
  assert.equal(entries[2].consultationId, "older");
});

test("history at a glance shows view all and show less only above the boundary", () => {
  const items = Array.from({ length: 6 }, (_, index) => ({
    consultationId: `c-${index}`,
    consultationDate: `2026-08-${String(23 - index).padStart(2, "0")}`,
    status: null,
    doctor: null,
    primaryText: `Consultation ${index + 1}`,
    followUp: null,
    detail: null,
    isCurrent: false,
  }));

  assert.equal(shouldShowConsultationHistoryToggle(5, 5), false);
  assert.equal(shouldShowConsultationHistoryToggle(6, 5), true);
  assert.deepEqual(getVisibleConsultationHistoryItems(items, false, 5).map((item) => item.consultationId), ["c-0", "c-1", "c-2", "c-3", "c-4"]);
  assert.deepEqual(getVisibleConsultationHistoryItems(items, true, 5).map((item) => item.consultationId), items.map((item) => item.consultationId));
  assert.equal(getConsultationHistorySectionTitle(12), "History at a glance (12 consultations)");
  assert.equal(getConsultationHistoryToggleLabel(false), "View all");
  assert.equal(getConsultationHistoryToggleLabel(true), "Show less");
});

