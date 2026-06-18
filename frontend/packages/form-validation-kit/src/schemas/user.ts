import { z } from "zod";

import { email, optionalString } from "../validators/common.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

export const userCreateSchema = z.object({
  firstName: optionalString().pipe(z.string().min(1, "First name is required.")),
  lastName: optionalString(),
  email: email(),
  role: z.string().trim().min(1, "Role is required."),
  tempPassword: optionalString(),
  active: z.boolean().optional().default(true),
  mobile: optionalIndianMobileNumber(),
});

export type UserCreateValues = z.infer<typeof userCreateSchema>;
