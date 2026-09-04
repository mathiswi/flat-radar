"use client";

import { useState } from "react";
import { ListingsGrid } from "@/components/ListingsGrid";
import { ListingsTable } from "@/components/ListingsTable";
import type { Listing } from "@/lib/types";

type View = "grid" | "table";

export function ViewToggle({ listings }: { listings: Listing[] }) {
  const [view, setView] = useState<View>("grid");
  const [hideDelisted, setHideDelisted] = useState(false);

  const delistedCount = listings.filter((l) => l.delistedAt != null).length;
  const visible = hideDelisted
    ? listings.filter((l) => l.delistedAt == null)
    : listings;

  return (
    <>
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
              onChange={(e) => setHideDelisted(e.target.checked)}
              className="accent-zinc-600"
            />
            Hide removed ({delistedCount})
          </label>
        )}
      </div>
      <div className={view === "grid" ? "" : "hidden"}>
        <ListingsGrid listings={visible} />
      </div>
      <div className={view === "table" ? "" : "hidden"}>
        <ListingsTable listings={visible} />
      </div>
    </>
  );
}
