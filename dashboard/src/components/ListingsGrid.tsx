"use client";

import Image from "next/image";

interface Listing {
  id: string;
  title: string;
  totalRent: number | null;
  size: number | null;
  rooms: number | null;
  location: string;
  district: string | null;
  source: string;
  url: string;
  thumbnailUrl: string | null;
  imageUrls: string[];
}

export function ListingsGrid({ listings }: { listings: Listing[] }) {
  if (listings.length === 0) {
    return <p className="text-zinc-500">No listings yet.</p>;
  }

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {listings.map((listing) => (
        <ListingCard key={listing.id} listing={listing} />
      ))}
    </div>
  );
}

function ListingCard({ listing }: { listing: Listing }) {
  const thumbnailUrl = listing.thumbnailUrl ?? listing.imageUrls?.[0] ?? null;
  const hasImage = !!thumbnailUrl;

  return (
    <a
      href={listing.url}
      target="_blank"
      rel="noopener noreferrer"
      className="group overflow-hidden rounded-lg border border-zinc-800 bg-zinc-900 transition-colors hover:border-zinc-700"
    >
      <div className="relative aspect-[4/3] bg-zinc-800">
        {hasImage ? (
          <Image
            src={thumbnailUrl}
            alt={listing.title}
            fill
            className="object-cover"
            sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-zinc-600">
            <svg
              className="h-12 w-12"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1.5}
                d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"
              />
            </svg>
          </div>
        )}
        <span className="absolute right-2 top-2 rounded bg-zinc-900/80 px-2 py-0.5 text-xs text-zinc-300">
          {listing.source}
        </span>
      </div>
      <div className="p-3">
        <h3 className="truncate text-sm font-medium text-zinc-100 group-hover:text-blue-400">
          {listing.title}
        </h3>
        <div className="mt-2 flex items-baseline gap-2">
          <span className="text-lg font-semibold text-zinc-100">
            {listing.totalRent != null
              ? `€${listing.totalRent > 10_000 ? listing.totalRent / 100 : listing.totalRent}`
              : "—"}
          </span>
          <span className="text-sm text-zinc-400">
            {listing.size != null ? `${listing.size} m²` : ""}
            {listing.rooms != null ? ` · ${listing.rooms} Zi.` : ""}
          </span>
        </div>
        <p className="mt-1 text-xs text-zinc-500">
          {listing.district ?? listing.location}
        </p>
      </div>
    </a>
  );
}
