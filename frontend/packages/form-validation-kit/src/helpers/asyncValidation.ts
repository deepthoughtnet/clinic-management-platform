import { validationMessages } from "./errorMessages.js";

export type AsyncValidationResult = {
  valid: boolean;
  message?: string;
};

export type AsyncValidationCallback<T> = (value: T, context: { signal: AbortSignal }) => Promise<boolean | string | AsyncValidationResult | null | undefined>;

function normalizeResult(result: boolean | string | AsyncValidationResult | null | undefined, defaultMessage: string): AsyncValidationResult {
  if (typeof result === "boolean") {
    return result ? { valid: true } : { valid: false, message: defaultMessage };
  }
  if (typeof result === "string") {
    return result ? { valid: false, message: result } : { valid: true };
  }
  if (!result) {
    return { valid: true };
  }
  return result.valid ? { valid: true } : { valid: false, message: result.message || defaultMessage };
}

export function createDebouncedValidator<T>(
  callback: AsyncValidationCallback<T>,
  options: { delayMs?: number; defaultMessage?: string } = {},
) {
  let timer: ReturnType<typeof setTimeout> | null = null;
  let controller: AbortController | null = null;
  let settleCurrent: ((result: AsyncValidationResult) => void) | null = null;
  let sequence = 0;

  return (value: T) => new Promise<AsyncValidationResult>((resolve) => {
    sequence += 1;
    const currentSequence = sequence;
    if (settleCurrent) {
      settleCurrent({ valid: true });
      settleCurrent = null;
    }
    if (timer) clearTimeout(timer);
    if (controller) controller.abort();
    controller = new AbortController();
    const currentController = controller;
    settleCurrent = resolve;

    timer = setTimeout(async () => {
      try {
        const result = await callback(value, { signal: currentController.signal });
        if (currentController.signal.aborted || currentSequence !== sequence) {
          if (settleCurrent === resolve) {
            settleCurrent = null;
            resolve({ valid: true });
          }
          return;
        }
        if (settleCurrent === resolve) {
          settleCurrent = null;
          resolve(normalizeResult(result, options.defaultMessage || validationMessages.duplicateValue));
        }
      } catch {
        if (currentController.signal.aborted || currentSequence !== sequence) {
          if (settleCurrent === resolve) {
            settleCurrent = null;
            resolve({ valid: true });
          }
          return;
        }
        if (settleCurrent === resolve) {
          settleCurrent = null;
          resolve({ valid: false, message: options.defaultMessage || validationMessages.duplicateValue });
        }
      }
    }, options.delayMs ?? 300);
  });
}

export function createUniqueEmailValidator(
  callback: AsyncValidationCallback<string>,
  options: { delayMs?: number; defaultMessage?: string } = {},
) {
  return createDebouncedValidator<string>(async (value, context) => {
    const normalized = typeof value === "string" ? value.trim().toLowerCase() : "";
    if (!normalized) return { valid: true };
    return callback(normalized, context);
  }, options);
}

export function createUniquePhoneValidator(
  callback: AsyncValidationCallback<string>,
  options: { delayMs?: number; defaultMessage?: string } = {},
) {
  return createDebouncedValidator<string>(async (value, context) => {
    const normalized = typeof value === "string" ? value.replace(/[^0-9]/g, "") : "";
    if (!normalized) return { valid: true };
    return callback(normalized, context);
  }, options);
}
