"use client";

import type { Listing } from "@/lib/types";

export function ListingsTable({ listings }: { listings: Listing[] }) {
  if (listings.length === 0) {
    return <p className="text-zinc-500">No listings yet.</p>;
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-zinc-800">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-zinc-800 bg-zinc-900 text-zinc-400">
          <tr>
            <th className="px-4 py-3 font-medium">Title</th>
            <th className="px-4 py-3 font-medium">Rent</th>
            <th className="px-4 py-3 font-medium">Size</th>
            <th className="px-4 py-3 font-medium">Rooms</th>
            <th className="px-4 py-3 font-medium">Location</th>
            <th className="px-4 py-3 font-medium">Source</th>
          </tr>
        </thead>
        <tbody>
          {listings.map((listing) => {
            const delisted = listing.delistedAt != null;
            return (
            <tr
              key={listing.id}
              className={`border-b border-zinc-800 last:border-0 hover:bg-zinc-900/50 ${
                delisted ? "text-zinc-500" : ""
              }`}
            >
              <td className="px-4 py-3">
                <a
                  href={listing.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-400 hover:underline"
                >
                  {listing.title}
                </a>
                {delisted && (
                  <span className="ml-2 rounded bg-red-900/80 px-1.5 py-0.5 text-xs font-medium text-red-100">
                    Entfernt
                  </span>
                )}
              </td>
              <td className="px-4 py-3">
                {listing.totalRent != null ? `€${listing.totalRent}` : "—"}
              </td>
              <td className="px-4 py-3">
                {listing.size != null ? `${listing.size} m²` : "—"}
              </td>
              <td className="px-4 py-3">
                {listing.rooms ?? "—"}
              </td>
              <td className="px-4 py-3">
                {listing.district ?? listing.location}
              </td>
              <td className="px-4 py-3">
                <span className="rounded bg-zinc-800 px-2 py-0.5 text-xs">
                  {listing.source}
                </span>
              </td>
            </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
