import { z } from "zod";

import { dateString, optionalString } from "../validators/common.js";

function optionalDateString() {
  return z.preprocess(
    (value) => (value == null || value === "" ? undefined : value),
    dateString().optional(),
  );
}

export const consultationSchema = z.object({
  chiefComplaint: optionalString(),
  diagnosis: optionalString(),
  followUpDate: optionalDateString(),
  notes: optionalString(),
});

export type ConsultationValues = z.infer<typeof consultationSchema>;
