import { z } from "zod";

import { email, optionalString } from "../validators/common.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

export const userCreateSchema = z.object({
  firstName: optionalString().pipe(z.string().min(1, "First name is required.")),
  lastName: optionalString(),
  username: optionalString(),
  email: email(),
  role: z.string().trim().min(1, "Role is required."),
  tempPassword: optionalString(),
  employeeCode: optionalString(),
  active: z.boolean().optional().default(true),
  mobile: optionalIndianMobileNumber(),
  department: optionalString(),
});

export type UserCreateValues = z.infer<typeof userCreateSchema>;
