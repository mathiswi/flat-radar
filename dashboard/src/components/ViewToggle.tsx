"use client";

import { useState } from "react";
import type { JSX } from "react";

type View = "grid" | "table";

export function ViewToggle({
  grid,
  table,
}: {
  grid: JSX.Element;
  table: JSX.Element;
}) {
  const [view, setView] = useState<View>("grid");

  return (
    <>
      <div className="mb-4 flex gap-2">
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
      </div>
      <div className={view === "grid" ? "" : "hidden"}>{grid}</div>
      <div className={view === "table" ? "" : "hidden"}>{table}</div>
    </>
  );
}
