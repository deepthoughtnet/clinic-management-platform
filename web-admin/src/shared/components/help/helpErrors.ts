import { ApiClientError } from "../../../api/restClient";

export const HELP_NOT_FOUND_MESSAGE = "No page-specific help is available for this page yet.";
export const HELP_SESSION_EXPIRED_MESSAGE = "Your session has expired. Please login again.";
export const HELP_PERMISSION_DENIED_MESSAGE = "You do not have permission to view help.";
export const HELP_LOAD_FAILED_MESSAGE = "Unable to load help. Please try again.";

function getStatus(error: unknown): number | null {
  if (error instanceof ApiClientError) {
    return error.status;
  }
  if (error && typeof error === "object" && "status" in error) {
    const status = (error as { status?: unknown }).status;
    return typeof status === "number" ? status : null;
  }
  return null;
}

export function isHelpNotFoundError(error: unknown): boolean {
  return getStatus(error) === 404;
}

export function getHelpErrorMessage(error: unknown): string {
  switch (getStatus(error)) {
    case 401:
      return HELP_SESSION_EXPIRED_MESSAGE;
    case 403:
      return HELP_PERMISSION_DENIED_MESSAGE;
    case 404:
      return HELP_NOT_FOUND_MESSAGE;
    case 500:
      return HELP_LOAD_FAILED_MESSAGE;
    default:
      return HELP_LOAD_FAILED_MESSAGE;
  }
}
