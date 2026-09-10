"use client";

import { useState } from "react";
import dynamic from "next/dynamic";
import { ListingsGrid } from "@/components/ListingsGrid";
import { ListingsTable } from "@/components/ListingsTable";
import { ListingDetail } from "@/components/ListingDetail";
import { FilterBar } from "@/components/FilterBar";
import { Pagination } from "@/components/Pagination";

// Leaflet touches `window`, so the map loads client-side only.
const ListingsMap = dynamic(
  () => import("@/components/ListingsMap").then((m) => m.ListingsMap),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[32rem] items-center justify-center rounded-xl border border-border bg-surface/40 text-sm text-muted">
        Loading map…
      </div>
    ),
  },
);
import {
  defaultSort,
  emptyFilters,
  filtersActive,
  matchesFilters,
  nextSort,
  sameSort,
  sortListings,
  sortPresets,
  type Filters,
  type Sort,
  type SortField,
} from "@/lib/filters";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

type View = "grid" | "table" | "map";

const PAGE_SIZE = 24;

export function ViewToggle({
  listings,
  feedDistricts = [],
}: {
  listings: Listing[];
  feedDistricts?: string[];
}) {
  const [view, setView] = useState<View>("grid");
  const [hideDelisted, setHideDelisted] = useState(true);
  const [sort, setSort] = useState<Sort>(defaultSort);
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Listing | null>(null);

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
  const applySort = (next: Sort) => {
    setSort(next);
    setPage(1);
  };
  // The dropdown reflects a matching preset, or a "Custom" slot when a table header
  // set a sort no preset covers (e.g. Rooms ascending).
  const activePreset = sortPresets.findIndex((p) => sameSort(p.sort, sort));
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
        <div className="flex border border-border">
          {(["grid", "table", "map"] as const).map((v) => (
            <button
              key={v}
              onClick={() => setView(v)}
              className={`px-3 py-1 text-sm font-semibold uppercase tracking-wide transition-colors ${
                view === v ? "bg-border text-bg" : "text-muted hover:text-text"
              }`}
            >
              {v}
            </button>
          ))}
        </div>

        {newCount > 0 && (
          <span className="inline-flex items-center gap-1.5 text-sm font-semibold uppercase tracking-wide text-muted">
            <span className="h-2.5 w-2.5 bg-signal" aria-hidden="true" />
            <span className="tnum text-text">{newCount}</span> new today
          </span>
        )}

        <div className="ml-auto flex flex-wrap items-center gap-x-4 gap-y-3">
          {delistedCount > 0 && (
            <label className="flex cursor-pointer select-none items-center gap-2 text-sm font-semibold uppercase tracking-wide text-muted">
              <input
                type="checkbox"
                checked={!hideDelisted}
                onChange={(e) => toggleHide(!e.target.checked)}
                className="h-4 w-4 accent-signal"
              />
              Show removed ({delistedCount})
            </label>
          )}
          <label className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-muted">
            Sort
            <select
              value={activePreset}
              onChange={(e) => applySort(sortPresets[Number(e.target.value)].sort)}
              className="border border-border bg-surface px-2 py-1 text-sm font-normal normal-case tracking-normal text-text focus:border-signal focus:outline-none"
            >
              {activePreset === -1 && (
                <option value={-1} disabled>
                  Custom
                </option>
              )}
              {sortPresets.map((preset, i) => (
                <option key={preset.label} value={i}>
                  {preset.label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>
      {view === "map" ? (
        // The map plots the whole filtered set, not one page.
        <ListingsMap listings={ordered} onSelect={setSelected} />
      ) : (
        <>
          <div className={view === "grid" ? "" : "hidden"}>
            <ListingsGrid listings={pageItems} onSelect={setSelected} />
          </div>
          <div className={view === "table" ? "" : "hidden"}>
            <ListingsTable
              listings={pageItems}
              onSelect={setSelected}
              sort={sort}
              onSortField={(field: SortField) => applySort(nextSort(sort, field))}
            />
          </div>
          <Pagination
            page={safePage}
            pageCount={pageCount}
            total={ordered.length}
            onPage={(p) => setPage(Math.min(Math.max(1, p), pageCount))}
          />
        </>
      )}

      {selected && <ListingDetail listing={selected} onClose={() => setSelected(null)} />}
    </>
  );
}
