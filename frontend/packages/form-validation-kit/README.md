# form-validation-kit

Shared frontend validation for Arogia Clinic Management Platform and future products.

This package contains:

- validators
- schemas
- messages
- thin React Hook Form adapters

It does not contain UI components.

## Import

```ts
import { createTenantSchema, zodFormResolver } from "@deepthoughtnet/form-validation-kit";
```

## Use with Zod directly

```ts
import { createTenantSchema } from "@deepthoughtnet/form-validation-kit";

const parsed = createTenantSchema.parse(formValues);
```

## Use with React Hook Form

```ts
import { useForm } from "react-hook-form";
import { z } from "zod";
import { createTenantSchema, zodFormResolver } from "@deepthoughtnet/form-validation-kit";

type CreateTenantForm = z.infer<typeof createTenantSchema>;

const form = useForm<CreateTenantForm>({
  resolver: zodFormResolver(createTenantSchema),
});
```

## Rollout Plan

1. Create Tenant
2. Clinic Profile
3. User/Admin creation
4. Patient registration
5. Patient login/OTP
6. Doctors
7. Appointments
8. Billing
9. Lab

## Rule

Keep this package focused on validation only. Do not add UI components or UI-specific state logic here.
