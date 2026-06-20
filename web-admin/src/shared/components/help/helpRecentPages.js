const STORAGE_KEY = "arogia.help.recentPages.v1";
const MAX_RECENT = 5;

function readStorage() {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item.pageKey === "string") : [];
  } catch {
    return [];
  }
}

function writeStorage(items) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_RECENT)));
  } catch {
    // Ignore storage errors in restricted environments.
  }
}

export function loadRecentHelpPages() {
  return readStorage();
}

export function recordRecentHelpPage(route) {
  if (!route || route.pageKey === "UNKNOWN_PAGE") return loadRecentHelpPages();
  const next = [
    {
      pageKey: route.pageKey,
      cmsPageKey: route.cmsPageKey,
      title: route.title,
      path: route.path,
      visitedAt: Date.now(),
    },
    ...readStorage().filter((item) => item.pageKey !== route.pageKey && item.cmsPageKey !== route.cmsPageKey),
  ].slice(0, MAX_RECENT);
  writeStorage(next);
  return next;
}

export function clearRecentHelpPages() {
  writeStorage([]);
}

export function getRecentHelpStorageKey() {
  return STORAGE_KEY;
}
