"use client";

export function Pagination({
  page,
  pageCount,
  total,
  onPage,
}: {
  page: number;
  pageCount: number;
  total: number;
  onPage: (p: number) => void;
}) {
  if (total === 0) return null;

  const btn =
    "rounded bg-zinc-800 px-3 py-1.5 text-sm text-zinc-300 enabled:hover:text-zinc-100 disabled:opacity-40";

  return (
    <div className="mt-6 flex items-center justify-center gap-3 text-sm text-zinc-400">
      <button className={btn} onClick={() => onPage(page - 1)} disabled={page <= 1}>
        ← Prev
      </button>
      <span>
        Page {page} of {pageCount} · {total} result{total === 1 ? "" : "s"}
      </span>
      <button
        className={btn}
        onClick={() => onPage(page + 1)}
        disabled={page >= pageCount}
      >
        Next →
      </button>
    </div>
  );
}
