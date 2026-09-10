"use client";

import { RelativeTime } from "@/components/RelativeTime";
import { formatIsoDate, isNew } from "@/lib/time";
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
            <th className="py-3 pl-4 pr-2" aria-label="Status"></th>
            <th className="px-3 py-3">Title</th>
            <th className="px-3 py-3">Rent (warm)</th>
            <th className="px-3 py-3">Size</th>
            <th className="px-3 py-3">Rooms</th>
            <th className="px-3 py-3">Frei ab</th>
            <th className="px-3 py-3">Location</th>
            <th className="px-3 py-3">Source</th>
            <th className="px-3 py-3 pr-4">Added</th>
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
