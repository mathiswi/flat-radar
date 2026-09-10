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
  /** Move-in month "YYYY-MM": keep listings available in or before this month. */
  availableBy: string;
  /** When true, "Available by" also excludes listings with no known move-in date. */
  availableExact: boolean;
};

export const emptyFilters: Filters = {
  district: "",
  source: "",
  minRent: "",
  maxRent: "",
  minRooms: "",
  minSize: "",
  maxSize: "",
  availableBy: "",
  availableExact: false,
};

export function filtersActive(f: Filters): boolean {
  // availableExact is a modifier on availableBy, not a constraint on its own, so it
  // doesn't count as "active" (and it's a boolean, not a "" sentinel like the rest).
  return (
    f.district !== "" ||
    f.source !== "" ||
    f.minRent !== "" ||
    f.maxRent !== "" ||
    f.minRooms !== "" ||
    f.minSize !== "" ||
    f.maxSize !== "" ||
    f.availableBy !== ""
  );
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

  // "Available by" a move-in month ("YYYY-MM"): dated listings must be free in or
  // before that month — compare the listing's year-month prefix, so a flat free on
  // the 15th still counts for its month. A listing with no stated date is *kept* by
  // default (an unknown date often means "sofort/nach Vereinbarung"), but the
  // `availableExact` toggle excludes those so the filter constrains to known dates.
  if (f.availableBy) {
    if (l.availableFrom != null) {
      if (l.availableFrom.slice(0, 7) > f.availableBy) return false;
    } else if (f.availableExact) {
      return false;
    }
  }

  return true;
}

/** A sortable column of the listings table (and the grid/map, which share the order). */
export type SortField =
  | "timestamp"
  | "totalRent"
  | "size"
  | "rooms"
  | "availableFrom"
  | "title"
  | "location"
  | "source";

export type SortDir = "asc" | "desc";

/** How the listings are ordered. Newest-first is the default. */
export type Sort = { field: SortField; dir: SortDir };

export const defaultSort: Sort = { field: "timestamp", dir: "desc" };

/** The value a field sorts on: a number, a string (ISO date sorts chronologically), or null. */
function sortValue(l: Listing, field: SortField): number | string | null {
  switch (field) {
    case "timestamp":
      return l.timestamp;
    case "totalRent":
      return l.totalRent;
    case "size":
      return l.size;
    case "rooms":
      return l.rooms;
    case "availableFrom":
      return l.availableFrom; // "YYYY-MM-DD" — lexical order is chronological
    case "title":
      return l.title;
    case "location":
      return l.location;
    case "source":
      return l.source;
  }
}

/** Comparator for a [sort]. Nulls always sink to the bottom, regardless of direction. */
function comparator(sort: Sort): (a: Listing, b: Listing) => number {
  const sign = sort.dir === "asc" ? 1 : -1;
  return (a, b) => {
    const va = sortValue(a, sort.field);
    const vb = sortValue(b, sort.field);
    if (va == null && vb == null) return 0;
    if (va == null) return 1;
    if (vb == null) return -1;
    const cmp =
      typeof va === "string" ? va.localeCompare(vb as string) : va - (vb as number);
    return sign * cmp;
  };
}

/** Returns a new array ordered by [sort], leaving the input untouched. */
export function sortListings(list: Listing[], sort: Sort): Listing[] {
  return [...list].sort(comparator(sort));
}

/** The presets offered by the global Sort dropdown (grid/table/map). */
export const sortPresets: { label: string; sort: Sort }[] = [
  { label: "Newest first", sort: { field: "timestamp", dir: "desc" } },
  { label: "Price: low to high", sort: { field: "totalRent", dir: "asc" } },
  { label: "Price: high to low", sort: { field: "totalRent", dir: "desc" } },
  { label: "Size: large to small", sort: { field: "size", dir: "desc" } },
];

export function sameSort(a: Sort, b: Sort): boolean {
  return a.field === b.field && a.dir === b.dir;
}

/** The direction a column adopts when first clicked (before any toggle). */
const defaultDir: Record<SortField, SortDir> = {
  timestamp: "desc",
  totalRent: "asc",
  size: "desc",
  rooms: "desc",
  availableFrom: "asc",
  title: "asc",
  location: "asc",
  source: "asc",
};

/** Header-click logic: flip direction if [field] is already active, else switch to it. */
export function nextSort(current: Sort, field: SortField): Sort {
  if (current.field === field) {
    return { field, dir: current.dir === "asc" ? "desc" : "asc" };
  }
  return { field, dir: defaultDir[field] };
}
