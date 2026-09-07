"use client";

import { RelativeTime } from "@/components/RelativeTime";
import { isNew } from "@/lib/time";
import type { Listing } from "@/lib/types";

export function ListingsTable({
  listings,
  onSelect,
}: {
  listings: Listing[];
  onSelect: (listing: Listing) => void;
}) {
  if (listings.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-border py-16 text-center text-muted">
        Nothing on the radar yet.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded-xl border border-border">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-border bg-surface text-xs text-muted">
          <tr>
            <th className="py-3 pl-4 pr-2 font-medium" aria-label="Status"></th>
            <th className="px-3 py-3 font-medium">Title</th>
            <th className="px-3 py-3 font-medium">Rent (warm)</th>
            <th className="px-3 py-3 font-medium">Size</th>
            <th className="px-3 py-3 font-medium">Rooms</th>
            <th className="px-3 py-3 font-medium">Location</th>
            <th className="px-3 py-3 font-medium">Source</th>
            <th className="px-3 py-3 pr-4 font-medium">Added</th>
          </tr>
        </thead>
        <tbody>
          {listings.map((listing) => {
            const delisted = listing.delistedAt != null;
            const fresh = !delisted && isNew(listing.timestamp);
            return (
              <tr
                key={listing.id}
                className={`border-b border-border last:border-0 hover:bg-surface/60 ${
                  delisted ? "text-removed" : "text-text/90"
                }`}
              >
                <td className="py-3 pl-4 pr-2">
                  {fresh && (
                    <span
                      className="block h-2 w-2 rounded-full bg-signal"
                      aria-label="New today"
                      title="New today"
                    />
                  )}
                </td>
                <td className="max-w-xs px-3 py-3">
                  <button
                    type="button"
                    onClick={() => onSelect(listing)}
                    className="block max-w-full truncate text-left hover:text-signal hover:underline"
                  >
                    {listing.title}
                  </button>
                  {delisted && (
                    <span className="mt-0.5 inline-block text-xs text-removed">Entfernt</span>
                  )}
                </td>
                <td className="tnum px-3 py-3">
                  {listing.totalRent != null ? `€${listing.totalRent}` : "—"}
                  {listing.baseRent != null && (
                    <span className="ml-1 text-xs text-muted">/ €{listing.baseRent} kalt</span>
                  )}
                </td>
                <td className="tnum px-3 py-3">
                  {listing.size != null ? `${listing.size} m²` : "—"}
                </td>
                <td className="tnum px-3 py-3">{listing.rooms ?? "—"}</td>
                <td className="px-3 py-3">{listing.district ?? listing.location}</td>
                <td className="px-3 py-3">
                  <span className="rounded-full bg-surface-2 px-2 py-0.5 text-xs text-muted">
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
