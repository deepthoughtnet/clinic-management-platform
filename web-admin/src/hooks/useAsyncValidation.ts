import * as React from "react";

import { createDebouncedValidator, type AsyncValidationCallback, type AsyncValidationResult } from "@deepthoughtnet/form-validation-kit";

export function useDebouncedAsyncValidation<T>(
  callback: AsyncValidationCallback<T>,
  options: { delayMs?: number; defaultMessage?: string } = {},
) {
  return React.useMemo(() => createDebouncedValidator(callback, options), [callback, options.delayMs, options.defaultMessage]);
}

export type { AsyncValidationResult };
