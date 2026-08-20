import { z } from "zod";

import { email, optionalEmployeeCode, optionalLoginId, optionalString, requiredString } from "../validators/common.js";
import { optionalIndianMobileNumber } from "../validators/india.js";

const CONTROL_CHAR_RE = /[\u0000-\u001F\u007F]/;
const MAX_NAME_LENGTH = 128;
const MAX_DISPLAY_NAME_LENGTH = 256;
const MAX_EMAIL_LENGTH = 255;
const MAX_DEPARTMENT_LENGTH = 128;
const MAX_ROLE_LENGTH = 64;
const OPTIONAL_TEXT_MAX_LENGTH = 128;

function textSchema(label: string, maxLength: number) {
  return requiredString(`${label} is required.`)
    .pipe(z.string().max(maxLength, `${label} must be ${maxLength} characters or fewer.`))
    .refine((value) => !CONTROL_CHAR_RE.test(value), `${label} contains invalid characters.`);
}

function optionalTextSchema(maxLength: number, label: string) {
  return optionalString()
    .refine((value) => value === undefined || value.length <= maxLength, `${label} must be ${maxLength} characters or fewer.`)
    .refine((value) => value === undefined || !CONTROL_CHAR_RE.test(value), `${label} contains invalid characters.`);
}

function departmentRoleCheck(values: { role?: string | null; department?: string | null }, ctx: z.RefinementCtx) {
  const role = values.role?.trim().toUpperCase() || "";
  const department = values.department?.trim() || "";
  if (!role || !department) return;
  const normalizedDepartment = department.toLowerCase();
  const departmentMatches = (terms: string[]) => terms.some((term) => normalizedDepartment.includes(term));

  let suspicious = false;
  if (role === "DOCTOR") {
    suspicious = departmentMatches(["reception", "billing", "pharmacy", "laboratory", "lab", "inventory", "administration", "admin", "engage", "care"]);
  } else if (role.startsWith("LAB_")) {
    suspicious = !departmentMatches(["lab", "laboratory", "pathology", "diagnostic"]);
  } else if (role.startsWith("PHARMACY") || role === "PHARMACIST" || role === "PHARMA") {
    suspicious = !departmentMatches(["pharmacy", "inventory", "dispens"]);
  } else if (role.startsWith("ENGAGE_")) {
    suspicious = !departmentMatches(["engage", "care", "carepilot"]);
  } else if (role === "CLINIC_ADMIN" || role === "TENANT_ADMIN" || role === "ADMIN") {
    suspicious = false;
  }

  if (suspicious) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["department"],
      message: "Choose a matching department for this role.",
    });
  }
}

export const userCreateSchema = z.object({
  firstName: textSchema("First name", MAX_NAME_LENGTH),
  lastName: optionalTextSchema(MAX_NAME_LENGTH, "Last name"),
  username: optionalLoginId(),
  email: email().pipe(z.string().max(MAX_EMAIL_LENGTH, `Email must be ${MAX_EMAIL_LENGTH} characters or fewer.`)),
  role: textSchema("Role", MAX_ROLE_LENGTH),
  tempPassword: optionalString(),
  employeeCode: optionalEmployeeCode(),
  active: z.boolean().optional().default(true),
  mobile: optionalIndianMobileNumber(),
  department: textSchema("Department", MAX_DEPARTMENT_LENGTH),
}).superRefine((values, ctx) => departmentRoleCheck(values, ctx));

export type UserCreateValues = z.infer<typeof userCreateSchema>;

export const userUpdateSchema = z.object({
  displayName: textSchema("Name", MAX_DISPLAY_NAME_LENGTH),
  email: email().pipe(z.string().max(MAX_EMAIL_LENGTH, `Email must be ${MAX_EMAIL_LENGTH} characters or fewer.`)),
  username: optionalLoginId(),
  employeeCode: optionalEmployeeCode(),
  mobile: optionalIndianMobileNumber(),
  department: textSchema("Department", MAX_DEPARTMENT_LENGTH),
  role: textSchema("Role", MAX_ROLE_LENGTH),
  active: z.boolean(),
}).superRefine((values, ctx) => departmentRoleCheck(values, ctx));

export type UserUpdateValues = z.infer<typeof userUpdateSchema>;
