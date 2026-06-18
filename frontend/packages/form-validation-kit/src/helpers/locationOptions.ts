import { countries } from "../data/countries.js";
import { indiaCities } from "../data/indiaCities.js";
import { indiaStates } from "../data/indiaStates.js";

type LocationOptionSource = readonly string[];

export function normalizeLocationText(value: string) {
  return value.trim().replace(/\s+/g, " ").toLowerCase();
}

function uniqueValues(values: LocationOptionSource) {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const value of values) {
    const key = normalizeLocationText(value);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(value);
  }
  return result;
}

function rankedSuggestions(query: string, values: LocationOptionSource, limit = 10) {
  const normalizedQuery = normalizeLocationText(query);
  const unique = uniqueValues(values);
  if (!normalizedQuery) {
    return unique.slice(0, limit);
  }

  const prefixMatches: string[] = [];
  const containsMatches: string[] = [];

  for (const value of unique) {
    const normalizedValue = normalizeLocationText(value);
    if (normalizedValue.startsWith(normalizedQuery)) {
      prefixMatches.push(value);
    } else if (normalizedValue.includes(normalizedQuery)) {
      containsMatches.push(value);
    }
  }

  return [...prefixMatches, ...containsMatches].slice(0, limit);
}

export function getCountrySuggestions(query: string, limit = 10) {
  return rankedSuggestions(query, countries, limit);
}

export function getIndiaStateSuggestions(query: string, limit = 10) {
  return rankedSuggestions(query, indiaStates, limit);
}

export function getIndiaCitySuggestions(query: string, limit = 10) {
  return rankedSuggestions(query, indiaCities, limit);
}

export function getCitySuggestions(query: string, country?: string, limit = 10) {
  const normalizedCountry = normalizeLocationText(country || "");
  if (!normalizedCountry || normalizedCountry === "india") {
    return getIndiaCitySuggestions(query, limit);
  }
  return [];
}
