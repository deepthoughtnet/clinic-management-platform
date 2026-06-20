export const HELP_OPEN_EVENT = "arogia:help:open";
export const HELP_CLOSE_EVENT = "arogia:help:close";

export function openGlobalHelp(payload) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(HELP_OPEN_EVENT, { detail: payload || {} }));
}

export function closeGlobalHelp() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(HELP_CLOSE_EVENT, { detail: {} }));
}

export function subscribeGlobalHelpEvents(onOpen, onClose) {
  if (typeof window === "undefined") {
    return () => undefined;
  }
  const handleOpen = (event) => onOpen((event && event.detail) || {});
  const handleClose = () => onClose();
  window.addEventListener(HELP_OPEN_EVENT, handleOpen);
  window.addEventListener(HELP_CLOSE_EVENT, handleClose);
  return () => {
    window.removeEventListener(HELP_OPEN_EVENT, handleOpen);
    window.removeEventListener(HELP_CLOSE_EVENT, handleClose);
  };
}

