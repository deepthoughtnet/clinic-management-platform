export const HELP_OPEN_EVENT = "arogia:help:open";
export const HELP_CLOSE_EVENT = "arogia:help:close";

export type HelpEventDetail = {
  pageKey?: string;
  source?: string;
};

export function openGlobalHelp(payload?: HelpEventDetail) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(HELP_OPEN_EVENT, { detail: payload || {} }));
}

export function closeGlobalHelp() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(HELP_CLOSE_EVENT, { detail: {} }));
}

export function subscribeGlobalHelpEvents(
  onOpen: (detail: HelpEventDetail) => void,
  onClose: () => void,
) {
  if (typeof window === "undefined") {
    return () => undefined;
  }
  const handleOpen = (event: Event) => {
    const customEvent = event as CustomEvent<HelpEventDetail>;
    onOpen(customEvent.detail || {});
  };
  const handleClose = () => onClose();
  window.addEventListener(HELP_OPEN_EVENT, handleOpen as EventListener);
  window.addEventListener(HELP_CLOSE_EVENT, handleClose as EventListener);
  return () => {
    window.removeEventListener(HELP_OPEN_EVENT, handleOpen as EventListener);
    window.removeEventListener(HELP_CLOSE_EVENT, handleClose as EventListener);
  };
}

