"use client";

import { useState } from "react";
import { ListingsGrid } from "@/components/ListingsGrid";
import { ListingsTable } from "@/components/ListingsTable";
import { FilterBar } from "@/components/FilterBar";
import { Pagination } from "@/components/Pagination";
import {
  emptyFilters,
  filtersActive,
  matchesFilters,
  sortLabels,
  sortListings,
  type Filters,
  type SortKey,
} from "@/lib/filters";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

type View = "grid" | "table";

const PAGE_SIZE = 24;
const sortKeys = Object.keys(sortLabels) as SortKey[];

export function ViewToggle({
  listings,
  feedDistricts = [],
}: {
  listings: Listing[];
  feedDistricts?: string[];
}) {
  const [view, setView] = useState<View>("grid");
  const [hideDelisted, setHideDelisted] = useState(true);
  const [sort, setSort] = useState<SortKey>("newest");
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(1);

  // District options come from the configured feeds (source of truth), unioned
  // with any districts present in the listings so legacy/delisted rows stay
  // filterable. Falls back to listing-derived districts if no feeds are passed.
  const districts = Array.from(
    new Set([
      ...feedDistricts,
      ...listings.map((l) => l.district).filter((d): d is string => !!d),
    ]),
  ).sort();

  const delistedCount = listings.filter((l) => l.delistedAt != null).length;
  const newCount = listings.filter((l) => l.delistedAt == null && isNew(l.timestamp)).length;

  const filtered = listings.filter(
    (l) => (!hideDelisted || l.delistedAt == null) && matchesFilters(l, filters),
  );
  const ordered = sortListings(filtered, sort);

  const pageCount = Math.max(1, Math.ceil(ordered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const pageItems = ordered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  // Any change to the result set jumps back to page 1 so the view isn't stranded.
  const applyFilters = (f: Filters) => {
    setFilters(f);
    setPage(1);
  };
  const applySort = (key: SortKey) => {
    setSort(key);
    setPage(1);
  };
  const toggleHide = (checked: boolean) => {
    setHideDelisted(checked);
    setPage(1);
  };

  return (
    <>
      <FilterBar
        filters={filters}
        districts={districts}
        onChange={applyFilters}
        onReset={() => applyFilters(emptyFilters)}
        active={filtersActive(filters)}
      />
      <div className="mb-5 flex flex-wrap items-center gap-x-4 gap-y-3">
        <div className="flex rounded-md border border-border p-0.5">
          {(["grid", "table"] as const).map((v) => (
            <button
              key={v}
              onClick={() => setView(v)}
              className={`rounded px-3 py-1 text-sm font-medium capitalize transition-colors ${
                view === v ? "bg-surface-2 text-text" : "text-muted hover:text-text"
              }`}
            >
              {v}
            </button>
          ))}
        </div>

        {newCount > 0 && (
          <span className="inline-flex items-center gap-1.5 text-sm text-muted">
            <span className="h-2 w-2 rounded-full bg-signal" aria-hidden="true" />
            <span className="tnum text-text">{newCount}</span> new today
          </span>
        )}

        <div className="ml-auto flex flex-wrap items-center gap-x-4 gap-y-3">
          {delistedCount > 0 && (
            <label className="flex cursor-pointer select-none items-center gap-2 text-sm text-muted">
              <input
                type="checkbox"
                checked={!hideDelisted}
                onChange={(e) => toggleHide(!e.target.checked)}
                className="accent-signal"
              />
              Show removed ({delistedCount})
            </label>
          )}
          <label className="flex items-center gap-2 text-sm text-muted">
            Sort
            <select
              value={sort}
              onChange={(e) => applySort(e.target.value as SortKey)}
              className="rounded-md border border-border bg-surface px-2 py-1 text-sm text-text focus:border-signal focus:outline-none"
            >
              {sortKeys.map((key) => (
                <option key={key} value={key}>
                  {sortLabels[key]}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      <div className={view === "grid" ? "" : "hidden"}>
        <ListingsGrid listings={pageItems} />
      </div>
      <div className={view === "table" ? "" : "hidden"}>
        <ListingsTable listings={pageItems} />
      </div>
      <Pagination
        page={safePage}
        pageCount={pageCount}
        total={ordered.length}
        onPage={(p) => setPage(Math.min(Math.max(1, p), pageCount))}
      />
    </>
  );
}
