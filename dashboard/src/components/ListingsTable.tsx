"use client";

import { RelativeTime } from "@/components/RelativeTime";
import type { Sort, SortField } from "@/lib/filters";
import { formatIsoDate, isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

/** Header cells; those with a `field` sort on click. Status (no field) is inert. */
const COLUMNS: { label: string; field?: SortField; className: string }[] = [
  { label: "", className: "py-3 pl-4 pr-2" },
  { label: "Title", field: "title", className: "px-3 py-3" },
  { label: "Rent (warm)", field: "totalRent", className: "px-3 py-3" },
  { label: "Size", field: "size", className: "px-3 py-3" },
  { label: "Rooms", field: "rooms", className: "px-3 py-3" },
  { label: "Frei ab", field: "availableFrom", className: "px-3 py-3" },
  { label: "Location", field: "location", className: "px-3 py-3" },
  { label: "Source", field: "source", className: "px-3 py-3" },
  { label: "Added", field: "timestamp", className: "px-3 py-3 pr-4" },
];

export function ListingsTable({
  listings,
  onSelect,
  sort,
  onSortField,
}: {
  listings: Listing[];
  onSelect: (listing: Listing) => void;
  sort: Sort;
  onSortField: (field: SortField) => void;
}) {
  if (listings.length === 0) {
    return (
      <div className="border-2 border-dashed border-border py-16 text-center text-muted">
        Nothing on the radar yet.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto border-2 border-border">
      <table className="w-full text-left text-sm">
        <thead className="border-b-2 border-border bg-surface text-xs font-semibold uppercase tracking-wide text-muted">
          <tr>
            {COLUMNS.map((col) => {
              if (!col.field) {
                return <th key="status" className={col.className} aria-label="Status" />;
              }
              const active = sort.field === col.field;
              return (
                <th
                  key={col.field}
                  className={col.className}
                  aria-sort={active ? (sort.dir === "asc" ? "ascending" : "descending") : "none"}
                >
                  <button
                    type="button"
                    onClick={() => onSortField(col.field!)}
                    className="flex items-center gap-1 uppercase tracking-wide transition-colors hover:text-text"
                  >
                    {col.label}
                    <span aria-hidden="true" className={active ? "text-signal" : "invisible"}>
                      {active && sort.dir === "asc" ? "▲" : "▼"}
                    </span>
                  </button>
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {listings.map((listing) => {
            const delisted = listing.delistedAt != null;
            const fresh = !delisted && isNew(listing.timestamp);
            return (
              <tr
                key={listing.id}
                className={`border-b border-line last:border-0 hover:bg-surface-2 ${
                  delisted ? "text-removed" : "text-text"
                }`}
              >
                <td className="py-3 pl-4 pr-2">
                  {fresh && (
                    <span
                      className="block h-2.5 w-2.5 bg-signal"
                      aria-label="New today"
                      title="New today"
                    />
                  )}
                </td>
                <td className="max-w-xs px-3 py-3">
                  <button
                    type="button"
                    onClick={() => onSelect(listing)}
                    className="block max-w-full truncate text-left font-medium hover:text-signal hover:underline"
                  >
                    {listing.title}
                  </button>
                  {delisted && (
                    <span className="mt-0.5 inline-block text-xs uppercase tracking-wide text-removed">
                      Entfernt
                    </span>
                  )}
                </td>
                <td className="px-3 py-3">
                  <span className="tnum font-display font-bold">
                    {listing.totalRent != null ? `€${listing.totalRent}` : "—"}
                  </span>
                  {listing.baseRent != null && (
                    <span className="tnum ml-1 text-xs text-muted">/ €{listing.baseRent} kalt</span>
                  )}
                </td>
                <td className="tnum px-3 py-3">
                  {listing.size != null ? `${listing.size} m²` : "—"}
                </td>
                <td className="tnum px-3 py-3">{listing.rooms ?? "—"}</td>
                <td className="tnum px-3 py-3">{formatIsoDate(listing.availableFrom) ?? "—"}</td>
                <td className="px-3 py-3">{listing.district ?? listing.location}</td>
                <td className="px-3 py-3">
                  <span className="bg-surface-2 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-muted">
                    {listing.source}
                  </span>
                </td>
                <td className="px-3 py-3 pr-4">
                  <RelativeTime ms={listing.timestamp} className="tnum text-muted" />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
