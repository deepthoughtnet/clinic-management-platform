function normalizeText(value) {
  return String(value || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function normalizeInterpretation(value) {
  return normalizeText(value).replaceAll("_", " ");
}

export function resolveTrustedLabStatus(concept) {
  const interpretation = normalizeInterpretation(concept?.interpretation);
  if (interpretation.includes("high") || interpretation.includes("above range")) {
    return { label: "HIGH", tone: "error" };
  }
  if (interpretation.includes("low") || interpretation.includes("below range")) {
    return { label: "LOW", tone: "warning" };
  }
  if (interpretation.includes("normal") || interpretation.includes("within range")) {
    return { label: "NORMAL", tone: "success" };
  }
  return { label: "UNKNOWN", tone: "default" };
}
