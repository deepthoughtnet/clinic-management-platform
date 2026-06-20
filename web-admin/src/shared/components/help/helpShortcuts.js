export function isHelpShortcutEvent(event) {
  if (!(event.ctrlKey || event.metaKey)) return false;
  return event.key === "/" || event.code === "Slash";
}

export function isTextEntryTarget(target) {
  if (!target || !(target instanceof HTMLElement)) return false;
  const tagName = target.tagName.toUpperCase();
  return tagName === "INPUT" || tagName === "TEXTAREA" || target.isContentEditable;
}
