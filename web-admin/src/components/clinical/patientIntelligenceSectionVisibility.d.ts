export function getVisibleSectionItems<T>(items: T[], expanded: boolean, limit: number): T[];
export function shouldShowSectionToggle(total: number, limit: number): boolean;
export function getSectionToggleLabel(expanded: boolean): string;
export function getSectionCountLabel(title: string, total: number): string;

