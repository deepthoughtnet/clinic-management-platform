export function getVisibleSectionItems(items, expanded, limit) {
  return expanded ? items : items.slice(0, limit);
}

export function shouldShowSectionToggle(total, limit) {
  return total > limit;
}

export function getSectionToggleLabel(expanded) {
  return expanded ? "Show less" : "View all";
}

export function getSectionCountLabel(title, total) {
  return total > 0 ? `${title} (${total})` : title;
}

