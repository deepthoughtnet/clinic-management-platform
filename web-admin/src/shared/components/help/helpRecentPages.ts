import type { HelpPageRoute } from "./helpPageRegistry";

export type HelpRecentPage = {
  pageKey: string;
  cmsPageKey: string;
  title: string;
  path: string;
  visitedAt: number;
};

const STORAGE_KEY = "arogia.help.recentPages.v1";
const MAX_RECENT = 5;

function readStorage(): HelpRecentPage[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as HelpRecentPage[];
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item.pageKey === "string") : [];
  } catch {
    return [];
  }
}

function writeStorage(items: HelpRecentPage[]) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items.slice(0, MAX_RECENT)));
  } catch {
    // Ignore storage errors in private mode or restricted environments.
  }
}

export function loadRecentHelpPages(): HelpRecentPage[] {
  return readStorage();
}

export function recordRecentHelpPage(route: HelpPageRoute | null | undefined) {
  if (!route || route.pageKey === "UNKNOWN_PAGE") return loadRecentHelpPages();
  const next: HelpRecentPage[] = [
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
