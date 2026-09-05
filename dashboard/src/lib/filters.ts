import type { Listing } from "@/lib/types";

/** Dashboard filter state. Empty string means "no constraint" for that field. */
export type Filters = {
  district: string;
  source: string;
  minRent: string;
  maxRent: string;
  minRooms: string;
  minSize: string;
  maxSize: string;
};

export const emptyFilters: Filters = {
  district: "",
  source: "",
  minRent: "",
  maxRent: "",
  minRooms: "",
  minSize: "",
  maxSize: "",
};

export function filtersActive(f: Filters): boolean {
  return Object.values(f).some((v) => v !== "");
}

function num(s: string): number | null {
  if (s.trim() === "") return null;
  const n = Number(s);
  return Number.isFinite(n) ? n : null;
}

/**
 * True when [l] passes every active constraint in [f]. A numeric constraint on a
 * field the listing doesn't have (null) excludes it, since we can't verify the bound.
 */
export function matchesFilters(l: Listing, f: Filters): boolean {
  if (f.district && l.district !== f.district) return false;
  if (f.source && l.source !== f.source) return false;

  const minRent = num(f.minRent);
  const maxRent = num(f.maxRent);
  if (minRent != null && (l.totalRent == null || l.totalRent < minRent)) return false;
  if (maxRent != null && (l.totalRent == null || l.totalRent > maxRent)) return false;

  const minRooms = num(f.minRooms);
  if (minRooms != null && (l.rooms == null || l.rooms < minRooms)) return false;

  const minSize = num(f.minSize);
  const maxSize = num(f.maxSize);
  if (minSize != null && (l.size == null || l.size < minSize)) return false;
  if (maxSize != null && (l.size == null || l.size > maxSize)) return false;

  return true;
}
