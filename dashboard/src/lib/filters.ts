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

/** How the listing grid/table is ordered. "newest" is the default. */
export type SortKey = "newest" | "priceAsc" | "priceDesc" | "sizeDesc";

export const sortLabels: Record<SortKey, string> = {
  newest: "Newest first",
  priceAsc: "Price: low to high",
  priceDesc: "Price: high to low",
  sizeDesc: "Size: large to small",
};

/** Orders two nullable numbers, always sinking nulls to the bottom regardless of direction. */
function nullsLast(a: number | null, b: number | null, cmp: (x: number, y: number) => number): number {
  if (a == null && b == null) return 0;
  if (a == null) return 1;
  if (b == null) return -1;
  return cmp(a, b);
}

/** Comparator per sort option. Listings missing the sorted field sink to the bottom. */
const comparators: Record<SortKey, (a: Listing, b: Listing) => number> = {
  newest: (a, b) => b.timestamp - a.timestamp,
  priceAsc: (a, b) => nullsLast(a.totalRent, b.totalRent, (x, y) => x - y),
  priceDesc: (a, b) => nullsLast(a.totalRent, b.totalRent, (x, y) => y - x),
  sizeDesc: (a, b) => nullsLast(a.size, b.size, (x, y) => y - x),
};

/** Returns a new array ordered by [key], leaving the input untouched. */
export function sortListings(list: Listing[], key: SortKey): Listing[] {
  return [...list].sort(comparators[key]);
}
