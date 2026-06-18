import { z } from "zod";

import { optionalString, requiredString } from "../validators/common.js";

export const careAiSessionSchema = z.object({
  sessionId: optionalString(),
  patientId: optionalString(),
  doctorUserId: optionalString(),
  mode: z.enum(["GENERAL", "APPOINTMENT", "FOLLOW_UP"]).default("GENERAL"),
  prompt: requiredString("Message is required."),
  context: optionalString(),
});

export type CareAiSessionValues = z.infer<typeof careAiSessionSchema>;
