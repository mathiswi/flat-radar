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
    "rounded-md border border-border px-3 py-1.5 text-sm text-muted transition-colors enabled:hover:border-muted enabled:hover:text-text disabled:opacity-40";

  return (
    <div className="mt-8 flex items-center justify-center gap-3 text-sm text-muted">
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
