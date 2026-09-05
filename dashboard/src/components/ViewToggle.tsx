"use client";

import { useState } from "react";
import { ListingsGrid } from "@/components/ListingsGrid";
import { ListingsTable } from "@/components/ListingsTable";
import { FilterBar } from "@/components/FilterBar";
import { Pagination } from "@/components/Pagination";
import { emptyFilters, filtersActive, matchesFilters, type Filters } from "@/lib/filters";
import type { Listing } from "@/lib/types";

type View = "grid" | "table";

const PAGE_SIZE = 24;

export function ViewToggle({ listings }: { listings: Listing[] }) {
  const [view, setView] = useState<View>("grid");
  const [hideDelisted, setHideDelisted] = useState(false);
  const [filters, setFilters] = useState<Filters>(emptyFilters);
  const [page, setPage] = useState(1);

  // District options come from the data present (sorted, de-duped).
  // TODO: source these from the feeds config once that exists.
  const districts = Array.from(
    new Set(listings.map((l) => l.district).filter((d): d is string => !!d)),
  ).sort();

  const delistedCount = listings.filter((l) => l.delistedAt != null).length;

  const filtered = listings.filter(
    (l) => (!hideDelisted || l.delistedAt == null) && matchesFilters(l, filters),
  );

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const pageItems = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  // Any change to the result set jumps back to page 1 so the view isn't stranded.
  const applyFilters = (f: Filters) => {
    setFilters(f);
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
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <button
          onClick={() => setView("grid")}
          className={`rounded px-3 py-1.5 text-sm font-medium transition-colors ${
            view === "grid"
              ? "bg-zinc-700 text-zinc-100"
              : "bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          }`}
        >
          Grid
        </button>
        <button
          onClick={() => setView("table")}
          className={`rounded px-3 py-1.5 text-sm font-medium transition-colors ${
            view === "table"
              ? "bg-zinc-700 text-zinc-100"
              : "bg-zinc-800 text-zinc-400 hover:text-zinc-200"
          }`}
        >
          Table
        </button>
        {delistedCount > 0 && (
          <label className="ml-auto flex cursor-pointer select-none items-center gap-2 text-sm text-zinc-400">
            <input
              type="checkbox"
              checked={hideDelisted}
              onChange={(e) => toggleHide(e.target.checked)}
              className="accent-zinc-600"
            />
            Hide removed ({delistedCount})
          </label>
        )}
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
        total={filtered.length}
        onPage={(p) => setPage(Math.min(Math.max(1, p), pageCount))}
      />
    </>
  );
}
